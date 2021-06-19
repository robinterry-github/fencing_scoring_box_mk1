//===========================================================================//
//                                                                           //
//  Desc:    Arduino Code to implement a fencing scoring apparatus           //
//  Dev:     Wnew                                                            //
//  Date:    Nov    2012                                                     //
//  Updated: Sept   2015                                                     //
//  Updated: May 23 2021 Robin Terry, Skipton, UK                            //
//                                                                           //
//  Notes:   1. Basis of algorithm from digitalwestie on github. Thanks Mate //
//           2. Used uint8_t instead of int where possible to optimise       //
//           3. Set ADC prescaler to 16 faster ADC reads                     //
//                                                                           //
//  To do:   1. Could use shift reg on lights and mode LEDs to save pins     //
//           2. Use interrupts for buttons                                   //
//           3. Implement short circuit LEDs (already provision for it)      //
//           4. Set up debug levels correctly                                //
//                                                                           //     
//  RT changes (thanks to both wnew and digitalwestie)                       //
//           1. Added support for TM1637 4-digit 7-segment display           //   
//           2. Added 74HC595 shift register for yellow/red card lights      //          
//           3. Added IR support (Hobby Components IR handset)               //
//           4. Added timers and priority                                    //
//           5. Added bout mode and sparring mode                            //
//           6. Added short-circuit support (both LEDs and display)          //
//           7. Added off-target hit support for foil using 7-segment        //
//           8. Updated sabre timings to latest standard                     //
//           9. Added check for 8 sabre hits in a bout                       //
//          10. Added a up/down-counting stopwatch                           //
//          11. Stores the selected weapon type in EEPROM                    //
//          12. Stores the current mode (spar/bout/stopwatch) in EEPROM      //
//          13. Added in the serial port indicator (for external indication) //
//          14. The timer shows 1/100 sec in last 9 seconds                  //
//===========================================================================//

//============
// #defines
//============
//#define DEBUG_L1               // level 1 debug
//#define DEBUG_L2               // level 2 debug
//#define DEBUG_L3               // level 3 debug
//#define DEBUG_L4               // level 4 debug
//#define DEBUG_L5               // level 5 debug
//#define DEBUG_L6               // level 6 debug
//#define DEBUG_L7               // level 7 debug
//#define DEBUG_IR               // debug the IR reception
//#define OFFTARGET_LEDS         // define this if you have fitted the discrete off-target LEDs
#define DISP_IR_CARDS_BOX        // define this to enable 7-segment display, IR control and card LEDs -
                                 // for a simple hit indicator only box, you can undefine this
#define FREQUENT_IRPOLL          // define this to increase the amount of IR polling         
#define SERIAL_INDICATOR         // Send serial data out to an indicator application

#ifdef DISP_IR_CARDS_BOX
#define LOW_POWER                // support low-power for battery operation
#define IRLIB2                   // use IRLib2 instead of IRRemote (IRLib2 is better, but bigger)

#ifdef IRLIB2
// IR receiver frame timeout
// You might need to modify this for different IR handsets
#define IR_FRAMETIMEOUT 5000
#endif

#define STOPWATCH                // enable the stopwatch
#define EEPROM_STORAGE           // use EEPROM for storing values over power-off
//#define SPAR_INCR_SCORE        // automatically increment score after a hit in sparring mode
#endif

#define BUZZERTIME     (1000)    // length of time the buzzer is kept on after a hit (ms)
#define TESTPOINTTIME  (500)     // length of time the buzzer and lights are kept on when point testing (ms)
#define LIGHTTIME      (3000)    // length of time the lights are kept on after a hit (ms)
#define BAUDRATE       (230400)  // baud rate of the serial debug interface
#define ONESEC         (1000)
#define HUNDSEC        (10)
#define ONESEC_US      (1000000)
#define BUTTONSCAN     (200)              // button scan period (ms)
#define BUTTONDEBOUNCE (BUTTONSCAN*10)    // button debounce (in ms, but a whole number of scan periods)
#define BOUTTIME       (180)     // 3 minutes bout time
#define PRITIME        (60)      // 1 minute priority time
#define BREAKTIME      (60)      // 1 minute break time
#define HITDISPTIME    (200)     // hit display flash time (ms)
#define SCOREFLASHTIME (1000)    // score flashup display time (ms)
#define MAXSCORE       (99)
#define MAXSHORTCIRC   (3000)    // Short circuit persist time (ms)
#define MAXSABREHITS   (8)       // If a sabre fencer makes 8 hits in one bout, stop the bout
#define DIMDELAY       (5UL*60UL*1000UL)  // Delay before starting to dim the LED display (ms)
#define DIMINTERVAL    (500)              // Interval between LED display dimming cycle steps (ms)
#define MAXSTOPWATCH   ((60UL*60UL)-1)    // Maximum stopwatch time (59:59)

#ifdef EEPROM_STORAGE
#define NV_WEAPON      (16)
#define NV_STATE       (17)
#endif

#define FENCER_A       (0)
#define FENCER_B       (1)

#define A_YELLOW       (0x80)
#define A_RED          (0x40)
#define B_YELLOW       (0x20)
#define B_RED          (0x10)
#define A_SHORT        (0x08)
#define B_SHORT        (0x04)
#define A_ALL          (A_YELLOW | A_RED | A_SHORT)
#define B_ALL          (B_YELLOW | B_RED | B_SHORT)

#ifdef DISP_IR_CARDS_BOX
#ifdef IRLIB2
#include "IRLibRecv.h"
#include "IRLibDecodeBase.h"
// Change the protocol header for different IR handsets
#include "IRLib_P01_NEC.h"
#include "IRLibCombo.h"
#else
#include "IRremote.h"
#endif
#endif

#ifdef DISP_IR_CARDS_BOX
// 7-segment display
#include "TM1637Display.h"

#ifdef LOW_POWER
// Low-power support
#include "LowPower.h"
#endif
#endif

#ifdef EEPROM_STORAGE
// EEPROM non-volatile storage support
#include "EEPROM.h"
#endif

// Various debug levels
#if defined(DEBUG_L1) || defined(DEBUG_L2) \
 || defined(DEBUG_L3) || defined(DEBUG_L4) \
 || defined(DEBUG_L5) || defined(DEBUG_L6) \
 || defined(DEBUG_L7) || defined(DEBUG_IR)
#define DEBUG_ALL
#endif

//============
// Pin Setup
//============

// GPIO 0 unused
// GPIO 1 unused
const uint8_t  clockPin      =  2;    // 74HC595 clock pin (11)
const uint8_t  buttonPin     =  3;    // Button
const uint8_t  irrecvPin     =  4;    // IR receive
const uint8_t  buzzerPin     =  5;    // buzzer pin
const uint8_t  clkPin        =  6;    // 7-seg display CLK pin
const uint8_t  dioPin        =  7;    // 7-seg display DIO pin
const uint8_t  latchPin      =  8;    // 74HC595 latch pin (12)
const uint8_t  onTargetA     =  9;    // On  Target A Light (red)
const uint8_t  onTargetB     = 10;    // On  Target B Light (green)
#ifdef OFFTARGET_LEDS
const uint8_t  offTargetA    = 11;    // Off Target A Light
const uint8_t  offTargetB    = 12;    // Off Target B Light
#endif
const uint8_t  dataPin       = 13;    // 74HC595 data pin (14)
const uint8_t  groundPinA    = A0;    // Fencer A pin C - Analog
const uint8_t  weaponPinA    = A1;    // Fencer A pin B - Analog
const uint8_t  lamePinA      = A2;    // Fencer A pin A - Analog
const uint8_t  groundPinB    = A3;    // Fencer B pin C - Analog
const uint8_t  weaponPinB    = A4;    // Fencer B pin B - Analog
const uint8_t  lamePinB      = A5;    // Fencer B pin A - Analog

#ifdef DISP_IR_CARDS_BOX
// IR object
IRrecv irRecv(irrecvPin);
#ifdef IRLIB2
IRdecode irDecode;
uint16_t irBuffer[RECV_BUF_LENGTH];
#endif
#endif

//=========================
// Values of analogue reads
//=========================
int weapon[2] = { 0, 0 };
int lame[2]   = { 0, 0 };
int ground[2] = { 0, 0 };

//=======================
// Depress and timeouts
//=======================
long depressTime[2]  = { 0, 0 };
long shortCircuit[2] = { 0, 0 };
bool scDisplay[2] = { false, false };
#ifdef IRDEBOUNCE
long irDebounce      = 0;
#endif
bool lockedOut       = false;
long timer           = 0;
long timerMs         = 0;
long timerMins       = 0;
long timerSecs       = 0;
long timerHund       = 0;
long timerInterval   = ONESEC;
bool timerStart      = false;
bool timerLast9s     = false;
long timerMax        = BOUTTIME;
long hitDisplayTimer = 0;
long hitDisplayTime  = HITDISPTIME;
long resetTimer      = 0;
int  currentFencer   = 0;
long swMins          = 0;
long swSecs          = 0;

// Total score for all bouts since restarting the bout
int  score[2]        = { 0, 0 };

/* Score for just this bout - this can go negative
   if the referee removes points from the fencer */
int  scoreThisBout[2]= { 0, 0 };
int  cardLeds        = 0;
bool cardLedUpdate   = true;
long buttonScan      = 0;
bool buttonPressed   = false;
long buttonDebounce  = 0;
bool modeChange      = false;
bool scoreFlash      = false;
long scoreFlashTimer = 0;
bool maxSabreHits[2] = { false, false };

// Yellow and red cards
bool red[2]          = { false, false };
bool yellow[2]       = { false, false };

//==========================
// Lockout & Depress Times
//==========================
// the lockout time between hits for foil is 300ms +/-25ms
// the minimum amount of time the tip needs to be depressed for foil 14ms +/-1ms
// the lockout time between hits for epee is 45ms +/-5ms (40ms -> 50ms)
// the minimum amount of time the tip needs to be depressed for epee 2ms
// the lockout time between hits for sabre is 170ms +/-10ms (new 2016 rule, was 120ms)
// the minimum amount of time the tip needs to be depressed for sabre 0.1ms -> 1ms
// These values are stored as micro seconds for more accuracy
//                         foil    epee   sabre
const long lockout [] = {300000,  45000, 170000};  // the lockout time between hits
const long depress [] = { 14000,   2000,   1000};  // the minimum amount of time the tip needs to be depressed

//===========
// Hit states
//===========
bool depressed[]      = { false, false };
bool released[]       = { true,  true  };
bool hitOnTarg[]      = { false, false };
bool hitOffTarg[]     = { false, false };
bool lockOutOffTarg[] = { true, true };

//===============
// State machines
//===============
enum Priority
{
    PRI_IDLE,
    PRI_CHOOSE,
    PRI_SELECTED,
    PRI_END
};

enum TimeState
{
   TIM_STOPPED,
   TIM_BREAK,
   TIM_BOUT,
   TIM_PRIORITY,
   TIM_STOPWATCH
};

enum BoutState
{
   STA_NONE,
   STA_SPAR,
   STA_CONTINUE,
   STA_BREAK,
   STA_TP_BREAK,
   STA_STARTBOUT,
   STA_BOUT,
   STA_TP_BOUT,
   STA_PRIORITY,
   STA_ENDPRI,
   STA_TP_PRI,
   STA_STOPWATCH
};

enum HitDisplay
{
   HIT_IDLE,
   HIT_ON,
   HIT_OFF
};

enum Reset
{
   RES_IDLE,
   RES_BUZZER,
   RES_TESTPOINT,
   RES_LIGHTS,
   RES_SHORT,
   RES_OFF
};

enum Weapon
{
   NONE    = -1,
   FOIL    = 0,
   EPEE    = 1,
   SABRE   = 2
};

enum Key
{
  K_NONE,
  K_BREAK,
  K_RESET_TIMER,
  K_START_BOUT,
  K_PRIORITY,
  K_CLEAR_SCORES,
  K_WIND_BACK,
  K_WIND_FORWARD,
  K_LEFT,
  K_RIGHT,
  K_OK,
  K_CARD_A,
  K_CARD_B,
  K_DEC_SCORE,
  K_INC_SCORE,
  K_CLEAR_CARDS
};

enum Disp
{
  DISP_NONE,
  DISP_SCORE,
  DISP_TIME,
  DISP_PRI
};

enum Hit
{
   HIT_NONE,
   HIT_ONTARGET,
   HIT_OFFTARGET
};

enum StopWatchCount
{
   SW_UP,
   SW_DOWN,
   SW_END
};

enum StopWatchEdit
{
   SW_NONE,
   SW_MINS,
   SW_SECS
};

#define DIM_BRIGHTEST     (7)
#define DIM_DIMMEST       (0)
#define MAX_DIMCYCLE      (12)

#define LED_NONE          (0)
#define LED_YELLOW        (1)
#define LED_RED           (2)
#define LED_BOTH          (3)

// Weapon type defaults to foil
Weapon weaponType = FOIL;

//===============
// State machines
//===============
BoutState       boutState         = STA_NONE;
TimeState       timeState         = TIM_STOPPED;
HitDisplay      hitDisplay        = HIT_IDLE;
Priority        priState          = PRI_IDLE;
Reset           resetState        = RES_IDLE;
Key             lastKey           = K_NONE;
Disp            currentDisp       = DISP_NONE;
#ifdef STOPWATCH
StopWatchCount  swCount           = SW_UP; 
StopWatchEdit   swEdit            = SW_NONE;
#endif    
uint8_t         dimSetting        = DIM_BRIGHTEST;
uint8_t         dimCycle          = 0;
unsigned long   dimTimer          = 0;
unsigned long   lastKeyCode       = 0;
uint8_t         priFencer         = 0; 
bool            disableScore      = false;
Hit             hitDisplayFlag[2] = { HIT_NONE, HIT_NONE };
uint8_t         lastHit           = 0;
int             ledFlag[2]        = { LED_NONE, LED_NONE };
const int       ledBits[][4]      = 
{
   { 0, A_YELLOW, A_RED, A_YELLOW | A_RED },
   { 0, B_YELLOW, B_RED, B_YELLOW | B_RED }
};

#ifdef DEBUG_L3
long now, loopCount = 0;
#endif

// 7-segment display
#ifdef DISP_IR_CARDS_BOX
TM1637Display disp = TM1637Display(clkPin, dioPin);
#endif

// Patterns
#ifdef DISP_IR_CARDS_BOX
const uint8_t priDisp[] = 
{
  SEG_A | SEG_G | SEG_D,
  SEG_A | SEG_G | SEG_D
};

// Display the weapon (foil, epee, sabre) on the 7-segment display
const uint8_t weaponDisp[][4] =
{
   // foil
   {
      SEG_A | SEG_G | SEG_E | SEG_F,
      SEG_A | SEG_B | SEG_C | SEG_D | SEG_E | SEG_F,
      SEG_E | SEG_F,
      SEG_D | SEG_E | SEG_F
   },
   
   // epee
   {
      SEG_A | SEG_D | SEG_E | SEG_F | SEG_G,
      SEG_A | SEG_B | SEG_G | SEG_F | SEG_E,
      SEG_A | SEG_D | SEG_E | SEG_F | SEG_G,
      SEG_A | SEG_D | SEG_E | SEG_F | SEG_G,
   },
   
   // sabre
   {
      SEG_A | SEG_C | SEG_D | SEG_F | SEG_G,
      SEG_A | SEG_B | SEG_C | SEG_E | SEG_F | SEG_G,
      SEG_C | SEG_D | SEG_E | SEG_F | SEG_G,
      SEG_E | SEG_G
   }
};

const uint8_t sparDisp[] =
{
   SEG_A | SEG_C | SEG_D | SEG_F | SEG_G,
   SEG_A | SEG_B | SEG_E | SEG_F | SEG_G,
   SEG_A | SEG_B | SEG_C | SEG_E | SEG_F | SEG_G,
   SEG_E | SEG_G
};

const uint8_t boutDisp[] =
{
   SEG_C | SEG_D | SEG_E | SEG_F | SEG_G,
   SEG_C | SEG_D | SEG_E | SEG_G,
   SEG_C | SEG_D | SEG_E,
   SEG_D | SEG_E | SEG_F | SEG_G
};

const uint8_t prioDisp[] =
{
   SEG_A | SEG_B | SEG_E | SEG_F | SEG_G,
   SEG_E | SEG_G,
   SEG_E,
   SEG_C | SEG_D | SEG_E | SEG_G
};

const uint8_t restDisp[]
{
  SEG_E | SEG_G,
  SEG_A | SEG_D | SEG_E | SEG_F | SEG_G,
  SEG_A | SEG_C | SEG_D | SEG_F | SEG_G,
  SEG_D | SEG_E | SEG_F | SEG_G
};

const uint8_t sparNoHit[] =
{
   SEG_G, SEG_G, SEG_G, SEG_G
};

const uint8_t sparHit[][2] =
{
   // Fencer A spar hit
   { 
      SEG_B | SEG_C | SEG_E | SEG_F | SEG_G, 0
   },

   // Fencer B spar hit
   {
      0, SEG_B | SEG_C | SEG_E | SEG_F | SEG_G
   }
};

const uint8_t sparOffHit[][2] =
{
   // Fencer A spar off-target hit
   { 
      SEG_C | SEG_D | SEG_E | SEG_G, 0
   },

   // Fencer B spar off-target hit
   {
      0, SEG_C | SEG_D | SEG_E | SEG_G
   }
};

const uint8_t shortDisp[] =
{
   SEG_A | SEG_C | SEG_D | SEG_F | SEG_G,
   SEG_A | SEG_D | SEG_E | SEG_F
};

const uint8_t stopWatchDisp[] =
{
   SEG_A | SEG_C | SEG_D | SEG_F | SEG_G,
   SEG_D | SEG_E | SEG_F | SEG_G,
   SEG_A | SEG_B | SEG_C | SEG_D | SEG_E | SEG_F,
   SEG_A | SEG_B | SEG_E | SEG_F | SEG_G
};

const uint8_t dimDisp[MAX_DIMCYCLE][4] =
{
   {
      SEG_A, 0, 0, 0
   },
   {
      0, SEG_A, 0, 0
   },
   {
      0, 0, SEG_A, 0
   },
   {
      0, 0, 0, SEG_A
   },
   {
      0, 0, 0, SEG_B
   },
   {
      0, 0, 0, SEG_C
   },
   {
      0, 0, 0, SEG_D
   },
   {
      0, 0, SEG_D, 0
   },
   {
      0, SEG_D, 0, 0
   },
   {
      SEG_D, 0, 0, 0
   },
   {
      SEG_E, 0, 0, 0
   },
   {
      SEG_F, 0, 0, 0
   }
};

const uint8_t blankDisp[] = 
{ 
   0, 0 
};

const uint8_t touchDisp[][2] =
{
   {
      SEG_A | SEG_B | SEG_C | SEG_D,
      SEG_G,
   },
   {
      SEG_G,
      SEG_A | SEG_D | SEG_E | SEG_F
   }
};
#endif

bool inBout()
{
   return ((boutState != STA_SPAR) && (boutState != STA_BREAK) && (boutState != STA_STOPWATCH)) ? true:false;
}

bool inBoutOrBreak()
{
   return ((boutState > STA_SPAR) && (boutState != STA_STOPWATCH)) ? true:false;
}

bool inBoutOrSpar()
{
   return ((boutState != STA_BREAK) && (boutState != STA_STOPWATCH)) ? true:false;
}

bool inSpar()
{
   return (boutState == STA_SPAR) ? true:false;
}

bool inBreak()
{
   return (boutState == STA_BREAK) ? true:false;
}

bool inBoutStart()
{
   return (boutState == STA_STARTBOUT) ? true:false;
}

bool inStopWatch()
{
   return (boutState == STA_STOPWATCH) ? true:false;
}

bool inTestPoint()
{
   return (boutState == STA_TP_BOUT || boutState == STA_TP_PRI || boutState == STA_TP_BREAK) ? true:false;
}

bool pointCanBeTested()
{
   return inTestPoint() || inBreak() || inBoutStart();
}

bool isHit()
{
   if (hitOnTarg[FENCER_A] || hitOnTarg[FENCER_B])
   {
      return lockedOut ? true:false;
   }

   // Signal an off-target hit for foil
   else if (weaponType == FOIL)
   {
      if (hitOffTarg[FENCER_A] || hitOffTarg[FENCER_B])
      {
          return lockedOut ? true:false;
      }
   }
   return false;
}

bool scDisplayActive()
{
   return (shortCircuit[FENCER_A] || shortCircuit[FENCER_B]) ? true:false;
}

bool priorityInactive()
{
   if (priState == PRI_END)
   {
      priState = PRI_IDLE;
   }
   return (priState == PRI_IDLE) ? true:false;
}

//===================
// Display the weapon (foil, epee, sabre) on the 7-segment
//===================
void displayWeapon()
{
   displayWeapon(true);
}

void displayWeapon(bool lights)
{
#ifdef DISP_IR_CARDS_BOX
   disp.clear();
   setBrightness(DIM_BRIGHTEST);
   disp.setSegments(weaponDisp[weaponType], 4, 0);
#endif

   // Turn all lights on
   if (lights)
   {
      digitalWrite(onTargetA,  HIGH);
      digitalWrite(onTargetB,  HIGH);
#ifdef OFFTARGET_LEDS
      digitalWrite(offTargetA, HIGH);
      digitalWrite(offTargetB, HIGH);
#endif
      updateCardLeds(A_ALL | B_ALL);

      // Buzz for one second
      buzzer(true);
      delay(1000);

      // Turn all lights off
      digitalWrite(onTargetA,  LOW);
      digitalWrite(onTargetB,  LOW);
#ifdef OFFTARGET_LEDS
      digitalWrite(offTargetA, LOW);
      digitalWrite(offTargetB, LOW);
#endif
      updateCardLeds(0);
   }
}

void displayState()
{
   displayState(boutState);
}

void displayState(enum BoutState state)
{
#ifdef DISP_IR_CARDS_BOX
   setBrightness(DIM_BRIGHTEST);
   switch (state)
   {
      case STA_SPAR:
         disp.setSegments(sparDisp, 4, 0);
         break;

      case STA_BOUT:
      case STA_TP_BOUT:
      case STA_STARTBOUT:
      case STA_CONTINUE:
        disp.setSegments(boutDisp, 4, 0);
        break;

     case STA_PRIORITY:
     case STA_TP_PRI:
     case STA_ENDPRI:
        disp.setSegments(prioDisp, 4, 0);
        break;

     case STA_BREAK:
     case STA_TP_BREAK:
        disp.setSegments(restDisp, 4, 0);
        break;

     case STA_STOPWATCH:
#ifdef STOPWATCH
        disp.setSegments(stopWatchDisp, 4, 0);
#endif
        break;
   }
#endif
}

void displayWeaponAndState()
{
#ifdef SERIAL_INDICATOR
   indicateWeapon();
#endif
   displayWeapon(false);
   delay(1000);
   displayState();
   delay(1000);
}

void displayTouch(bool touchActive)
{
#ifdef DISP_IR_CARDS_BOX
#ifdef STOPWATCH
   if (!inStopWatch())
#endif
   {
      if (priorityInactive())
      {
         if (timeState == TIM_STOPPED)
         {
            setBrightness(DIM_BRIGHTEST);

            if (touchActive)
            {
               disp.setSegments(shortCircuit[FENCER_A] ? touchDisp[FENCER_A]:sparNoHit, 2, 0);
               disp.setSegments(shortCircuit[FENCER_B] ? touchDisp[FENCER_B]:sparNoHit, 2, 2);
#ifdef DEBUG_L5
               Serial.println("touch active");
#endif
            }
            else
            {
               restoreDisplay();
#ifdef DEBUG_L5
               Serial.println("touch inactive");
#endif
            }
         }
      }
   }
#endif
}

//===================
// Display the short-circuit status on the 7-segment
//===================
void displayShortCircuit()
{
#ifdef DISP_IR_CARDS_BOX
   setBrightness(DIM_BRIGHTEST);
   disp.setSegments(scDisplay[FENCER_A] ? shortDisp:sparNoHit, 2, 0);
   disp.setSegments(scDisplay[FENCER_B] ? shortDisp:sparNoHit, 2, 2);
#endif
}

//===================
// Display the score on the 7-segment
//===================
void displayScore()
{ 
#ifdef DEBUG_L1
  Serial.println("display score");
#endif

  // If priority display is active, then don't do this
  if (priState == PRI_CHOOSE)
  {
     return;
  }

  // Is a short-circuit being displayed?
  else if (scDisplayActive())
  {
     return;
  }

  // End of priority period?
  else if (priState == PRI_END)
  {
     switch (hitDisplay)
     {
        case HIT_IDLE:
        case HIT_ON:
#ifdef DISP_IR_CARDS_BOX
           setBrightness(DIM_BRIGHTEST);
           disp.showNumberDecEx(score[FENCER_A], 0, false, 2, 0);
           disp.showNumberDecEx(score[FENCER_B], 0, false, 2, 2);
#endif
          // Flash the hit LED of the fencer who won priority
          digitalWrite((priFencer == FENCER_A) ? onTargetA:onTargetB, HIGH);
          break;

        case HIT_OFF:
#ifdef DISP_IR_CARDS_BOX
           // Flash the score of the fencer who won priority
           disp.setSegments(blankDisp, 2, (priFencer == FENCER_A) ? 0:2);
#endif
           digitalWrite(onTargetA, LOW);
           digitalWrite(onTargetB, LOW);
           break;
     }
  }

  // Show score
  else if (!disableScore)
  {
     switch (hitDisplay)
     {
        case HIT_IDLE:
        case HIT_ON:
#ifdef DISP_IR_CARDS_BOX
           setBrightness(DIM_BRIGHTEST);
#endif
           // Show scores if not off-target hit
           if (hitDisplayFlag[FENCER_A] != HIT_OFFTARGET
               &&
               hitDisplayFlag[FENCER_B] != HIT_OFFTARGET)
           {
#ifdef DISP_IR_CARDS_BOX
              disp.showNumberDecEx(score[FENCER_A], 0, false, 2, 0);
              disp.showNumberDecEx(score[FENCER_B], 0, false, 2, 2);
#endif
              // If we've hit maximum sabre hits, then flash both hit LEDs
              if (maxSabreHits[FENCER_A])
              {
                 digitalWrite(onTargetA, HIGH);
              }
              if (maxSabreHits[FENCER_B])
              {
                 digitalWrite(onTargetB, HIGH);
              }
           }

           // Off-target hit
           else
           {
              if (hitDisplayFlag[FENCER_A] == HIT_OFFTARGET)
              {
#ifdef DISP_IR_CARDS_BOX
                 disp.setSegments(sparOffHit[FENCER_A], 2, 0);
#endif
#ifndef OFFTARGET_LEDS
                 // Flash the 'on target A' LED
                 digitalWrite(onTargetA, HIGH);
#endif
              }
              else
              {
#ifdef DISP_IR_CARDS_BOX
                 disp.setSegments(blankDisp, 2, 0);
#endif
              }
              if (hitDisplayFlag[FENCER_B] == HIT_OFFTARGET)
              {
#ifdef DISP_IR_CARDS_BOX
                 disp.setSegments(sparOffHit[FENCER_B], 2, 2);
#endif
#ifndef OFFTARGET_LEDS
                 // Flash the 'on target B' LED
                 digitalWrite(onTargetB, HIGH);
#endif
              }
              else
              {
#ifdef DISP_IR_CARDS_BOX
                 disp.setSegments(blankDisp, 2, 2);
#endif
              }
           }
           break;

        case HIT_OFF:
           // If we've reached maximum sabre hits, flash entire display
           if (maxSabreHits[FENCER_A] || maxSabreHits[FENCER_B])
           {
#ifdef DISP_IR_CARDS_BOX
              setBrightness(DIM_BRIGHTEST);
              disp.setSegments(blankDisp, 2, 0);
              disp.setSegments(blankDisp, 2, 2); 
#endif
              digitalWrite(onTargetA, LOW);
              digitalWrite(onTargetB, LOW);
           }

           // Only flash the display for the fencer who hit
           else switch (hitDisplayFlag[FENCER_A])
           {
              case HIT_NONE:
              default:
                 break;

              case HIT_OFFTARGET:
#ifndef OFFTARGET_LEDS
                 // Flash the 'on target A' LED
                 digitalWrite(onTargetA, LOW);
#endif
                 // Drop through

              case HIT_ONTARGET:
                 // Blank score for fencer A
#ifdef DISP_IR_CARDS_BOX
                 setBrightness(DIM_BRIGHTEST);
                 disp.setSegments(blankDisp, 2, 0);
#endif
                 break;
           }
           switch (hitDisplayFlag[FENCER_B])
           {
              case HIT_NONE:
              default:
                 break;

              case HIT_OFFTARGET:
#ifndef OFFTARGET_LEDS
                 // Flash the 'on target B' LED
                 digitalWrite(onTargetB, LOW);
#endif
                 // Drop through

              case HIT_ONTARGET:
                 // Blank score for fencer B
#ifdef DISP_IR_CARDS_BOX
                 setBrightness(DIM_BRIGHTEST);
                 disp.setSegments(blankDisp, 2, 2);
#endif
                 break;
           }
           break;
     }
     currentDisp = DISP_SCORE;
  }

  // Just show a blank display
  else
  {     
     switch (hitDisplay)
     {
        case HIT_IDLE:
#ifdef DISP_IR_CARDS_BOX
          setBrightness(DIM_BRIGHTEST);
          disp.setSegments(sparNoHit, 4, 0);
#endif
          break;

        case HIT_ON:
#ifdef DISP_IR_CARDS_BOX
          setBrightness(DIM_BRIGHTEST);
#endif
          // Fencer A hit?
          switch (hitDisplayFlag[FENCER_A])
          {
             case HIT_NONE:
             default:
#ifdef DISP_IR_CARDS_BOX
                disp.setSegments(sparNoHit, 2, 0);
#endif
                break;

             case HIT_ONTARGET:
#ifdef DISP_IR_CARDS_BOX
                disp.setSegments(sparHit[FENCER_A], 2, 0);
#endif
                break;

             case HIT_OFFTARGET:
#ifdef DISP_IR_CARDS_BOX
                disp.setSegments(sparOffHit[FENCER_A], 2, 0);
#endif
#ifndef OFFTARGET_LEDS
                // Flash the 'on target A' LED
                digitalWrite(onTargetA, HIGH);
#endif
                break;
          }

          // Fencer B hit?
          switch (hitDisplayFlag[FENCER_B])
          {
             case HIT_NONE:
             default:
#ifdef DISP_IR_CARDS_BOX
                disp.setSegments(sparNoHit, 2, 2);
#endif
                break;

             case HIT_ONTARGET:
#ifdef DISP_IR_CARDS_BOX
                disp.setSegments(sparHit[FENCER_B], 2, 2);
#endif
                break;

             case HIT_OFFTARGET:
#ifdef DISP_IR_CARDS_BOX
                disp.setSegments(sparOffHit[FENCER_B], 2, 2);
#endif
#ifndef OFFTARGET_LEDS
                // Flash the 'on target B' LED
                digitalWrite(onTargetB, HIGH);
#endif
                break;
          }
          break;

        case HIT_OFF:
#ifdef DISP_IR_CARDS_BOX
          setBrightness(DIM_BRIGHTEST);
#endif
          if (hitDisplayFlag[FENCER_A])
          {
#ifdef DISP_IR_CARDS_BOX
             disp.setSegments(blankDisp, 2, 0);
#endif
#ifndef OFFTARGET_LEDS
             if (hitDisplayFlag[FENCER_A] == HIT_OFFTARGET)
             {
                // Flash the 'on target A' LED
                digitalWrite(onTargetA, LOW);
             }
#endif
          }
          if (hitDisplayFlag[FENCER_B])
          {
#ifdef DISP_IR_CARDS_BOX
             disp.setSegments(blankDisp, 2, 2);
#endif
#ifndef OFFTARGET_LEDS
             if (hitDisplayFlag[FENCER_B] == HIT_OFFTARGET)
             {
                // Flash the 'on target B' LED
                digitalWrite(onTargetB, LOW);
             }
#endif
          }
          break;
     }
     currentDisp = DISP_SCORE;
  }
#ifdef SERIAL_INDICATOR
  if (!disableScore)
  {
     char ind[10];
     /* Two sprintfs are needed because of an awful bug in the Arduino
        libraries which means that sprintf() can't take more than one argument! */
     sprintf(&ind[0], "*%02d",  score[FENCER_A]);
     sprintf(&ind[3], "%02d",   score[FENCER_B]);
     Serial.println(ind);
  }
#endif
}

//===================
// Display the time on the 7-segment
//===================
void displayTime()
{
#ifdef DEBUG_L1
  Serial.println("display time");
#endif

   // If priority display is active, then don't do this
   if (priState == PRI_CHOOSE)
   {
      return;
   }

   // Is a short-circuit being displayed?
   else if (scDisplayActive())
   {
      return;
   }

   // Not temporarily displaying score?
   else if (!scoreFlash)
   {
#ifdef STOPWATCH
      if (swEdit != SW_NONE)
      {
#ifdef DISP_IR_CARDS_BOX
         setBrightness(DIM_BRIGHTEST);
         disp.showNumberDecEx(swMins, 0b01000000, true, 2, 0);
         disp.showNumberDecEx(swSecs, 0b01000000, true, 2, 2);
#ifdef DEBUG_L6
         Serial.println("display stopwatch");
#endif
#endif
      }
      else if (!timerStart)
#endif
      {
#ifdef DEBUG_L6
         Serial.println("display time");
#endif
#ifdef DISP_IR_CARDS_BOX
         setBrightness(DIM_BRIGHTEST);

         // Show the 1/100 second timer
         if (timerLast9s)
         {
            if (timerHund >= 0)
            {
               disp.showNumberDecEx(timerSecs, 0b01000000, false, 2, 0);
               disp.showNumberDecEx(timerHund, 0b01000000, true,  2, 2);
            }
         }

         // Show the 1 second timer
         else
         {
            disp.showNumberDecEx(timerMins, 0b01000000, true, 2, 0);
            disp.showNumberDecEx(timerSecs, 0b01000000, true, 2, 2);
         }
#endif
      }
      currentDisp = DISP_TIME;
   }
#ifdef SERIAL_INDICATOR
   char ind[10];

   /* Two sprintfs are needed because of an awful bug in the Arduino
      libraries which means that sprintf() can't take more than one argument! */
#ifdef STOPWATCH
   if (inStopWatch())
   {
      if (swEdit != SW_NONE)
      {
         sprintf(&ind[0], "@%02d",  swMins);
         sprintf(&ind[3], "%02d",   swSecs);
      }
      else
      {
         sprintf(&ind[0], "@%02d",  timerMins);
         sprintf(&ind[3], "%02d",   timerSecs);
      }
      Serial.println(ind);
   }
   else
#endif
   if (timerLast9s)
   {
      if (timerHund >= 0)
      {
         sprintf(&ind[0], "@%02d",  timerSecs);
         sprintf(&ind[3], "%02d",   timerHund);
         Serial.println(ind);
      }
   }
   else
   {
      /* Two sprintfs are needed because of an awful bug in the Arduino
         libraries which means that sprintf() can't take more than one argument! */
      sprintf(&ind[0], "@%02d",  timerMins);
      sprintf(&ind[3], "%02d",   timerSecs);
      Serial.println(ind);
   }
#endif
}

void displayPri()
{
#ifdef DISP_IR_CARDS_BOX
   // Is a short-circuit being displayed?
   if (scDisplayActive())
   {
      return;
   }
   else
   {
      disp.clear();
      setBrightness(DIM_BRIGHTEST);
      if (priFencer == FENCER_A)
      {
         disp.setSegments(priDisp, 2, 0);

         // Show fencer A has won priority on the hit LEDs
         if (priState == PRI_SELECTED || priState == PRI_END)
         {
            digitalWrite(onTargetA, HIGH);
            digitalWrite(onTargetB, LOW);
#ifdef SERIAL_INDICATOR
            Serial.println("$H1");
#endif
         }
      }
      else
      {
         disp.setSegments(priDisp, 2, 2);

         // Show fencer B has won priority on the hit LEDs
         if (priState == PRI_SELECTED || priState == PRI_END)
         {
            digitalWrite(onTargetA, LOW);
            digitalWrite(onTargetB, HIGH);
#ifdef SERIAL_INDICATOR
            Serial.println("$H2");
#endif
         }
      }
   }
#endif
   currentDisp = DISP_PRI;
}

void setBrightness(uint8_t dim)
{
#ifdef DISP_IR_CARDS_BOX
   // If we were previously in screen saver, then clear the display
   if (dim == DIM_BRIGHTEST && dimSetting == DIM_DIMMEST)
   {
      disp.clear();
   }
   dimTimer   = millis();
   dimSetting = dim;
   disp.setBrightness(dim, true);
#endif
}

void displayDimCycle()
{
#ifdef DISP_IR_CARDS_BOX
   disp.setSegments(dimDisp[dimCycle], 4, 0);
#endif
}

bool restoreDisplayAfterSleep()
{
#ifdef DISP_IR_CARDS_BOX
   if (dimSetting == DIM_DIMMEST)
   {
      // Display the current operating mode briefly
      keyClick();
      displayWeaponAndState();

      // Restore the last display
      return restoreDisplay();
   }
#endif
}

bool restoreDisplay()
{
   bool ret = false;

#ifdef DISP_IR_CARDS_BOX
   switch (currentDisp)
   {
      case DISP_SCORE:
         displayScore();
         ret = true;
         break;

      case DISP_TIME:
         displayTime();
         ret = true;
         break;

      case DISP_PRI:
         displayPri();
         ret = true;
         break;

      default:
         break;
   }
#endif
   return ret;
}

//================
// Configuration
//================
void setup() 
{
#ifdef DEBUG_ALL
   Serial.begin(BAUDRATE);
#endif
#ifdef DEBUG_ALL
   Serial.println("");
   Serial.println("Fencing Scoring Box");
   Serial.println("===================");
#endif

#ifndef DEBUG_L1
   // Set the shift register pins to outputs
   pinMode(latchPin,   OUTPUT);
   pinMode(clockPin,   OUTPUT);
   pinMode(dataPin,    OUTPUT);
   updateCardLeds(0);

   // Set the button pin to INPUT
   pinMode(buttonPin,  INPUT);
#endif
#ifdef OFFTARGET_LEDS
   pinMode(offTargetA, OUTPUT);
   pinMode(offTargetB, OUTPUT);
#endif
   pinMode(onTargetA,  OUTPUT);
   pinMode(onTargetB,  OUTPUT);
   pinMode(buzzerPin,  OUTPUT);

   // this optimises the ADC to make the sampling rate quicker
   adcOpt();

   // Turn on the 7-segment LED display
#ifdef DISP_IR_CARDS_BOX
   setBrightness(DIM_BRIGHTEST);
#endif

#ifdef EEPROM_STORAGE
   Weapon w = readWeapon();

   // If the weapon type is unset, default to FOIL
   if (w == NONE)
   {
      weaponType = FOIL;
      writeWeapon(weaponType);
   }
   else
   {
      weaponType = w;
   }

   BoutState m = readState();

   // If the state is unset, default to SPAR
   if (m == STA_NONE)
   {
      boutState = STA_SPAR;
      writeState(boutState);
   }
   else
   {
      boutState = m;
   }
#else
   weaponType = FOIL;
   boutState = STA_SPAR;
#endif
#ifdef SERIAL_INDICATOR
   Serial.begin(BAUDRATE);
   Serial.println("");
   Serial.println("!GO");
   indicateWeapon();
#endif

   // Restart the box
   restartBox();
#ifdef DISP_IR_CARDS_BOX
   irRecv.enableAutoResume(irBuffer);
#ifdef IR_FRAMETIMEOUT
   irRecv.setFrameTimeout(IR_FRAMETIMEOUT);
#endif
   irRecv.enableIRIn();
#endif
}

#ifdef SERIAL_INDICATOR
void indicateWeapon()
{
   switch (weaponType)
   {
      case FOIL:
      default:
         Serial.println("!TF");
         break;

      case EPEE:
         Serial.println("!TE");
         break;

      case SABRE:
         Serial.println("!TS");
         break;
   }
}
#endif

//=============
// Restart the box
//=============
void restartBox()
{
   restartBox(boutState);
}

void restartBox(BoutState state)
{
   displayWeapon();

   resetValues();
   resetLights();
   resetCards();

   hitDisplay               = HIT_IDLE;
   resetState               = RES_IDLE;
   priFencer                = 0;
   lastHit                  = 0; 
   hitDisplayFlag[FENCER_A] = HIT_NONE;
   hitDisplayFlag[FENCER_B] = HIT_NONE;
   lockOutOffTarg[FENCER_A] = true;
   lockOutOffTarg[FENCER_B] = true;
   score[FENCER_A]          = 0;
   score[FENCER_B]          = 0;
   shortCircuit[FENCER_A]   = 0;
   shortCircuit[FENCER_B]   = 0;
   scoreThisBout[FENCER_A]  = 0;
   scoreThisBout[FENCER_B]  = 0;
   maxSabreHits[FENCER_A]   = false;
   maxSabreHits[FENCER_B]   = false;

   switch (state)
   {
      case STA_SPAR:
      default:
         boutState = state;
         startSpar();
         break;

      case STA_STARTBOUT:
      case STA_BOUT:
      case STA_TP_BOUT:
      case STA_TP_PRI:
      case STA_PRIORITY:
      case STA_BREAK:
      case STA_TP_BREAK:
         boutState = STA_STARTBOUT;
         startBout();
         break;

      case STA_STOPWATCH:
         boutState = state;
         startStopWatch();
         break;
   }
}

void choosePriority()
{
#ifdef SERIAL_INDICATOR
   Serial.println("!PC");
#endif
#ifdef DISP_IR_CARDS_BOX
   displayState(STA_PRIORITY);
#endif
   delay(1000);
   priState   = PRI_CHOOSE;
   priFencer  = 0;
}

//=============
// Timer functions
//=============
void setTimer(int time)
{
#ifdef DISP_IR_CARDS_BOX
   timeState  = TIM_STOPPED;
   timerStart = false;

   if (time > 0)
   {
      timerMax = time;
   }
   timer         = time;
   timerMs       = 0;
   timerMins     = timer/60;
   timerSecs     = timer%60;
   timerHund     = 0;
   timerLast9s   = false;
   timerInterval = ONESEC;
#ifdef DEBUG_L6
   Serial.print("setting timer to ");
   Serial.println(time);
#endif
#endif
}

//=============
// Restart main timer
//=============
void restartTimer()
{
#ifdef DISP_IR_CARDS_BOX
    if (timeState == TIM_STOPPED)
    {
        timerStart = true;
        timerMs    = millis();
    }
#endif
}

//===================
// Increment main timer
//===================
int incTimer(int inc)
{
#ifdef DISP_IR_CARDS_BOX
   if (timeState == TIM_STOPPED)
   {
      if (timerMax > 0 && (timer+inc) > timerMax)
      {
         inc = timerMax-timer;
      }
      timer     += inc;
      timerSecs += inc;
      if (timerSecs >= 60)
      {
        timerMins++;
        timerSecs -= 60;
      }
#ifdef DEBUG_L6
      Serial.println("increment timer");
#endif
      return 1;
   }
#endif
   return 0;
}

//===================
// Decrement main timer
//===================
int decTimer(int inc)
{
#ifdef DISP_IR_CARDS_BOX
   if (timeState == TIM_STOPPED)
   {
      timer -= inc;
      if (timerSecs < inc)
      {
        if (timerMins > 0)
        {
          timerMins--;
          timerSecs = 60+timerSecs-inc;
        }
      }
      else
      {
         timerSecs -= inc;
      }
#ifdef DEBUG_L6
      Serial.println("decrement timer");
#endif
      return 1;
   }
#endif
   return 0;
}

//===================
// Add one to fencer score
//===================
void addScore(int fencer)
{
#ifdef DISP_IR_CARDS_BOX
   if (!disableScore)
   {
      if (score[fencer] < MAXSCORE)
      {
         score[fencer]++;
         scoreThisBout[fencer]++;
         if (checkSabreHits())
         {
            startHitDisplay();
         }
         displayScore();
#ifdef DEBUG_L1
         Serial.print("increment score for fencer ");
         Serial.print(fencer == 0 ? "A":"B");
         Serial.print(", score ");
         Serial.print(score[fencer]);
         Serial.print(", this bout ");
         Serial.println(scoreThisBout[fencer]);
#endif
      }
   }
#endif
}

//===================
// Subtract one from fencer score
//===================
void subScore(int fencer)
{
#ifdef DISP_IR_CARDS_BOX
   if (!disableScore)
   {
      if (score[fencer] > 0)
      {
         score[fencer]--;
         scoreThisBout[fencer]--;
         if (checkSabreHits())
         {
            startHitDisplay();
         }
         displayScore();
#ifdef DEBUG_L1
         Serial.print("decrement score for fencer ");
         Serial.println(fencer == 0 ? "A":"B");
         Serial.print(", score ");
         Serial.print(score[fencer]);
         Serial.print(", this bout ");
         Serial.println(scoreThisBout[fencer]);
#endif
      }
   }
#endif
}

void resetScore()
{
#ifdef DISP_IR_CARDS_BOX
   score[FENCER_A] = score[FENCER_B] = 0;
   scoreThisBout[FENCER_A] = scoreThisBout[FENCER_B] = 0;
   displayScore();
#ifdef DEBUG_L1
   Serial.println("reset scores");
#endif
   if (inBout())
   {
      delay(1000);
      displayTime();
   }
#endif
}

//===================
// Update the card LEDs (yellow/red)
//==================
void updateCardLeds(int Leds)
{
#ifdef DISP_IR_CARDS_BOX
#ifdef DEBUG_L1
   Serial.print("card LEDs ");
   Serial.println(cardLeds, HEX);
#else
   digitalWrite(latchPin, LOW); 
   shiftOut(dataPin, clockPin, LSBFIRST, Leds); 
   digitalWrite(latchPin, HIGH);
#endif
   cardLedUpdate = false;
#endif
}

//===================
// Control the buzzer
//===================
void buzzer(bool buzz)
{
   digitalWrite(buzzerPin,  buzz ? HIGH:LOW);
}

void keyClick()
{
   buzzer(true);
   delay(5);
   buzzer(false);
}

void shortBeep()
{
   delay(100);
   buzzer(true);
   delay(50);
   buzzer(false);
}

//============
// Count down the main timer
//============
int countDown(int timerGap)
{
#ifdef DISP_IR_CARDS_BOX
   if (timerLast9s)
   {
      /* Counting down in hundredths of seconds */
      if (timerHund <= 0)
      {
         /* Has the timer expired? */
         if (--timerSecs < 0)
         {
            timer         = timerSecs = 0;
            timerLast9s   = false;
            timerInterval = ONESEC;
         }
         else
         {
            timerHund += 99;
         }
      }
      else
      {
         timerHund -= timerGap;
      }
   }
   else if (timer > 0)
   {
      timer--;
      if (timerSecs <= 0)
      {
         timerMins--;
         timerSecs = 59;
      }
      else
      {
         timerSecs -= timerGap;
      }
   }
   else
   {
      timerMins     = timerSecs = 0;
      timerLast9s   = false;
      timerInterval = ONESEC;
   }
   return (timer == 0) ? 1:0;
#else
   return 0;
#endif
}

//=============
// IR handling
//=============
#ifdef DISP_IR_CARDS_BOX
void transIR(unsigned long key)
{
#ifdef DEBUG_L1
   Serial.print("key:");
   Serial.println(key, HEX);
#endif
   /* Wake up display, if asleep -
      don't process the key otherwise */
   if (restoreDisplayAfterSleep())
   {
      return;
   }

   // Record last key
   if (key != 0xFFFFFFFF)
   {
      lastKeyCode = key;
   }

  //=============================
  // Hobby Components handset
  //=============================
  switch (key)
  {
  case 0xFF6897: // *
     if (priorityInactive())
     {
        // In SPARRING mode? Go into BOUT mode
        if (inSpar())
        {
           // Go into BOUT mode
           keyClick();
           startBout();
        }
#ifdef STOPWATCH
        else if (inStopWatch())
        {
           if (timeState == TIM_STOPPED)
           {
              // In STOPWATCH mode? Go into SPARRING mode
              keyClick();
              startSpar();
           }
        }
#endif
        // Only allow these keys if the timer is not running
        else if (timeState == TIM_STOPPED)
        {
           switch (boutState)
           {                 
              case STA_STARTBOUT:
#ifdef STOPWATCH
                 // Bout not started - go into STOPWATCH mode
                 keyClick();
                 startStopWatch();
#else
                 keyClick();
                 startSpar();
#endif
                 break;

              default:
                 // In the middle of a bout - restart the bout
                 keyClick();
                 startBout();
                 break;
           }
        }

        // Timer is running - temporarily show the score
        else
        {
           keyClick();
           scoreFlash = true;
        }
        lastKey = K_START_BOUT;
        resetHits();
     }

     // In priority select mode, this key will stop selection (same as #)
     else if (priState == PRI_CHOOSE)
     {
        keyClick();
        priState = PRI_SELECTED;
     }
     break;
  case 0xFF629D: // 2
     if (priorityInactive())
     {
        if (timeState == TIM_STOPPED)
        {
           switch (boutState)
           {
              case STA_BREAK:
              case STA_TP_BREAK:
                 keyClick();
#ifdef DEBUG_L1
                 Serial.println("reset break timer");
#endif
                 setTimer(BREAKTIME);
                 displayTime();
                 break;

              case STA_PRIORITY:
              case STA_TP_PRI:
                 keyClick();
#ifdef DEBUG_L1
                 Serial.println("reset priority timer");
#endif
                 setTimer(PRITIME);
                 displayTime();
                 break;

              case STA_BOUT:
              case STA_TP_BOUT:
                 keyClick();
#ifdef DEBUG_L1
                 Serial.println("reset bout timer");
#endif
                 setTimer(BOUTTIME);
                 displayTime();
                 break;

              default:
                 break;
           }
        }
        lastKey = K_RESET_TIMER;
        resetHits();
     }
     break;
  case 0xFF10EF: // LEFT
     if (priorityInactive())
     {
        if (inBoutOrSpar())
        {
           // Change to fencer A for last hit
           if (!disableScore)
           {
              if (timeState == TIM_STOPPED)
              {
                 keyClick();
                 lastHit = 1 << FENCER_A;
#ifdef DISP_IR_CARDS_BOX
                 // Flash fencer A score once
                 disp.setSegments(blankDisp, 2, 0);
                 disp.showNumberDecEx(score[FENCER_B], 0, false, 2, 2);
                 delay(250);
#endif
                 displayScore();
              }

              // Timer is running - temporarily show the score
              else
              {
                 keyClick();
                 scoreFlash = true;
              }
              lastKey = K_LEFT;
           }
        }

        // In stopwatch - allow editing of minutes
#ifdef STOPWATCH
        else if (inStopWatch())
        {
           // If the stopwatch is stopped, then allow time editing
           if (timeState == TIM_STOPPED)
           {
              if (swEdit == SW_NONE || swEdit == SW_SECS)
              {
                 keyClick();

                 if (swEdit == SW_NONE)
                 {
                    swMins = timerMins;
                    swSecs = timerSecs;
                 }
                 swEdit = SW_MINS;
#ifdef DISP_IR_CARDS_BOX
                 // Flash stopwatch minutes once
                 disp.setSegments(blankDisp, 2, 0);
                 delay(250);
                 displayTime();
#endif
              }
           }
        }
#endif
        // In priority select mode, this key will stop selection (same as #)
        else if (priState == PRI_CHOOSE)
        {
           keyClick();
           priState = PRI_SELECTED;
        }
        resetHits();
     }
        
     // In priority select mode, this key will stop selection (same as #)
     else if (priState == PRI_CHOOSE)
     {
        keyClick();
        priState = PRI_SELECTED;
     }
     break;
  case 0xFF38C7: // OK
     if (priorityInactive())
     {
#ifdef STOPWATCH
        if (inStopWatch())
        {
           keyClick();

           // Restart the stopwatch
           if (timeState == TIM_STOPPED)
           {
              // Stopwatch has expired, so allow it to restart
              if (swCount == SW_END)
              {
                 swCount = SW_UP;
              }
#ifdef DEBUG_L4
              Serial.println("restart stopwatch");
#endif
              restartTimer();
              stopWatchLeds();
              timeState = TIM_STOPWATCH;
           }

           // Pause the stopwatch
           else
           {
#ifdef DEBUG_L4
              Serial.println("pause stopwatch");
#endif
              timeState = TIM_STOPPED;
           }
           displayTime();
        }
        else 
#endif
        // Are we in a bout, or in the break period?
        if (inBoutOrBreak())
        {
           if (timeState != TIM_STOPPED)
           {
              keyClick();
              switch (boutState)
              {
                 case STA_BREAK:
                 case STA_TP_BREAK:
#ifdef DEBUG_L1
                    Serial.println("break timer stopped");
#endif
                    boutState = STA_TP_BREAK;
                    break;

                 case STA_PRIORITY:
                 case STA_ENDPRI:
                 case STA_TP_PRI:
#ifdef DEBUG_L1
                    Serial.println("priority timer stopped");
#endif
                    boutState = STA_TP_PRI;
                    break;

                 case STA_BOUT:
                 case STA_TP_BOUT:
#ifdef DEBUG_L1
                    Serial.println("bout timer stopped");
#endif
                    boutState = STA_TP_BOUT;
                    break;
              }
              timeState = TIM_STOPPED;
           }

           // Turn the lights off after a hit
           else if (resetState == RES_LIGHTS)
           {
              // Turn lights off
              keyClick();
              resetLights();
              resetState = RES_IDLE;
              resetTimer = 0;

              // Force hits to be reset
              resetHits(true);

              // Go into point test mode
              switch (boutState)
              {
                 case STA_BREAK:
                    boutState = STA_TP_BREAK;
                    resetValues();
                    break;

                 case STA_BOUT:
                    boutState = STA_TP_BOUT;
                    resetValues();
                    break;

                 case STA_PRIORITY:
                    boutState = STA_TP_PRI;
                    resetValues();
                    break;

                 default:
                    break;
              }

              // Switch back to timer display
              displayTime();
#ifdef DEBUG_L1
              Serial.println("Turn off lights");
#endif
           }
           else
           {
              keyClick();
              resetValues();
              switch (boutState)
              {
                 case STA_ENDPRI:
                    boutState = STA_CONTINUE;
                    resetLights();
                    break;
                    
                 case STA_CONTINUE:
                    setTimer(BOUTTIME);
                    boutState = STA_STARTBOUT;
                    displayTime();
#ifdef DEBUG_L1
                    Serial.println("bout continuing");
#endif
                    break;
                 
                 case STA_STARTBOUT:
                    // If we've switched to score display, switch back to time
                    if (currentDisp == DISP_SCORE)
                    {
                       displayTime();
                    }
                    else
                    {
                       restartTimer();
                       boutState = STA_BOUT;
#ifdef DEBUG_L1
                       Serial.println("start bout timer");
#endif
                    }
                    break;

                 case STA_BREAK:
                 case STA_TP_BREAK:
                    // If we've switched to score display, switch back to time
                    if (currentDisp == DISP_SCORE)
                    {
                       displayTime();
                    }
                    else
                    {
                       restartTimer();
                       timeState = TIM_BREAK;
#ifdef DEBUG_L1
                       Serial.println("break timer running");
#endif
                    }
                    break;

                 case STA_PRIORITY:
                 case STA_TP_PRI:
                    // If we've switched to score display, switch back to time
                    if (currentDisp == DISP_SCORE)
                    {
                       displayTime();
                    }
                    else
                    {
                       restartTimer();
                       timeState = TIM_PRIORITY;
                       boutState = STA_PRIORITY;
#ifdef DEBUG_L1
                       Serial.println("priority timer running");
#endif
                    }
                    break;

                 case STA_BOUT:
                 case STA_TP_BOUT:
                    // If we've switched to score display, switch back to time
                    if (currentDisp == DISP_SCORE)
                    {
                       displayTime();
                    }
                    else
                    {
                       restartTimer();
                       timeState = TIM_BOUT;
                       boutState = STA_BOUT;
#ifdef DEBUG_L1
                       Serial.println("bout timer running");
#endif
                    }
                    break;
              }
           }
        }

        // In SPAR mode, use OK to clear scores
        else if (!disableScore)
        {
           keyClick();
           resetScore();
        }
        resetHits();
     }

     // In priority select mode, this key will stop selection (same as #)
     else if (priState == PRI_CHOOSE)
     {
        keyClick();
        priState = PRI_SELECTED;
     }
     lastKey = K_OK;
     break;
  case 0xFF5AA5: // RIGHT
     if (priorityInactive())
     {
        if (inBoutOrSpar())
        {
           // Change to fencer B for last hit
           if (!disableScore)
           {
              if (timeState == TIM_STOPPED)
              {
                 keyClick();
                 lastHit = 1 << FENCER_B;

                 // Flash fencer B score once
#ifdef DISP_IR_CARDS_BOX
                 disp.showNumberDecEx(score[FENCER_A], 0, false, 2, 0);
                 disp.setSegments(blankDisp, 2, 2);
                 delay(250);
#endif
                 displayScore();
              }

              // Timer is running - temporarily show the score
              else
              {
                 keyClick();
                 scoreFlash = true;
              }
              lastKey = K_RIGHT;
           }
        }

        // In stopwatch - allow editing of seconds
#ifdef STOPWATCH
        else if (inStopWatch())
        {
           // If the stopwatch is stopped, then allow time editing
           if (timeState == TIM_STOPPED)
           {
              if (swEdit == SW_NONE || swEdit == SW_MINS)
              {
                 keyClick();
                 if (swEdit == SW_NONE)
                 {
                    swMins = timerMins;
                    swSecs = timerSecs;
                 }
                 swEdit = SW_SECS;
#ifdef DISP_IR_CARDS_BOX
                 // Flash stopwatch seconds once
                 disp.setSegments(blankDisp, 2, 2);
                 delay(250);
                 displayTime();
#endif
              }
           }
        }
#endif
        resetHits();
     }
     
     // In priority select mode, this key will stop selection (same as #)
     else if (priState == PRI_CHOOSE)
     {
        keyClick();
        priState = PRI_SELECTED;
     }
     break;
  case 0xFF4AB5: // DOWN
     if (priorityInactive())
     {
        if (inBoutOrSpar())
        {
           if (!disableScore)
           {
              if (timeState == TIM_STOPPED)
              {
                 if (lastHit)
                 {
                    keyClick();
                 }
                 
                 // DOWN decrements last hit score
                 if (lastHit & (1 << FENCER_A))
                 {
                    subScore(FENCER_A);
                 }
                 if (lastHit & (1 << FENCER_B))
                 {
                    subScore(FENCER_B);
                 }
              }

              // Timer is running - temporarily show the score
              else
              {
                 keyClick();
                 scoreFlash = true;
              }
              lastKey = K_DEC_SCORE;
              resetHits();
           }
        }
        
        // In stopwatch - allow decrement of minutes or seconds
#ifdef STOPWATCH
        else if (inStopWatch())
        {
           // If the stopwatch is stopped, then allow time editing
           if (timeState == TIM_STOPPED)
           {
              if (swEdit == SW_NONE)
              {
                 swEdit = SW_MINS;
                 swMins = timerMins;
                 swSecs = timerSecs;
              }
           }
           switch (swEdit)
           {
              case SW_MINS:
                if (swMins > 0)
                {
                   keyClick();
                   swMins--;
                }
                setStopWatch();
                displayTime();
                lastKey = K_WIND_BACK;
                break;

              case SW_SECS:
                keyClick();
                if (swSecs > 0)
                {
                   swSecs--;
                }

                // Wrap around
                else
                {
                   swSecs = 59;
                }
                setStopWatch();
                displayTime();
                lastKey = K_WIND_BACK;
                break;
           }
        }
#endif
     }
     
     // In priority select mode, this key will stop selection (same as #)
     else if (priState == PRI_CHOOSE)
     {
        keyClick();
        priState = PRI_SELECTED;
     }
     break;
  case 0xFF9867: // 0
     if (priorityInactive())
     {
        if (inBoutOrBreak())
        {
           if (timeState == TIM_STOPPED)
           {
              keyClick();
              startBreak();
           }
        }
        lastKey = K_BREAK;
        resetHits();
     }

     // In priority select mode, this key will stop selection (same as #)
     else if (priState == PRI_CHOOSE)
     {
        keyClick();
        priState = PRI_SELECTED;
     }
     break;
  case 0xFF18E7: // UP
     if (priorityInactive())
     {
        if (inBoutOrSpar())
        {
           if (!disableScore)
           {
              if (timeState == TIM_STOPPED)
              {
                 if (lastHit)
                 {
                    keyClick();
                 }

                 // UP increments last hit score
                 if (lastHit & (1 << FENCER_A))
                 {
                    addScore(FENCER_A);
                 }
                 if (lastHit & (1 << FENCER_B))
                 {
                    addScore(FENCER_B);
                 }
              }

              // Timer is running - temporarily show the score
              else
              {
                 keyClick();
                 scoreFlash = true;
              }
              lastKey = K_INC_SCORE;
              resetHits();
           }
        }

        // In stopwatch - allow increment of minutes or seconds
#ifdef STOPWATCH
        else if (inStopWatch())
        {
           // If the stopwatch is stopped, then allow time editing
           if (timeState == TIM_STOPPED)
           {
              if (swEdit == SW_NONE)
              {
                 swEdit = SW_MINS;
                 swMins = timerMins;
                 swSecs = timerSecs;
              }
           }
           switch (swEdit)
           {
              case SW_MINS:
                if (swMins < 59)
                {
                   keyClick();
                   swMins++;
                }
                setStopWatch();
                displayTime();
                lastKey = K_WIND_FORWARD;
                break;

              case SW_SECS:
                keyClick();
                if (swSecs < 59)
                {
                   swSecs++;
                }

                // Wrap around
                else
                {
                   swSecs = 0;
                }
                setStopWatch();
                displayTime();
                lastKey = K_WIND_FORWARD;
                break;
           }
        }
#endif
     }

     // In priority select mode, this key will stop selection (same as #)
     else if (priState == PRI_CHOOSE)
     {
        keyClick();
        priState = PRI_SELECTED;
     }
     break;
  case 0xFFB04F: // #
#ifdef STOPWATCH
     if (inStopWatch())
     {
        // Stopwatch running? If so, reset and restart it
        if (timeState == TIM_STOPWATCH)
        {
           keyClick();
           resetStopWatch(true);
           stopWatchLeds();
        }
                
        // If the timer has been stopped, reset it without starting it
        else if (timer > 0)
        {
           // If the timer is at maximum already, clear it
           if (timerSecs == swSecs && timerMins == swMins)
           {
              swSecs = swMins = 0;
              swEdit = SW_MINS;
           }
           keyClick();
           resetStopWatch(false);
           stopWatchLeds();
        }
     }
     else 
#endif
     if (inBout())
     {
        if (timeState == TIM_STOPPED)
        {
           keyClick();
           if (priorityInactive())
           {
#ifdef DEBUG_L1
              Serial.println("choosing priority");
#endif
              choosePriority();
           }
           else
           {
              priState = PRI_SELECTED;
           }
        }
     }

     // In sparring mode, toggle score display
     else if (inSpar())
     {
        keyClick();
        disableScore = disableScore ? false:true;

        // Clear scores if they are disabled
        if (disableScore)
        {
           resetScore();
        }
        displayScore();
#ifdef DEBUG_L1
        Serial.print("score display ");
        Serial.println(disableScore ? "off":"on");
#endif
     }
     lastKey = K_PRIORITY;
     resetHits();
     break;
  case 0xFFA25D: // 1
     if (priorityInactive())
     {
        // If in a bout, wind back timer by 1 second
        if (inBoutOrBreak())
        {
           if (timeState == TIM_STOPPED)
           {
              keyClick();
              if (decTimer(1))
              {
#ifdef DEBUG_L1
                 Serial.println("timer back by 1 second");
#endif
              }
              else
              {
#ifdef DEBUG_L1
                 Serial.println("timer at minimum");
#endif
                 setTimer(0);
              }
              displayTime();
           }
           lastKey = K_WIND_BACK;
        }
        resetHits();
     }
     break;
  case 0xFFE21D: // 3
     if (priorityInactive())
     {
        // If in a bout, wind forward timer by 1 second
        if (inBoutOrBreak())
        {
           if (timeState == TIM_STOPPED)
           {
              keyClick();
              if (incTimer(1))
              {
#ifdef DEBUG_L1
                 Serial.println("timer forward by 1 second");
#endif
              }
              else
              {
#ifdef DEBUG_L1
                 Serial.println("timer at maximum");
#endif
                 setTimer(BOUTTIME);
              }
              displayTime();
           }
           lastKey = K_WIND_FORWARD;
        }
        resetHits();
     }
     break;
  case 0xFF22DD: // 4
     break;
  case 0xFF02FD: // 5
     if (priorityInactive())
     {
        if (inBoutOrSpar())
        {
           if (!disableScore)
           {
              if (timeState == TIM_STOPPED)
              { 
                 keyClick();
                 resetScore();
              }
           }
        }
        lastKey = K_CLEAR_SCORES;
        resetHits();
     }
     break;
  case 0xFFC23D: // 6
     break;
  case 0xFFE01F: // 7
     if (priorityInactive())
     {
        if (inBout())
        {
           if (timeState == TIM_STOPPED)
           {
              keyClick();
              if (ledFlag[FENCER_A] == LED_BOTH)
              {
                 ledFlag[FENCER_A] = LED_NONE;
              }
              else
              {
                 ledFlag[FENCER_A]++;
              }
              cardLeds     &= ~ledBits[FENCER_A][LED_BOTH];
              cardLeds     |=  ledBits[FENCER_A][ledFlag[FENCER_A]];
              cardLedUpdate = true;
#ifdef DEBUG_L1
              Serial.print("fencer A card ");
              Serial.println(ledFlag[FENCER_A]);
#endif
           }
        }
        lastKey = K_CARD_A;
        resetHits();
     }
     break;
  case 0xFFA857: // 8
     if (priorityInactive())
     {
        if (inBout())
        {
           if (timeState == TIM_STOPPED)
           {
              keyClick();
              ledFlag[FENCER_A]  = ledFlag[FENCER_B] = LED_NONE;

              cardLeds          &= ~ledBits[FENCER_A][LED_BOTH];
              cardLeds          &= ~ledBits[FENCER_B][LED_BOTH];
              cardLedUpdate      = true;
#ifdef DEBUG_L1
              Serial.println("clear all cards");
#endif
           }
        }
     }
     lastKey = K_CLEAR_CARDS;
     resetHits();
     break;
  case 0xFF906F: // 9
     if (priorityInactive())
     {
        if (inBout())
        {
           if (timeState == TIM_STOPPED)
           {
              keyClick();
              if (ledFlag[FENCER_B] == LED_BOTH)
              {
                 ledFlag[FENCER_B] = LED_NONE;
              }
              else
              {
                 ledFlag[FENCER_B]++;
              }
              cardLeds     &= ~ledBits[FENCER_B][LED_BOTH];
              cardLeds     |=  ledBits[FENCER_B][ledFlag[FENCER_B]];
              cardLedUpdate = true;
#ifdef DEBUG_L1
              Serial.print("fencer B card ");
              Serial.println(ledFlag[FENCER_B]);
#endif
           }
        }
        lastKey = K_CARD_B;
        resetHits();
     }
     break;
  case 0xFFFFFFFF: // repeat
     // Only certain keys can repeat
     if (lastKey == K_WIND_BACK 
         || 
         lastKey == K_WIND_FORWARD)
     {
        // Recursively call with last keypress
        transIR(lastKeyCode);
     }
     break;
  default:
#ifdef DEBUG_L1
     Serial.println(key, HEX);
#endif
     break;
  }
}
#endif

//=============
// Start a new bout
//=============
void startBout()
{
#ifdef SERIAL_INDICATOR
   Serial.println("!BS");
   Serial.println("*0000");
#endif
#ifdef DISP_IR_CARDS_BOX
   priState                = PRI_IDLE;
#ifdef STOPWATCH
   swEdit                  = SW_NONE;
#endif
   disableScore            = false;
   score[FENCER_A]         = score[FENCER_B] = 0;
   lastHit                 = 0;
   maxSabreHits[FENCER_A]  = false;
   maxSabreHits[FENCER_B]  = false;
   scoreThisBout[FENCER_A] = 0;
   scoreThisBout[FENCER_B] = 0;
   boutState              = STA_STARTBOUT;
   resetState              = RES_IDLE;
   hitDisplay              = HIT_IDLE;
   resetHits();
#ifdef EEPROM_STORAGE
   writeState(boutState);
#endif
   setBrightness(DIM_BRIGHTEST);
   disp.setSegments(boutDisp, 4, 0);

   resetCards();
#endif
   resetLights();
#ifdef DISP_IR_CARDS_BOX
   delay(1000);
   setTimer(BOUTTIME);
   displayTime();
#ifdef DEBUG_L1
   Serial.println("bout start mode");
#endif
#endif
}

void continueBout()
{
#ifdef SERIAL_INDICATOR
   Serial.println("!BC");
#endif
#ifdef DISP_IR_CARDS_BOX
   priState = PRI_IDLE;
   setTimer(BOUTTIME);
   displayScore();
   boutState = STA_CONTINUE; 
   scoreThisBout[FENCER_A] = scoreThisBout[FENCER_B] = 0;
#ifdef DEBUG_L1
   Serial.println("continue bout");
#endif
#endif
}

void buzzerTimeout()
{
   int  i;

   for (i = 0; i < 3; i++)
   {
      buzzer(true);
      delay(100);
      buzzer(false);
      delay(100);
   }
}

void endOfBout()
{
#ifdef SERIAL_INDICATOR
   Serial.println("!BE");
#endif
#ifdef DISP_IR_CARDS_BOX
   buzzerTimeout();

   displayScore();
#ifdef DEBUG_L1
   Serial.println("end of bout");
#endif
#endif   
}

//=============
// Start a priority period
//=============
void startPriority()
{
#ifdef SERIAL_INDICATOR
   Serial.println("!PS");
#endif
#ifdef DISP_IR_CARDS_BOX
#ifdef DEBUG_L1
   Serial.println(
      priFencer == FENCER_B ? 
      "priority mode (fencer B)":
      "priority mode (fencer A)");
#endif
   disableScore = false;
   displayPri();
   delay(1000);
   resetLights();
   displayScore();
   delay(1000);
   priState = PRI_IDLE;
   setTimer(PRITIME);
   displayTime();
   boutState = STA_PRIORITY;
#endif
}

void endPriority()
{
#ifdef SERIAL_INDICATOR
   Serial.println("!PE");
#endif
#ifdef DISP_IR_CARDS_BOX
   priState   = PRI_END;
   timeState  = TIM_STOPPED;
   boutState  = STA_ENDPRI;

   /* If one fencer has a higher score then the other at the end of
      the priority time period, then make them the priority fencer */
   if (score[FENCER_A] > score[FENCER_B])
   {
      priFencer = FENCER_A;
   }
   else if (score[FENCER_B] > score[FENCER_A])
   {
      priFencer = FENCER_B;
   }
   startHitDisplay();
   displayScore();
#endif
}

//=============
// Start sparring (no timer)
//=============
void startSpar()
{
#ifdef SERIAL_INDICATOR
   Serial.println("!SS");
#endif
#ifdef DEBUG_L1
   Serial.println("sparring mode - no timer");
#endif
   boutState      = STA_SPAR;
#ifdef EEPROM_STORAGE
   writeState(boutState);
#endif
   timeState       = TIM_STOPPED;
   priState        = PRI_IDLE;
#ifdef STOPWATCH
   swEdit          = SW_NONE;
#endif
   disableScore    = true;
   score[FENCER_A] = score[FENCER_B] = 0;
   lastHit         = 0;
#ifdef DISP_IR_CARDS_BOX
   displayState();
#endif
   resetValues();
   resetCards();
   resetLights();
   updateCardLeds(0);
   delay(1000);
   displayScore();
}

void startBreak()
{
#ifdef DISP_IR_CARDS_BOX
#ifdef SERIAL_INDICATOR
   Serial.println("!RS");
#endif
#ifdef DEBUG_L1
   Serial.println("1 minute break"); 
#endif
   boutState = STA_BREAK;
   displayState();
   delay(1000);
   setTimer(BREAKTIME);
   restartTimer();
#endif
}

void startStopWatch()
{
#ifdef STOPWATCH
   boutState = STA_STOPWATCH;
#ifdef SERIAL_INDICATOR
   Serial.println("!WS");
#endif
#ifdef EEPROM_STORAGE
   writeState(boutState);
#endif
   timeState     = TIM_STOPPED;
   swMins        = 0;
   swSecs        = 0;
   timerLast9s   = false;
   timerInterval = ONESEC;
   swCount       = SW_UP;
   swEdit        = SW_MINS;

   displayState();
   delay(1000);
   resetStopWatch(false);
#endif
#ifdef DEBUG_L4
   Serial.println("start stopwatch");
#endif
}

void setStopWatch()
{
#ifdef STOPWATCH
   timerMins = swMins;
   timerSecs = swSecs;
#ifdef DEBUG_L4
   Serial.print("set stopwatch to ");
   Serial.print(swMins);
   Serial.print(":");
   Serial.println(swSecs);
#endif
   setTimer(swMins*60+swSecs);
#endif
}

void resetStopWatch(bool restart)
{
#ifdef STOPWATCH
#ifdef SERIAL_INDICATOR
   Serial.println("!WR");
#endif
#ifdef DEBUG_L4
   Serial.println("reset stopwatch");
#endif
   timeState = TIM_STOPPED;
   setTimer(swMins*60+swSecs);
   displayTime();

   // Illuminate the LEDs at the start
   if (restart)
   {
      digitalWrite(onTargetA, HIGH);
      digitalWrite(onTargetB, LOW);
      updateCardLeds(A_ALL);

      // If restarting the timer...
      restartTimer();
   }
#endif
}

void stopWatchLeds()
{
#ifdef STOPWATCH
  uint8_t stopWatchLeds;

  // Toggle the two hit LEDs
  stopWatchLeds = timerSecs % 2;

  // Illuminate the LEDs until stopwatch expires
  if (swCount == SW_END)
  {
     digitalWrite(onTargetA, LOW);
     digitalWrite(onTargetB, LOW);
     updateCardLeds(0);
#ifdef SERIAL_INDICATOR
     Serial.println("!RL");
#endif
  }
  else
  {
     digitalWrite(onTargetA, stopWatchLeds ? LOW:HIGH);
     digitalWrite(onTargetB, stopWatchLeds ? HIGH:LOW);
     updateCardLeds(stopWatchLeds ? B_ALL:A_ALL);
#ifdef SERIAL_INDICATOR
     Serial.println(stopWatchLeds ? "$H4":"$H3");
#endif
  }
#endif
}

int runStopWatch()
{
   int ret = 0;
#ifdef STOPWATCH
   if (boutState == STA_STOPWATCH)
   {
      // Starting for first time
      if (swEdit != SW_NONE)
      {
         swEdit = SW_NONE;
         if (swMins > 0 || swSecs > 0)
         {
            swCount = SW_DOWN;
         }
         else
         {
            swCount = SW_UP;
         }
      }
#ifdef DEBUG_L4
      Serial.println("run stopwatch");
#endif
      switch (swCount)
      {
         case SW_UP:
            if (timer < MAXSTOPWATCH)
            {
               timer++;
               if (timerSecs >= 59)
               {
                  timerMins++;
                  timerSecs = 0;
               }
               else
               {
                  timerSecs++;
               }
            }

            // Wrap around
            else
            {
               timer = timerMins = timerSecs = 0;
            }
            displayTime();
            stopWatchLeds();
            break;

         case SW_DOWN:
            if (timer > 0)
            {
               timer--;
               if (timerSecs <= 0)
               {
                  timerMins--;
                  timerSecs = 59;
               }
               else
               {
                  timerSecs--;
               }
            }
            displayTime();

            // Stopwatch has expired?
            if (timer == 0)
            {
               timerMins = timerSecs = 0;
               swCount   = SW_END;
               stopWatchLeds();
               timeState = TIM_STOPPED;
               buzzerTimeout();
               ret       = 1;
            }
            else
            {
               stopWatchLeds();
            }
            break;
      }
   }
#endif
   return ret;
}

#ifdef DISP_IR_CARDS_BOX
void pollIR()
{
#ifdef IRLIB2
   if (irRecv.getResults()) 
   {
     irDecode.decode(); 
     if (irDecode.protocolNum == NEC)
     {
        transIR(irDecode.value);
#ifdef DEBUG_IR
        irDecode.dumpResults(true);
#endif
     }
     irRecv.enableIRIn();
  }
#else
   decode_results res;

   while (irRecv.decode(&res))
   {
      transIR(res.value);
      irRecv.resume();
   }
#endif
}
#endif

bool checkSabreHits()
{
#ifdef DISP_IR_CARDS_BOX
   maxSabreHits[FENCER_A] = maxSabreHits[FENCER_B] = false;

   if (weaponType == SABRE)
   {
      // If one sabre fencer has achieved 8 hits this bout...
      if (scoreThisBout[FENCER_A] >= MAXSABREHITS)
      {
         maxSabreHits[FENCER_A] = true;
      }
      if (scoreThisBout[FENCER_B] >= MAXSABREHITS)
      {
          maxSabreHits[FENCER_B] = true;
      }
   }
   return (maxSabreHits[FENCER_A] || maxSabreHits[FENCER_B]) ? true:false;
#else
   return false;
#endif
}

void startHitDisplay()
{
   startHitDisplay(HITDISPTIME);
}

void startHitDisplay(long time)
{
#ifdef DISP_IR_CARDS_BOX
   if (hitDisplay == HIT_IDLE)
   {
      hitDisplay      = HIT_ON;
      hitDisplayTime  = time;
      hitDisplayTimer = millis();

      // Select the type of hit for fencer A
      if (hitOnTarg[FENCER_A])
      {
         hitDisplayFlag[FENCER_A] = HIT_ONTARGET;
      }
      else if (hitOffTarg[FENCER_A])
      {
         hitDisplayFlag[FENCER_A] = HIT_OFFTARGET;
      }

      // Select the type of hit for fencer B
      if (hitOnTarg[FENCER_B])
      {
         hitDisplayFlag[FENCER_B] = HIT_ONTARGET;
      }
      else if (hitOffTarg[FENCER_B])
      {
         hitDisplayFlag[FENCER_B] = HIT_OFFTARGET;
      }
   }
#endif
}

//=============
// ADC config
//=============
void adcOpt()
{
   // the ADC only needs a couple of bits, the atmega is an 8 bit micro
   // so sampling only 8 bits makes the values easy/quicker to process
   // unfortunately this method only works on the Due.
   //analogReadResolution(8);

   // Data Input Disable Register
   // disconnects the digital inputs from which ever ADC channels you are using
   // an analogue input will be float and cause the digital input to constantly
   // toggle high and low, this creates noise near the ADC, and uses extra 
   // power Secondly, the digital input and associated DIDR switch have a
   // capacitance associated with them which will slow down your input signal
   // if you’re sampling a highly resistive load 
   DIDR0 = 0x7F;

   // set the prescaler for the ADCs to 16 this allowes the fastest sampling
   bitClear(ADCSRA, ADPS0);
   bitClear(ADCSRA, ADPS1);
   bitSet  (ADCSRA, ADPS2);
}

//============
// Main loop
//============
void loop() 
{
   int    expired, timerGap;
   long   now;
   
   // use a for() as a main loop as the loop() has too much overhead for fast analogReads
   // we get a 3-4% speed up on the loop this way
   for (;;) 
   {
      // Don't read the analogue pins if not a bout nor sparring
      if (!inStopWatch())
      {
         // read analogue pins
         weapon[FENCER_A] = analogRead(weaponPinA);
         weapon[FENCER_B] = analogRead(weaponPinB);
         lame[FENCER_A]   = analogRead(lamePinA);
         lame[FENCER_B]   = analogRead(lamePinB);

         // We need a ground pin measurement for sabre
         if (weaponType == SABRE)
         {
            ground[FENCER_A] = analogRead(groundPinA);
            ground[FENCER_B] = analogRead(groundPinB);
         }

         // Do processing for a given weapon
         switch (weaponType)
         {
            case EPEE:
               epee();
               break;

            case FOIL:
               foil();
               break;
            
            case SABRE:
               sabre();
               break;
            
            default:
               break;
         }
      }

      // Check LED display dimming and 'screen saver'
#ifdef DISP_IR_CARDS_BOX
      if (dimSetting == DIM_BRIGHTEST)
      {
         if (millis() - dimTimer > DIMDELAY)
         {
            // Start 'screen saver'
            setBrightness(DIM_DIMMEST);
            dimTimer = millis();
            dimCycle = 0;

            displayDimCycle();
         }
      }
      else
      {
         // 'Screen saver' is operating
#ifdef LOW_POWER
         /* This doesn't sleep for long, because the timer that
            is used for IR detection wakes it up - however, any CPU
            idle time that we can obtain is useful for power saving */
         LowPower.idle(SLEEP_FOREVER, ADC_OFF, TIMER2_ON, 
               TIMER1_OFF, TIMER0_ON, SPI_OFF, USART0_OFF, TWI_OFF);
#endif
         if (millis() - dimTimer > DIMINTERVAL)
         {
            if (++dimCycle >= MAX_DIMCYCLE)
            {
               dimCycle = 0;
            }
            dimTimer = millis();
            displayDimCycle();
         }
      }
#endif

#ifdef DISP_IR_CARDS_BOX
      // Poll the IR to see if a key has been pressed
      pollIR();
#endif

      // Check for hits and signal
      if (isHit())
      {
         // Can we do a point test?
         if (pointCanBeTested())
         {
            signalHits();
           
            // Start the reset state machine to turn off buzzer and lights
            if (!resetState)
            {
               resetState = RES_TESTPOINT;
               resetTimer = millis();
            }
            restoreDisplay();
         }

         // Regular hit during a bout?
         else if (timeState != TIM_STOPPED)
         {
            signalHits();
           
            // Start the reset state machine to turn off buzzer and lights
            if (!resetState)
            {
               resetState = RES_BUZZER;
               resetTimer = millis();
            }
#ifdef DEBUG_L1
            Serial.println("hit - stop the timer");
#endif 
            // Stop the timer
            timeState  = TIM_STOPPED;

            // Start the hit display state machine
            startHitDisplay();

            // Increment the score
            if (hitOnTarg[FENCER_A])
            {
               addScore(FENCER_A);
            }
            if (hitOnTarg[FENCER_B])
            {
               addScore(FENCER_B);
            }

            // Display the current score
            displayScore();
         }

         // In sparring mode
         else if (inSpar())
         {
            if (!resetState)
            {
              signalHits();
              startHitDisplay();

#ifdef SPAR_INCR_SCORE
              // Do automatic scoring
              if (hitOnTarg[FENCER_A])
              {
                 addScore(FENCER_A);
              }
              if (hitOnTarg[FENCER_B])
              {
                 addScore(FENCER_B);
              }
#endif
              displayScore();

              // Start the reset state machine to turn off buzzer and lights
              resetState = RES_BUZZER;
              resetTimer = millis();
            }
         }
      }

#ifdef DISP_IR_CARDS_BOX
      // Poll the IR to see if a key has been pressed
#ifdef FREQUENT_IRPOLL
      pollIR();
#endif
#endif
      // Is the box changing mode?
      if (modeChange)
      {
         switch (weaponType)
         {
            case FOIL:
            case NONE:
               weaponType = EPEE;
#ifdef EEPROM_STORAGE
               writeWeapon(weaponType);
#endif
#ifdef SERIAL_INDICATOR
               indicateWeapon();
#endif
               restartBox();
               break;

            case EPEE:
               weaponType = SABRE;
#ifdef EEPROM_STORAGE
               writeWeapon(weaponType);
#endif
#ifdef SERIAL_INDICATOR
               indicateWeapon();
#endif
               restartBox();
               break;

            case SABRE:
               weaponType = FOIL;
#ifdef EEPROM_STORAGE
               writeWeapon(weaponType);
#endif
#ifdef SERIAL_INDICATOR
               indicateWeapon();
#endif
               restartBox();
               break;
         }
         modeChange = false;
      }

#ifdef DISP_IR_CARDS_BOX
      // Poll the IR to see if a key has been pressed
#ifdef FREQUENT_IRPOLL
      pollIR();
#endif
#endif

#ifdef DISP_IR_CARDS_BOX
      // Check for priority
      if (priState == PRI_CHOOSE)
      {
         // Oscillate between fencers when choosing priority
         priFencer ^= 1;
         displayPri();
      }
      else if (priState == PRI_SELECTED)
      {
         startPriority();
      }
#endif

      // Time to clear the hit flags and lockout?
      switch (resetState)
      {
         case RES_BUZZER:
            if (millis() - resetTimer >= BUZZERTIME)
            {
               // Clear hit and lockout
               resetValues();
               shortBeep();

               resetState = RES_LIGHTS;
               resetTimer = millis();
            }
            break;

         case RES_TESTPOINT:
            /* If we're in a bout, and a hit has already been cleared,
               then deal with the situation where a fencer tests their
               weapon point before restarting the bout
            */        
            if (millis() - resetTimer >= TESTPOINTTIME)
            {
               // Clear hit and lockout
               resetValues();
               shortBeep();
               
               // Turn lights off
               resetLights();
               resetState = RES_IDLE;
               resetTimer = 0;
            }
            break;
            
         case RES_LIGHTS:
            // Turn lights off automatically in sparring mode
            if (inSpar())
            {
               if (millis() - resetTimer >= LIGHTTIME)
               {
                  // Turn lights off
                  resetLights();
                  resetState = RES_IDLE;
                  resetTimer = 0;
               }
            }
            break;

         case RES_SHORT:
            if (millis() - resetTimer >= LIGHTTIME)
            {
               // Turn lights off
               resetLights();
               resetState = RES_IDLE;
               resetTimer = 0;
               restoreDisplay();
            }
            break;         

         case RES_OFF:
            resetLights();
            resetState = RES_IDLE;
            resetTimer = 0;
            break;

         default:
           break;
      }

#ifdef DISP_IR_CARDS_BOX
      // Poll the IR to see if a key has been pressed
#ifdef FREQUENT_IRPOLL
      pollIR();
#endif
#endif

      // Flash the display when a hit is active?
      if (hitDisplay)
      {
         if (millis() - hitDisplayTimer >= hitDisplayTime)
         {
            hitDisplay = (hitDisplay == HIT_ON) ? HIT_OFF:HIT_ON;
            displayScore();
            hitDisplayTimer = millis();
         }
      }

      // Short circuit on fencer A?
      if (shortCircuit[FENCER_A])
      {
         // Illuminate the short-circuit LED immediately
         if (!(cardLeds & A_SHORT))
         {
            cardLeds     |= A_SHORT;
            cardLedUpdate = true;
            displayTouch(true);
#ifdef DEBUG_L5
            Serial.println("short circuit LED on fencer A");
#endif
         }

         // For the 7-segment display, we need a short delay to check it's real
         if (millis() - shortCircuit[FENCER_A] >= MAXSHORTCIRC)
         {
            if (!scDisplay[FENCER_A])
            {
#ifdef DEBUG_L5
               Serial.println("short circuit on fencer A");
#endif
               scDisplay[FENCER_A] = true;
               displayShortCircuit();
            }
         }
      }

      // Extinguish the short-circuit LED immediately, if it was active
      else if (cardLeds & A_SHORT)
      {
         cardLeds           &= ~A_SHORT;
         cardLedUpdate       = true;
         scDisplay[FENCER_A] = false;
         displayTouch(false);
#ifdef DEBUG_L5
         Serial.println("short circuit LED off fencer A");
#endif
      }

      // Short circuit on fencer B?
      if (shortCircuit[FENCER_B])
      {
         // Illuminate the short-circuit LED immediately
         if (!(cardLeds & B_SHORT))
         {
            cardLeds     |= B_SHORT;
            cardLedUpdate = true;
            displayTouch(true);
#ifdef DEBUG_L5
            Serial.println("short circuit LED on fencer B");
#endif
         }

         // For the 7-segment display, we need a short delay to check it's real
         if (millis() - shortCircuit[FENCER_B] >= MAXSHORTCIRC)
         {
            if (!scDisplay[FENCER_B])
            {
#ifdef DEBUG_L5
               Serial.println("short circuit on fencer B");
#endif
               scDisplay[FENCER_B] = true;
               displayShortCircuit();
            }
         }
      }

      // Extinguish the short-circuit LED immediately, if it was active
      else if (cardLeds & B_SHORT)
      {
         cardLeds           &= ~B_SHORT;
         cardLedUpdate       = true;
         scDisplay[FENCER_B] = false;
         displayTouch(false);
#ifdef DEBUG_L5
         Serial.println("short circuit LED off fencer B");
#endif
      }

      // Update the 4 card LEDs (yellow/red for A/B)
      if (cardLedUpdate)
      {
         updateCardLeds(cardLeds);
      }

#ifdef DISP_IR_CARDS_BOX
      // Poll the IR to see if a key has been pressed
#ifdef FREQUENT_IRPOLL
      pollIR();
#endif
#endif

      // Do we need to flash up the score?
      if (scoreFlash)
      {
          if (scoreFlashTimer == 0)
          {
             scoreFlashTimer = millis();
             displayScore();
          }
          else if (millis() - scoreFlashTimer >= SCOREFLASHTIME)
          {
             scoreFlash      = false;
             scoreFlashTimer = 0;
             if (timeState != TIM_STOPPED)
             {
                displayTime();
             }
          }
      }

      // Button scan
      if ((millis() - buttonScan) > BUTTONSCAN)
      {
         if (timeState == TIM_STOPPED)
         {
            if (digitalRead(buttonPin) == LOW)
            {
               // Button is pressed - debounce
               if (buttonDebounce == 0)
               {
                  buttonDebounce = millis();
               }
               else if ((millis() - buttonDebounce) > BUTTONDEBOUNCE)
               {
                  if (!buttonPressed)
                  {
                     buttonPressed = true;
                     modeChange    = true;
#ifdef DEBUG_L1
                     Serial.println("button pressed");
#endif
                  }
               }
            }

            // Button released
            else
            {
               // Has the button been pressed for a short time?
               if (buttonPressed == false && buttonDebounce > 0)
               {
                  // Show the weapon type for 1 second
                  displayWeaponAndState();

                  restoreDisplay();
               }
               buttonPressed  = false;
               modeChange     = false;
               buttonDebounce = 0;
#ifdef DEBUG_L1
               Serial.println("button released");
#endif
            }
            buttonScan = millis();
         }
      }

#ifdef DISP_IR_CARDS_BOX
      // Poll the IR to see if a key has been pressed
#ifdef FREQUENT_IRPOLL
      pollIR();
#endif
#endif

      // Start the timer for the start of a bout
      if (timerStart)
      {
          timerMs = millis();
          timerStart = false;
          displayTime();
          switch (boutState)
          {
             case STA_STOPWATCH:
                timeState = TIM_STOPWATCH;
#ifdef DEBUG_L2
                Serial.println("priority starting");
#endif
                break;
                
             case STA_PRIORITY:
                timeState = TIM_PRIORITY;
#ifdef DEBUG_L2
                Serial.println("priority starting");
#endif
                break;

             case STA_BREAK:
                timeState = TIM_BREAK;
#ifdef DEBUG_L2
                Serial.println("break starting");
#endif
                break;

             case STA_BOUT:
                timeState = TIM_BOUT;
#ifdef DEBUG_L2
                Serial.println("bout starting");
#endif
                break;

             case STA_TP_BOUT:
             case STA_TP_PRI:
             case STA_TP_BREAK:
                break;
          }
      }

#ifdef DISP_IR_CARDS_BOX
      // Poll the IR to see if a key has been pressed
#ifdef FREQUENT_IRPOLL
      pollIR();
#endif
#endif

      // Handle main timer
      if (timeState != TIM_STOPPED)
      {
         if (millis() > (timerMs+timerInterval))
         {
            timerGap = (millis()-timerMs)/timerInterval;
#ifdef DEBUG_L6
            if (timerInterval == ONESEC)
               Serial.println("1 second gone");
#endif
            timerMs = millis();
#ifdef STOPWATCH
            if (timeState == TIM_STOPWATCH)
            {
               runStopWatch();
            }
            else
#endif
            {
               expired = countDown(timerGap);
               displayTime();

               if (inBout())
               {
                  if (timerMins == 0 && timerSecs <= 10)
                  {
                     if (!timerLast9s)
                     {
                        timerLast9s   = true;
                        timerInterval = HUNDSEC;
                        timerHund     = 0;
                     }
                  }
               }

               // Countdown expired?
               if (expired)
               {
                  if (timeState == TIM_BREAK)
                  {
                     timeState = TIM_STOPPED;
#ifdef DEBUG_L6
                     Serial.println("break timer expired");
#endif
                  }
                  else if (timeState == TIM_PRIORITY)
                  {
                     timeState = TIM_STOPPED;
#ifdef DEBUG_L6
                     Serial.println("priority timer expired");
#endif
                     endPriority();
                  }
                  else
                  {
                     timeState = TIM_STOPPED;
#ifdef DEBUG_L6
                     Serial.println("bout timer expired");
#endif
                     timerLast9s   = false;
                     timerInterval = ONESEC;
                  }

                  // Stay in the same mode, so that we can continue if needed
                  endOfBout();

                  if (boutState == STA_BOUT || boutState == STA_BREAK)
                  {
                     continueBout();
                  }
               }

               // Timer not yet expired
               else
               {
#ifdef DEBUG_L6
                  String info = String("timer: ") + timerMins + ":" + timerSecs + " score: " + score[FENCER_A] + ":" + score[FENCER_B];
                  Serial.println(info);
#endif
               }
            }
         }
      }

#ifdef DEBUG_L3
      long now;

      if (loopCount == 0) 
      {
         now = micros();
      }
      loopCount++;
      if ((micros() - now) >= ONESEC_US) 
      {
         Serial.print(loopCount);
         Serial.print(" readings in 1 sec");
         loopCount = 0;
         Serial.print(" weapon[FENCER_A] ");
         Serial.print(weapon[FENCER_A]);
         Serial.print(" lame[FENCER_A] ");
         Serial.print(lame[FENCER_A]);
         Serial.print(" ground[FENCER_A] ");
         Serial.print(ground[FENCER_A]);
         Serial.print(" weapon[FENCER_B] ");
         Serial.print(weapon[FENCER_B]);
         Serial.print(" lame[FENCER_B] ");
         Serial.print(lame[FENCER_B]);
         Serial.print(" ground[FENCER_B] ");
         Serial.println(ground[FENCER_B]);
      }
#endif
   }
}

//===================
// Main foil method
//===================
void foil() 
{
   long now = micros();
   if (((hitOnTarg[FENCER_A] || hitOffTarg[FENCER_A]) && (depressTime[FENCER_A] + lockout[FOIL] < now)) || 
       ((hitOnTarg[FENCER_B] || hitOffTarg[FENCER_B]) && (depressTime[FENCER_B] + lockout[FOIL] < now))) 
   {
      lockedOut = true;
   }

   // weapon A
   if (!hitOnTarg[FENCER_A] && !hitOffTarg[FENCER_A]) 
   {
      // Off-target hit?
      if (weapon[FENCER_A] > 900 && lame[FENCER_B] < 200) 
      {
         if (!depressed[FENCER_A]) 
         {
            depressTime[FENCER_A] = micros();
            depressed[FENCER_A]   = true;
         } 
         else 
         {
            now = micros();
            if (depressTime[FENCER_A] + depress[FOIL] <= now) 
            {
               if (!lockOutOffTarg[FENCER_A])
               {
                  hitOffTarg[FENCER_A]     = true;
                  lockOutOffTarg[FENCER_A] = true;
               }
               else
               {
                  hitOffTarg[FENCER_A]     = false;
               }
            }
         }
      } 
      else 
      {
         // A weapon is now plugged in, so allow off-target hits
         lockOutOffTarg[FENCER_A] = false;
         
         // On-target hit?
         if (weapon[FENCER_A] > 400 && weapon[FENCER_A] < 600 
             && 
             lame[FENCER_B] > 400 && lame[FENCER_B] < 600) 
         {
            if (!depressed[FENCER_A]) 
            {
               depressTime[FENCER_A] = micros();
               depressed[FENCER_A]   = true;
            } 
            else 
            {
               now = micros();
               if (depressTime[FENCER_A] + depress[FOIL] <= now) 
               {
                  if (released[FENCER_A])
                  {
                     hitOnTarg[FENCER_A]      = true;
                     lockOutOffTarg[FENCER_A] = true;
                     released[FENCER_A]       = false;
                  }
               }
            }
         }

         // No hit
         else 
         {
            released[FENCER_A] = true;

            // reset these values if the depress time is short.
            if (depressed[FENCER_A])
            {
               depressTime[FENCER_A] = 0;
               depressed[FENCER_A]   = false;
            }
         }
      }
   }

   // weapon B
   if (!hitOnTarg[FENCER_B] && !hitOffTarg[FENCER_B]) 
   {
      // Off-target hit?
      if (weapon[FENCER_B] > 900 && lame[FENCER_A] < 200) 
      {
         if (!depressed[FENCER_B]) 
         {
            depressTime[FENCER_B] = micros();
            depressed[FENCER_B]   = true;
         } 
         else 
         {
            now = micros();
            if (depressTime[FENCER_B] + depress[FOIL] <= now) 
            {
               if (!lockOutOffTarg[FENCER_B])
               {
                  hitOffTarg[FENCER_B]     = true;
                  lockOutOffTarg[FENCER_B] = true;
               }
               else
               {
                  hitOffTarg[FENCER_B]     = false;
               }
            }
         }
      } 
      else 
      {
         // A weapon is now plugged in, so allow off-target hits
         lockOutOffTarg[FENCER_B] = false;

         // On-target hit?
         if (weapon[FENCER_B] > 400 && weapon[FENCER_B] < 600 
             && 
             lame[FENCER_A] > 400 && lame[FENCER_A] < 600) 
         {
            if (!depressed[FENCER_B]) 
            {
               depressTime[FENCER_B] = micros();
               depressed[FENCER_B]   = true;
            } 
            else 
            {
               now = micros();
               if (depressTime[FENCER_B] + depress[FOIL] <= now) 
               {
                  if (released[FENCER_B])
                  {
                     hitOnTarg[FENCER_B]      = true;
                     lockOutOffTarg[FENCER_B] = true;
                     released[FENCER_B]       = false;
                  }
               }
            }
         } 

         // No hit
         else 
         {
            released[FENCER_B] = true;

            // reset these values if the depress time is short.
            if (depressed[FENCER_B])
            {
               depressTime[FENCER_B] = 0;
               depressed[FENCER_B]   = false;
            }
         }
      }
   }
}

//===================
// Main epee method
//===================
void epee()
{
   long now = micros();
   if ((hitOnTarg[FENCER_A] && (depressTime[FENCER_A] + lockout[EPEE] < now)) 
       || 
       (hitOnTarg[FENCER_B] && (depressTime[FENCER_B] + lockout[EPEE] < now))) 
   {
      lockedOut = true;
   }

   // Weapon A
   // No hit yet && weapon depress && opponent body touched
   if (!hitOnTarg[FENCER_A] && !lockedOut)
   {
      if (weapon[FENCER_A] > 400 && weapon[FENCER_A] < 600 
          && 
          lame[FENCER_A] > 400 && lame[FENCER_A] < 600) 
      {
         shortCircuit[FENCER_A] = 0;

         if (!depressed[FENCER_A]) 
         {
            depressTime[FENCER_A] = micros();
            depressed[FENCER_A]   = true;
         } 
         else 
         {
            now = micros();
            if (depressTime[FENCER_A] + depress[EPEE] <= now) 
            {
               if (released[FENCER_A])
               {
                  hitOnTarg[FENCER_A] = true;
                  released[FENCER_A]  = false;
               }
            }
         }
      }

      // Short-circuit of some kind?
      else if (
                 (weapon[FENCER_A] > 200 && weapon[FENCER_A] < 400 
                  && 
                  lame[FENCER_A] > 200 && lame[FENCER_A] < 400)
               ||
                 (weapon[FENCER_A] > 400 && weapon[FENCER_A] < 600
                  &&
                  lame[FENCER_A] < 200)
              )   
      {
         // Short circuit on fencer A
         if (!shortCircuit[FENCER_A])
         {
            shortCircuit[FENCER_A] = millis();
         }
      }

      // Not a hit
      else 
      {
         released[FENCER_A]     = true;
         shortCircuit[FENCER_A] = 0;
         
         // reset these values if the depress time is short.
         if (depressed[FENCER_A]) 
         {
            depressTime[FENCER_A] = 0;
            depressed[FENCER_A]   = false;
         }
      }
   }

   // Weapon B
   // No hit yet && weapon depress && opponent body touched
   if (!hitOnTarg[FENCER_B] && !lockedOut)
   {
      if (weapon[FENCER_B] > 400 && weapon[FENCER_B] < 600 
          && 
          lame[FENCER_B] > 400 && lame[FENCER_B] < 600) 
      {
         shortCircuit[FENCER_B] = 0;

         if (!depressed[FENCER_B]) 
         {
            depressTime[FENCER_B] = micros();
            depressed[FENCER_B]   = true;
         } 
         else 
         {
            now = micros();
            if (depressTime[FENCER_B] + depress[EPEE] <= now) 
            {
               if (released[FENCER_B])
               {
                  hitOnTarg[FENCER_B] = true;
                  released[FENCER_B]  = false;
               }
            }
         }
      }
     
      // Short-circuit of some kind?
      else if (
                 (weapon[FENCER_B] > 200 && weapon[FENCER_B] < 400 
                  && 
                  lame[FENCER_B] > 200 && lame[FENCER_B] < 400)
               ||
                 (weapon[FENCER_B] > 400 && weapon[FENCER_B] < 600
                  &&
                  lame[FENCER_B] < 200)
              )
      {
         // Short circuit on fencer B
         if (!shortCircuit[FENCER_B])
         {
            shortCircuit[FENCER_B] = millis();
         }
      }

      // Not a hit
      else 
      {
         released[FENCER_B]     = true;
         shortCircuit[FENCER_B] = 0;
         
         // reset these values if the depress time is short.
         if (depressed[FENCER_B]) 
         {
            depressTime[FENCER_B] = 0;
            depressed[FENCER_B]   = false;
         }
      }
   }
}

//===================
// Main sabre method
//===================
void sabre() 
{
   long now = micros();
   if ((hitOnTarg[FENCER_A] && (depressTime[FENCER_A] + lockout[SABRE] < now)) || 
       (hitOnTarg[FENCER_B] && (depressTime[FENCER_B] + lockout[SABRE] < now))) 
   {
      lockedOut = true;
   }

   // weapon A
   if (!hitOnTarg[FENCER_A]) 
   { 
      // Check for weapon pin being disconnected from ground pin
      if (abs(weapon[FENCER_A] - ground[FENCER_A]) > 200)
      {
         if (!lockOutOffTarg[FENCER_A])
         {
            if (!shortCircuit[FENCER_A])
            {
               shortCircuit[FENCER_A] = millis();
            }
         }
      }
      else 
      {
         // A weapon is now plugged in, so allow off-target hits
         lockOutOffTarg[FENCER_A] = false;

         // On-target hit
         if (weapon[FENCER_A] > 250 && weapon[FENCER_A] < 400 
             &&
             ground[FENCER_A] > 250 && ground[FENCER_B] < 400
             &&
             lame[FENCER_B] > 250 && lame[FENCER_B] < 400) 
         {
            shortCircuit[FENCER_A] = 0;
         
            if (!depressed[FENCER_A]) 
            {
               depressTime[FENCER_A] = micros();
               depressed[FENCER_A]   = true;
            } 
            else 
            {
               now = micros();
               if (depressTime[FENCER_A] + depress[SABRE] <= now) 
               {
                  if (released[FENCER_A])
                  {
                     hitOnTarg[FENCER_A] = true;
                     released[FENCER_A]  = false;
                  }
               }
            }
         }
  
         // Not a hit
         else 
         {
            released[FENCER_A]     = true;
            shortCircuit[FENCER_A] = 0;

            // reset these values if the depress time is short.
            if (depressed[FENCER_A])
            {
               depressTime[FENCER_A] = 0;
               depressed[FENCER_A]   = false;
            }
         }
      }
   }

   // weapon B
   if (!hitOnTarg[FENCER_B]) 
   {
      // Check for weapon pin being disconnected from ground pin
      if (abs(weapon[FENCER_B] - ground[FENCER_B]) > 200)
      {
         if (!lockOutOffTarg[FENCER_B])
         {
            if (!shortCircuit[FENCER_B])
            {
               shortCircuit[FENCER_B] = millis();
            }
         }
      }
      else
      {
         // A weapon is now plugged in, so allow off-target hits
         lockOutOffTarg[FENCER_B] = false;

         // ignore if B has already hit on target
         if (weapon[FENCER_B] > 250 && weapon[FENCER_B] < 400 
             && 
             ground[FENCER_B] > 250 && ground[FENCER_B] < 400
             &&
             lame[FENCER_A] > 250 && lame[FENCER_A] < 400) 
         {
            shortCircuit[FENCER_B] = 0;

            if (!depressed[FENCER_B]) 
            {
               depressTime[FENCER_B] = micros();
               depressed[FENCER_B]   = true;
            } 
            else 
            {
               now = micros();
               if (depressTime[FENCER_B] + depress[SABRE] <= now) 
               {
                  if (released[FENCER_B])
                  {
                     hitOnTarg[FENCER_B] = true;
                     released[FENCER_B]  = false;
                  }
               }
            }
         }

         // Not a hit
         else 
         {
            released[FENCER_B]     = true;
            shortCircuit[FENCER_B] = 0;
 
            // reset these values if the depress time is short.
            if (depressed[FENCER_B])
            {
               depressTime[FENCER_B] = 0;
               depressed[FENCER_B]   = false;
            }
         }
      }
   }
}

//==============
// Signal Hits
//==============
void signalHits() 
{
   // non time critical, this is run after a hit has been detected
   if (lockedOut) 
   {
      lastHit = (hitOnTarg[FENCER_A] ? (1 << FENCER_A):0) | (hitOnTarg[FENCER_B] ? (1 << FENCER_B):0);

#ifdef OFFTARGET_LEDS
      digitalWrite(onTargetA,  hitOnTarg[FENCER_A]  ? HIGH:LOW);
      digitalWrite(onTargetB,  hitOnTarg[FENCER_B]  ? HIGH:LOW);
      digitalWrite(offTargetA, hitOffTarg[FENCER_A] ? HIGH:LOW);
      digitalWrite(offTargetB, hitOffTarg[FENCER_B] ? HIGH:LOW);
#else
      digitalWrite(onTargetA,  (hitOnTarg[FENCER_A] | hitOffTarg[FENCER_A]) ? HIGH:LOW);
      digitalWrite(onTargetB,  (hitOnTarg[FENCER_B] | hitOffTarg[FENCER_B]) ? HIGH:LOW);
#endif
#ifdef SERIAL_INDICATOR
      String ind = String("");

      if (hitOnTarg[FENCER_A])
      {
         ind += String("$H1");
      }
      if (hitOnTarg[FENCER_B])
      {
         ind += String("$H2");
      }
      if (hitOffTarg[FENCER_A])
      {
         ind += String("$O0");
      }
      if (hitOffTarg[FENCER_B])
      {
         ind += String("$O1");
      }
      Serial.println(ind);
#endif
      buzzer(true);
#ifdef DEBUG_L2
      String serData = String("hit on  target A : ") + hitOnTarg[FENCER_A]  + "\n"
                            + "hit off target A : "  + hitOffTarg[FENCER_A] + "\n"
                            + "hit on  target B : "  + hitOnTarg[FENCER_B]  + "\n"
                            + "hit off target B : "  + hitOffTarg[FENCER_B] + "\n"
                            + "locked out       : "  + lockedOut   + "\n";
      Serial.println(serData);
#endif
   }
}

//======================
// Reset all variables
//======================

void resetValues() 
{
   // Turn off buzzer
   buzzer(false);

   resetTimer               = 0;
   lockedOut                = false;
   depressTime[FENCER_A]    = 0;
   depressed[FENCER_A]      = false;
   depressTime[FENCER_B]    = 0;
   depressed[FENCER_B]      = false;
   hitOnTarg[FENCER_A]      = false;
   hitOffTarg[FENCER_A]     = false;
   hitOnTarg[FENCER_B]      = false;
   hitOffTarg[FENCER_B]     = false;
   lockOutOffTarg[FENCER_A] = true;
   lockOutOffTarg[FENCER_B] = true;
   weapon[FENCER_A]         = 0;
   weapon[FENCER_B]         = 0;
   lame[FENCER_A]           = 0;
   lame[FENCER_B]           = 0;
#ifdef DEBUG_L1
   Serial.println("reset hit values");
#endif
}

void resetCards()
{
#ifdef DISP_IR_CARDS_BOX
   red[FENCER_A]         = false;
   yellow[FENCER_A]      = false;
   red[FENCER_B]         = false;
   yellow[FENCER_B]      = false;
#ifdef DEBUG_L1
   Serial.println("reset cards");
#endif
#endif
#ifdef SERIAL_INDICATOR
   Serial.println("!RC");
#endif
}

void resetHits()
{
   resetHits(false);  
}

void resetHits(bool force)
{
   bool redispScore = false;

   // Decide if we need to redisplay the score
   if (hitDisplay != HIT_IDLE)
   {
      redispScore = true;
   }
   
   // Stop the hit display
   if ((redispScore
       && 
       (!maxSabreHits[FENCER_A] && !maxSabreHits[FENCER_B])) 
       || 
       force)
   {
      hitDisplayFlag[FENCER_A] = HIT_NONE;
      hitDisplayFlag[FENCER_B] = HIT_NONE;
      hitDisplay               = HIT_IDLE;

      if (force)
      {
         maxSabreHits[FENCER_A] = maxSabreHits[FENCER_B] = false;
#ifdef DEBUG_L1
         Serial.println("force reset hit display");
#endif
      }
      else
      {
#ifdef DEBUG_L1
         Serial.println("reset hit display");
#endif
      }

      // Only redisplay the score if the hit display has changed
      if (redispScore)
      {
         displayScore();
      }
   }
}

void resetLights()
{
   digitalWrite(onTargetA,  LOW);
   digitalWrite(onTargetB,  LOW);
#ifdef OFFTARGET_LEDS
   digitalWrite(offTargetA, LOW);
   digitalWrite(offTargetB, LOW);
#endif

   scDisplay[FENCER_A] = false;
   scDisplay[FENCER_B] = false;
#ifdef DEBUG_L1
   Serial.println("reset lights");
#endif
   // Remove the short circuit lights
   cardLeds      &= ~(A_SHORT | B_SHORT);
   ledFlag[FENCER_A] = ledFlag[FENCER_B] = LED_NONE;
   cardLedUpdate = true;

   // If sparring, turn off hit display
   if (inSpar())
   {
      resetHits();
   }
#ifdef SERIAL_INDICATOR
   Serial.println("!RL");
#endif
}

#ifdef EEPROM_STORAGE
void writeWeapon(Weapon w)
{
   EEPROM.update(NV_WEAPON, w);
}

Weapon readWeapon()
{
   int w = EEPROM.read(NV_WEAPON);

   switch (w)
   {
      case 0:
         return FOIL;

      case 1:
         return EPEE;

      case 2:
         return SABRE;

      default:
         return NONE;
   }
}

void writeState(BoutState state)
{
   switch (state)
   {
      case STA_SPAR:
         EEPROM.update(NV_STATE, 0);
         break;

      case STA_BOUT:
      case STA_STARTBOUT:
         EEPROM.update(NV_STATE, 1);
         break;

      case STA_STOPWATCH:
         EEPROM.update(NV_STATE, 2);
         break;

      default:
         break;
   }
}

BoutState readState()
{
   int m = EEPROM.read(NV_STATE);

   switch (m)
   {
      case 0:
         return STA_SPAR;

      case 1:
         return STA_STARTBOUT;

      case 2:
         return STA_STOPWATCH;

      default:
         return STA_NONE;
   }
}
#endif
