package com.robinterry.fencingboxapp;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import android.app.Activity;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.view.GestureDetectorCompat;

import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;

import android.app.PendingIntent;
import android.app.UiModeManager;
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
import android.os.IBinder;
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

import android.util.Log;
import com.robinterry.fencingboxapp.databinding.ActivityMainBinding;
import com.robinterry.fencingboxapp.databinding.ActivityMainLandBinding;

import com.robinterry.fencingboxapp.C.*;

@SuppressWarnings("ALL")
public class FencingBoxActivity extends AppCompatActivity
        implements ServiceConnection, SerialListener,
        GestureDetector.OnGestureListener, GestureDetector.OnDoubleTapListener {
    private static final String TAG = FencingBoxActivity.class.getSimpleName();
    private Box box;
    private Box demoBox;
    private FencingBoxKeys keyHandler;
    private enum Connected {False, Pending, True};
    public enum PassivityCard {None, Yellow, Red1, Red2};
    public static enum Orientation {Portrait, Landscape};
    public enum Motion {None, Up, Down, Left, Right};
    public enum Platform {Phone, TV};
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
    private Connected serialConnected = Connected.False;
    private UsbSerialPort usbSerialPort;
    private SerialService service;
    private boolean initialStart = true;
    private boolean isResumed = false;
    private SerialSocket socket;
    private final int ledSize = 200;
    private boolean soundMute = false;
    private boolean displayPaused = false;
    private FencingBoxSound sound, click;
    private boolean networkOnline = false;

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
    private NetworkBroadcast bc;
    private static boolean txFullStarted = false;
    public static WifiManager.MulticastLock wifiLock;
    public static FencingBoxList boxList;
    private GestureDetectorCompat gesture;
    private Menu optionsMenu = null;
    private boolean optionsMenuActive = false;

    /* Settings flags */

    public FencingBoxActivity() {
        Log.d(TAG, "initialising broadcast receiver");
        thisActivity = this;
        final BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                Log.d(TAG, "broadcast receiver intent " + intent);
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
        demoBox = new Box(2);
        demoBox.hitA = Box.Hit.OnTarget;
        demoBox.hitB = Box.Hit.OnTarget;
        demoBox.timeMins = "01";
        demoBox.timeSecs = "24";
        demoBox.timeHund = "00";
        demoBox.scoreA = "02";
        demoBox.scoreB = "10";
        demoBox.mode = Box.Mode.Demo;
        demoBox.cardA = Box.redCardBit | Box.yellowCardBit | Box.shortCircuitBit;
        demoBox.cardB = Box.redCardBit | Box.yellowCardBit | Box.shortCircuitBit;
        demoBox.passivityTimer = 55;
        demoBox.passivityActive = true;
        demoBox.pCard[0] = PassivityCard.Yellow;
        demoBox.pCard[1] = PassivityCard.Red1;
        demoBox.priA = true;
        demoBox.priB = true;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "onCreate start");
        super.onCreate(savedInstanceState);

        try {
            bc = new NetworkBroadcast(this);

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

        /* Display handler */
        box.disp = new FencingBoxDisplay(this, box, layout, orientation, portBinding, landBinding);

        /* List of other fencing boxes on the network */
        boxList = new FencingBoxList(box, box.piste);

        // Set the content view from the view binding for the new orientation
        setContentView(mainBinding);

        // Set up the display
        box.disp.createLights();
        box.disp.setupText(box, layout, orientation);

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                try {
                    // Set status bar to entirely black
                    Window window = thisActivity.getWindow();
                    window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
                    window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
                    window.setStatusBarColor(Color.BLACK);

                    // Set action bar (title bar/app bar) to entirely black
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
            /* Keypress handler when it's a TV platform */
            keyHandler = new FencingBoxKeys() {
                @Override
                public String processKey(Character c) {
                    Log.i(TAG, "TV keypress " + c.toString());
                    if (isSerialConnected()) {
                        /* If the fencing scoring box is connected, send the key to that */
                        String msg = "/" + c.toString();
                        return msg;
                    } else {
                        /* When the fencing scoring box is not connected */
                        switch (c) {
                            case 'D': /* Down */
                                break;
                            case 'L': /* Left */
                                break;
                            case 'U': /* Up */
                                break;
                            case 'R': /* Right */
                                break;
                            case 'K': /* OK, Play/Pause*/
                                if (optionsMenuActive) {
                                    /* Select the item */
                                    optionsMenuActive = false;
                                }
                                break;
                            case '*': /* Channel up, Page up */
                                break;
                            case '$': /* Channel down */
                                break;
                            case '#': /* Page down, Fast forward */
                                break;
                            case 'B': /* Back */
                                if (optionsMenuActive) {
                                    /* Hide the menu */
                                    optionsMenuActive = false;
                                }
                                break;
                            case 'C': /* Record */
                                break;
                            case 'W': /* Rewind */
                                break;
                            case 'G': /* Guide */
                                break;
                            case 'M': /* Menu */
                                try {
                                    onOptionsItemSelected(optionsMenu.findItem(R.id.piste_select));
                                    optionsMenuActive = true;
                                } catch (Exception e) {
                                    /* Ignore if the options menu has not been created yet */
                                }
                                break;
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
                                break;
                            default:
                                break;
                        }
                    }
                    return "";
                }
            };
        } else {
            platform = Platform.Phone;
            /* Keypress handler when it's a phone platform */
            keyHandler = new FencingBoxKeys() {
                @Override
                public String processKey(Character c) {
                    Log.i(TAG, "Phone keypress " + c.toString());
                    if (isSerialConnected()) {
                        /* If the fencing scoring box is connected, send the key to that */
                        String msg = "/" + c.toString();
                        return msg;
                    } else {
                        /* When the fencing scoring box is not connected */
                    }
                    return "";
                }
            };
        }
        Log.d(TAG, "onCreate end");
    }

    @Override
    protected void onStart() {
        Log.d(TAG, "onStart start");
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
        Log.d(TAG, "onStart end");
    }

    @Override
    protected void onStop() {
        Log.d(TAG, "onStop start");
        super.onStop();
        displayPaused = true;
        sound.soundOff(true);
        Log.d(TAG, "onStop end");
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        Log.d(TAG, "onWindowFocusChanged start hasFocus " + hasFocus);
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            box.disp.hideUI();
        } else {
            box.disp.showUI();
        }
        Log.d(TAG, "onWindowFocusChanged end");
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy start");
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
        }
        Log.d(TAG, "onDestroy end");
    }

    @Override
    protected void onNewIntent(Intent intent) {
        Log.d(TAG, "onNewIntent start");
        super.onNewIntent(intent);
        if ("android.hardware.usb.action.USB_DEVICE_ATTACHED".equals(intent.getAction())) {
            Log.i(TAG, "USB device attached");
        }
        Log.d(TAG, "onNewIntent end");
    }

    @Override
    protected void onResume() {
        Log.d(TAG, "onResume start");
        super.onResume();
        displayPaused = false;
        if (!isResumed) {
            isResumed = true;
            bindService(new Intent(this, SerialService.class), this, Context.BIND_AUTO_CREATE);
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (box.isModeNone()) {
            box.disp.showUI();
        } else {
            box.disp.hideUI();
        }
        sound.soundOff();
        Log.d(TAG, "onResume end");
    }

    @Override
    protected void onPause() {
        Log.d(TAG, "onPause start");
        super.onPause();
        displayPaused = true;
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        sound.soundOff(true);
        Log.d(TAG, "onPause end");
    }

    @Override
    protected void onRestart() {
        Log.d(TAG, "onRestart start");
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
        Log.d(TAG, "onRestart end");
    }

    @Override
    public void onConfigurationChanged(Configuration newConf) {
        Log.d(TAG, "onConfigurationChanged start");
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
        } else {
            box.disp.hideUI();
        }
        box.disp.setupText(box, layout, orientation);
        if (box.isModeDemo()) {
            showDemo();
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
        Log.d(TAG, "onConfigurationChanged end");
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        Log.d(TAG, "onTouchEvent start");
        gesture.onTouchEvent(event);
        return super.onTouchEvent(event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        Log.i(TAG, "key code " + keyCode);
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_DOWN:
            case KeyEvent.KEYCODE_D:
                keyHandler.addKey('D');
                return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_L:
                keyHandler.addKey('L');
                return true;
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_U:
                keyHandler.addKey('U');
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
            case KeyEvent.KEYCODE_R:
                keyHandler.addKey('R');
                return true;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
            case KeyEvent.KEYCODE_K:
                keyHandler.addKey('K');
                return true;
            case KeyEvent.KEYCODE_CHANNEL_UP:
            case KeyEvent.KEYCODE_PAGE_UP:
            case KeyEvent.KEYCODE_STAR:
            case KeyEvent.KEYCODE_SEARCH:
                keyHandler.addKey('*');
                return true;
            case KeyEvent.KEYCODE_CHANNEL_DOWN:
                keyHandler.addKey('$');
                return true;
            case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
            case KeyEvent.KEYCODE_PAGE_DOWN:
            case KeyEvent.KEYCODE_POUND:
                keyHandler.addKey('#');
                return true;
            case KeyEvent.KEYCODE_BACK:
            case KeyEvent.KEYCODE_B:
                keyHandler.addKey('B');
                return true;
            case KeyEvent.KEYCODE_VOLUME_MUTE:
                soundMute = (soundMute == true) ? false : true;
                return super.onKeyUp(keyCode, event);
            case KeyEvent.KEYCODE_MEDIA_RECORD:
                keyHandler.addKey('C');
                return true;
            case KeyEvent.KEYCODE_MEDIA_REWIND:
                keyHandler.addKey('W');
                return true;
            case KeyEvent.KEYCODE_GUIDE:
                keyHandler.addKey('G');
                return true;
            case KeyEvent.KEYCODE_MENU:
                keyHandler.addKey('M');
                return true;
            case KeyEvent.KEYCODE_0:
                keyHandler.addKey('0');
                return true;
            case KeyEvent.KEYCODE_1:
                keyHandler.addKey('1');
                return true;
            case KeyEvent.KEYCODE_2:
                keyHandler.addKey('2');
                return true;
            case KeyEvent.KEYCODE_3:
                keyHandler.addKey('3');
                return true;
            case KeyEvent.KEYCODE_4:
                keyHandler.addKey('4');
                return true;
            case KeyEvent.KEYCODE_5:
                keyHandler.addKey('5');
                return true;
            case KeyEvent.KEYCODE_6:
                keyHandler.addKey('6');
                return true;
            case KeyEvent.KEYCODE_7:
                keyHandler.addKey('7');
                return true;
            case KeyEvent.KEYCODE_8:
                keyHandler.addKey('8');
                return true;
            case KeyEvent.KEYCODE_9:
                keyHandler.addKey('9');
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
        /* Change the options menu to show "Show demo" or "Clear demo" */
        MenuItem item = menu.findItem(R.id.menu_demo);
        if (box.isModeDemo() || box.isModeNone()) {
            item.setVisible(true);
            item.setEnabled(true);
            item.setTitle(
                    box.isModeDemo() ? R.string.demo_off_label : R.string.demo_on_label);
        } else {
            item.setVisible(false);
            item.setEnabled(false);
        }
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_weapon_select:
                /* Open the Weapon Select activity */
                Intent weaponSelectIntent = new Intent("com.robinterry.fencingboxapp.WEAPON_SELECT");
                weaponSelectIntent.addCategory("android.intent.category.DEFAULT");
                startActivityForResult(weaponSelectIntent, WeaponSelect.ACTIVITY_CODE);
                break;

            case R.id.menu_piste_select:
                /* Open the Piste Select activity */
                Intent pisteSelectIntent = new Intent("com.robinterry.fencingboxapp.PISTE_SELECT");
                pisteSelectIntent.addCategory("android.intent.category.DEFAULT");
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
                    box.piste = Integer.valueOf(i.getIntExtra("piste", 1));
                }
                synchronized (boxList) {
                    boxList.setMyPiste(box.piste);
                }
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

    public void showDemo() {
        Log.i(TAG, "Show demo");
        box.disp.displayBox(demoBox);
        box.disp.setVolumeMuted(true);
        box.disp.setOnline(true);
        if (!soundMute) {
            sound.soundOn(1000);
        }
    }

    public void startSystemMonitor() {
        final int delayMillis = 500;

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
                            } else {
                                box.disp.setVolumeMuted(false);
                                box.disp.setOnline(false);
                            }
                        } else {
                            box.disp.blankBatteryLevel();
                            box.disp.setVolumeMuted(false);
                            box.disp.setOnline(false);
                        }
                    }
                });
                handler.postDelayed(this, delayMillis);
            }
        };
        handler.postDelayed(r, delayMillis);
    }

    public void startBoxMonitor() {
        final int delayMillis = 250;

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

    public boolean isSerialConnected() {
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
        box.scoreA = s_A;
        box.scoreB = s_B;
        if (box.isModeConnected()) {
            box.disp.displayScore(box.scoreA, box.scoreB);
        } else {
            clearScore();
        }
    }

    public void clearScore() {
        box.scoreA = box.scoreB = "00";
        box.disp.clearScore(orientation);
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

        if (box.isModeSparring() || box.isModeDisplay()) {
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
        box.disp.displayPriority(box.priA, box.priB);
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
                box.disp.displayPassivityAsPiste(box.piste);
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

    public synchronized void processData(byte data[]) {
        StringBuilder str = new StringBuilder();
        for (byte b : data) {
            str.append(String.format("%02X", b));
        }

        for (int i = 0; i < data.length; ) {
            if (data[i] == cmdMarker) {
                i += 1;
                String cmd = new String(data, i, 2, StandardCharsets.UTF_8);
                i += 2;
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
        switch (cmd) {
            case "GO":
                Log.i(TAG, "fencing box started up");
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
                break;

            case "PC":
                Log.i(TAG, "choosing priority");
                box.disp.hideUI();
                Toast.makeText(getApplicationContext(), R.string.priority, Toast.LENGTH_SHORT).show();
                box.disp.setProgressBarVisibility(View.VISIBLE);
                clearPriority();
                box.disp.clearClock(Color.GREEN);
                setHitLights(Box.Hit.OnTarget, Box.Hit.OnTarget);
                break;

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
                break;

            case "BE":
                Log.i(TAG, "bout end");
                break;

            case "P0":
                box.disp.setProgressBarVisibility(View.INVISIBLE);
                box.disp.hideUI();
                setHitLights(Box.Hit.None, Box.Hit.None);
                setPriorityA();
                Log.i(TAG, "priority fencer A start");
                break;

            case "P1":
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
                WeaponSelect.setWeapon(box.weapon);
                setScore();
                setClock();
                setCard();
                Toast.makeText(getApplicationContext(), R.string.weapon_foil, Toast.LENGTH_SHORT).show();
                break;

            case "TE":
                Log.i(TAG, "weapon: epee");
                box.weapon = box.changeWeapon = Box.Weapon.Epee;
                WeaponSelect.setWeapon(box.weapon);
                setScore();
                setClock();
                setCard();
                Toast.makeText(getApplicationContext(), R.string.weapon_epee, Toast.LENGTH_SHORT).show();
                break;

            case "TS":
                Log.i(TAG, "weapon: sabre");
                box.weapon = box.changeWeapon = Box.Weapon.Sabre;
                WeaponSelect.setWeapon(box.weapon);
                setScore();
                setClock();
                setCard();
                Toast.makeText(getApplicationContext(), R.string.weapon_sabre, Toast.LENGTH_SHORT).show();
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
        scoreHidden = false;
        setScore(s_A, s_B);
    }

    public synchronized void processHit(String hit) {
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
        Log.i(TAG, "process card");
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
            switch (box.changeWeapon) {
                case Foil:
                    msg = "/f";
                    break;

                case Epee:
                    msg = "/e";
                    break;

                case Sabre:
                    msg = "/s";
                    break;

                default:
                    break;
            }
        } else {
            while (keyHandler.keyPresent()) {
                Character key = keyHandler.getKey();
                msg = keyHandler.processKey(key);
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
        String s = String.format("%02dS%c%c:%s:%s",
                box.piste,
                cHitA,
                cHitB,
                box.scoreA,
                box.scoreB);
        return s;
    }

    public String msgClock() {
        String s = String.format("T%s:%s:%s",
                box.timeMins,
                box.timeSecs,
                box.timeHund);
        return s;
    }

    public String cardStr(Integer card) {
        String cs = "";
        cs += ((card & Box.yellowCardBit) != 0) ? "y" : "-";
        cs += ((card & Box.redCardBit) != 0) ? "r" : "-";
        cs += ((card & Box.shortCircuitBit) != 0) ? "s" : "-";
        return cs;
    }

    public String msgCard() {
        String s = String.format("C%s:%s",
                cardStr(box.cardA),
                cardStr(box.cardB));
        return s;
    }

    public String msgPriority() {
        String s = String.format("P%c:%c",
                box.priA ? 'y' : '-',
                box.priB ? 'y' : '-');
        return s;
    }

    public String msgResetLights() {
        String s = String.format("%02dR", box.piste);
        return s;
    }

    public String msgFull() {
        return msgScore() + msgClock() + msgPriority() + msgCard();
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
                for (; ; ) {
                    try {
                        if (bc != null) {
                            if (serialConnected == Connected.True) {
                                String msg = msgFull();
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
                        Thread.sleep(250);
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
    }

    public void reconnect() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                disconnect();
                while (serialConnected != Connected.True) {
                    Log.i(TAG, "reconnecting USB device");
                    try {
                        Thread.sleep(1000);
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
        //box.disp.hideUI();
        final int MAX_FLING = 200;
        if (e1.getAction() == MotionEvent.ACTION_DOWN && e2.getAction() == MotionEvent.ACTION_UP) {
            if (e1.getX() - e2.getX() > MAX_FLING) {
                motion = Motion.Left;
                processGesture(motion);
                return true;
            } else if (e2.getX() - e1.getX() > MAX_FLING) {
                motion = Motion.Right;
                processGesture(motion);
                return true;
            } else if (e2.getY() - e1.getY() > MAX_FLING) {
                motion = Motion.Down;
                processGesture(motion);
                return true;
            } else if (e1.getY() - e2.getY() > MAX_FLING) {
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
        //box.disp.hideUI();
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
        //box.disp.hideUI();
        return false;
    }

    private void processGesture(Motion motion) {
        Box b = null;

        /* If this is the first time, ignore the motion and just display */
        if (box.isModeNone() && !boxList.empty()) {
            try {
                b = boxList.currentBox();
                if (b != null) {
                    box.setModeDisplay();
                    box.disp.hideUI();
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
}