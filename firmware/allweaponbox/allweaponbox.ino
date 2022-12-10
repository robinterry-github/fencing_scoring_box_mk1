//===========================================================================//
//                                                                           //
//  Desc:    Arduino Code to implement a fencing scoring apparatus           //
//  Dev:     Wnew                                                            //
//  Date:    Nov         2012                                                //
//  Updated: Sept        2015                                                //
//  Updated: December 10 2022 Robin Terry, Skipton, UK                       //
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
//          13. Added in the serial port indicator (for repeater)            //
//          14. The timer shows 1/100 sec in last 9 seconds                  //
//          15. Support for a passivity timer and cards                      //
//          16. Support for polling the repeater for keypresses              //
//          17. Support for new 2023 passivity rule                          //
//===========================================================================//

//============
// #defines
//============
//#define DEBUG_L1               // Level 1 debug
//#define DEBUG_L2               // Level 2 debug
//#define DEBUG_L3               // Level 3 debug
//#define DEBUG_L4               // Level 4 debug
//#define DEBUG_L5               // Level 5 debug
//#define DEBUG_L6               // Level 6 debug
//#define DEBUG_L7               // Level 7 debug
//#define DEBUG_IR               // Debug the IR reception
//#define OFFTARGET_LEDS         // Define this if you have fitted the discrete off-target LEDs
#define ENABLE_DISPLAY           // Define this to enable 7-segment display and card LEDs -
                                 // For a simple hit indicator only box, you can undefine this                                  
#define ENABLE_REPEATER          // Send serial data out to an indicator application
#define ENABLE_IR                // Enable IR support

// Set default weapon to either FOIL, EPEE or SABRE
#define DEFAULT_WEAPON           FOIL

// If no display is enabled, then disable IR support
#ifndef ENABLE_DISPLAY
#undef ENABLE_IR
#endif

// Enable key translation for either IR or Bluetooth
#if defined(ENABLE_IR) || defined(ENABLE_REPEATER)
#define ENABLE_IR_TRANS
#endif

#ifdef ENABLE_DISPLAY
#define LOW_POWER                // Support low-power for battery operation
#endif

#ifdef ENABLE_IR
#define FREQUENT_IRPOLL          // Define this to increase the amount of IR polling     
#define IRLIB2                   // Use IRLib2 instead of IRRemote (IRLib2 is better, but bigger)

#ifdef IRLIB2
// IR receiver frame timeout for the IRLib2 library
// You might need to modify this for different IR handsets
#define IR_FRAMETIMEOUT     6000
#endif

#define MAX_DELAY_IR_REPEAT 300  // Initial period in milliseconds before repeats start
#define MAX_KEYPRESS_GAP    200  // Gap between keypresses which resets the IR state machine
#endif

#define ENABLE_STOPWATCH         // Enable the stopwatch
#define EEPROM_STORAGE           // Use EEPROM for storing values over power-off
//#define SPAR_INCR_SCORE        // Automatically increment score after a hit in sparring mode
#define BOUT_INCR_SCORE          // Automatically increment score after a hit in bout mode

#define PASSIVITY                // Support for passivity monitoring

#ifdef PASSIVITY
#define PASSIVITY_SIGNAL         // Enable signalling passivity timeout on the hit LEDs
#define NEW_PASSIVITY_2023       // Enable new 2023 passivity rule
#endif

#define PRITIMER_RANDOM          // Enable generation of random time for priority selection

/* Constants */
#define BUZZERTIME     (1000)    // Length of time the buzzer is kept on after a hit (ms)
#define TESTPOINTTIME  (500)     // Length of time the buzzer and lights are kept on when point testing (ms)
#define LIGHTTIME      (3000)    // Length of time the lights are kept on after a hit (ms)
#define BAUDRATE       (115200)  // Baud rate of the serial debug interface
#define ONESEC         (1000UL)
#define HUNDSEC        (10)
#define ONESEC_US      (1000000)
#define BUTTONSCAN     (200)              // Button scan period (ms)
#define BUTTONDEBOUNCE (BUTTONSCAN*10)    // Button debounce (in ms, but a whole number of scan periods)
#define BOUTTIME       (180)     // 3 minutes bout time
#define PRITIME        (60)      // 1 minute priority time
#define BREAKTIME      (60)      // 1 minute break time
#define HITDISPTIME    (200)     // Hit display flash time (ms)
#define SCOREFLASHTIME (1000)    // Score flashup display time (ms)
#define MAXSCORE       (99)
#define MAXSHORTCIRC   (3000)    // Short circuit persist time (ms)
#define MAXSABREHITS   (8)       // If a sabre fencer makes 8 hits in one bout, stop the bout
#define DIMDELAY       (5UL*60UL*ONESEC)  // Delay before starting to dim the LED display (ms)
#define DIMINTERVAL    (500)              // Interval between LED display dimming cycle steps (ms)
#define MAX_ENABLE_STOPWATCH  ((60UL*60UL)-1)    // Maximum stopwatch time (59:59)
#define REPEATERPOLL   (100)
#define MAXPISTE       30        // Maximum piste (1-MAXPISTE inclusive)

#ifdef PASSIVITY
#define MAX_PASSIVITY  (60UL)             // Passivity timer (seconds)
#define MAX_PASSIVITY_SIGNAL (100)        // Passivity signal time (ms)
#endif

#ifdef EEPROM_STORAGE
#define NV_WEAPON      (16)
#define NV_MODE        (17)
#define NV_PISTE       (18)
#endif

#ifdef PRITIMER_RANDOM
#define PRITIMER_RANGE (3000)
#define PRITIMER_MIN   (3000)
#endif

#define FENCER_A       (0)
#define FENCER_B       (1)

/* These bits match the hardware design */
#define A_NONE         (~A_ALL)
#define B_NONE         (~B_ALL)
#define A_YELLOW       (0x80)
#define A_RED          (0x40)
#define B_YELLOW       (0x20)
#define B_RED          (0x10)
#define A_SHORT        (0x08)
#define B_SHORT        (0x04)
#define A_ALL          (A_YELLOW | A_RED | A_SHORT)
#define B_ALL          (B_YELLOW | B_RED | B_SHORT)

#ifdef ENABLE_IR
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

#ifdef ENABLE_DISPLAY
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

#ifdef ENABLE_REPEATER
// Enable this when testing the repeater
//#define ENABLE_REPEATER_TEST

// Enable this to switch on polling for repeater key presses
#define REPEATER_POLLING

#ifdef REPEATER_POLLING
// Enable this to make the repeater do the keyclick
#define REPEATER_KEYCLICK
#endif
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

#ifdef ENABLE_IR
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
int  prevScore[2]    = { 0, 0 };

/* Score for just this bout - this can go negative
   if the referee removes points from the fencer */
int  scoreThisBout[2]= { 0, 0 };
int  cardLeds        = 0;
bool cardLedUpdate   = true;
long buttonScan      = 0;
bool buttonPressed   = false;
long buttonDebounce  = 0;
bool weaponChange    = false;
bool pisteChange     = false;
int  newPiste        = 1;
bool scoreFlash      = false;
long scoreFlashTimer = 0;
bool maxSabreHits[2] = { false, false };

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
   TIM_ENABLE_STOPWATCH
};

enum BoutState
{
   STA_NONE,
   STA_SPAR,
   STA_TP_CONTINUE,
   STA_BREAK,
   STA_TP_BREAK,
   STA_STARTBOUT,
   STA_BOUT,
   STA_TP_BOUT,
   STA_TP_ENDBOUT,
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
   SABRE   = 2,
};

enum Key
{
  K_NONE,
  K_BREAK,
  K_RESET_TIMER,
  K_START_BOUT,
  K_PRIORITY,
  K_CLEAR_SCORES,
  K_SWAP_SCORES,
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

// Weapon type default
Weapon weaponType    = DEFAULT_WEAPON;
Weapon newWeaponType = NONE;

//===============
// State machines
//===============
BoutState       boutState            = STA_NONE;
TimeState       timeState            = TIM_STOPPED;
HitDisplay      hitDisplay           = HIT_IDLE;
Priority        priState             = PRI_IDLE;
Reset           resetState           = RES_IDLE;
Key             lastKey              = K_NONE;
Disp            currentDisp          = DISP_NONE;
#ifdef ENABLE_STOPWATCH
StopWatchCount  swCount              = SW_UP; 
StopWatchEdit   swEdit               = SW_NONE;
#endif    
uint8_t         dimSetting           = DIM_BRIGHTEST;
uint8_t         dimCycle             = 0;
long            dimTimer             = 0;
long            lastKeyCode          = 0;
uint8_t         priFencer            = FENCER_A; 
#ifdef PRITIMER_RANDOM
unsigned long   priChooseRndMs       = 0;
unsigned long   priChooseTime        = 0;
#endif
bool            disableScore         = false;
Hit             hitDisplayFlag[2]    = { HIT_NONE, HIT_NONE };
uint8_t         lastHit              = 0;
int             ledFlag[2]           = { LED_NONE, LED_NONE };
const int       ledBits[][4]         = 
{
   { 0, A_YELLOW, A_RED, A_YELLOW | A_RED },
   { 0, B_YELLOW, B_RED, B_YELLOW | B_RED }
};

#ifdef DEBUG_L3
long loopNow = 0, loopCount = 0;
#endif

// 7-segment display
#ifdef ENABLE_DISPLAY
TM1637Display disp = TM1637Display(clkPin, dioPin);
#endif

// Patterns
#ifdef ENABLE_DISPLAY
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
   SEG_A | SEG_B | SEG_C | SEG_D | SEG_E | SEG_F | SEG_G,
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
  SEG_A | SEG_E | SEG_F,
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

// Passivity
#ifdef PASSIVITY
enum PassivityCard
{
   P_CARD_NONE, 
   P_CARD_YELLOW, 
   P_CARD_RED_1,
#ifndef NEW_PASSIVITY_2023
   P_CARD_RED_2
#endif
};

long passivityTimer       = 0;
bool passivityActive      = false;
bool passivitySignalled   = false;
long passivitySignalTimer = 0;
PassivityCard pCard[2]    = { P_CARD_NONE, P_CARD_NONE };
#endif

#ifdef ENABLE_REPEATER
long repeaterPollTime     = 0;
bool repeaterPresent      = true;
#endif

#ifdef ENABLE_REPEATER
void sendRepeater(String msg)
{
   if (repeaterPresent)
   {
      sendRepeaterRaw(msg);
   
      // Delay the polling
      if (repeaterPollTime <= REPEATERPOLL)
      {
         repeaterPollTime += REPEATERPOLL;
      }
   }
}

void sendRepeaterRaw(String msg)
{
   Serial.print(msg);
#ifdef ENABLE_REPEATER_TEST
   Serial.println(" ");
#endif
}
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
   return (boutState == STA_TP_BOUT || boutState == STA_TP_PRI || boutState == STA_TP_BREAK || boutState == STA_TP_CONTINUE) ? true:false;
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

bool priorityChoose()
{
   return (priState == PRI_CHOOSE) ? true:false;
}

bool priorityInactive()
{
   if (priState == PRI_END)
   {
      priState = PRI_IDLE;
   }
   return (priState == PRI_IDLE) ? true:false;
}

bool timerInactive()
{
   return (weaponType == SABRE) ? true:false;
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
#ifdef ENABLE_DISPLAY
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
#ifdef ENABLE_DISPLAY
      updateCardLeds(A_ALL | B_ALL);
#endif
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
      buzzer(false);
#ifdef ENABLE_DISPLAY
      updateCardLeds(0);
#endif
   }
   else
   {
      delay(1000);
   }
}

void displayState()
{
   displayState(boutState);
}

void displayState(enum BoutState state)
{
#ifdef ENABLE_DISPLAY
   setBrightness(DIM_BRIGHTEST);
   switch (state)
   {
      case STA_SPAR:
         disp.setSegments(sparDisp, 4, 0);
         break;

      case STA_BOUT:
      case STA_TP_BOUT:
      case STA_STARTBOUT:
      case STA_TP_ENDBOUT:
      case STA_TP_CONTINUE:
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
#ifdef ENABLE_STOPWATCH
        disp.setSegments(stopWatchDisp, 4, 0);
#endif
        break;
   }
#endif
}

void displayWeaponAndState()
{
#ifdef ENABLE_REPEATER
   indicateWeapon();
#endif
#ifdef ENABLE_DISPLAY
   displayWeapon(false);
   delay(1000);
   displayState();
   delay(1000);
#endif
}

void displayTouch(bool touchActive)
{
#ifdef ENABLE_STOPWATCH
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
#ifdef ENABLE_DISPLAY
               disp.setSegments(shortCircuit[FENCER_A] ? touchDisp[FENCER_A]:sparNoHit, 2, 0);
               disp.setSegments(shortCircuit[FENCER_B] ? touchDisp[FENCER_B]:sparNoHit, 2, 2);
#endif
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
}

//===================
// Display the short-circuit status on the 7-segment
//===================
void displayShortCircuit()
{
#ifdef ENABLE_DISPLAY
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

  // If priority choose display is active, then don't do this
  if (priorityChoose())
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
#ifdef ENABLE_DISPLAY
           setBrightness(DIM_BRIGHTEST);
           disp.showNumberDecEx(score[FENCER_A], 0, false, 2, 0);
           disp.showNumberDecEx(score[FENCER_B], 0, false, 2, 2);
#endif
          // Flash the hit LED of the fencer who won priority
          digitalWrite((priFencer == FENCER_A) ? onTargetA:onTargetB, HIGH);
          break;

        case HIT_OFF:
#ifdef ENABLE_DISPLAY
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
#ifdef ENABLE_DISPLAY
           setBrightness(DIM_BRIGHTEST);
#endif
           // Show scores if not off-target hit
           if (hitDisplayFlag[FENCER_A] != HIT_OFFTARGET
               &&
               hitDisplayFlag[FENCER_B] != HIT_OFFTARGET)
           {
#ifdef ENABLE_DISPLAY
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
#ifdef ENABLE_DISPLAY
                 disp.setSegments(sparOffHit[FENCER_A], 2, 0);
#endif
#ifndef OFFTARGET_LEDS
                 // Flash the 'on target A' LED
                 digitalWrite(onTargetA, HIGH);
#endif
              }
              else
              {
#ifdef ENABLE_DISPLAY
                 disp.setSegments(blankDisp, 2, 0);
#endif
              }
              if (hitDisplayFlag[FENCER_B] == HIT_OFFTARGET)
              {
#ifdef ENABLE_DISPLAY
                 disp.setSegments(sparOffHit[FENCER_B], 2, 2);
#endif
#ifndef OFFTARGET_LEDS
                 // Flash the 'on target B' LED
                 digitalWrite(onTargetB, HIGH);
#endif
              }
              else
              {
#ifdef ENABLE_DISPLAY
                 disp.setSegments(blankDisp, 2, 2);
#endif
              }
           }
           break;

        case HIT_OFF:
           // If we've reached maximum sabre hits, flash entire display
           if (maxSabreHits[FENCER_A] || maxSabreHits[FENCER_B])
           {
#ifdef ENABLE_DISPLAY
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
#ifdef ENABLE_DISPLAY
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
#ifdef ENABLE_DISPLAY
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
#ifdef ENABLE_DISPLAY
          setBrightness(DIM_BRIGHTEST);
          disp.setSegments(sparNoHit, 4, 0);
#endif
          break;

        case HIT_ON:
#ifdef ENABLE_DISPLAY
          setBrightness(DIM_BRIGHTEST);
#endif
          // Fencer A hit?
          switch (hitDisplayFlag[FENCER_A])
          {
             case HIT_NONE:
             default:
#ifdef ENABLE_DISPLAY
                disp.setSegments(sparNoHit, 2, 0);
#endif
                break;

             case HIT_ONTARGET:
#ifdef ENABLE_DISPLAY
                disp.setSegments(sparHit[FENCER_A], 2, 0);
#endif
                break;

             case HIT_OFFTARGET:
#ifdef ENABLE_DISPLAY
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
#ifdef ENABLE_DISPLAY
                disp.setSegments(sparNoHit, 2, 2);
#endif
                break;

             case HIT_ONTARGET:
#ifdef ENABLE_DISPLAY
                disp.setSegments(sparHit[FENCER_B], 2, 2);
#endif
                break;

             case HIT_OFFTARGET:
#ifdef ENABLE_DISPLAY
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
#ifdef ENABLE_DISPLAY
          setBrightness(DIM_BRIGHTEST);
#endif
          if (hitDisplayFlag[FENCER_A])
          {
#ifdef ENABLE_DISPLAY
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
#ifdef ENABLE_DISPLAY
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
#ifdef ENABLE_REPEATER
  if (repeaterPresent)
  {
     if (!disableScore)
     {
        char ind[10];
        /* Two sprintfs are needed because of an awful bug in the Arduino
           libraries which means that sprintf() can't take more than one argument! */
        sprintf(&ind[0], "*%02d",  score[FENCER_A]);
        sprintf(&ind[3], "%02d",   score[FENCER_B]);
        Serial.print(ind);
     }
     else
     {
        Serial.print("!HS");
     }
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

   // If priority choose display is active, then don't do this
   if (priorityChoose())
   {
      return;
   }

   // Is a short-circuit being displayed?
   else if (scDisplayActive())
   {
      return;
   }

   // Don't display if the timer is supposed to be inactive
   else if (timerInactive())
   {
      disp.setSegments(sparNoHit, 4, 0);
      return;
   }

   // Not temporarily displaying score?
   else if (!scoreFlash)
   {
#ifdef ENABLE_STOPWATCH
      if (swEdit != SW_NONE)
      {
#ifdef ENABLE_DISPLAY
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
#ifdef ENABLE_DISPLAY
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
#ifdef ENABLE_REPEATER
   if (repeaterPresent)
   {
      char ind[10];

      /* Two sprintfs are needed because of an awful bug in the Arduino
         libraries which means that sprintf() can't take more than one argument! */
#ifdef ENABLE_STOPWATCH
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
         Serial.print(ind);
#ifdef ENABLE_REPEATER_TEST
         Serial.println(" ");
#endif
      }
      else
#endif
      if (timerLast9s)
      {
         if (timerHund >= 0)
         {
            sprintf(&ind[0], ":%02d",  timerSecs);
            sprintf(&ind[3], "%02d",   timerHund);
            Serial.print(ind);
#ifdef ENABLE_REPEATER_TEST
            Serial.println(" ");
#endif
         }
      }
      else
      { 
         /* Two sprintfs are needed because of an awful bug in the Arduino
            libraries which means that sprintf() can't take more than one argument! */
         sprintf(&ind[0], "@%02d",  timerMins);
         sprintf(&ind[3], "%02d",   timerSecs);
         Serial.print(ind);
#ifdef ENABLE_REPEATER_TEST
         Serial.println(" ");
#endif
      }
   }
#endif
}

void displayPriority()
{
   // Is a short-circuit being displayed?
   if (scDisplayActive())
   {
      return;
   }
   else
   {
#ifdef ENABLE_DISPLAY
      disp.clear();
      setBrightness(DIM_BRIGHTEST);
#endif
      if (priFencer == FENCER_A)
      {
#ifdef ENABLE_DISPLAY
         disp.setSegments(priDisp, 2, 0);
#endif
         // Show fencer A has won priority on the hit LEDs
         if (priState == PRI_SELECTED || priState == PRI_END)
         {
            digitalWrite(onTargetA, HIGH);
            digitalWrite(onTargetB, LOW);
#ifdef ENABLE_REPEATER
            sendRepeater("$H3");
#endif
         }
      }
      else
      {
#ifdef ENABLE_DISPLAY
         disp.setSegments(priDisp, 2, 2);
#endif
         // Show fencer B has won priority on the hit LEDs
         if (priState == PRI_SELECTED || priState == PRI_END)
         {
            digitalWrite(onTargetA, LOW);
            digitalWrite(onTargetB, HIGH);
#ifdef ENABLE_REPEATER
            sendRepeater("$H4");
#endif
         }
      }
   }
   currentDisp = DISP_PRI;
}

void setBrightness(uint8_t dim)
{
#ifdef ENABLE_DISPLAY
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
#ifdef ENABLE_DISPLAY
   disp.setSegments(dimDisp[dimCycle], 4, 0);
#endif
}

bool restoreDisplayAfterSleep()
{
#ifdef ENABLE_DISPLAY
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

#ifdef ENABLE_DISPLAY
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
         displayPriority();
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
#ifdef ENABLE_REPEATER
bool waitSerial(int response[], int rxData[], long waitUs)
{
   long currentTime = micros();
   long serialWaitTime = micros() + waitUs;
   
   for (int i = 0;;)
   {
      if (Serial.available())
      {
         rxData[i] = Serial.read();
         
         /* Wildcard in response, or valid response character? */
         if (response[i] == '*' || rxData[i] == response[i])
         {
            i++;
            if (response[i] == '\0')
            {
               return true;
            }
         }

         /* Bad response */
         else
         {
            break;
         }
      }

      /* Check the timeout at the end - check for wraparound */
      if (micros() > serialWaitTime || micros() < currentTime)
      {
         break;
      }
   }
   return false;
}

bool sendRepeaterGo()
{
#ifdef EEPROM_STORAGE
   char msg[6];

   sprintf(msg, "!GO%02d", readPiste());
   sendRepeaterRaw(msg);
#else
   sendRepeaterRaw("!GO01");
#endif
   
   /* Wait one second for initial "OK" response from repeater */
   int  response[] = { 'O', 'K', '\0' };
   int  rxData[]   = { 0, 0 };

   return waitSerial(response, rxData, ONESEC_US);
}
#endif

void setup() 
{
#ifdef DEBUG_ALL
   Serial.begin(BAUDRATE);
   while (!Serial)
      ;
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
   digitalWrite(offTargetA, LOW);
   pinMode(offTargetA, OUTPUT);
   digitalWrite(offTargetA, LOW);
   pinMode(offTargetB, OUTPUT);
#endif
   digitalWrite(onTargetA, LOW);
   pinMode(onTargetA,  OUTPUT);
   digitalWrite(onTargetB, LOW);
   pinMode(onTargetB,  OUTPUT);
   digitalWrite(buzzerPin, LOW);
   pinMode(buzzerPin,  OUTPUT);

   // this optimises the ADC to make the sampling rate quicker
   adcOpt();

   // Turn on the 7-segment LED display
#ifdef ENABLE_DISPLAY
   setBrightness(DIM_BRIGHTEST);
#endif

#ifdef EEPROM_STORAGE
   Weapon w = readWeapon();

   // If the weapon type is unset, default
   if (w == NONE)
   {
      weaponType = DEFAULT_WEAPON;
      writeWeapon(weaponType);
   }
   else
   {
      weaponType = w;
   }

#if defined(ENABLE_DISPLAY) || defined(ENABLE_REPEATER)
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
   boutState  = STA_SPAR;
   writeState(boutState);
#endif
#else
   weaponType = DEFAULT_WEAPON;
   boutState  = STA_SPAR;
#endif
#ifdef ENABLE_REPEATER
   Serial.begin(BAUDRATE);
   Serial.println("");

   /* Send the "!GO" message to search for a repeater */
   if (sendRepeaterGo())
   {
      repeaterPresent = true;

      /* Initial poll delayed for one second */
      repeaterPollTime = millis()+ONESEC;
   }

   /* Repeater is not present */
   else
   {
      repeaterPresent  = false;
   }
#ifdef ENABLE_REPEATER_TEST
   repeaterPresent = true;
#endif   
   indicateWeapon();
#endif

   // Restart the box
   restartBox();
#ifdef ENABLE_IR
   irRecv.enableAutoResume(irBuffer);
#ifdef IR_FRAMETIMEOUT
   irRecv.setFrameTimeout(IR_FRAMETIMEOUT);
#endif
   irRecv.enableIRIn();
#endif
}

#ifdef ENABLE_REPEATER
void indicateWeapon()
{
   switch (weaponType)
   {
      case FOIL:
      default:
         sendRepeater("!TF");
         break;

      case EPEE:
         sendRepeater("!TE");
         break;

      case SABRE:
         sendRepeater("!TS");
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
   resetValues();
   resetLights();
   resetCards();
   resetPassivity();

   hitDisplay               = HIT_IDLE;
   resetState               = RES_IDLE;
   priFencer                = FENCER_A;
   lastHit                  = 0; 
   hitDisplayFlag[FENCER_A] = hitDisplayFlag[FENCER_B] = HIT_NONE;
   lockOutOffTarg[FENCER_A] = lockOutOffTarg[FENCER_B] = true;
   score[FENCER_A]          = prevScore[FENCER_A]      = 0;
   score[FENCER_B]          = prevScore[FENCER_B]      = 0;
   shortCircuit[FENCER_A]   = shortCircuit[FENCER_B]   = 0;
   scoreThisBout[FENCER_A]  = scoreThisBout[FENCER_B]  = 0;
   maxSabreHits[FENCER_A]   = maxSabreHits[FENCER_B]   = false;

   displayWeapon();

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
      case STA_TP_ENDBOUT:
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
#ifdef ENABLE_REPEATER
   sendRepeater("!PC");
#endif
#ifdef ENABLE_DISPLAY
   displayState(STA_PRIORITY);
#endif
   delay(1000);
   priState   = PRI_CHOOSE;
   priFencer  = FENCER_A;
#ifdef PRITIMER_RANDOM
   // Use the microsecond timer to set a random time for priority selection
   priChooseRndMs = (((micros() ^ 0x49A0F44BUL) * 0x3CD1EE57UL) % PRITIMER_RANGE) + PRITIMER_MIN;
   priChooseTime  = millis();
#endif
}

//=============
// Timer functions
//=============
void setTimer(int time)
{
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
}

//=============
// Restart main timer
//=============
void restartTimer()
{
    if (timeState == TIM_STOPPED)
    {
        timerStart = true;
        timerMs    = millis();
#ifdef ENABLE_REPEATER
        sendRepeater("!CR");
#endif        
    }
}

// Passivity processing
void restartPassivity()
{
#ifdef PASSIVITY
   passivityActive    = true;
   passivitySignalled = false;
#endif
}

void startPassivity()
{
#ifdef PASSIVITY
   restartPassivity();
#ifdef ENABLE_REPEATER
   sendRepeater("!VS");
#endif
#endif
}

void clearPassivity()
{
#ifdef PASSIVITY
   passivityActive = passivitySignalled = false;
   passivityTimer  = passivitySignalTimer = 0;
#ifdef ENABLE_REPEATER
   sendRepeater("!VC");
#endif
#endif
}

void signalPassivity(bool on)
{
#ifdef PASSIVITY
   if (passivityActive)
   {
      if (on)
      {
#ifdef PASSIVITY_SIGNAL
         digitalWrite(onTargetA, HIGH);
         digitalWrite(onTargetB, HIGH);
#endif 
         passivitySignalTimer = millis();
         passivitySignalled   = true;
#ifdef ENABLE_REPEATER
         sendRepeater("!VT");
#endif
      }
      else
      {
#ifdef PASSIVITY_SIGNAL
         digitalWrite(onTargetA, LOW);
         digitalWrite(onTargetB, LOW);
#endif
         passivityActive = false;
         passivityTimer = passivitySignalTimer = 0;
      }
   }
#endif
}

void checkPassivity()
{
#ifdef PASSIVITY
   if (passivityActive)
   {
      if (passivitySignalTimer > 0)
      {
         if (millis() > (passivitySignalTimer+MAX_PASSIVITY_SIGNAL))
         {
            signalPassivity(false);
         }
      }
      else if (passivityTimer >= MAX_PASSIVITY)
      {
         signalPassivity(true);
      }
   }
#endif
}

void awardPCard(int fencer, PassivityCard pass)
{
   pCard[fencer] = pass;
#ifdef ENABLE_REPEATER
   switch (pass)
   {
      case P_CARD_NONE:
         sendRepeater("+" + String(fencer) + "0");
         break;

      case P_CARD_YELLOW:
         sendRepeater("+" + String(fencer) + "1");
         break;

      case P_CARD_RED_1:
         sendRepeater("+" + String(fencer) + "2");
         break;
#ifndef NEW_PASSIVITY_2023
      case P_CARD_RED_2:
         sendRepeater("+" + String(fencer) + "3");
         break;
#endif
   }
#endif
}

void awardPCard(int fencer)
{
   switch (pCard[fencer])
   {
      case P_CARD_NONE:
         awardPCard(fencer, P_CARD_YELLOW);
         break;

       case P_CARD_YELLOW:
          awardPCard(fencer, P_CARD_RED_1);
          break;

       case P_CARD_RED_1:
#ifdef NEW_PASSIVITY_2023
          /* 2023 rule - P-yellow, P-red, P-black */
          awardPCard(fencer, P_CARD_NONE);
#else
          /* Pre-2023 rule - P-yellow, P-red1, P-ref2, P-black */
          awardPCard(fencer, P_CARD_RED_2);
#endif
          break;

#ifndef NEW_PASSIVITY_2023
       case P_CARD_RED_2:
          awardPCard(fencer, P_CARD_NONE);
          break;
#endif

       default:
          break;
   }
}

void awardPassivity()
{
#ifdef PASSIVITY
   if (inBout() /* && passivitySignalled */)
   {
#ifndef NEW_PASSIVITY_2023
      if (score[FENCER_A] < score[FENCER_B])
      {
         awardPCard(FENCER_A);
      }
      else if (score[FENCER_B] < score[FENCER_A])
      {
         awardPCard(FENCER_B);
      }
      else
#endif
      {
         awardPCard(FENCER_A);
         awardPCard(FENCER_B);
      }
   }
#endif
}

//===================
// Increment main timer manually
//===================
int incTimer(int inc = 1)
{
   if (timeState == TIM_STOPPED)
   {
      /* Reached current top limit? */
      if (timerMax > 0 && timer >= timerMax)
      {
         return 0;
      }

      /* Are we into hundredths of seconds? */
      timerLast9s = (timerMins == 0 && timerSecs < 10) ? true:false;

      /* Increment in hundredths of seconds */
      if (timerLast9s)
      {
         timerInterval = HUNDSEC;
         
         timerHund += inc;
         if (timerHund >= 100)
         {
            timerHund -= 100;
            timerSecs++;
            timer++;
         }
      }

      /* Increment in seconds */
      else
      {
         timerInterval = ONESEC;
         timer += inc;
         timerHund  = 0;
         
         timerSecs += inc;
      }
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
   return 0;
}

//===================
// Decrement main timer manually
//===================
int decTimer(int dec = 1)
{
   if (timeState == TIM_STOPPED)
   {
      /* Reached bottom limit? */
      if (timerMins == 0 && timerSecs == 0 && timerHund == 0)
      {
         return 0;
      }

      /* Are we into hundredths of seconds? */
      timerLast9s = (timerMins == 0 && timerSecs < 10) ? true:false;

      /* Decrement in hundredths of seconds */
      if (timerLast9s)
      {
         timerInterval = HUNDSEC;

         timerHund -= dec;
         if (timerHund < 0)
         {
            if (timerSecs > 0)
            {
               timerHund += 100;
               timerSecs--;
               timer--;
            }
         }
      }

      /* Decrement in seconds */
      else
      {
         timerInterval = ONESEC;
         timer -= dec;
         timerHund = 0;

         if (timerSecs < dec)
         {
            if (timerMins > 0)
            {
               timerMins--;
               timerSecs = 60+timerSecs-dec;
            }
         }
         else
         {
            timerSecs -= dec;
         }
      }
#ifdef DEBUG_L6
      Serial.println("decrement timer");
#endif
      return 1;
   }
   return 0;
}

//===================
// Add one to fencer score
//===================
void addScore(int fencer)
{
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
}

//===================
// Subtract one from fencer score
//===================
void subScore(int fencer)
{
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
}

void resetScore()
{
   score[FENCER_A] = prevScore[FENCER_A] = scoreThisBout[FENCER_A] = 0;
   score[FENCER_B] = prevScore[FENCER_B] = scoreThisBout[FENCER_B] = 0;
   displayScore();
#ifdef DEBUG_L1
   Serial.println("reset scores");
#endif
   if (inBout())
   {
      delay(1000);
      displayTime();
   }
}

void swapScores()
{
   if (inBoutOrSpar())
   {
      int tScore              = score[FENCER_A];
      int tPrevScore          = prevScore[FENCER_A];
      int tScoreThisBout      = scoreThisBout[FENCER_A];
   
      score[FENCER_A]         = score[FENCER_B];
      prevScore[FENCER_A]     = prevScore[FENCER_B];
      scoreThisBout[FENCER_A] = scoreThisBout[FENCER_B];

      // Redisplay the updated score
      score[FENCER_B]         = tScore;
      prevScore[FENCER_B]     = tPrevScore;
      scoreThisBout[FENCER_B] = tScoreThisBout;

      if (currentDisp == DISP_TIME)
      {
         // If time was showing, then only display score briefly
         displayScore();
         delay(1000);
         displayTime();
      }
      else
      {
         displayScore();
      }
   }
}

//===================
// Update the card LEDs (yellow/red)
//==================
void updateCardLeds(int Leds)
{
#ifdef ENABLE_DISPLAY
#ifdef DEBUG_L1
   Serial.print("card LEDs ");
   Serial.println(cardLeds, HEX);
#else
   digitalWrite(latchPin, LOW); 
   shiftOut(dataPin, clockPin, LSBFIRST, Leds); 
   digitalWrite(latchPin, HIGH);
#endif
#endif
#ifdef ENABLE_REPEATER
   if (!inStopWatch())
   {
      int LedsA = 0;
      if (cardLeds & A_YELLOW)
      {
         LedsA |= 1;
      }
      if (cardLeds & A_RED)
      {
         LedsA |= 2;
      }
      if (cardLeds & A_SHORT)
      {
         LedsA |= 4;
      }
      String cardA = "?0" + String(LedsA);
      sendRepeater(cardA);
      int LedsB = 0;
      if (cardLeds & B_YELLOW)
      {
         LedsB |= 1;
      }
      if (cardLeds & B_RED)
      {
         LedsB |= 2;
      }
      if (cardLeds & B_SHORT)
      {
         LedsB |= 4;
      }
      String cardB = "?1" + String(LedsB);
      sendRepeater(cardB);
   }
#endif
   cardLedUpdate = false;
}

//===================
// Control the buzzer
//===================
void buzzer(bool buzz)
{
   buzzer(buzz, true);
}

void buzzer(bool buzz, bool indicate)
{
#ifdef ENABLE_REPEATER
   if (repeaterPresent)
   {
      if (!indicate)
      {
         digitalWrite(buzzerPin,  buzz ? HIGH:LOW);
      }
      else
      {
         sendRepeater(buzz ? "!Z1":"!Z0");
      }
   }
   else
#endif
   {
      digitalWrite(buzzerPin,  buzz ? HIGH:LOW);
   }
}

void keyClick()
{
#ifdef REPEATER_KEYCLICK
   if (repeaterPresent)
   {
      sendRepeater("!KC");
      delay(5);
   }
   else
#endif   
   { 
      buzzer(true, false);
      delay(5);
      buzzer(false, false);
   }
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
   if (timerInactive())
   {
      return 0;
   }
   else if (timerLast9s)
   {
      /* Counting down in hundredths of seconds */
      if (timerHund < 0)
      {
#ifdef PASSIVITY
         if (passivityActive)
         {
            if (passivityTimer < MAX_PASSIVITY)
            {
               passivityTimer++;
            }
         }
#endif     
         /* Has the timer expired? */
         if (--timerSecs < 0)
         {
            timer         = timerSecs = 0;
            timerLast9s   = false;
            timerInterval = ONESEC;
         }
         else
         {
            timerHund += 100;
         }
      }
      else
      {
         timerHund -= timerGap;
      }
   }
   else if (timer > 0)
   {
#ifdef PASSIVITY
      if (passivityActive)
      {
         if (passivityTimer < MAX_PASSIVITY)
         {
            passivityTimer++;
         }
      }
#endif
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
}

//==========================
// IR/Bluetooth key handling
//==========================
#ifdef ENABLE_IR_TRANS
void transIR(unsigned long key)
{
   static unsigned long delayIRRepeat = 0;
   static unsigned long keypressGap   = 0;
#ifdef DEBUG_L1
   Serial.print("key:");
   Serial.println(key, HEX);
#endif

   /* Has it been a while since the last keypress? */
   if (millis()-keypressGap > MAX_KEYPRESS_GAP)
   {
      lastKeyCode   = 0;
      delayIRRepeat = millis();
   }
   keypressGap = millis();
   
   /* Wake up display, if asleep -
      don't process the key otherwise */
   if (restoreDisplayAfterSleep())
   {
      return;
   }

   // Record last key
   if (key != 0xFFFFFFFF)
   {
      if (lastKeyCode != key)
      {
         lastKeyCode = key;
      }
   }

  //=============================
  // Hobby Components handset and repeater control
  //=============================
  switch (key)
  {
  case 'B': case 'b': // BACK key on repeater control
     if (timeState != TIM_STOPPED)
     {
        keyClick();
        timeState = TIM_STOPPED;
        displayTime();
        break;
     }

     /* Otherwise drop through */
     
  case 0xFF6897: // *
  case '*':
  case 'u': case 'd': // Repeater only
     if (priorityInactive())
     {
        // In SPARRING mode? Go into BOUT mode
        if (inSpar())
        {
           // Go into BOUT mode
           keyClick();

           // Go backwards for the 'd' key
           if (key == 'd')
           {
              startStopWatch();
           }
           else
           {
              startBout();
           }
        }
#ifdef ENABLE_STOPWATCH
        // In ENABLE_STOPWATCH mode?
        else if (inStopWatch())
        {
           if (timeState == TIM_STOPPED)
           {
              // If the timer has been stopped, reset it without starting it
              if (timerMins != swMins || timerSecs != swSecs)
              {
                 keyClick();
                 resetStopWatch();
              }
              else
              {        
                 // Stopwatch already at zero, so go into SPARRING mode
                 keyClick();
                 if (key == '$')
                 {
                    startBout();
                 }
                 else
                 {
                    startSpar();
                 }
              }
           }
        }
#endif
        // Only allow these keys if the timer is not running
        else if (timeState == TIM_STOPPED)
        {
           switch (boutState)
           {                 
              case STA_STARTBOUT:
#ifdef ENABLE_STOPWATCH
                 // Bout not started - go into ENABLE_STOPWATCH mode
                 keyClick();
                 if (key == '$')
                 {
                    startSpar();
                 }
                 else
                 {
                    startStopWatch();
                 }
#else
                 keyClick();
                 startSpar();
#endif
                 break;

              case STA_TP_CONTINUE:
                // In the middle of a bout - continue the bout
                keyClick();
                continueBout(STA_TP_BOUT);
                break;

             case STA_TP_BOUT:
                // At the start of a bout - end bout
                keyClick();
                continueBout(STA_TP_ENDBOUT);
                break;   

              case STA_TP_ENDBOUT:
              default:
                 // After continue - restart
                 keyClick();
                 resetPassivity();
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

#ifndef PRITIMER_RANDOM
     // In priority select mode, this key will stop selection (same as #)
     else if (priorityChoose())
     {
        keyClick();
        priState = PRI_SELECTED;
     }
#endif
     break;
  case 0xFF629D: // 2
  case '2':
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
              case STA_STARTBOUT:
              case STA_TP_BOUT:
              case STA_TP_ENDBOUT:
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
  case 'L': case 'l':
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
#ifdef ENABLE_DISPLAY
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
#ifdef ENABLE_STOPWATCH
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
#ifdef ENABLE_DISPLAY
                 // Flash stopwatch minutes once
                 disp.setSegments(blankDisp, 2, 0);
                 delay(250);
                 displayTime();
#endif
              }
           }
        }
#endif
#ifndef PRITIMER_RANDOM
        // In priority select mode, this key will stop selection (same as #)
        else if (priorityChoose())
        {
           keyClick();
           priState = PRI_SELECTED;
        }
#endif
        resetHits();
     }

#ifndef PRITIMER_RANDOM
     // In priority select mode, this key will stop selection (same as #)
     else if (priorityChoose())
     {
        keyClick();
        priState = PRI_SELECTED;
     }
#endif
     break;
  case 0xFF38C7: // OK
  case 'K': case 'k':
  case 'P': // Repeater only
     if (priorityInactive())
     {
#ifdef ENABLE_STOPWATCH
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
              timeState = TIM_ENABLE_STOPWATCH;
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
                 case STA_TP_ENDBOUT:
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
              
              /* If the new score is different from the old one, clear passivity */
              if (
                    (prevScore[FENCER_A] != score[FENCER_A])
                    ||
                    (prevScore[FENCER_B] != score[FENCER_B])
                 )
              {
                 clearPassivity();
                 prevScore[FENCER_A] = score[FENCER_A];
                 prevScore[FENCER_B] = score[FENCER_B];
              }

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
                    boutState = STA_TP_CONTINUE;
                    resetLights();
                    break;
                    
                 case STA_TP_CONTINUE:
                    //setTimer(BOUTTIME);
                    continueBout(STA_TP_BOUT);
                    boutState = STA_STARTBOUT;
                    //resetCards();
                    //displayTime();
#ifdef DEBUG_L1
                    Serial.println("bout continuing");
#endif
#ifdef ENABLE_REPEATER
                    //sendRepeater("!BC");
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
                       startPassivity();
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
                 case STA_TP_ENDBOUT:
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
#ifdef ENABLE_REPEATER
                       sendRepeater("!BR");
#endif
                       restartPassivity();
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

#ifndef PRITIMER_RANDOM
     // In priority select mode, this key will stop selection (same as #)
     else if (priorityChoose())
     {
        keyClick();
        priState = PRI_SELECTED;
     }
#endif
     lastKey = K_OK;
     break;
  case 0xFF5AA5: // RIGHT
  case 'R': case 'r':
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
#ifdef ENABLE_DISPLAY
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
#ifdef ENABLE_STOPWATCH
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
#ifdef ENABLE_DISPLAY
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

#ifndef PRITIMER_RANDOM
     // In priority select mode, this key will stop selection (same as #)
     else if (priorityChoose())
     {
        keyClick();
        priState = PRI_SELECTED;
     }
#endif
     break;
  case 0xFF4AB5: // DOWN
  case 'D':
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
#ifdef ENABLE_STOPWATCH
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

#ifndef PRITIMER_RANDOM
     // In priority select mode, this key will stop selection (same as #)
     else if (priorityChoose())
     {
        keyClick();
        priState = PRI_SELECTED;
     }
#endif
     break;
  case 0xFF9867: // 0
  case '0': case 'G': case 'W': // Repeater only
     if (priorityInactive())
     {
        if (inBoutOrBreak())
        {
           if (timeState == TIM_STOPPED && resetState == RES_IDLE)
           {
              keyClick();
              startBreak();
           }
        }
        lastKey = K_BREAK;
        resetHits();
     }

#ifndef PRITIMER_RANDOM
     // In priority select mode, this key will stop selection (same as #)
     else if (priorityChoose())
     {
        keyClick();
        priState = PRI_SELECTED;
     }
#endif
     break;
  case 0xFF18E7: // UP
  case 'U':
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
#ifdef ENABLE_STOPWATCH
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

#ifndef PRITIMER_RANDOM
     // In priority select mode, this key will stop selection (same as #)
     else if (priorityChoose())
     {
        keyClick();
        priState = PRI_SELECTED;
     }
#endif
     break;
  case 0xFFB04F: // #
  case '#': case 'F':
#ifdef ENABLE_STOPWATCH
     if (inStopWatch())
     {
        // Stopwatch running? If so, reset and restart it
        if (timeState == TIM_ENABLE_STOPWATCH)
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
        if (timeState == TIM_STOPPED && resetState == RES_IDLE)
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
  case '1':
     if (priorityInactive())
     {
        // If in a bout, wind back timer
        if (inBoutOrBreak())
        {
           if (timeState == TIM_STOPPED)
           {
              if (decTimer())
              {
                 keyClick();
#ifdef DEBUG_L1
                 Serial.println("timer back");
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
  case '3':
     if (priorityInactive())
     {
        // If in a bout, wind forward timer
        if (inBoutOrBreak())
        {
           if (timeState == TIM_STOPPED)
           {
              if (incTimer())
              {
                 keyClick();
#ifdef DEBUG_L1
                 Serial.println("timer forward");
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
  case '4':
     if (priorityInactive())
     {
        if (inBoutOrSpar())
        {
           if (!disableScore)
           {
              if (timeState == TIM_STOPPED)
              {
                 if (resetState == RES_IDLE)
                 {
                    keyClick();
                    swapScores();
                    resetHits();
                    lastKey = K_SWAP_SCORES;
                 }
              }
           }
        }
     }
     break;
  case 0xFF02FD: // 5
  case '5':
     if (priorityInactive())
     {
        if (inBoutOrSpar())
        {
           if (!disableScore)
           {
              if (timeState == TIM_STOPPED)
              {
                 if (resetState == RES_IDLE)
                 {
                    keyClick();
                    resetScore();
                    resetHits();
                    lastKey = K_CLEAR_SCORES;
                 }
              }
           }
        }
        lastKey = K_CLEAR_SCORES;
     }
     break;
  case 0xFFC23D: // 6
  case '6':
     break;
  case 0xFFE01F: // 7
  case '7':
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
  case '8':
     if (priorityInactive())
     {
        if (inBout())
        {  
           if (timeState == TIM_STOPPED)
           {
              keyClick();
#ifdef ENABLE_REPEATER
#ifdef PASSIVITY
              if (repeaterPresent)
              {
                 awardPassivity();
#ifdef DEBUG_L1
                 Serial.println("award passivity card");
#endif
              }
              else
#endif
#endif
              {
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
     }
     lastKey = K_CLEAR_CARDS;
     resetHits();
     break;
  case 0xFF906F: // 9
  case '9':
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
  case 0xFFFFFFFF: // IR repeat
     // Only certain keys can repeat
     if (lastKey == K_WIND_BACK 
         || 
         lastKey == K_WIND_FORWARD)
     {
        /* Handle the initial delay in IR repeat */
        if (millis()-delayIRRepeat > MAX_DELAY_IR_REPEAT)
        {
           // Recursively call with last keypress
           transIR(lastKeyCode);
        }
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
#ifdef ENABLE_REPEATER
   sendRepeater("!BS");
   sendRepeater("*0000");
#endif
   priState                = PRI_IDLE;
#ifdef ENABLE_STOPWATCH
   swEdit                  = SW_NONE;
#endif
   disableScore            = false;
   score[FENCER_A]         = prevScore[FENCER_A] = 0; 
   score[FENCER_B]         = prevScore[FENCER_B] = 0;
   lastHit                 = 0;
   maxSabreHits[FENCER_A]  = false;
   maxSabreHits[FENCER_B]  = false;
   scoreThisBout[FENCER_A] = 0;
   scoreThisBout[FENCER_B] = 0;
   boutState               = STA_STARTBOUT;
   resetState              = RES_IDLE;
   hitDisplay              = HIT_IDLE;
   resetHits();
#ifdef EEPROM_STORAGE
   writeState(boutState);
#endif
#ifdef ENABLE_DISPLAY
   setBrightness(DIM_BRIGHTEST);
   disp.setSegments(boutDisp, 4, 0);
#endif
   resetCards();
   resetLights();
   delay(1000);
   setTimer(BOUTTIME);
   displayTime();
#ifdef DEBUG_L1
   Serial.println("bout start mode");
#endif
}

void continueBout(enum BoutState newBoutState)
{
   /* Don't reset passivity */
   priState = PRI_IDLE;
   setTimer(BOUTTIME);
   boutState = newBoutState;
   if (boutState != STA_TP_CONTINUE)
   {
      displayTime();
#ifdef ENABLE_REPEATER
      sendRepeater("!BC");
#endif
   }
   else
   {
      displayScore();
   }
   
   scoreThisBout[FENCER_A] = scoreThisBout[FENCER_B] = 0;
   resetCards();
#ifdef DEBUG_L1
   Serial.println("continue bout");
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
#ifdef ENABLE_REPEATER
   sendRepeater("!BE");
#endif
   buzzerTimeout();
   displayScore();
#ifdef DEBUG_L1
   Serial.println("end of bout");
#endif
   clearPassivity();
}

//=============
// Start a priority period
//=============
void startPriority()
{
#ifdef DEBUG_L1
   Serial.println(
      priFencer == FENCER_B ? 
      "priority mode (fencer B)":
      "priority mode (fencer A)");
#endif
   disableScore = false;
   displayPriority();
   delay(1000);
   resetLights();
   displayScore();
#ifdef ENABLE_REPEATER
   sendRepeater(priFencer == FENCER_A ? "!P0":"!P1");
#endif
   delay(1000);
   priState = PRI_IDLE;
   setTimer(PRITIME);
   displayTime();
   boutState = STA_TP_PRI;
   clearPassivity();
}

void endPriority()
{
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
#ifdef ENABLE_REPEATER
   sendRepeater("!PE");
   sendRepeater(priFencer == FENCER_A ? "$H3":"$H4");
#endif
   startHitDisplay();
   displayScore();
}

//=============
// Start sparring (no timer)
//=============
void startSpar()
{
#ifdef ENABLE_REPEATER
   sendRepeater("!SS");
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
   resetState      = RES_IDLE;
#ifdef ENABLE_STOPWATCH
   swEdit          = SW_NONE;
#endif
   disableScore    = true;
   score[FENCER_A] = score[FENCER_B] = 0;
   lastHit         = 0;
#ifdef ENABLE_DISPLAY
   displayState();
#endif
   resetValues();
   resetCards();
   resetPassivity();
   resetLights();
   clearPassivity();
   updateCardLeds(0);
   delay(1000);
   displayScore();
}

void startBreak()
{
#ifdef ENABLE_REPEATER
   sendRepeater("!RS");
#endif
#ifdef DEBUG_L1
   Serial.println("1 minute break"); 
#endif
   boutState = STA_BREAK;
#ifdef ENABLE_DISPLAY
   displayState();
#endif
   delay(1000);
   setTimer(BREAKTIME);
   restartTimer();
   clearPassivity();
}

void startStopWatch()
{
#ifdef ENABLE_STOPWATCH
   boutState = STA_STOPWATCH;
#ifdef ENABLE_REPEATER
   sendRepeater("!WS");
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
#ifdef ENABLE_STOPWATCH
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

void resetStopWatch()
{
   resetStopWatch(false, false);
}

void resetStopWatch(bool restart)
{
   resetStopWatch(restart, true);
}

void resetStopWatch(bool restart, bool lightLeds)
{
#ifdef ENABLE_STOPWATCH
#ifdef DEBUG_L4
   Serial.println("reset stopwatch");
#endif
   timeState = TIM_STOPPED;
   setTimer(swMins*60+swSecs);
   displayTime();

   if (restart)
   {
      restartTimer();
      lightLeds = true;
   }

   if (lightLeds)
   {
      digitalWrite(onTargetA, HIGH);
      digitalWrite(onTargetB, LOW);
      updateCardLeds(A_ALL);
#ifdef ENABLE_REPEATER
     sendRepeater("$S0");
#endif
   }
   else
   {
      digitalWrite(onTargetA, LOW);
      digitalWrite(onTargetB, LOW);
      updateCardLeds(0);
#ifdef ENABLE_REPEATER
      sendRepeater("!RL");
#endif
   }
#ifdef ENABLE_REPEATER
   sendRepeater("!WR");
#endif
#endif
}

void stopWatchLeds()
{
#ifdef ENABLE_STOPWATCH
  uint8_t stopWatchLeds;

  // Toggle the two hit LEDs
  stopWatchLeds = timerSecs % 2;

  // Illuminate the LEDs until stopwatch expires
  if (swCount == SW_END)
  {
     digitalWrite(onTargetA, LOW);
     digitalWrite(onTargetB, LOW);
     updateCardLeds(0);
#ifdef ENABLE_REPEATER
     sendRepeater("!RL");
#endif
  }
  else
  {
     digitalWrite(onTargetA, stopWatchLeds ? LOW:HIGH);
     digitalWrite(onTargetB, stopWatchLeds ? HIGH:LOW);
     updateCardLeds(stopWatchLeds ? B_ALL:A_ALL);
#ifdef ENABLE_REPEATER
     sendRepeater(stopWatchLeds ? "$S1":"$S0");
#endif
  }
#endif
}

int runStopWatch()
{
   int ret = 0;
#ifdef ENABLE_STOPWATCH
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
            if (timer < MAX_ENABLE_STOPWATCH)
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
#ifdef ENABLE_REPEATER
               sendRepeater("!WW");
#endif
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
               swMins    = swSecs = 0;
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

#ifdef ENABLE_IR
void pollIR()
{
#ifdef IRLIB2
   irDecode.value = 0;
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

   res.value = 0;
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
}

void startHitDisplay()
{
   startHitDisplay(HITDISPTIME);
}

void startHitDisplay(long time)
{
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

void doWeapon()
{
   // Don't read the analogue pins if in stopwatch mode or choosing priority
   if (!inStopWatch() && !priorityChoose())
   {
      // Read analogue pins
      weapon[FENCER_A] = analogRead(weaponPinA);
      weapon[FENCER_B] = analogRead(weaponPinB);
      lame[FENCER_A]   = analogRead(lamePinA);
      lame[FENCER_B]   = analogRead(lamePinB);

      switch (weaponType)
      {
         case EPEE:
            epee();
            break;

         case FOIL:
            foil();
            break;
            
         case SABRE:
            // We need a ground pin measurement for sabre
            ground[FENCER_A] = analogRead(groundPinA);
            ground[FENCER_B] = analogRead(groundPinB);
            sabre();
            break;
            
         default:
            break;
      }
#ifdef DEBUG_L3
      if (loopCount == 0) 
      {
         loopNow = micros();
      }
      loopCount++;
      if ((micros() - loopNow) >= ONESEC_US) 
      {
         Serial.print(loopCount);
         Serial.print(" readings in 1 sec");
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
         loopCount = 0;
      }
#endif
   }
}

//============
// Main loop
//============
void loop() 
{
   int    expired, timerGap;
   long   now;

   for (;;) 
   {
      // Do processing for a given weapon
      doWeapon();

      // Check LED display dimming and 'screen saver'
#ifdef ENABLE_DISPLAY
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

#ifdef ENABLE_IR
      // Poll the IR to see if a key has been pressed
      pollIR();
#endif
      // Do processing for a given weapon
      doWeapon();
      repeaterPollForKey();

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

#ifdef BOUT_INCR_SCORE
            /* Increment the score -
               only do this for epee, as for foil and
               sabre there are other considerations,
               like right of way, to decide upon before
               a point is awarded - so the referee has to
               award the point or not
            */
            if (weaponType == EPEE)
            {
               if (hitOnTarg[FENCER_A])
               {
                  addScore(FENCER_A);
               }
               if (hitOnTarg[FENCER_B])
               {
                  addScore(FENCER_B);
               }
            }
#endif
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

#ifdef ENABLE_IR
      // Poll the IR to see if a key has been pressed
#ifdef FREQUENT_IRPOLL
      pollIR();
#endif
#endif
      // Do processing for a given weapon
      doWeapon();

      // Is the box changing weapon?
      if (weaponChange)
      {
         if (newWeaponType == NONE) {
            switch (weaponType)
            {
               case FOIL:
               case NONE:
                  newWeaponType = EPEE;
                  break;

               case EPEE:
                  newWeaponType = SABRE;
                  break;

               case SABRE:
                  newWeaponType = FOIL;
                  break;
            }
         }
         if (newWeaponType != NONE) {
            weaponType = newWeaponType;

            /* Save the new weapon type */
#ifdef EEPROM_STORAGE
            writeWeapon(weaponType);
#endif
#ifdef ENABLE_REPEATER
            indicateWeapon();
#endif
            restartBox();
            newWeaponType = NONE;
         }
         weaponChange = false;
      }

      /* Is the box changing piste? */
      if (pisteChange)
      {
          /* The box only stores the piste - it doesn't need to know it otherwise */
#ifdef EEPROM_STORAGE
          writePiste(newPiste);
#endif
          pisteChange = false;
      }

#ifdef ENABLE_IR
      // Poll the IR to see if a key has been pressed
#ifdef FREQUENT_IRPOLL
      pollIR();
#endif
#endif
      // Do processing for a given weapon
      doWeapon();

      // Alternate priority display
      if (priorityChoose())
      {
         // Oscillate between fencers when choosing priority
         priFencer = (priFencer == FENCER_A) ? FENCER_B:FENCER_A;
#ifdef ENABLE_DISPLAY
         displayPriority();
#endif
#ifdef PRITIMER_RANDOM
         // Stop choosing when the timer expires
         if (millis() - priChooseTime >= priChooseRndMs)
         {
            priState      = PRI_SELECTED;
            priChooseTime = priChooseRndMs = 0;
         }
#endif
      }

      // Priority selection complete?
      if (priState == PRI_SELECTED)
      {
         startPriority();
      }

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

#ifdef ENABLE_IR
      // Poll the IR to see if a key has been pressed
#ifdef FREQUENT_IRPOLL
      pollIR();
#endif
#endif
      // Do processing for a given weapon
      doWeapon();
      repeaterPollForKey();

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

#ifdef PASSIVITY
      if (timeState != TIM_STOPPED)
      {
         checkPassivity();
      }
#endif
      // Do processing for a given weapon
      doWeapon();

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
#ifdef ENABLE_REPEATER
               sendRepeater("<01");
#endif
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
#ifdef ENABLE_REPEATER
         sendRepeater("<00");
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
#ifdef ENABLE_REPEATER
               sendRepeater("<11");
#endif
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
#ifdef ENABLE_REPEATER
         sendRepeater("<10");
#endif
      }

      // Update the 4 card LEDs (yellow/red for A/B)
      if (cardLedUpdate)
      {
         updateCardLeds(cardLeds);
      }

#ifdef ENABLE_IR
      // Poll the IR to see if a key has been pressed
#ifdef FREQUENT_IRPOLL
      pollIR();
#endif
#endif
      // Do processing for a given weapon
      doWeapon();
      repeaterPollForKey();

      // Alternate priority display
      if (priorityChoose())
      {
         // Oscillate between fencers when choosing priority
         priFencer = (priFencer == FENCER_A) ? FENCER_B:FENCER_A;
#ifdef ENABLE_DISPLAY
         displayPriority();
#endif
#ifdef PRITIMER_RANDOM
         // Stop choosing when the timer expires
         if (millis() - priChooseTime >= priChooseRndMs)
         {
            priState      = PRI_SELECTED;
            priChooseTime = priChooseRndMs = 0;
         }
#endif
      }

      // Priority selection complete?
      if (priState == PRI_SELECTED)
      {
         startPriority();
      }

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
            
      // Do processing for a given weapon
      doWeapon();

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
                     weaponChange    = true;
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
               weaponChange     = false;
               buttonDebounce = 0;
#ifdef DEBUG_L1
               Serial.println("button released");
#endif
            }
            buttonScan = millis();
         }
      }

#ifdef ENABLE_IR
      // Poll the IR to see if a key has been pressed
#ifdef FREQUENT_IRPOLL
      pollIR();
#endif
#endif
      // Do processing for a given weapon
      doWeapon();

      // Start the timer for the start of a bout
      if (timerStart)
      {
          timerMs = millis();
          timerStart = false;
          displayTime();
          switch (boutState)
          {
             case STA_STOPWATCH:
                timeState = TIM_ENABLE_STOPWATCH;
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
             case STA_TP_ENDBOUT:
             case STA_TP_PRI:
             case STA_TP_BREAK:
                break;
          }
      }

#ifdef ENABLE_IR
      // Poll the IR to see if a key has been pressed
#ifdef FREQUENT_IRPOLL
      pollIR();
#endif
#endif
      // Do processing for a given weapon
      doWeapon();
      repeaterPollForKey();

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
#ifdef ENABLE_STOPWATCH
            if (timeState == TIM_ENABLE_STOPWATCH)
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
                     clearPassivity();
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
                     continueBout(STA_TP_CONTINUE);
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
#ifdef PASSIVITY
      if (timeState != TIM_STOPPED)
      {
         checkPassivity();
      }
#endif
      repeaterPollForKey();      
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
#ifdef ENABLE_REPEATER
      if (repeaterPresent)
      {
         String ind = String("");

         if (hitOnTarg[FENCER_A])
         {
            ind += String("$H1\n");
         }
         if (hitOnTarg[FENCER_B])
         {
            ind += String("$H2\n");
         }
         if (hitOffTarg[FENCER_A])
         {
            ind += String("$O0\n");
         }
         if (hitOffTarg[FENCER_B])
         {
            ind += String("$O1\n");
         }
         Serial.print(ind);
      }
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
   cardLeds      = 0;
   cardLedUpdate = true;

#ifdef DEBUG_L1
   Serial.println("reset cards");
#endif
#ifdef ENABLE_REPEATER
   sendRepeater("?C0");
   sendRepeater("?D0");
#endif
}

void resetPassivity()
{
   /* Reset the passivity cards */
   awardPCard(FENCER_A, P_CARD_NONE);
   awardPCard(FENCER_B, P_CARD_NONE);
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
#ifdef ENABLE_REPEATER
   sendRepeater("!RL");
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

void writePiste(int p)
{
   EEPROM.update(NV_PISTE, p);
}

int readPiste()
{
   int p = EEPROM.read(NV_PISTE);
   if (p <= 0 || p > MAXPISTE)
   {
      p = 1;
   }
   return p;
}

void writeState(BoutState state)
{
   switch (state)
   {
      case STA_SPAR:
         EEPROM.update(NV_MODE, 0);
         break;

      case STA_BOUT:
      case STA_STARTBOUT:
         EEPROM.update(NV_MODE, 1);
         break;

      case STA_STOPWATCH:
         EEPROM.update(NV_MODE, 2);
         break;

      default:
         break;
   }
}

BoutState readState()
{
   int m = EEPROM.read(NV_MODE);

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

void repeaterPollForKey()
{
   /* Poll the repeater after a period of time */
   if (millis() >= repeaterPollTime)
   {
#ifdef REPEATER_POLLING
      if (repeaterPresent)
      {
         /* Only write if there are enough spaces in the write buffer */
         if (Serial.availableForWrite() > 10)
         {
            repeaterPollTime = millis()+REPEATERPOLL;
            Serial.flush();
            Serial.print("/?");
#ifdef ENABLE_REPEATER_TEST
            Serial.println(" ");
#endif
            int response[] = { '/', '*', '*', '*', '\0' };
            int rxData[] = { 0, 0, 0, 0 };

            /* Wait for 1ms for a key back from the repeater, if any */
            if (waitSerial(response, rxData, 1000))
            {
               unsigned long key = (unsigned long) rxData[1];

               /* Valid keypress? ('-' means 'no key') */
               if (key != '-')
               {
                  /* Is the repeater app changing weapon? */
                   switch (key)
                   {
                      case 'f':
                         /* Change weapon to foil */
                         newWeaponType = FOIL;
                         weaponChange  = true;
                         break;

                      case 'e':
                         /* Change weapon to epee */ 
                         newWeaponType = EPEE;
                         weaponChange  = true;
                         break;

                      case 's':
                         /* Change weapon to sabre */
                         newWeaponType = SABRE;
                         weaponChange  = true;
                         break;

                      case 'p':
                        /* Change piste */
                        if (rxData[2] >= '0' && rxData[2] <= '9'
                            &&
                            rxData[3] >= '0' && rxData[3] <= '9')
                        {
                           newPiste = (rxData[2]-'0')*10 + (rxData[3]-'0');
                           pisteChange = true;
                        }

                      default:
                         /* Keypress from repeater */
                         transIR(key);
                         break;
                   }
               }
            }
         }
      }
#endif
   }
}
