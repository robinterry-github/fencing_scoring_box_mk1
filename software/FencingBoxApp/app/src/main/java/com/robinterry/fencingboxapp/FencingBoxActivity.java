package com.robinterry.fencingboxapp;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import android.app.Activity;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.view.GestureDetectorCompat;
import androidx.core.view.WindowCompat;

import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;

import android.app.PendingIntent;
import android.app.UiModeManager;
import android.content.SharedPreferences;
import android.net.wifi.WifiManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.os.Build;
import android.os.IBinder;
import android.os.Vibrator;
import android.view.GestureDetector;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;
import android.view.View;
import android.view.WindowManager;
import android.view.Window;
import android.view.MotionEvent;
import android.view.KeyEvent;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.BatteryManager;
import java.lang.String;
import java.lang.Integer;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.io.IOException;
import com.robinterry.fencingboxapp.FencingBoxDisplay.FaceType;

import android.util.Log;
import com.robinterry.fencingboxapp.databinding.ActivityMainBinding;
import com.robinterry.fencingboxapp.databinding.ActivityMainLandBinding;

/* Import constant values */
import com.robinterry.constants.C;

@SuppressWarnings("ALL")
public class FencingBoxActivity extends AppCompatActivity
        implements ServiceConnection, SerialListener,
        GestureDetector.OnGestureListener, GestureDetector.OnDoubleTapListener {
    private static final String TAG = FencingBoxActivity.class.getSimpleName();
    private Box box;
    private Box[] demoBox;
    private int whichDemo = 0;
    private FencingBoxKeys keyHandler;
    private enum Connected {False, Pending, True};
    public enum PassivityCard {None, Yellow, Red1, Red2};
    public static enum Orientation {Portrait, Landscape};
    public enum Motion {None, Up, Down, Left, Right};
    public enum Platform {Phone, TV};
    public enum VibrationState {Off, On};
    public Orientation orientation = Orientation.Portrait;
    public Motion motion = Motion.None;
    public Platform platform = Platform.Phone;
    public static final boolean useBroadcast = true;
    private boolean batteryDangerActive = false;
    private boolean batteryDangerFlash = false;
    public int stopwatchHours = 0;
    private boolean scoreHidden = false;
    public static final Integer hitAColor = 0xFFFF0000;
    public static final Integer hitBColor = 0xFF00FF00;
    public static final Integer inactiveColor = 0xFFE0E0E0;
    public static final Integer yellowCardColor = 0xFFFFFF00;
    public static final Integer redCardColor = 0xFFFF0000;
    public FencingBoxActivity thisActivity;
    public ConstraintLayout layout;
    private static Connected serialConnected = Connected.False;
    private UsbSerialPort usbSerialPort;
    private SerialService service;
    private boolean initialStart = true;
    private boolean isResumed = false;
    private SerialSocket socket;
    private boolean soundMute = false;
    private boolean displayPaused = false;
    private FencingBoxSound sound, click;
    private boolean networkOnline = false;
    private VibrationState vibrationState = VibrationState.On;

    /* View bindings */
    private ActivityMainBinding portBinding = null;
    private ActivityMainLandBinding landBinding = null;
    private View mainBinding;

    /* Commands from the fencing scoring box */
    private final byte cmdMarker = '!';
    private final byte clockMarker1 = '@';
    private final byte clockMarker2 = ':';
    private final byte scoreMarker = '*';
    private final byte hitMarker = '$';
    private final byte cardMarker = '?';
    private final byte passivityCardMarker = '+';
    private final byte shortCircuitMarker = '<';
    private final byte pollMarker = '/';
    private boolean monitorStarted = false;
    private int batteryLvl = 0;
    private String currentTime;
    private NetworkBroadcast bc = null;
    private boolean txFullStarted = false;
    public static WifiManager.MulticastLock wifiLock;
    public static FencingBoxList boxList;
    private GestureDetectorCompat gesture;
    private Menu optionsMenu = null;
    private static boolean optionsMenuActive = false;

    /* Shared preferences */
    public SharedPreferences pref;
    public SharedPreferences.Editor editor;

    public ActionBar actionBar;

    public FencingBoxActivity() {
        thisActivity = this;
        final BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (com.robinterry.fencingboxapp.Constants.INTENT_ACTION_GRANT_USB.equals(intent.getAction())) {
                    Boolean granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false);
                    Log.i(TAG, "GRANT_USB intent " + granted + " received, trying to connect");
                    connect(granted);
                }
            }
        };

        /* Various fencing box related variables */
        box = new Box(1);

        /* Set up the demo display */
        demoBox = new Box[] {
            new Box(),
            new Box()
        };
        demoBox[0].piste = 1;
        demoBox[1].piste = 2;
        if (C.DOC_DISPLAY) {
            demoBox[0].hitA = Box.Hit.OnTarget;
            demoBox[0].hitB = Box.Hit.OnTarget;
            demoBox[0].timeMins = "00";
            demoBox[0].timeSecs = "00";
            demoBox[0].timeHund = "00";
            demoBox[0].scoreA = "00";
            demoBox[0].scoreB = "00";
            demoBox[0].period = 8;
            demoBox[0].mode = Box.Mode.Demo;
            demoBox[0].cardA = Box.redCardBit | Box.yellowCardBit | Box.shortCircuitBit;
            demoBox[0].cardB = Box.redCardBit | Box.yellowCardBit | Box.shortCircuitBit;
            demoBox[0].passivityTimer = 60;
            demoBox[0].passivityActive = true;
            demoBox[0].pCard[0] = PassivityCard.Red2;
            demoBox[0].pCard[1] = PassivityCard.Yellow;
            demoBox[0].priA = true;
            demoBox[0].priB = true;
            demoBox[0].priIndicator = false;

            demoBox[1].hitA = demoBox[0].hitA;
            demoBox[1].hitB = demoBox[0].hitB;
            demoBox[1].timeMins = demoBox[0].timeMins;
            demoBox[1].timeSecs = demoBox[0].timeSecs;
            demoBox[1].timeHund = demoBox[0].timeHund;
            demoBox[1].scoreA = demoBox[0].scoreA;
            demoBox[1].scoreB = demoBox[0].scoreB;
            demoBox[1].period = demoBox[0].period;
            demoBox[1].mode = Box.Mode.Demo;
            demoBox[1].cardA = demoBox[0].cardA;
            demoBox[1].cardB = demoBox[0].cardB;
            demoBox[1].passivityTimer = 0;
            demoBox[1].passivityActive = false;
            demoBox[1].pCard[0] = demoBox[0].pCard[0];
            demoBox[1].pCard[1] = demoBox[0].pCard[1];
            demoBox[1].priA = demoBox[0].priA;
            demoBox[1].priB = demoBox[0].priB;
            demoBox[1].priIndicator = demoBox[0].priIndicator;
        } else {
            demoBox[0].hitA = Box.Hit.OnTarget;
            demoBox[0].hitB = Box.Hit.OnTarget;
            demoBox[0].timeMins = "01";
            demoBox[0].timeSecs = "24";
            demoBox[0].timeHund = "00";
            demoBox[0].scoreA = "02";
            demoBox[0].scoreB = "08";
            demoBox[0].period = 3;
            demoBox[0].mode = Box.Mode.Demo;
            demoBox[0].cardA = Box.redCardBit | Box.yellowCardBit | Box.shortCircuitBit;
            demoBox[0].cardB = Box.redCardBit | Box.yellowCardBit | Box.shortCircuitBit;
            demoBox[0].passivityTimer = 55;
            demoBox[0].passivityActive = true;
            demoBox[0].pCard[0] = PassivityCard.Yellow;
            demoBox[0].pCard[1] = PassivityCard.Red2;
            demoBox[0].priA = true;
            demoBox[0].priB = true;
            demoBox[0].priIndicator = true;

            demoBox[1].hitA = Box.Hit.OnTarget;
            demoBox[1].hitB = Box.Hit.None;
            demoBox[1].timeMins = "00";
            demoBox[1].timeSecs = "27";
            demoBox[1].scoreA = "14";
            demoBox[1].scoreB = "11";
            demoBox[1].period = 5;
            demoBox[1].mode = Box.Mode.Demo;
            demoBox[1].cardA = 0;
            demoBox[1].cardB = 0;
            demoBox[1].pCard[0] = PassivityCard.None;
            demoBox[1].pCard[1] = PassivityCard.None;
            demoBox[1].priA = false;
            demoBox[1].priB = false;
            demoBox[1].priIndicator = false;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        if (C.DEBUG) {
            Log.d(TAG, "onCreate start " + savedInstanceState);
        }
        super.onCreate(savedInstanceState);

        try {
            if (bc == null) {
                bc = new NetworkBroadcast(this);
            }

            /* Get the Wifi multicast lock to allow UDP broadcasts/multicasts to be received */
            WifiManager wifi = (WifiManager) getSystemService(Context.WIFI_SERVICE);
            if (wifi != null) {
                wifiLock = wifi.createMulticastLock(TAG);
                wifiLock.acquire();
            }
        } catch (IOException e) {
            Log.e(TAG, "Unable to create broadcast socket, error " + e);
            bc = null;
        }

        // Get the view bindings for each orientation layout
        landBinding = ActivityMainLandBinding.inflate(getLayoutInflater());
        portBinding = ActivityMainBinding.inflate(getLayoutInflater());

        // Get the current orientation
        orientation = getCurrentOrientation();
        if (orientation == Orientation.Landscape) {
            Log.i(TAG, "initial orientation is landscape");
            mainBinding = landBinding.getRoot();
            layout = (ConstraintLayout) mainBinding;
        } else {
            Log.i(TAG, "initial orientation is portrait");
            mainBinding = portBinding.getRoot();
            layout = (ConstraintLayout) mainBinding;
        }

        // Set up shared preferences
        pref = getSharedPreferences("fencing_box", Context.MODE_PRIVATE);
        editor = pref.edit();

        // Find out what vibration state was stored previously - if anything
        synchronized (pref) {
            String vibState = pref.getString("fencing_box_vibration_state", null);
            if (vibState == null) {
                vibrationState = VibrationState.On;
                synchronized (editor) {
                    editor.putString("fencing_box_vibration_state", "on");
                    editor.apply();
                }
            } else if (vibState.equals("off")) {
                vibrationState = VibrationState.Off;
            } else {
                vibrationState = VibrationState.On;
            }
        }

        actionBar = getSupportActionBar();

        /* Display handler */
        box.disp = new FencingBoxDisplay(this, box, layout, orientation, portBinding, landBinding);

        /* List of other fencing boxes on the network */
        boxList = new FencingBoxList(this, box, box.piste);

        // Set the content view from the view binding for the new orientation
        setContentView(mainBinding);

        // Set up the display
        box.disp.createLights();
        box.disp.setupText(box, layout, orientation);

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                try {
                    /* Set status bar to entirely black */
                    Window window = thisActivity.getWindow();
                    window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
                    window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
                    window.setStatusBarColor(Color.BLACK);

                    /* Make layout take up entire area of screen, including under the navigation bar */
                    WindowCompat.setDecorFitsSystemWindows(window, false);

                    /* Set action bar (title bar/app bar) to entirely black */
                    ActionBar bar = getSupportActionBar();
                    bar.setBackgroundDrawable(new ColorDrawable(Color.BLACK));
                } catch (Exception e) {
                    Log.e(TAG, "Unable to change status or action bar color");
                }
            }
        });
        box.disp.showUI();

        sound = new FencingBoxSound(2400,
                48000,
                FencingBoxSound.waveformType.SQUARE,
                32767,
                getApplicationContext());
        sound.enable();

        click = new FencingBoxSound(2400,
                48000,
                FencingBoxSound.waveformType.SQUARE,
                32767,
                5,
                getApplicationContext());
        click.enable();

        gesture = new GestureDetectorCompat(this, this);
        gesture.setOnDoubleTapListener(this);

        /* Set up keypress handlers for either TV operation or phone operation */
        UiModeManager uiModeMgr = (UiModeManager) getSystemService(UI_MODE_SERVICE);
        if (uiModeMgr.getCurrentModeType() == Configuration.UI_MODE_TYPE_TELEVISION) {
            platform = Platform.TV;

            /* TVs don't vibrate */
            vibrationState = VibrationState.Off;

            /* The 'Select' button on the Weapon Select activity
            is invisible when the app is run on an Android TV platform */
            WeaponSelect.setButtonInvisible(true);

            /* Keypress handler when it's a TV platform */
            keyHandler = new FencingBoxKeys() {
                @Override
                public String processKey(Character c) {
                    Log.i(TAG, "TV keypress " + c.toString());
                    if (isSerialConnected() && C.SEND_KEYS_TO_BOX) {
                        /* If the fencing scoring box is connected, send the key to that */
                        String msg = "/" + c.toString() + "--";
                        return msg;
                    } else {
                        /* When the fencing scoring box is not connected, or when
                           configured not to send keys to the box even when connected */
                        switch (c) {
                            case 'D': /* Down */
                            case 'U': /* Up */
                            case 'G': /* Guide */
                            case 'M': /* Menu */
                            case 'K': /* OK, Play/Pause */
                                if (!optionsMenuActive) {
                                    try {
                                        /* Show the menu */
                                        openOptionsMenu();
                                        onOptionsItemSelected(optionsMenu.findItem(R.id.piste_select));
                                        optionsMenuActive = true;
                                    } catch (Exception e) {
                                        /* Ignore if the options menu has not been created yet */
                                    }
                                }
                                break;
                            case 'B': /* Back */
                                if (optionsMenuActive) {
                                    /* Hide the menu */
                                    getSupportActionBar().closeOptionsMenu();
                                }
                                optionsMenuActive = false;
                                break;
                            case 'L': /* Left */
                            case 'R': /* Right */
                            case '*': /* Search */
                            case 'u': /* Page up, Channel up */
                            case 'd': /* Page down, Channel down */
                            case 'W': /* Rewind */
                            case 'F': /* Fast forward */
                            case 'C': /* Record */
                            case '0': /* Numeric keys */
                            case '1':
                            case '2':
                            case '3':
                            case '4':
                            case '5':
                            case '6':
                            case '7':
                            case '8':
                            case '9':
                            default:
                                break;
                        }
                    }
                    return "";
                }
            };
            Log.i(TAG, "TV key handler created " + keyHandler);
        } else {
            platform = Platform.Phone;
            /* Keypress handler when it's a phone platform */
            keyHandler = new FencingBoxKeys() {
                @Override
                public String processKey(Character c) {
                    Log.i(TAG, "Phone keypress " + c.toString());
                    if (isSerialConnected() && C.SEND_KEYS_TO_BOX) {
                        /* If the fencing scoring box is connected, send the key to that */
                        String msg = "/" + c.toString() + "--";
                        return msg;
                    } else {
                        /* When the fencing scoring box is not connected */
                        switch (c) {
                            case 'D': /* Down */
                            case 'U': /* Up */
                            case 'G': /* Guide */
                            case 'M': /* Menu */
                            case 'K': /* OK, Play/Pause */
                                if (!optionsMenuActive) {
                                    try {
                                        /* Show the menu */
                                        openOptionsMenu();
                                        onOptionsItemSelected(optionsMenu.findItem(R.id.piste_select));
                                        optionsMenuActive = true;
                                    } catch (Exception e) {
                                        /* Ignore if the options menu has not been created yet */
                                    }
                                }
                                break;
                            case 'B': /* Back */
                                if (optionsMenuActive) {
                                    /* Hide the menu */
                                    getSupportActionBar().closeOptionsMenu();
                                    optionsMenuActive = false;
                                }
                                break;
                            case 'L': /* Left */
                            case 'R': /* Right */
                            case '*': /* Search */
                            case 'u': /* Page up, Channel up */
                            case 'd': /* Page down, Channel down */
                            case 'W': /* Rewind */
                            case 'F': /* Fast forward */
                            case 'C': /* Record */
                            case '0': /* Numeric keys */
                            case '1':
                            case '2':
                            case '3':
                            case '4':
                            case '5':
                            case '6':
                            case '7':
                            case '8':
                            case '9':
                            default:
                                break;
                        }
                    }
                    return "";
                }
            };
        }
        if (C.DEBUG) {
            Log.d(TAG, "onCreate end");
        }
    }

    @Override
    protected void onStart() {
        if (C.DEBUG) {
            Log.d(TAG, "onStart start");
        }
        super.onStart();

        /* Start RX and TX broadcast threads */
        if (bc != null) {
            bc.start();
        }

        displayPaused = false;
        startService(new Intent(this, SerialService.class));
        orientation = getCurrentOrientation();
        box.disp.setupText(box, orientation);

        if (box.isModeDemo()) {
            box.disp.hideUI();
            showDemo();
        } else {
            if (box.isModeNone()) {
                box.disp.showUI();
            } else {
                box.disp.hideUI();
            }
            setHitLights();
            setScore();
            setClock();
            setCard();
            setPriority();
            setPassivity();
            setPassivityCard();
        }
        if (!monitorStarted) {
            startSystemMonitor();
            startBoxMonitor();
            monitorStarted = true;
        }
        if (!txFullStarted) {
            startTxFull();
            txFullStarted = true;
        }
        if (C.DEBUG) {
            Log.d(TAG, "onStart end");
        }
    }

    @Override
    protected void onStop() {
        if (C.DEBUG) {
            Log.d(TAG, "onStop start");
        }
        super.onStop();
        displayPaused = true;
        sound.soundOff(true);
        if (C.DEBUG) {
            Log.d(TAG, "onStop end");
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            box.disp.hideUI();
        } else {
            box.disp.showUI();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (serialConnected != Connected.False) {
            disconnect(true);
        }
        stopService(new Intent(this, SerialService.class));
        sound.soundOff(true);
        landBinding = null;
        portBinding = null;
        try {
            wifiLock.release();
        } catch (Exception e) {
            /* Ignore */
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if ("android.hardware.usb.action.USB_DEVICE_ATTACHED".equals(intent.getAction())) {
            Log.i(TAG, "USB device attached");
            onSerialConnect();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        displayPaused = false;
        if (!isResumed) {
            isResumed = true;
            bindService(new Intent(this, SerialService.class), this, Context.BIND_AUTO_CREATE);
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (box.isModeNone()) {
            box.disp.showUI();
        } else if (!box.isModeDemo()) {
            box.disp.hideUI();
        }
        sound.soundOff();
    }

    @Override
    protected void onPause() {
        super.onPause();
        displayPaused = true;
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        sound.soundOff(true);
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        box.disp.setupText(box, orientation);
        setHitLights();
        setScore();
        setClock();
        setCard();
        setPriority();
        setPassivity();
        setPassivityCard();
        sound.soundOff();
    }

    @Override
    public void onConfigurationChanged(Configuration newConf) {
        super.onConfigurationChanged(newConf);
        /* Checks the orientation of the screen */
        orientation = getCurrentOrientation();
        if (orientation == Orientation.Landscape) {
            Log.i(TAG, "orientation is now landscape");
            mainBinding = landBinding.getRoot();
            orientation = Orientation.Landscape;
        } else {
            Log.i(TAG, "orientation is now portrait");
            mainBinding = portBinding.getRoot();
            orientation = Orientation.Portrait;
        }

        // Set the content view from the view binding for the new orientation
        setContentView(mainBinding);
        layout = (ConstraintLayout) mainBinding;

        // Display the screen in the new orientation
        if (box.isModeNone()) {
            box.disp.showUI();
        } else if (!box.isModeDemo()) {
            box.disp.hideUI();
        }
        box.disp.setupText(box, layout, orientation);
        if (box.isModeDemo()) {
            showDemo(whichDemo);
        } else if (box.isModeDisplay()) {
            try {
                Box b = boxList.currentBox();
                box.disp.displayBox(b);
            } catch (Exception e) {
                Log.i(TAG, "No box to display");
            }
        } else {
            setHitLights();
            setScore();
            setClock();
            setCard();
            setPriority();
            setPassivity();
            setPassivityCard();
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        gesture.onTouchEvent(event);
        return super.onTouchEvent(event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        Log.i(TAG, "key code " + keyCode);
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_DOWN:
            case KeyEvent.KEYCODE_D:
                synchronized (keyHandler) {
                    keyHandler.addKey('D');
                }
                return true;
            case KeyEvent.KEYCODE_CHANNEL_DOWN:
            case KeyEvent.KEYCODE_PAGE_DOWN:
                synchronized (keyHandler) {
                    keyHandler.addKey('d');
                }
                return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_L:
                synchronized (keyHandler) {
                    keyHandler.addKey('L');
                }
                return true;
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_U:
                synchronized (keyHandler) {
                    keyHandler.addKey('U');
                }
                return true;
            case KeyEvent.KEYCODE_CHANNEL_UP:
            case KeyEvent.KEYCODE_PAGE_UP:
                synchronized (keyHandler) {
                    keyHandler.addKey('u');
                }
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
            case KeyEvent.KEYCODE_R:
                synchronized (keyHandler) {
                    keyHandler.addKey('R');
                }
                return true;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
            case KeyEvent.KEYCODE_K:
                synchronized (keyHandler) {
                    keyHandler.addKey('K');
                }
                return true;
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                synchronized (keyHandler) {
                    keyHandler.addKey('P');
                }
                return true;
            case KeyEvent.KEYCODE_STAR:
            case KeyEvent.KEYCODE_SEARCH:
                synchronized (keyHandler) {
                    keyHandler.addKey('*');
                }
                return true;
            case KeyEvent.KEYCODE_POUND:
                synchronized (keyHandler) {
                    keyHandler.addKey('#');
                }
                return true;
            case KeyEvent.KEYCODE_BACK:
            case KeyEvent.KEYCODE_B:
                synchronized (keyHandler) {
                    keyHandler.addKey('B');
                }
                return true;
            case KeyEvent.KEYCODE_VOLUME_MUTE:
                soundMute = (soundMute == true) ? false : true;
                return super.onKeyUp(keyCode, event);
            case KeyEvent.KEYCODE_MEDIA_RECORD:
                synchronized (keyHandler) {
                    keyHandler.addKey('C');
                }
                return true;
            case KeyEvent.KEYCODE_MEDIA_REWIND:
                synchronized (keyHandler) {
                    keyHandler.addKey('W');
                }
                return true;
            case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
                synchronized (keyHandler) {
                    keyHandler.addKey('F');
                }
                return true;
            case KeyEvent.KEYCODE_GUIDE:
                keyHandler.addKey('G');
                return true;
            case KeyEvent.KEYCODE_MENU:
                synchronized (keyHandler) {
                    keyHandler.addKey('M');
                }
                return true;
            case KeyEvent.KEYCODE_0:
                synchronized (keyHandler) {
                    keyHandler.addKey('0');
                }
                return true;
            case KeyEvent.KEYCODE_1:
                synchronized (keyHandler) {
                    keyHandler.addKey('1');
                }
                return true;
            case KeyEvent.KEYCODE_2:
                synchronized (keyHandler) {
                    keyHandler.addKey('2');
                }
                return true;
            case KeyEvent.KEYCODE_3:
                synchronized (keyHandler) {
                    keyHandler.addKey('3');
                }
                return true;
            case KeyEvent.KEYCODE_4:
                synchronized (keyHandler) {
                    keyHandler.addKey('4');
                }
                return true;
            case KeyEvent.KEYCODE_5:
                synchronized (keyHandler) {
                    keyHandler.addKey('5');
                }
                return true;
            case KeyEvent.KEYCODE_6:
                synchronized (keyHandler) {
                    keyHandler.addKey('6');
                }
                return true;
            case KeyEvent.KEYCODE_7:
                synchronized (keyHandler) {
                    keyHandler.addKey('7');
                }
                return true;
            case KeyEvent.KEYCODE_8:
                synchronized (keyHandler) {
                    keyHandler.addKey('8');
                }
                return true;
            case KeyEvent.KEYCODE_9:
                synchronized (keyHandler) {
                    keyHandler.addKey('9');
                }
                return true;
            default:
                Log.i(TAG, "unrecognised keycode " + keyCode);
                return super.onKeyUp(keyCode, event);
        }
    }

    @Override
    public void onBackPressed() {
        keyHandler.addKey('B');
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu, menu);
        this.optionsMenu = menu;
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuItem item;
        item = menu.findItem(R.id.menu_demo);
        if (box.isModeDemo() || box.isModeNone() || C.DOC_DISPLAY) {
            /* Change the options menu item to show "Show demo" or "Clear demo" */
            item.setVisible(true);
            item.setEnabled(true);
            item.setTitle(
                    box.isModeDemo() ? R.string.demo_off_label : R.string.demo_on_label);
        } else {
            item.setVisible(false);
            item.setEnabled(false);
        }

        /* Change the options menu to show "Vibration on" or "Vibration off" */
        item = menu.findItem(R.id.menu_vibration_ctrl);
        if (box.isModeDemo() || C.DOC_DISPLAY) {
            /* Show menu options when demo mode is active */
            item.setVisible(true);
            item.setEnabled(true);
        } else if (!isVibrationPossible()) {
            /* Don't show this option when vibration isn't possible */
            item.setVisible(false);
            item.setEnabled(false);
        } else {
            item.setTitle(
                    vibrationState == VibrationState.On ?
                            R.string.vibration_off_label : R.string.vibration_on_label);
        }

        /* Select the font item */
        item = menu.findItem(R.id.menu_font_select);
        switch (box.disp.getTypeface()) {
            case Digital:
                item.setTitle(R.string.font_normal_label);
                break;

            case Normal:
                item.setTitle(R.string.font_digital_label);
                break;

            default:
                break;
        }

        /* Select the weapon select item (only visible when connected to box) */
        item = menu.findItem(R.id.menu_weapon_select);
        item.setEnabled(isSerialConnected() ? true:false);

        /* Select the piste select item (only visible when connected to box) */
        item = menu.findItem(R.id.menu_piste_select);
        item.setEnabled(isSerialConnected() ? true:false);

        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_vibration_ctrl:
                /* Change the vibration flag */
                switch (vibrationState) {
                    case Off:
                        vibrationState = VibrationState.On;
                        synchronized (editor) {
                            editor.putString("fencing_box_vibration_state", "on");
                            editor.apply();
                        }
                        break;
                    case On:
                        vibrationState = VibrationState.Off;
                        synchronized (editor) {
                            editor.putString("fencing_box_vibration_state", "off");
                            editor.apply();
                        }
                        break;
                }
                break;

            case R.id.menu_weapon_select:
                /* Open the Weapon Select activity */
                optionsMenu.close();
                Intent weaponSelectIntent = new Intent("com.robinterry.fencingboxapp.WEAPON_SELECT");
                weaponSelectIntent.addCategory("android.intent.category.DEFAULT");
                weaponSelectIntent.putExtra("weapon", box.weapon.toString());
                startActivityForResult(weaponSelectIntent, WeaponSelect.ACTIVITY_CODE);
                break;

            case R.id.menu_piste_select:
                /* Open the Piste Select activity */
                optionsMenu.close();
                Intent pisteSelectIntent = new Intent("com.robinterry.fencingboxapp.PISTE_SELECT");
                pisteSelectIntent.addCategory("android.intent.category.DEFAULT");
                pisteSelectIntent.putExtra("piste", box.piste);
                startActivityForResult(pisteSelectIntent, PisteSelect.ACTIVITY_CODE);
                break;

            case R.id.menu_demo:
                /* Go into demo mode or out */
                switch (box.getBoxMode()) {
                    case None:
                        box.saveMode();
                        box.setModeDemo();
                        showDemo();
                        break;
                    case Demo:
                        box.restoreMode();
                        setHitLights();
                        setScore();
                        setClock();
                        setCard();
                        setPriority();
                        setPassivity();
                        setPassivityCard();
                        setPeriod();
                        break;
                    default:
                        break;
                }
                break;

            case R.id.menu_font_select:
                Box b;
                if (box.isModeDisplay()) {
                    b = boxList.currentBox();
                } else {
                    b = box;
                }
                switch (box.disp.getTypeface()) {
                    case Digital:
                        box.disp.setTypeface(b, FaceType.Normal);
                        if (box.isModeDemo()) {
                            showDemo(whichDemo);
                        } else if (box.isModeDisplay()) {
                            box.disp.displayBox(b);
                        } else {
                            setScore();
                            setClock();
                            setPassivity();
                        }
                        break;

                    case Normal:
                        box.disp.setTypeface(b, FaceType.Digital);
                        if (box.isModeDemo()) {
                            showDemo(whichDemo);
                        } else if (box.isModeDisplay()) {
                            box.disp.displayBox(b);
                        } else {
                            setScore();
                            setClock();
                            setPassivity();
                        }
                        break;

                    default:
                        break;
                }
                break;

            default:
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent i) {
        super.onActivityResult(requestCode, resultCode, i);
        if (requestCode == PisteSelect.ACTIVITY_CODE) {
            if (resultCode == Activity.RESULT_OK) {
                synchronized (bc) {
                    box.changePiste = Integer.valueOf(i.getIntExtra("piste", 1));
                    if (!isSerialConnected()) {
                        box.piste = box.changePiste;
                    }
                }
                synchronized (boxList) {
                    boxList.setMyPiste(box.changePiste);
                }
                String pisteString = getResources().getString(R.string.piste) + String.format(" %02d", box.changePiste);
                Toast.makeText(getApplicationContext(), pisteString, Toast.LENGTH_SHORT).show();
            }
        }
        if (requestCode == WeaponSelect.ACTIVITY_CODE) {
            if (resultCode == Activity.RESULT_OK) {
                String wp = i.getStringExtra("weapon");
                synchronized (bc) {
                    switch (wp) {
                        case "FOIL":
                            box.changeWeapon = Box.Weapon.Foil;
                            break;
                        case "EPEE":
                            box.changeWeapon = Box.Weapon.Epee;
                            break;
                        case "SABRE":
                            box.changeWeapon = Box.Weapon.Sabre;
                            break;
                    }
                }
            }
        }
    }

    @Override
    public void openOptionsMenu() {
        final View view = getWindow().getDecorView().findViewById(R.id.action_bar);
        if (view instanceof Toolbar) {
            final Toolbar toolbar = (Toolbar) view;
            toolbar.showOverflowMenu();
            optionsMenuActive = true;
        }
    }

    @Override
    public void closeOptionsMenu() {
        final View view = getWindow().getDecorView().findViewById(R.id.action_bar);
        if (view instanceof Toolbar) {
            final Toolbar toolbar = (Toolbar) view;
            toolbar.hideOverflowMenu();
        }
        optionsMenuActive = false;
    }

    public void showDemo() {
        whichDemo = 0;
        showDemo(whichDemo);
        box.disp.setVolumeMuted(true);
        box.disp.setOnline(true);
        box.disp.setVibrate(true);
        if (!soundMute) {
            sound.soundOn(1000);
        }
    }

    public void showDemo(int which) {
        Log.i(TAG, "Show demo " + which + ", piste " + demoBox[which].piste);
        box.disp.displayBox(demoBox[which]);
    }

    public void startSystemMonitor() {
        final int delayMillis = C.SYSTEM_MONITOR_INTERVAL;

        HandlerThread handlerThread = new HandlerThread("systemMonitor");
        handlerThread.start();
        final Handler handler = new Handler(handlerThread.getLooper());
        final Runnable r = new Runnable() {
            @Override
            public void run() {
                if (!box.disp.isUIVisible()) {
                    BatteryManager batt = (BatteryManager) getApplicationContext().getSystemService(BATTERY_SERVICE);
                    batteryLvl = batt.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
                    if (batteryLvl >= 0 && batteryLvl <= 100) {
                        if (batteryLvl <= C.BATTERY_DANGER_LEVEL) {
                            if (!batteryDangerActive) {
                                batteryDangerActive = batteryDangerFlash = true;
                            } else {
                                batteryDangerFlash = batteryDangerFlash ? false : true;
                            }
                        } else {
                            batteryDangerActive = batteryDangerActive = false;
                        }
                    }
                    Date curTime = Calendar.getInstance().getTime();
                    SimpleDateFormat dateFormat = new SimpleDateFormat("hh:mm");
                    currentTime = dateFormat.format(curTime);
                }
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (!box.disp.isUIVisible()) {
                            box.disp.setBatteryLevel(batteryLvl, batteryDangerFlash);
                            box.disp.setTime(currentTime);

                            // In demo mode, show all icons
                            if (box.isModeDemo()) {
                                box.disp.setVolumeMuted(true);
                                box.disp.setOnline(true);
                                box.disp.setVibrate(true);
                            } else if (!box.isModeNone()) {
                                // Control the "volume muted" icon
                                box.disp.setVolumeMuted(soundMute || sound.isMuted());
                                // Control the "online" icon
                                if (bc != null) {
                                    bc.checkNetworkConnection(thisActivity);
                                    try {
                                        bc.tryConnect();
                                        box.disp.setOnline(bc.isNetworkOnline());
                                    } catch (IOException e) {
                                        Log.e(TAG, "Network unable to connect, error " + e);
                                        box.disp.setOnline(false);
                                    }
                                }
                                // Control the 'vibrate' icon
                                box.disp.setVibrate(vibrationState == VibrationState.On);
                            } else {
                                box.disp.setVolumeMuted(false);
                                box.disp.setOnline(false);
                                box.disp.setVibrate(false);
                            }
                        } else {
                            box.disp.blankBatteryLevel();
                            box.disp.setVolumeMuted(false);
                            box.disp.setOnline(false);
                        }
                        if (!isSerialConnected()) {
                            /* If we won't get a poll, then just read the keys */
                            synchronized (keyHandler) {
                                if (keyHandler.keyPresent()) {
                                    Character key = keyHandler.getKey();
                                    keyHandler.processKey(key);
                                }
                            }
                        }
                    }
                });
                handler.postDelayed(this, delayMillis);
            }
        };
        handler.postDelayed(r, delayMillis);
    }

    public void startBoxMonitor() {
        final int delayMillis = C.BOX_MONITOR_INTERVAL;

        HandlerThread handlerThread = new HandlerThread("boxMonitor");
        handlerThread.start();
        final Handler handler = new Handler(handlerThread.getLooper());
        final Runnable r = new Runnable() {
            @Override
            public void run() {
                if (box.isModeDisplay()) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                Box b = boxList.currentBox();
                                if (b.changed) {
                                    box.disp.displayBox(b);
                                    b.changed = false;
                                } else {
                                    if (b.rxMessages > 0) {
                                        b.rxMessages--;
                                    } else if (b.rxOk) {
                                        /* No messages received - highlight this */
                                        b.rxOk = false;
                                        box.disp.displayBox(b);
                                    }
                                }
                            } catch (IndexOutOfBoundsException e) {
                                /* Do nothing */
                            }
                        }
                    });
                }
                handler.postDelayed(this, delayMillis);
            }
        };
        handler.postDelayed(r, delayMillis);
    }

    public Orientation getOrientation() {
        return orientation;
    }

    public Orientation getCurrentOrientation() {
        Configuration newConf = getResources().getConfiguration();
        return (newConf.orientation == Configuration.ORIENTATION_LANDSCAPE) ?
                Orientation.Landscape : Orientation.Portrait;
    }

    protected void connect() {
        connect(null);
    }

    protected void connect(Boolean permissionGranted) {
        Log.i(TAG, "connecting to USB device");
        UsbDevice device = null;
        UsbManager usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
        for (UsbDevice v : usbManager.getDeviceList().values()) {
            Log.i(TAG, "found USB product: " + v.getProductName() + " vendor: "
                    + v.getVendorId() + " device: " + v.getDeviceName());
            if (v.getProductName().equals("USB Serial")) {
                device = v;
                break;
            }
        }
        if (device == null) {
            Log.e(TAG, "connection failed: device not found");
            return;
        }
        UsbSerialDriver driver = UsbSerialProber.getDefaultProber().probeDevice(device);
        if (driver == null) {
            driver = com.robinterry.fencingboxapp.CustomProber.getCustomProber().probeDevice(device);
        }
        if (driver == null) {
            Log.e(TAG, "connection failed: no driver for device");
            return;
        }
        usbSerialPort = driver.getPorts().get(C.USB_PORT_NUMBER);
        UsbDeviceConnection usbConnection = usbManager.openDevice(driver.getDevice());
        if (usbConnection == null && permissionGranted == null && !usbManager.hasPermission(driver.getDevice())) {
            PendingIntent usbPermissionIntent = PendingIntent.getBroadcast(this, 0, new Intent(com.robinterry.fencingboxapp.Constants.INTENT_ACTION_GRANT_USB), 0);
            usbManager.requestPermission(driver.getDevice(), usbPermissionIntent);
            return;
        }
        if (usbConnection == null) {
            if (!usbManager.hasPermission(driver.getDevice()))
                Log.e(TAG, "connection failed: permission denied");
            else
                Log.e(TAG, "connection failed: open failed");
            return;
        }

        serialConnected = Connected.Pending;
        try {
            usbSerialPort.open(usbConnection);
            usbSerialPort.setParameters(C.USB_BAUD_RATE, UsbSerialPort.DATABITS_8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE);
            socket = new SerialSocket(this.getApplicationContext(), usbConnection, usbSerialPort);
            service.connect(socket);
            // usb connect is not asynchronous. connect-success and connect-error are returned immediately from socket.connect
            // for consistency to bluetooth/bluetooth-LE app use same SerialListener and SerialService classes
            onSerialConnect();
        } catch (Exception e) {
            onSerialConnectError(e);
        }
    }

    public void disconnect() {
        disconnect(false);
    }

    private void disconnect(boolean quitActivity) {
        Log.i(TAG, "disconnected from USB device");
        serialConnected = Connected.False;
        if (bc != null) {
            bc.connected(false);
        }
        if (quitActivity) {
            service.disconnect();
            usbSerialPort = null;
        }
    }

    public static boolean isSerialConnected() {
        return serialConnected == Connected.True;
    }

    public void setHitLights() {
        setHitLights(box.hitA, box.hitB);
    }

    public void setHitLights(Box.Hit h_A, Box.Hit h_B) {
        box.hitA = h_A;
        box.hitB = h_B;
        if (box.isModeConnected()) {
            displayHitLights();
        } else {
            clearHitLights();
        }
    }

    public void clearHitLights() {
        box.hitA = box.hitB = Box.Hit.None;
        displayHitLights();
    }

    public void displayHitLights() {
        box.disp.displayHitLights(box.hitA, box.hitB);
    }

    public void setScoreA(String s_A) {
        box.scoreA = s_A;
        setScore(box.scoreA, box.scoreB);
    }

    public void setScoreB(String s_B) {
        box.scoreB = s_B;
        setScore(box.scoreA, box.scoreB);
    }

    public void setScore() {
        setScore(box.scoreA, box.scoreB);
    }

    public void setScore(String s_A, String s_B) {
        if (!box.isModeWeaponTest()) {
            box.scoreA = s_A;
            box.scoreB = s_B;
            if (box.isModeConnected()) {
                box.disp.displayScore(box.scoreA, box.scoreB);
                setPeriod();
            } else {
                clearScore();
            }
        } else {
            clearScore();
        }
    }

    public void clearScore() {
        box.scoreA = box.scoreB = "00";
        box.disp.clearScore(orientation);
        box.disp.blankPeriod();
    }

    public void resetClock() {
        if (box.isModeBout()) {
            box.timeMins = "03";
        } else {
            box.timeMins = "00";
        }
        box.timeSecs = "00";
        box.timeHund = "00";
        if (box.isModeSparring() || box.isModeDisplay()) {
            clearClock();
        } else if (box.isModeConnected()) {
            setClock(box, box.timeMins, box.timeSecs, box.timeHund, false);
        } else {
            clearClock();
        }
    }

    public void resetScore() {
        box.scoreA = box.scoreB = "00";
        box.period = 1;
        if (box.isModeBout()) {
            setScore(box.scoreA, box.scoreB);
        } else {
            clearScore();
        }
    }

    public boolean setClock() {
        return setClock(box, box.timeMins, box.timeSecs, box.timeHund, false);
    }

    public boolean setClock(Box box, String mins, String secs, String hund, boolean hundActive) {
        boolean clockChanged = true;

        if (box.isModeSparring() || box.isModeDisplay() || box.isModeWeaponTest()) {
            clearClock();
            clockChanged = false;
        } else if (box.isModeConnected()) {
            /* Has the clock minutes and seconds changed? */
            if (mins.equals(box.timeMins) && secs.equals(box.timeSecs)) {
                clockChanged = false;
            }
            box.timeMins = mins;
            box.timeSecs = secs;
            box.timeHund = hund;
            box.disp.displayClock(box.timeMins, box.timeSecs, box.timeHund, hundActive);
        } else {
            clearClock();
        }
        return clockChanged;
    }

    public void clearClock() {
        box.disp.clearClock(Color.BLACK);
    }

    public void setCard() {
        setCard("0", box.cardA);
        setCard("1", box.cardB);
    }

    public void setCard(String whichFencer, Integer card) {
        box.disp.displayCard(whichFencer, card);
    }

    public void resetCard() {
        box.cardA = box.cardB = 0;
        clearCard();
    }

    public void clearCard() {
        setCard("0", 0);
        setCard("1", 0);
    }

    public void setPriorityA() {
        setPriority(true, false);
    }

    public void setPriorityB() {
        setPriority(false, true);
    }

    public void setPriority() {
        setPriority(this.box.priA, this.box.priB);
    }

    public void setPriority(boolean priA, boolean priB) {
        this.box.priA = priA;
        this.box.priB = priB;
        if (box.isModeConnected()) {
            displayPriority();
        } else {
            clearPriority();
        }
    }

    public void clearPriority() {
        box.priA = box.priB = false;
        displayPriority();
    }

    public void displayPriority() {
        box.disp.displayPriority(box.priIndicator, box.priA, box.priB);
    }

    public void restartPassivity(int pClock) {
        box.passivityActive = true;
        setPassivity(pClock);
    }

    public void restartPassivity() {
        box.passivityActive = true;
        setPassivity();
    }

    public void setPassivity() {
        setPassivity(box.passivityTimer);
    }

    public void setPassivity(int pClock) {
        if (box.isModeConnected()) {
            if (isSerialConnected()) {
                if (box.passivityActive) {
                    box.disp.setPassivityClockColor(Color.GREEN);
                    if (box.isModeBout()) {
                        if (pClock > C.PASSIVITY_MAX_TIME) {
                            box.passivityTimer = C.PASSIVITY_MAX_TIME;
                            displayPassivity(box, C.PASSIVITY_MAX_TIME);
                        } else {
                            box.passivityTimer = pClock;
                            displayPassivity(box, pClock);
                        }
                    } else if (box.isModeStopwatch()) {
                        box.passivityTimer = pClock;
                        displayPassivity(box, pClock);
                    } else {
                        clearPassivity();
                    }
                } else {
                    clearPassivity();
                }
            } else {
                clearPassivity();
            }
        } else {
            clearPassivity();
        }
    }

    public void clearPassivity() {
        box.passivityActive = false;
        box.passivityTimer = C.PASSIVITY_MAX_TIME;
        if (box.isModeBout() || box.isModeStopwatch()) {
            box.disp.setPassivityClockColor(Color.GREEN);
        } else {
            box.disp.setPassivityClockColor(Color.BLACK);
        }
        box.disp.blankPassivityClock();
        displayPassivityCard();
    }

    public void displayPassivity(Box box, int pClock) {
        if (box.isModeConnected()) {
            if (isSerialConnected()) {
                if (box.isModeBout() || box.isModeStopwatch()) {
                    box.disp.displayPassivityAsClock(pClock);
                } else {
                    box.disp.setPassivityClockColor(Color.BLACK);
                }
            } else {
                box.disp.displayPassivityAsPiste(box);
            }
        } else {
            clearPassivity();
        }
    }

    public void clearPassivityCard() {
        setPassivityCard(0, PassivityCard.None);
        setPassivityCard(1, PassivityCard.None);
    }

    public void setPassivityCard() {
        displayPassivityCard();
    }

    public void setPassivityCard(String whichFencer, String whichCard) {
        final PassivityCard[] cards = {
                PassivityCard.None,
                PassivityCard.Yellow,
                PassivityCard.Red1,
                PassivityCard.Red2
        };

        int card = Integer.parseInt(whichCard);

        if (card >= 0 && card < PassivityCard.values().length) {
            if (whichFencer.equals("0")) {
                setPassivityCard(0, cards[card]);
            } else if (whichFencer.equals("1")) {
                setPassivityCard(1, cards[card]);
            }
        }
    }

    public void setPassivityCard(int fencer, PassivityCard card) {
        box.pCard[fencer] = card;
        displayPassivityCard(fencer);
    }

    public void displayPassivityCard() {
        displayPassivityCard(0);
        displayPassivityCard(1);
    }

    public void displayPassivityCard(int fencer) {
        box.disp.displayPassivityCard(box, fencer);
    }

    public void setPeriod() {
        if (box.isModeConnected()
                &&
                (box.isModeBout() || box.isModeDemo() || box.isModeDisplay())) {
            box.disp.displayPeriod(box.period);
        } else {
            box.disp.blankPeriod();
        }
    }

    public synchronized void processData(byte data[]) {
        StringBuilder str = new StringBuilder();
        for (byte b : data) {
            str.append(String.format("%02X", b));
        }
        if (C.DEBUG) {
            Log.d(TAG, "Data: " + str);
        }
        for (int i = 0; i < data.length; ) {
            if (data[i] == cmdMarker) {
                i += 1;
                String cmd = new String(data, i, 2, StandardCharsets.UTF_8);
                i += 2;
                /* The "GO" command takes the current piste as an argument */
                if (cmd.equals("GO")) {
                    cmd += new String(data, i, 2, StandardCharsets.UTF_8);
                    i += 2;
                }
                processCmd(cmd);
            } else if (data[i] == scoreMarker) {
                i += 1;
                String s_A = new String(data, i, 2, StandardCharsets.UTF_8);
                i += 2;
                String s_B = new String(data, i, 2, StandardCharsets.UTF_8);
                i += 2;
                processScore(s_A, s_B);
            } else if (data[i] == hitMarker) {
                i += 1;
                String hit = new String(data, i, 2, StandardCharsets.UTF_8);
                i += 2;
                processHit(hit);
            } else if (data[i] == clockMarker1) {
                i += 1;
                String min = new String(data, i, 2, StandardCharsets.UTF_8);
                i += 2;
                String sec = new String(data, i, 2, StandardCharsets.UTF_8);
                i += 2;
                processClock(min, sec, "00", false);
            } else if (data[i] == clockMarker2) {
                i += 1;
                String sec = new String(data, i, 2, StandardCharsets.UTF_8);
                i += 2;
                String hund = new String(data, i, 2, StandardCharsets.UTF_8);
                i += 2;
                processClock("00", sec, hund, true);
            } else if (data[i] == cardMarker) {
                i += 1;
                String whichFencer = new String(data, i, 1, StandardCharsets.UTF_8);
                i += 1;
                String whichCard = new String(data, i, 1, StandardCharsets.UTF_8);
                i += 1;
                processCard(whichFencer, whichCard);
            } else if (data[i] == passivityCardMarker) {
                i += 1;
                String whichFencer = new String(data, i, 1, StandardCharsets.UTF_8);
                i += 1;
                String whichCard = new String(data, i, 1, StandardCharsets.UTF_8);
                i += 1;
                clearPassivity();
                setPassivityCard(whichFencer, whichCard);
            } else if (data[i] == shortCircuitMarker) {
                i += 1;
                String whichFencer = new String(data, i, 1, StandardCharsets.UTF_8);
                i += 1;
                String scState = new String(data, i, 1, StandardCharsets.UTF_8);
                i += 1;
                clearPassivity();
                setShortCircuit(whichFencer, scState);
            } else if (data[i] == pollMarker) {
                i += 1;
                if (data[i] == '?') {
                    processPoll();
                    i += 1;
                }
            } else {
                i += 1;
            }
        }
    }

    public synchronized void processCmd(String cmd) {
        int newPiste;
        String weaponString;

        /* The "GO" command contains a two-digit piste number sent by the box */
        if (cmd.substring(0, 2).equals("GO")) {
            try {
                newPiste = Integer.parseInt(cmd.substring(2));
            } catch (NumberFormatException e) {
                newPiste = 1;
            }
            box.changePiste = box.piste = newPiste;
            serialConnected = Connected.True;
            if (bc != null) {
                bc.connected(true);
            }
            Log.i(TAG, "fencing box started up, piste " + box.piste);
            box.setModeNone();
            invalidateOptionsMenu();
            box.disp.hideUI();
            clearHitLights();
            clearScore();
            clearClock();
            clearCard();
            clearPriority();
            clearPassivity();
            clearPassivityCard();
            try {
                socket.write("OK".getBytes(StandardCharsets.UTF_8));
            } catch (IOException e) {
                Log.e(TAG, "unable to respond to GO command");
            }

        /* These other commands do not have parameters */
        } else switch (cmd) {
            case "BS":
                Log.i(TAG, "bout start");
                box.disp.hideUI();
                box.setModeBout();
                invalidateOptionsMenu();
                Toast.makeText(getApplicationContext(), R.string.mode_bout, Toast.LENGTH_SHORT).show();
                resetScore();
                resetClock();
                resetCard();
                clearPriority();
                clearPassivity();
                clearPassivityCard();
                break;

            case "BR":
                Log.i(TAG, "bout resume");
                restartPassivity();
                displayPassivityCard();
                break;

            case "BC":
                Log.i(TAG, "bout continue");
                if (box.period < 9) {
                    box.period++;
                } else {
                    box.period = 1;
                }
                setPeriod();
                break;

            case "BE":
                Log.i(TAG, "bout end");
                break;

            case "PC":
                Log.i(TAG, "choosing priority");
                box.priIndicator = true;
                box.disp.hideUI();
                Toast.makeText(getApplicationContext(), R.string.priority, Toast.LENGTH_SHORT).show();
                box.disp.setProgressBarVisibility(View.VISIBLE);
                clearPriority();
                box.disp.clearClock(Color.GREEN);
                setHitLights(Box.Hit.OnTarget, Box.Hit.OnTarget);
                break;

            case "P0":
                box.priIndicator = false;
                box.disp.setProgressBarVisibility(View.INVISIBLE);
                box.disp.hideUI();
                setHitLights(Box.Hit.None, Box.Hit.None);
                setPriorityA();
                Log.i(TAG, "priority fencer A start");
                break;

            case "P1":
                box.priIndicator = false;
                box.disp.setProgressBarVisibility(View.INVISIBLE);
                box.disp.hideUI();
                setHitLights(Box.Hit.None, Box.Hit.None);
                setPriorityB();
                Log.i(TAG, "priority fencer B start");
                break;

            case "PE":
                box.disp.hideUI();
                Log.i(TAG, "priority end");
                break;

            case "SS":
                Log.i(TAG, "sparring start");
                box.disp.hideUI();
                box.setModeSparring();
                invalidateOptionsMenu();
                Toast.makeText(getApplicationContext(), R.string.mode_spar, Toast.LENGTH_SHORT).show();
                resetScore();
                resetClock();
                resetCard();
                clearPriority();
                clearPassivity();
                clearPassivityCard();
                break;

            case "HS":
                Log.i(TAG, "hide score");
                scoreHidden = true;
                clearScore();
                break;

            case "RS":
                Log.i(TAG, "1 minute rest start");
                box.disp.hideUI();
                Toast.makeText(getApplicationContext(), R.string.rest_period, Toast.LENGTH_SHORT).show();
                break;

            case "WS":
                Log.i(TAG, "stopwatch start");
                box.disp.hideUI();
                box.setModeStopwatch();
                invalidateOptionsMenu();
                Toast.makeText(getApplicationContext(), R.string.mode_stopwatch, Toast.LENGTH_SHORT).show();
                resetScore();
                resetClock();
                resetCard();
                clearPriority();
                clearPassivity();
                clearPassivityCard();
                stopwatchHours = 0;
                restartPassivity(stopwatchHours);
                break;

            case "WR":
                Log.i(TAG, "stopwatch reset");
                if (!box.isModeStopwatch()) {
                    box.disp.hideUI();
                    box.setModeStopwatch();
                    Toast.makeText(getApplicationContext(), R.string.mode_stopwatch, Toast.LENGTH_SHORT).show();
                    resetScore();
                    resetClock();
                    resetCard();
                    clearPriority();
                }
                clearPassivity();
                clearPassivityCard();
                stopwatchHours = 0;
                restartPassivity(stopwatchHours);
                break;

            case "WW":
                Log.i(TAG, "stopwatch wrap");
                if (stopwatchHours >= 99) {
                    stopwatchHours = 0;
                } else {
                    stopwatchHours++;
                }
                setPassivity(stopwatchHours);
                break;

            case "WT":
                Log.i(TAG, "weapon test");
                box.disp.hideUI();
                box.setModeWeaponTest();
                invalidateOptionsMenu();
                Toast.makeText(getApplicationContext(), R.string.mode_weapontest, Toast.LENGTH_SHORT).show();
                resetScore();
                resetClock();
                resetCard();
                clearPriority();
                clearPassivity();
                clearPassivityCard();
                break;

            case "RL":
                Log.i(TAG, "reset lights");
                box.disp.hideUI();
                box.hitA = box.hitB = Box.Hit.None;
                setHitLights(box.hitA, box.hitB);
                clearPriority();
                sound.soundOff(true);
                break;

            case "TF":
                Log.i(TAG, "weapon: foil");
                box.weapon = box.changeWeapon = Box.Weapon.Foil;
                setScore();
                setClock();
                setCard();
                weaponString = getResources().getString(R.string.weapon_foil) + String.format(" %02d", box.piste);
                Toast.makeText(getApplicationContext(), weaponString, Toast.LENGTH_SHORT).show();
                break;

            case "TE":
                Log.i(TAG, "weapon: epee");
                box.weapon = box.changeWeapon = Box.Weapon.Epee;
                setScore();
                setClock();
                setCard();
                weaponString = getResources().getString(R.string.weapon_epee) + String.format(" %02d", box.piste);
                Toast.makeText(getApplicationContext(), weaponString, Toast.LENGTH_SHORT).show();
                break;

            case "TS":
                Log.i(TAG, "weapon: sabre");
                box.weapon = box.changeWeapon = Box.Weapon.Sabre;
                setScore();
                setClock();
                setCard();
                weaponString = getResources().getString(R.string.weapon_sabre) + String.format(" %02d", box.piste);
                Toast.makeText(getApplicationContext(), weaponString, Toast.LENGTH_SHORT).show();
                break;

            case "VS":
                Log.i(TAG, "passivity start");
                box.passivityActive = true;
                box.passivityTimer = C.PASSIVITY_MAX_TIME;
                setPassivity(box.passivityTimer);
                displayPassivityCard();
                break;

            case "VC":
                Log.i(TAG, "passivity clear");
                clearPassivity();
                displayPassivityCard();
                break;

            case "VT":
                Log.i(TAG, "passivity signal");
                setPassivity(0);
                box.passivityActive = false;
                break;

            case "Z1":
                Log.i(TAG, "sound on");
                if (!soundMute) {
                    sound.soundOn();
                }
                break;

            case "Z0":
                Log.i(TAG, "sound off");
                sound.soundOff();
                break;

            case "CR":
                Log.i(TAG, "clock restart");
                box.disp.hideUI();
                break;

            case "KC":
                processKeyClick();
                break;

            default:
                Log.e(TAG, "unknown command " + cmd);
                break;
        }
    }

    public synchronized void processScore(String s_A, String s_B) {
        if (C.DEBUG) {
            Log.d(TAG, "process score, box " + box + "/" + s_A + ":" + s_B);
        }
        scoreHidden = false;
        setScore(s_A, s_B);
    }

    public synchronized void processHit(String hit) {
        if (C.DEBUG) {
            Log.d(TAG, "process hit, box " + box + "/" + hit);
        }
        /* Process off-target hits first */
        if (box.weapon == Box.Weapon.Foil) {
            if (hit.equals("O0")) {
                box.disp.hideUI();
                box.hitA = Box.Hit.OffTarget;
            }
            if (hit.equals("O1")) {
                box.disp.hideUI();
                box.hitB = Box.Hit.OffTarget;
            }
        }

        /* Process on-target hits */
        if (hit.equals("H0")) {
            box.disp.hideUI();
            box.hitA = box.hitB = Box.Hit.None;
        }
        if (hit.equals("H1")) {
            box.disp.hideUI();
            box.hitA = Box.Hit.OnTarget;
        }
        if (hit.equals("H2")) {
            box.disp.hideUI();
            box.hitB = Box.Hit.OnTarget;
        }
        if (hit.equals("H3")) {
            box.disp.hideUI();
            box.hitA = Box.Hit.OnTarget;
            box.hitB = Box.Hit.None;
        }
        if (hit.equals("H4")) {
            box.disp.hideUI();
            box.hitA = Box.Hit.None;
            box.hitB = Box.Hit.OnTarget;
        }
        if (hit.equals("S0")) {
            box.hitA = Box.Hit.OnTarget;
            box.hitB = Box.Hit.None;
        }
        if (hit.equals("S1")) {
            box.hitA = Box.Hit.None;
            box.hitB = Box.Hit.OnTarget;
        }
        setHitLights(box.hitA, box.hitB);
    }

    public synchronized void processCard(String whichFencer, String whichCard) {
        if (C.DEBUG) {
            Log.d(TAG, "process card, box " + box + "/" + whichFencer + ":" + whichCard);
        }
        box.disp.hideUI();

        if (whichFencer.equals("0")) {
            box.cardA = Integer.parseInt(whichCard);
            setCard(whichFencer, box.cardA);
        } else if (whichFencer.equals("1")) {
            box.cardB = Integer.parseInt(whichCard);
            setCard(whichFencer, box.cardB);
        }
    }

    public synchronized void processClock(String min, String sec, String hund, boolean hundActive) {
        if (C.DEBUG) {
            Log.d(TAG, "process clock, box " + box + "/" + min + ":" + sec + ":" + hund);
        }
        if (setClock(box, min, sec, hund, hundActive)) {
            if (box.isModeBout()) {
                if (box.passivityActive && box.passivityTimer > 0) {
                    box.passivityTimer--;
                    setPassivity(box.passivityTimer);
                }
            }
        }
    }

    public synchronized void processPoll() {
        /* If one or more keys has been pressed, send them back one by one.
           The poll command sent by the fencing scoring box is '/?' and the
           repeater responds with '/' plus a key, for example '/K' for OK.
           Key list:
           0-9
           * (CHANNEL UP)
           # (CHANNEL DOWN)
           K (OK)
           U (UP)
           D (DOWN)
           L (LEFT)
           R (RIGHT)
           B (BACK)
        */

        String msg = "";

        /* Has the weapon changed? */
        if (box.changeWeapon != box.weapon) {
            box.weapon = box.changeWeapon;

            switch (box.changeWeapon) {
                case Foil:
                    msg = "/f--";
                    break;

                case Epee:
                    msg = "/e--";
                    break;

                case Sabre:
                    msg = "/s--";
                    break;

                default:
                    break;
            }
        } else if (box.changePiste != box.piste) {
            Log.i(TAG, "New piste " + box.changePiste);
            box.piste = box.changePiste;
            synchronized (boxList) {
                boxList.setMyPiste(box.piste);
            }
            msg = "/p" + String.format("%02d", box.changePiste);
        } else {
            synchronized (keyHandler) {
                while (keyHandler.keyPresent()) {
                    Character key = keyHandler.getKey();
                    msg = keyHandler.processKey(key);
                }
            }
        }

        /* If a response has to be sent, send it */
        if (!msg.isEmpty()) {
            try {
                socket.write(msg.getBytes(StandardCharsets.UTF_8));
            } catch (IOException e) {
                Log.e(TAG, "unable to respond to poll");
            }
        }
    }

    public void setShortCircuit(String fencer, String scState) {
        Log.i(TAG, "short-circuit: fencer " + fencer + " state " + scState);
        // Ignore for now, as the short-circuit LED already works
    }

    public synchronized void processKeyClick() {
        click.soundOn();
    }

    public String msgScore() {
        char cHitA, cHitB;
        String s;

        if (box.priIndicator) {
            cHitA = cHitB = 'p';
        } else {
            switch (box.hitA) {
                case None:
                default:
                    cHitA = '-';
                    break;
                case OnTarget:
                    cHitA = 'h';
                    break;
                case OffTarget:
                    cHitA = 'o';
                    break;
            }
            switch (box.hitB) {
                case None:
                default:
                    cHitB = '-';
                    break;
                case OnTarget:
                    cHitB = 'h';
                    break;
                case OffTarget:
                    cHitB = 'o';
                    break;
            }
        }
        if (++box.msgIndex > C.MAX_MSGINDEX) {
            box.msgIndex = 0;
        }
        if (box.getBoxMode() == Box.Mode.Bout) {
            s = String.format("%04d|%02d|%d|S%c%c:%s:%s",
                    box.msgIndex,
                    box.piste,
                    box.period,
                    cHitA,
                    cHitB,
                    box.scoreA,
                    box.scoreB);
        } else {
            s = String.format("%04d|%02dS--:--:--",
                    box.msgIndex,
                    box.piste);
        }
        if (C.DEBUG) {
            Log.d(TAG, "msgScore " + s);
        }
        return s;
    }

    public String msgClock() {
        String s;
        if (box.getBoxMode() == Box.Mode.Bout) {
            s = String.format("T%s:%s:%s",
                    box.timeMins,
                    box.timeSecs,
                    box.timeHund,
                    box.period);
        } else {
            s = "T--:--:--";
        }
        if (C.DEBUG) {
            Log.d(TAG, "msgClock " + s);
        }
        return s;
    }

    public String cardStr(Integer card) {
        String cs = "";
        /* A three-character string */
        cs += ((card & Box.yellowCardBit) != 0) ? "y" : "-";
        cs += ((card & Box.redCardBit) != 0) ? "r" : "-";
        cs += ((card & Box.shortCircuitBit) != 0) ? "s" : "-";
        return cs;
    }

    public String msgCard() {
        String s;
        if (box.getBoxMode() == Box.Mode.Bout) {
            s = String.format("C%s:%s",
                    cardStr(box.cardA),
                    cardStr(box.cardB));
        } else {
            s = "C---:---";
        }
        if (C.DEBUG) {
            Log.d(TAG, "msgCard " + s);
        }
        return s;
    }

    public String msgPriority() {
        String s;
        if (box.getBoxMode() == Box.Mode.Bout) {
            s = String.format("P%c:%c",
                    box.priA ? 'y' : box.priIndicator ? '?' : '-',
                    box.priB ? 'y' : box.priIndicator ? '?' : '-');
        } else {
            s = "P-:-";
        }
        if (C.DEBUG) {
            Log.d(TAG, "msgPriority " + s);
        }
        return s;
    }

    public String msgResetLights() {
        String s = String.format("%02dR", box.piste);
        return s;
    }

    public String msgPassivity() {
        Character[] c = new Character[2];
        String s;
        if (box.getBoxMode() == Box.Mode.Bout) {
            for (int i = 0; i < 2; i++) {
                switch (box.pCard[i]) {
                    case None:
                    default:
                        c[i] = '-';
                        break;
                    case Yellow:
                        c[i] = '0';
                        break;
                    case Red1:
                        c[i] = '1';
                        break;
                    case Red2:
                        c[i] = '2';
                        break;
                }
            }
            s = String.format("V%c:%c", c[0], c[1]);
        } else {
            s = "V-:-";
        }
        return s;
    }

    public String msgFull() {
        String s = msgScore() + msgClock() + msgPriority() + msgCard() + msgPassivity();
        if (C.DEBUG) {
            Log.d(TAG, "msgFull " + s);
        }
        return s;
    }

    public void txResetLights() {
        String msg = msgResetLights();
        if (bc != null) {
            if (serialConnected == Connected.True) {
                if (useBroadcast) {
                    synchronized (bc) {
                        bc.send(msg);
                    }
                } else {
                    synchronized (bc) {
                        bc.send(msg);
                    }
                }
            }
        }
    }

    public void startTxFull() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                if (C.DEBUG) {
                    Log.d(TAG, "Starting TX send thread");
                }
                for (;;) {
                    try {
                        if (C.DEBUG) {
                            Log.d(TAG, "Start full TX, bc " +
                                    bc + ", serial connected " + serialConnected +
                                    "box info " + box);
                        }
                        if (bc != null) {
                            /* Only send messages in bout mode */
                            if (serialConnected == Connected.True) {
                                String msg = msgFull();
                                if (C.DEBUG) {
                                    Log.d(TAG, "TX message " + msg);
                                }
                                if (useBroadcast) {
                                    synchronized (bc) {
                                        bc.send(msg);
                                    }
                                } else {
                                    synchronized (bc) {
                                        bc.send(msg);
                                    }
                                }
                            }
                        }
                        Thread.sleep(C.TX_INTERVAL);
                    } catch (Exception e) {
                        Log.e(TAG, "Unable to send full broadcast message");
                    }
                }
            }
        }).start();
    }

    /*
     * SerialListener
     */
    @Override
    public void onSerialConnect() {
        if (socket != null) {
            Log.i(TAG, "connected to " + socket.getName());
            serialConnected = Connected.True;
            if (bc != null) {
                bc.connected(true);
            }
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    clearHitLights();
                    clearScore();
                    clearClock();
                    clearCard();
                    clearPriority();
                    clearPassivity();
                    clearPassivityCard();
                    box.disp.setProgressBarVisibility(View.INVISIBLE);
                }
            });
        } else {
            Log.i(TAG, "Connecting to serial port");
            connect();
        }
    }

    public void reconnect() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                disconnect();
                while (serialConnected != Connected.True) {
                    Log.i(TAG, "reconnecting USB device");
                    try {
                        Thread.sleep(C.USB_RECONNECT_DELAY);
                    } catch (InterruptedException e) {
                        break;
                    }
                    connect();
                }
                Log.i(TAG, "reconnected USB device");
                if (bc != null) {
                    bc.connected(true);
                }
            }
        }).start();
    }

    @Override
    public void onSerialConnectError(Exception e) {
        Log.i(TAG, "connection failed: " + e.getMessage());
        if (bc != null) {
            bc.connected(false);
        }
        if (C.DISPLAY_AFTER_CONNECT_ERROR) {
            if (boxList.empty()) {
                box.setModeNone();
            } else {
                box.setModeDisplay();
            }
        } else {
            box.setModeNone();
        }
        invalidateOptionsMenu();
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                box.disp.showUI();
                clearHitLights();
                clearScore();
                clearClock();
                clearCard();
                clearPriority();
                clearPassivity();
                clearPassivityCard();
                box.disp.setProgressBarVisibility(View.GONE);
            }
        });
        reconnect();
    }

    @Override
    public void onSerialRead(byte[] data) {
        /* Process the incoming data here */
        if (!displayPaused) {
            String s = new String(data, StandardCharsets.UTF_8);
            processData(data);
        }
    }

    @Override
    public void onSerialIoError(Exception e) {
        Log.i(TAG, "connection lost: " + e.getMessage());
        if (bc != null) {
            bc.connected(false);
        }
        if (C.DISPLAY_AFTER_CONNECT_ERROR) {
            if (boxList.empty()) {
                box.setModeNone();
            } else {
                box.setModeDisplay();
            }
        } else {
            box.setModeNone();
        }
        invalidateOptionsMenu();
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                box.disp.showUI();
                clearHitLights();
                clearScore();
                clearClock();
                clearCard();
                clearPriority();
                clearPassivity();
                clearPassivityCard();
                box.disp.setProgressBarVisibility(View.GONE);
            }
        });
        reconnect();
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder binder) {
        service = ((SerialService.SerialBinder) binder).getService();
        service.attach(this);
        if (initialStart && isResumed) {
            initialStart = false;
            Log.i(TAG, "service connected - connecting to device");
            runOnUiThread(this::connect);
        }
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        service = null;
        Log.i(TAG, "service not connected");
    }

    @Override
    public boolean onDown(MotionEvent e1) {
        //box.disp.hideUI();
        return false;
    }

    @Override
    public void onLongPress(MotionEvent e1) {
        box.disp.hideUI();
    }

    @Override
    public boolean onFling(MotionEvent e1, MotionEvent e2, float velX, float velY) {
        if (e1.getAction() == MotionEvent.ACTION_DOWN && e2.getAction() == MotionEvent.ACTION_UP) {
            int height = getResources().getDisplayMetrics().heightPixels;
            int width  = getResources().getDisplayMetrics().widthPixels;

            /* The fling distance as a ratio of the width or height depending on the direction */
            int flingY = height/C.FLING_RATIO;
            int flingX = width/C.FLING_RATIO;
            if (e1.getX() - e2.getX() > flingX) {
                motion = Motion.Left;
                processGesture(motion);
                return true;
            } else if (e2.getX() - e1.getX() > flingX) {
                motion = Motion.Right;
                processGesture(motion);
                return true;
            } else if (e2.getY() - e1.getY() > flingY) {
                motion = Motion.Down;
                processGesture(motion);
                return true;
            } else if (e1.getY() - e2.getY() > flingY) {
                motion = Motion.Up;
                processGesture(motion);
                return true;
            } else {
                motion = Motion.None;
            }
        }
        return false;
    }

    @Override
    public boolean onScroll(MotionEvent e1, MotionEvent e2, float distX, float distY) {
        //box.disp.hideUI();
        return false;
    }

    @Override
    public void onShowPress(MotionEvent e1) {
        //box.disp.hideUI();
    }

    @Override
    public boolean onSingleTapUp(MotionEvent e1) {
        box.disp.hideUI();
        return false;
    }

    @Override
    public boolean onSingleTapConfirmed(MotionEvent e1) {
        if (box.isModeNone()) {
            if (!boxList.empty()) {
                try {
                    Box b = boxList.currentBox();
                    if (b != null) {
                        box.setModeDisplay();
                        box.disp.hideUI();
                        box.disp.displayBox(b);
                        return true;
                    }
                } catch (IndexOutOfBoundsException e) {
                    Log.i(TAG, "No box to display");
                }
            }
        } else {
            box.disp.hideUI();
        }
        return false;
    }

    @Override
    public boolean onDoubleTap(MotionEvent e1) {
        if (box.isModeNone()) {
            if (!boxList.empty()) {
                try {
                    Box b = boxList.currentBox();
                    if (b != null) {
                        box.setModeDisplay();
                        box.disp.hideUI();
                        box.disp.displayBox(b);
                        return true;
                    }
                } catch (IndexOutOfBoundsException e) {
                    Log.i(TAG, "No box to display");
                }
            }
        } else {
            box.disp.hideUI();
        }
        return false;
    }

    @Override
    public boolean onDoubleTapEvent(MotionEvent e1) {
        box.disp.hideUI();
        return false;
    }

    private void processGesture(Motion motion) {
        Box b = null;

        if (box.isModeDemo()) {
            whichDemo ^= 1;
            showDemo(whichDemo);
        } else if (box.isModeNone() && !boxList.empty()) {
            /* If this is the first time, ignore the motion and just display */
            try {
                b = boxList.currentBox();
                if (b != null) {
                    box.setModeDisplay();
                    //box.disp.hideUI();
                    box.disp.displayBox(b);
                }
            } catch (IndexOutOfBoundsException e) {
                Log.i(TAG, "No box to display");
            }
        } else {
            switch (motion) {
                case Down:
                    if (box.isModeDisplay()) {
                        try {
                            b = boxList.nextBox();
                        } catch (IndexOutOfBoundsException e) {
                            Log.i(TAG, "No boxes to display");
                        }
                    }
                    break;

                case Up:
                    if (box.isModeDisplay()) {
                        try {
                            b = boxList.prevBox();
                        } catch (IndexOutOfBoundsException e) {
                            Log.i(TAG, "No boxes to display");
                        }
                    }
                    break;

                case Right:
                    if (box.isModeDisplay()) {
                        try {
                            b = boxList.prevBox();
                        } catch (IndexOutOfBoundsException e) {
                            Log.i(TAG, "No boxes to display");
                        }
                    }
                    break;

                case Left:
                    if (box.isModeDisplay()) {
                        try {
                            b = boxList.nextBox();
                        } catch (IndexOutOfBoundsException e) {
                            Log.i(TAG, "No boxes to display");
                        }
                    }
                    break;

                case None:
                    break;
            }
        }
        if (b != null) {
            box.disp.displayBox(b);
        }
    }

    public boolean isVibrationPossible() {
        if (platform == Platform.TV) {
            /* Don't vibrate if this is a TV */
            return false;
        } else  if (isSerialConnected()) {
            /* Don't vibrate if the app is connected to the fencing scoring box */
            return false;
        } else if (box.isModeDemo()) {
            /* Don't vibrate in demo mode */
            return false;
        } else {
            /* Check that the unit can vibrate */
            Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            if (!v.hasVibrator()) {
                return false;
            } else {
                /* The unit can vibrate */
                return true;
            }
        }
    }

    public boolean isVibrationOn() {
        try {
            if (!isVibrationPossible()) {
                /* This unit can never vibrate */
                return false;
            } else if (box.isModeNone()) {
                /* Don't vibrate if the display is not active, but this can change */
                return false;
            } else if (displayPaused) {
                /* Don't vibrate if the activity is not visible to the user */
                return false;
            } else {
                return vibrationState == VibrationState.On;
            }
        } catch (Exception e) {
            return false;
        }
    }
}