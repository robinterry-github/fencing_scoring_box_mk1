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
#define PISTES     30
#define INTERVAL   250
#define CLKINCR    1
#define HITTIMER   5
#define HITBLOCK   5
#define MAX_MSGIDX 9999

enum Dir
{
   DIR_NONE = 0,
   DIR_RX = 1,
   DIR_TX = 2
};

int  sock = 0;
int  quitThread = 0;
int  verbose = 0;
char ipAddr[IASIZE];
int  txPistes = PISTES;
int  cards = 0;
int  allTx = 0;
int  rxPiste = 1;

struct Box
{
   int                  msgIndex;
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
   int                  mins, secs, clock, hitTimer, hitBlock;
   int                  hitA, hitB;
   int                  scoreA, scoreB;
   int                  cardA, cardB;
   time_t               tm;
   struct tm            gmt;
};

struct Box boxList[PISTES+1];
struct Box *me, *meTx[PISTES], *meRx;
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
            b->sock = socket(AF_INET, SOCK_DGRAM, IPPROTO_IP);
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
               return 0;
            }

         case DIR_TX:
            /* Don't try to transmit to yourself */
            if (!b->localHost)
            {
               b->sock = socket(AF_INET, SOCK_DGRAM, IPPROTO_IP);
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
   else if (i <= txPistes)
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
               if (++b->msgIndex > MAX_MSGIDX)
               {
                  b->msgIndex = 0;
               }
               sprintf(txrxString, "%04d|%02dS%c%c:%02d:%02dT%02d:%02d:00P-:-C%s:%s",
                  b->msgIndex,
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
                     if (b->hitTimer > 0)
                     {
                        b->hitTimer -= CLKINCR;
                        if (b->hitTimer <= 0)
                        {
                           b->hitTimer = b->hitA = b->hitB = 0;
                           b->hitBlock = HITBLOCK;
                        }
                     }
                     else
                     {
                        if (b->hitBlock > 0)
                        {
                           b->hitBlock -= CLKINCR;
                           if (b->hitBlock < 0)
                           {
                              b->hitBlock = 0;
                           }
                        }
                        b->secs -= CLKINCR;
                        if (b->secs < 0)
                        {
                           b->secs += 60;

                           /* Has the timer expired? */
                           if (--(b->mins) < 0)
                           {
                              /* Restart the timer */
                              b->mins = 2;

                              /* Reset hits and scores */
                              b->hitA     = b->hitB     = 0;
                              b->scoreA   = b->scoreB   = 0;
                              b->hitTimer = b->hitBlock = 0;
                           }
                        }
                     }

                     /* Hit? */
                     if (
                           (b->mins == 0 && b->secs < 30)
                           ||
                           (b->mins == 2 && b->secs > 30)
                           ||
                           (b->hitTimer > 0)
                           ||
                           (b->hitBlock > 0))
                     {
                        /* No hit allowed */
                     }
                     else if (random()%15 == 0)
                     {
                        b->hitTimer = HITTIMER;

                        /* Trigger a hit */
                        switch (random()%3)
                        {
                           case 0:
                              if (b->scoreA < 15) 
                              {
                                 b->hitA = 1;
                                 b->hitB = 0;
                                 b->scoreA++;
                              }
                              break;

                           case 1:
                              if (b->scoreB < 15) 
                              {
                                 b->hitA = 0;
                                 b->hitB = 1;
                                 b->scoreB++;
                              }
                              break;

                           case 2:
                              if (b->scoreA < 14 && b->scoreB < 14)
                              {
                                 b->hitA = 1;
                                 b->hitB = 1;
                                 b->scoreA++;
                                 b->scoreB++;
                              }
                              break;
                        }
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
               int piste = atoi(&txrxString[5]);
               if (piste == rxPiste)
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
   printf("FencingBoxTest [-port P] [-tx] [-verbose] [-txpistes P] [-cards] [-alltx] [-rxpiste P]\n");
   printf("-port P      set IP network port to P\n");
   printf("-tx          enable TX testing\n");
   printf("-verbose     verbose operation\n");
   printf("-txpistes    number of transmitting pistes (between 1 and %d)\n", PISTES);
   printf("-cards       display a random setting for penalty cards and short-circuit indication\n");
   printf("-alltx       all pistes are transmit only - there is no receiving piste\n");
   printf("-rxpiste P   set the piste number that we expect to receive from (between 1 and %d)\n", PISTES);
   printf("             the test application will only support one receiving piste\n");
   printf("\n");
}

int main(int argc, char *argv[])
{
   int i, j;
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
      else if (!strcmp(argv[i], "-verbose"))
      {
         verbose = 1;
      }
      else if (!strcmp(argv[i], "-cards"))
      {
         cards = 1;
      }
      else if (!strcmp(argv[i], "-alltx"))
      {
         allTx = 1, rxPiste = 0;
      } 
      else if (!strcmp(argv[i], "-txpistes"))
      {
         if (++i >= argc)
	      {
	         printf("ERROR: missing argument to '-txPistes'\n");
	         printUsage();
	         return 1;
	      }
	      else if ((txPistes = atoi(argv[i])) < 1 || txPistes > PISTES)
	      {
	         printf("ERROR: invalid argument to '-txPistes'\n");
	         printUsage();
	         return 1;
	      }
      }
      else if (!strcmp(argv[i], "-rxpiste"))
      {
         if (++i >= argc)
	      {
	         printf("ERROR: missing argument to '-rxpiste'\n");
	         printUsage();
	         return 1;
	      }
	      else if ((rxPiste = atoi(argv[i])) < 1 || rxPiste > PISTES)
	      {
	         printf("ERROR: invalid argument to '-rxpiste'\n");
	         printUsage();
	         return 1;
	      }
         else if (allTx)
         {
            printf("You cannot have '-alltx' with '-rxpiste' - cancelling '-alltx'\n");
            allTx = 0;
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

   time(&tm);
   srandom((unsigned int) tm);

   if (allTx)
   {
      meRx = NULL;
   }
   else
   {
      meRx = &boxList[0];
   }
   for (i = 0; i < txPistes; i++)
   {
      meTx[i] = &boxList[i+(allTx ? 0:1)];
   }
   boxListIdx = txPistes+1;
   for (i = 0, j = 1; i < txPistes; i++, j++)
   {
      if (j == rxPiste)
      {
         j++;
      }
      meTx[i]->dir    = DIR_TX;
      meTx[i]->piste  = j;
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
      meTx[i]->hitA     = 0;
      meTx[i]->hitB     = 0;
      meTx[i]->hitTimer = 0;
      meTx[i]->hitBlock = 0;
      meTx[i]->scoreA   = 0; 
      meTx[i]->scoreB   = 0;
      if (cards)
      {
         meTx[i]->cardA    = random()%8;
         meTx[i]->cardB    = random()%8;
      }
      pthread_create(&meTx[i]->thread, NULL, txrxCommsThread, meTx[i]);
   }
   if (!allTx)
   {
      meRx->dir = DIR_RX;
      meRx->port = DEFPORT;
      meRx->piste = rxPiste;
      pthread_create(&meRx->thread, NULL, txrxCommsThread, meRx);
   }

   while (!quitThread)
   {
       sleep(1);
   }
   close(sock);

   return 0;
}
