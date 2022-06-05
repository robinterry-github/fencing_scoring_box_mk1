#include <stdio.h>      /* for printf() and fprintf() */
#include <signal.h>
#include <time.h>
#include <sys/socket.h> /* for socket(), connect(), sendto(), and recvfrom() */
#include <sys/types.h>  /* for pid_t */
#include <sys/ioctl.h>  /* for ioctl() */
#include <arpa/inet.h>  /* for sockaddr_in and inet_addr() */
#include <stdlib.h>     /* for atoi() and exit() */
#include <string.h>     /* for memset() */
#include <unistd.h>     /* for close() */
#include <net/if.h>     /* for struct ifreq */
#include <errno.h>
#include <pthread.h>

#define MAXSTRING  255     /* Longest string to send or receive */
#define DEFPORT    28888   /* Default port number */
#define IASIZE     20
#define RXBUFSIZE  (MAXSTRING+1) 
#define IPMC_PORT  DEFPORT 
#define IPMC_ADDR  "224.0.0.1"
#define BOXES      20
#define INTERVAL   250
#define CLKINCR    1 

enum Dir
{
   DIR_NONE = 0,
   DIR_RX = 1,
   DIR_TX = 2
};

int  sock = 0;
int  quitThread = 0;
int  noBCast = 1;
int  noMCast = 0;
int  verbose = 0;
char ipAddr[IASIZE];
int  pistes = BOXES;

struct Box
{
   char                 host[IASIZE];
   int                  port, idx;
   enum Dir             dir;
   int                  piste;
   int                  sock, rx;
   struct sockaddr_in   addr;
   struct ip_mreq       mc;
   pthread_t            thread;
   int                  localHost;
   char                 rxBuf[RXBUFSIZE];
   int                  rxPtr, rdPtr;
   int                  mins, secs, clock;
   int                  hitA, hitB;
   int                  scoreA, scoreB;
   int                  cardA, cardB;
   time_t               tm;
   struct tm            gmt;
};

struct Box boxList[BOXES+1];
struct Box *me, *meTx[3], *meRx;
int boxListIdx;
pthread_t bcThread;
int threadIdx = 0;
int txEnable = 0;
unsigned short broadcastPort;
void *txrxCommsThread(void *arg);

void Error(struct Box *b, char *host, char *errorMessage)
{
   char timeBuf[30];

   if (b)
   {
      time(&b->tm);
      gmtime_r(&b->tm, &b->gmt);
      strftime(timeBuf, 30, "%H:%M:%S", &b->gmt);
   }
   else
   {
      strcpy(timeBuf, "-");
   }
   printf("ERROR: (%s) %s:\"%s\", %s\n", host, timeBuf, errorMessage, strerror(errno));
}

int openConnection(struct Box *b)
{
   int reuseAddr = 1;
   char timeBuf[30];

   if (b->sock == 0)
   {
      switch (b->dir)
      {
         case DIR_RX:
            if (!noMCast)
            {
               b->sock = socket(AF_INET, SOCK_DGRAM, IPPROTO_IP);
            }
            else
            {
               b->sock = socket(PF_INET, SOCK_STREAM, IPPROTO_TCP);
            }
            if (b->sock < 0)
            {
               Error(b, b->host, "socket() failed");
               return -1;
            }
            else if (setsockopt(b->sock, SOL_SOCKET, SO_REUSEADDR, (void *) &reuseAddr,
                sizeof(reuseAddr)) < 0)
            {
               Error(b, b->host, "setsockopt() failed");
               return -1;
            }
            else
            {
               b->piste                 = -1;
               b->rxPtr = b->rdPtr      = 0;
               b->addr.sin_family       = AF_INET;
               b->addr.sin_addr.s_addr  = htonl(INADDR_ANY);
               b->addr.sin_port         = htons(b->port);
               bind(b->sock, (struct sockaddr *) &b->addr, sizeof(b->addr));
               if (!noMCast)
               {
                  strcpy(b->host, IPMC_ADDR);
                  b->mc.imr_multiaddr.s_addr = inet_addr(IPMC_ADDR);         
                  b->mc.imr_interface.s_addr = htonl(INADDR_ANY);         
                  if (setsockopt(b->sock, 
                     IPPROTO_IP, IP_ADD_MEMBERSHIP, &b->mc, sizeof(b->mc)) < 0)
                  {
                     Error(NULL, "localhost", "setsockopt() IP multicast failed");
                     return -1;
                  }
                  else
                  {
                     b->rx = b->sock;
                  }
                  time(&b->tm);
                  gmtime_r(&b->tm, &b->gmt);
                  strftime(timeBuf, 30, "%H:%M:%S", &b->gmt);
                  printf("%s: socket %02d joined RX multicast group %s:%d\n", timeBuf, b->rx, IPMC_ADDR, b->port);
               }
               else
               {
                  listen(b->sock, pistes);
                  b->rx = accept(b->sock, NULL, NULL);
               }
               return 0;
            }

         case DIR_TX:
            if (txEnable | !noMCast)
            {
               /* Don't try to transmit to yourself */
               if (!b->localHost)
               {
                  if (!noMCast) 
                  {
                     b->sock = socket(AF_INET, SOCK_DGRAM, IPPROTO_IP);
                  }
                  else
                  {
                     b->sock = socket(PF_INET, SOCK_STREAM, IPPROTO_TCP);
                  }
                  if (b->sock < 0)
                  {
                     Error(b, b->host, "socket failed");
                     return -1;
                  }
                  else if (setsockopt(b->sock, SOL_SOCKET, SO_REUSEADDR, (void *) &reuseAddr,
                     sizeof(reuseAddr)) < 0)
                  {
                     Error(b, b->host, "setsockopt() failed");
                     return -1;
                  }
                  else
                  {
                     b->addr.sin_family = AF_INET;
                     b->addr.sin_port = htons(b->port);
                     b->addr.sin_addr.s_addr = inet_addr(IPMC_ADDR); 
                     if (!noMCast)
                     {
                        strcpy(b->host, IPMC_ADDR);
                        b->mc.imr_multiaddr.s_addr = inet_addr(IPMC_ADDR);         
                        b->mc.imr_interface.s_addr = htonl(INADDR_ANY);         
                        if (setsockopt(b->sock, 
                           IPPROTO_IP, IP_ADD_MEMBERSHIP, &b->mc, sizeof(b->mc)) < 0)
                        {
                           Error(NULL, "localhost", "setsockopt() IP multicast failed");
                           return -1;
                        }
                        time(&b->tm);
                        gmtime_r(&b->tm, &b->gmt);
                        strftime(timeBuf, 30, "%H:%M:%S", &b->gmt);
                        printf("%s: socket %02d, piste %02d joined TX multicast group %s:%d\n", 
                           timeBuf, b->sock, b->piste, IPMC_ADDR, b->port);
                     }
                     else
                     {
                        if (connect(b->sock, (struct sockaddr *) &b->addr, sizeof(b->addr)) < 0)
                        {
                           Error(b, b->host, "connect failed");
                           return -1;
                        }
                     }
                  }
               }
               return 0;
            }
            break;

         case DIR_NONE:
            break;

         default:
            break;
      }
   }
   return -1;
}
   
int addBox(char *msg, int port)
{
   int i;
   struct Box b;

   b.piste = atoi(&msg[0]);
   b.localHost = 0;

   /* The direction is in reference to us, so
      we listen to DIR_RX, and transmit to DIR_TX */
   b.dir = (b.piste > 0) ? DIR_RX:DIR_TX;

   /* Have we received a message we ourselves sent? */
   if (!strcmp(ipAddr, &msg[3]))
   {
      return 0;
   }
   else
   {
      /* Have we already recorded this? */
      for (i = 0; i < boxListIdx; i++)
      {
         if (!strcmp(boxList[i].host, &msg[3]))
         {
            /* Same IP and no change of direction? */
            if (boxList[i].dir == b.dir)
            {
               return 0;
            }
            else
            {
               /* Overwrite this box, since the direction has changed */
               break;
            }
         }
      }
   }

   if (msg[2] != ':')
   {
      return -1;
   }
   else if (i <= pistes)
   {
      b.idx = i;
      strcpy(b.host, &msg[3]);
      printf("Adding box %s to %d\n", b.host, b.idx);
      b.port = port;
      if (pthread_create(&b.thread, NULL, txrxCommsThread, &b))
      {
         printf("Unable to create thread for %s\n", b.host);
         return -1;
      }
      else
      {
         boxList[i] = b;

         /* Is it a new box? */
         if (i == boxListIdx)
         {
            boxListIdx++;
         }
      }
      return 0;
   }
   else
   {
      return -1;
   }
}

void signalHandler(int sig)
{
   int i;

   switch (sig)
   {
      case SIGQUIT:
      case SIGINT:
         if (!quitThread)
         {
             close(sock);
             quitThread = 1;
             pthread_kill(bcThread, SIGQUIT);
             for (i = 0; i < boxListIdx; i++)
             {
                pthread_kill(boxList[i].thread, SIGQUIT);
             }
             exit(0);
         }
         break;

      default:
         printf("SIGNAL %d raised\n", sig);
         break;
   }
}

char *getIPAddr(int sock, struct in_addr *ip, struct in_addr *bc, char *addrBuf, char *iface)
{
    struct ifreq ifr;
    struct sockaddr_in *sa = (struct sockaddr_in *) &ifr.ifr_addr;
    struct in_addr mask;

    ifr.ifr_addr.sa_family = AF_INET;
    strncpy(ifr.ifr_name, iface, IFNAMSIZ-1);

    ioctl(sock, SIOCGIFADDR, &ifr);

    ip->s_addr = sa->sin_addr.s_addr;

    ifr.ifr_addr.sa_family = AF_INET;
    strncpy(ifr.ifr_name, iface, IFNAMSIZ-1);

    ioctl(sock, SIOCGIFNETMASK, &ifr);

    mask = sa->sin_addr;

    /* Calculate the broadcast address of the subnet */
    bc->s_addr = ip->s_addr & mask.s_addr;
    bc->s_addr |= ~mask.s_addr;
    
    strcpy(addrBuf, inet_ntoa(*ip));
    return addrBuf;
}

void processRx(struct Box *b)
{
   int   i, j;
   char  buf[MAXSTRING];

   if (b->rxPtr != b->rdPtr)
   {
      for (i = b->rdPtr, j = 0;;)
      {
         if (i == b->rxPtr)
         {
            break;
         }
         else
         {
            buf[j] = b->rxBuf[i];
            if (++i >= RXBUFSIZE)
            {
               i = 0;
            }
            if (buf[j] == '\n')
            {
               buf[j] = '\0';
               printf("%16s RX: %d [%s]\n", b->host, b->idx, buf);
               j = 0;
               b->rdPtr = i;
            }
            else if (j < 20)
            {
               j++;
            }
         }
      }
   }
}            

void *rxBroadcastThread(void *arg)
{
   char bcString[MAXSTRING+1];     /* Buffer for received string */
   int bcStringLen;                /* Length of received string */

   /* Child process - receive broadcast messages */
   while (!quitThread)
   {
      /* Receive a single datagram from the server */
      if ((bcStringLen = recvfrom(sock, bcString, MAXSTRING, 0, NULL, 0)) < 0)
      {
          if (errno != EBADF)
          {
             Error(NULL, "localhost", "recvfrom failed");
          }
          quitThread = 1;
      }
      else
      {
         bcString[bcStringLen] = '\0';
         printf("%16s RX: %s\n", noMCast ? "BROADCAST":"MULTICAST", bcString);    /* Print the received string */
         addBox(bcString, broadcastPort+1);
      }
  }
  return NULL;
}

char *getCard(int card)
{
   switch (card%8)
   {
      case 0:
      default:
         return "---";

      case 1:
         return "y--";

      case 2:
         return "-r-";

      case 3:
         return "yr-";

      case 4:
         return "--s";

      case 5:
         return "y-s";

      case 6:
         return "yr-";

      case 7:
         return "yrs";
   }
}

void *txrxCommsThread(void *arg)
{
   char txrxString[MAXSTRING+1]; /* Buffer for transmitted string */
   int i, ret, txrxStringLen;    /* Length of transmitted string */
   struct Box *b = (struct Box *) arg;
   struct timespec tm;
   char timeBuf[50];
   time_t tm1, tm2;

   tm.tv_sec  = INTERVAL/1000;
   tm.tv_nsec = (INTERVAL%1000)*1000*1000;

   tm1 = time(NULL);

   switch (b->dir)
   {
      case DIR_TX:
         if (openConnection(b))
            return NULL;
         else while (!quitThread)
         {
            /* Transmitting to a receiver */
            if (!b->localHost)
            {
               sprintf(txrxString, "%02dS%c%c:%02d:%02dT%02d:%02d:00P-:-C%s:%s",
                  b->piste, 
                  b->hitA ? 'h':'-',
                  b->hitB ? 'h':'-',
                  b->scoreA,
                  b->scoreB,
                  b->mins, b->secs,
                  getCard(b->cardA),
                  getCard(b->cardB));

               tm2 = time(NULL);
               if (tm2 > tm1)
               {
                  b->clock -= (tm2-tm1);
                  if (b->clock < 0)
                  {
                     b->clock += CLKINCR;
                     b->secs -= CLKINCR;
                     if (b->secs < 0)
                     {
                        b->secs += 60;
                        if (--(b->mins) < 0)
                           b->mins = 2;
                     }
                  }
                  tm1 = tm2;
               }
               txrxStringLen = strlen(txrxString);
               if (verbose)
               {
                  time(&b->tm);
                  gmtime_r(&b->tm, &b->gmt);
                  strftime(timeBuf, 30, "%H:%M:%S", &b->gmt);
                  printf("%s: sending  %s to   %s:%d on socket %d\n", 
                     timeBuf, txrxString, b->host, b->port, b->sock);
               }
               {
                  if ((ret = sendto(b->sock, txrxString, txrxStringLen, 0, 
                     (struct sockaddr *) &b->addr, sizeof(b->addr))) != txrxStringLen)
                  {
                     if (errno != EBADF && errno != ENOTCONN)
                     {
                        Error(b, b->host, "sendto failed");
                        quitThread = 1;
                        break;
                     }
                     else
                     {
                        printf("Receiver has dropped the connection\n");
                        b->dir = DIR_NONE;
                        close(b->sock);
                        b->sock = 0;
                        quitThread = 1;
                        break;
                     }
                  }
               }
               if (quitThread)
               {
                  break;
               }
            }
            nanosleep(&tm, NULL);
         }
         break;

      case DIR_RX:
         if (openConnection(b))
            return NULL;
         else while (!quitThread)
         {
            /* Receiving from a transmitter */
            txrxStringLen = recvfrom(b->rx, txrxString, MAXSTRING, 0, NULL, 0);
            if (txrxStringLen < 0)
            {
               printf("recvfrom returned %d\n", errno);
               if (errno != EBADF)
               {
                  Error(b, b->host, "recvfrom failed");
                  quitThread = 1;
               }
               else
               {
                  printf("Transmitter has dropped the connection\n");
                  b->dir = DIR_NONE;
                  close(b->sock);
                  b->sock = 0;
               }
            }
            else if (txrxStringLen > 0)
            {
               int piste = atoi(txrxString);
               for (i = 0; i < pistes; i++)
               {
                  if (meTx[i]->piste == piste)
                  {
                     break;
                  }
               }
               if (i >= pistes)
               {
                  time(&b->tm);
                  gmtime_r(&b->tm, &b->gmt);
                  strftime(timeBuf, 30, "%H:%M:%S", &b->gmt);
                  printf("%s: received %s from %s:%d on socket %d\n", 
                     timeBuf, txrxString, b->host, b->port, b->rx);
                  for (i = 0; i < txrxStringLen; i++)
                  {
                     b->rxBuf[b->rxPtr] = txrxString[i];
                     if (++b->rxPtr >= RXBUFSIZE)
                     {
                        b->rxPtr = 0;
                     }
                     processRx(b);
                  }
               }
            }
         }
         break;

      case DIR_NONE:
         break;

      default:
         break;
  }
  return NULL;
}

void printUsage(void)
{
   printf("FencingBoxTest [-port P] [-tx] [-bc] [-nomc] [-verbose] [-pistes P]\n");
   printf("-port P    set IP network port to P\n");
   printf("-tx        enable TX testing\n");
   printf("-bc        enable broadcast of IP messages\n");
   printf("-nomc      disable IP multicast\n");
   printf("-verbose   verbose operation\n");
   printf("-pistes    number of pistes (between 1 and %d)\n", BOXES);
   printf("\n");
}

int main(int argc, char *argv[])
{
   int i;
   struct sockaddr_in broadcastAddr; /* Broadcast Address */
   int broadcastLen;                 /* Length of broadcast structure */
   int broadcastPermission;          /* Socket opt to set permission to broadcast */
   int reuseAddr;                    /* Socket opt to reuse the address */
   char sendString[MAXSTRING+1];     /* Buffer for sent string */
   int sendStringLen;                /* Length of sent string */
   struct in_addr ip, bc;
   struct ip_mreq mc;
   time_t tm;

   broadcastPort = DEFPORT;

   for (i = 1; i < argc; i++)
   {
      if (!strcmp(argv[i], "--help"))
      {
         printUsage();
         return 0;
      }
      else if (!strcmp(argv[i], "-port"))
      {
         if (++i >= argc)
	      {
	         printf("ERROR: missing argument to '-port'\n");
	         printUsage();
	         return 1;
	      }
	      else
	      {
            broadcastPort = atoi(argv[i]);
	      }
      }
      else if (!strcmp(argv[i], "-tx"))
      {
         txEnable = 1;
      }
      else if (!strcmp(argv[i], "-bc"))
      {
         noBCast = 0;
      }
      else if (!strcmp(argv[i], "-nomc"))
      {
         noMCast = 1;
      }
      else if (!strcmp(argv[i], "-verbose"))
      {
         verbose = 1;
      }
      else if (!strcmp(argv[i], "-pistes"))
      {
         if (++i >= argc)
	      {
	         printf("ERROR: missing argument to '-pistes'\n");
	         printUsage();
	         return 1;
	      }
	      else if ((pistes = atoi(argv[i])) < 1 || pistes > BOXES)
	      {
	         printf("ERROR: invalid argument to '-pistes'\n");
	         printUsage();
	         return 1;
	      }
      }
      else
      {
         printf("ERROR: unknown argument\n");
	      printUsage();
	      return 1;
      }
   }
   memset(boxList, 0, sizeof(boxList));

   signal(SIGQUIT, signalHandler);
   signal(SIGINT,  signalHandler);

   /* Create a datagram socket using UDP */
   if ((sock = socket(PF_INET, SOCK_DGRAM, IPPROTO_UDP)) < 0)
   {
      Error(NULL, "localhost", "socket() failed");
      exit(1);
   }

   memset(&broadcastAddr, 0, sizeof(broadcastAddr));   /* Zero out structure */
   if (noMCast)
   {
      /* Get IP address and broadcast address of the interface */
      getIPAddr(sock, &ip, &bc, ipAddr, "en1");

      /* Construct bind structure */
      broadcastAddr.sin_family = AF_INET;                 /* Internet address family */
      broadcastAddr.sin_addr.s_addr = INADDR_ANY;         /* Any incoming interface */
      broadcastAddr.sin_port = htons(broadcastPort);      /* Broadcast port */

      /* Set socket to allow broadcast */
      broadcastPermission = reuseAddr = 1;
      if (setsockopt(sock, SOL_SOCKET, SO_REUSEADDR, (void *) &reuseAddr, sizeof(reuseAddr)) < 0)
      {
         Error(NULL, "localhost", "setsockopt() failed 1");
         exit(1);
      }

      /* Bind to the broadcast port */
      bind(sock, (struct sockaddr *) &broadcastAddr, sizeof(broadcastAddr));
    
      if (setsockopt(sock, SOL_SOCKET, SO_BROADCAST, (void *) &broadcastPermission, 
                sizeof(broadcastPermission)) < 0)
      {
         Error(NULL, "localhost", "setsockopt() failed 2");
         exit(1);
      }
      else
      {
         printf("Joined multicast group %s on port %d\n", IPMC_ADDR, IPMC_PORT);
      }
   }

   if (!noMCast)
   {
      time(&tm);
      srandom((unsigned int) tm);

      meRx = &boxList[0];
      for (i = 0; i < pistes; i++)
      {
         meTx[i] = &boxList[1+i];
      }
      boxListIdx = pistes+1;
      for (i = 0; i < pistes; i++)
      {
         meTx[i]->dir    = DIR_TX;
         meTx[i]->piste  = 2+i;
         meTx[i]->port   = DEFPORT;
         meTx[i]->clock  = 0;
         meTx[i]->secs   = (random()%(60/CLKINCR))*CLKINCR;
         if (meTx[i]->secs == 0)
         {
            meTx[i]->mins = 1+(random()%3);
         }
         else
         {
            meTx[i]->mins = random()%3;
         }
         switch (random()%6)
         {
            case 0:
            case 2:
            case 4:
               meTx[i]->hitA = 0;
               meTx[i]->hitB = 0;
               break;

            case 1:
               meTx[i]->hitA = 1;
               meTx[i]->hitB = 0;
               break;

            case 3:
               meTx[i]->hitA = 0;
               meTx[i]->hitB = 1;
               break;

            case 5:
               meTx[i]->hitA = 1;
               meTx[i]->hitB = 1;
               break;
         }
         meTx[i]->scoreA = random()%16;
         meTx[i]->scoreB = random()%16;
         meTx[i]->cardA  = random()%8;
         meTx[i]->cardB  = random()%8;
         pthread_create(&meTx[i]->thread, NULL, txrxCommsThread, meTx[i]);
      }
      meRx->dir = DIR_RX;
      meRx->port = DEFPORT;
      meRx->piste = 20;
      pthread_create(&meRx->thread, NULL, txrxCommsThread, meRx);
   }
   else
   {
      /* Start receive broadcast thread */
      if (pthread_create(&bcThread, NULL, rxBroadcastThread, NULL))
      {
         printf("Unable to create receive broadcast thread\n");
         exit(1);
      }
      me = &boxList[0];
      boxListIdx = 1;

      /* Parent process - send regular broadcast */
      if (txEnable)
      {
         /* Pretend to be a transmitter on piste 8 */
         sprintf(sendString, "08:%s", ipAddr);
         me->dir = DIR_TX;
      }
      else
      {
         /* Be a receiver */
         sprintf(sendString, "00:%s", ipAddr);
         me->dir = DIR_RX;
      }
      strcpy(me->host, ipAddr);
      me->localHost = 1;
      me->port = broadcastPort+1; 
      me->idx = 0;

      pthread_create(&me->thread, NULL, txrxCommsThread, me);
      sendStringLen = strlen(sendString);
      broadcastAddr.sin_addr.s_addr = bc.s_addr;
   }

   /* Transmit broadcast thread */
   while (!quitThread)
   {
       if (noBCast)
       {
          sleep(1);
       }
       else
       {
          /* Broadcast sendString in datagram to clients every second */
          if (sendto(sock, sendString, sendStringLen, 0, 
               (struct sockaddr *) &broadcastAddr, sizeof(broadcastAddr)) != sendStringLen)
          {
             Error(NULL, "localhost", "sendto() sent a different number of bytes than expected");
             quitThread = 1;
          }
          else
          {
             printf("%16s TX: %s, length %d\n", noMCast ? "BROADCAST":"MULTICAST", sendString, sendStringLen);
             sleep(1);   /* Avoids flooding the network */
          }
      }
   }
   printf("Transmit broadcast thread ending\n");
   close(sock);

   return 0;
}
