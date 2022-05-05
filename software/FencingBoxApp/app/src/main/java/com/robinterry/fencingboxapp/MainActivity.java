package com.robinterry.fencingboxapp;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;

import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;

import android.app.PendingIntent;
import android.net.wifi.WifiManager;
import android.widget.ProgressBar;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.res.Configuration;
import android.graphics.Typeface;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.os.Build;
import android.os.IBinder;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ImageView;
import android.view.View;
import android.view.WindowManager;
import android.view.Window;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.KeyEvent;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
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
import java.util.Queue;
import java.util.LinkedList;
import java.util.Iterator;
import java.io.IOException;

import android.util.Log;
import com.robinterry.fencingboxapp.databinding.ActivityMainBinding;
import com.robinterry.fencingboxapp.databinding.ActivityMainLandBinding;

@SuppressWarnings("ALL")
public class MainActivity extends AppCompatActivity implements ServiceConnection, SerialListener {
    private static final String TAG = "FencingBoxApp";
    private enum Connected {False, Pending, True}
    private enum Weapon {Foil, Epee, Sabre}
    private enum Mode {None, Sparring, Bout, Stopwatch, Demo}
    private enum PassivityCard {None, Yellow, Red1, Red2}
    public static enum Orientation {Portrait, Landscape}
    public static enum Hit {None, OnTarget, OffTarget}
    private Mode mode = Mode.None;
    private static Orientation orientation = Orientation.Portrait;
    private Weapon weapon = Weapon.Foil;
    private Weapon changeWeapon = weapon;
    private Integer piste = 1;
    public static final boolean useBroadcast = true;
    public static Orientation getOrientation() {
        return orientation;
    }
    public TextView textScore, textScoreA, textScoreB, textClock;
    public TextView priorityA, priorityB;
    public TextView passivityClock, batteryLevel, time;
    public TextView[] passCard = new TextView[] {null, null};
    private ImageView muteIcon;
    private boolean batteryDangerActive = false;
    private boolean batteryDangerFlash = false;
    public String scoreA = "00", scoreB = "00";
    public String timeMins = "00", timeSecs = "00", timeHund = "00";
    public int passivityTimer = 0;
    public boolean passivityActive = false;
    private Integer cardA = 0, cardB = 0;
    public int stopwatchHours = 0;
    public Hit hitA = Hit.None, hitB = Hit.None;
    private boolean priA = false, priB = false;
    private boolean scoreHidden = false;
    private final int passivityMaxTime = 60;
    private final int batteryDangerLevel = 15;
    public static final Integer hitAColor = 0xFFFF0000;
    public static final Integer hitBColor = 0xFF00FF00;
    public static final Integer inactiveColor = 0xFFE0E0E0;
    public static final Integer yellowCardColor = 0xFFFFFF00;
    public static final Integer redCardColor = 0xFFFF0000;
    public static final Integer yellowCardBit = 0x01;
    public static final Integer redCardBit = 0x02;
    public static final Integer shortCircuitBit = 0x04;
    public MainActivity thisActivity;
    public static ConstraintLayout layout;
    private Connected connected = Connected.False;
    private PassivityCard[] pCard = new PassivityCard[] {PassivityCard.None, PassivityCard.None};
    private final Integer portNum = 0;
    private final Integer baudRate = 115200;
    private UsbSerialPort usbSerialPort;
    private SerialService service;
    private boolean initialStart = true;
    private boolean isResumed = false;
    private SerialSocket socket;
    private HitLightView hitLightA, hitLightB;
    private CardLightView cardLightA, cardLightB;
    private final int ledSize = 200;
    private final boolean controlUI = true;
    private boolean visibleUI = true;
    private boolean soundMute = false;
    private boolean displayPaused = false;
    private FencingBoxSound sound, click;

    /* View bindings */
    private ActivityMainBinding portBinding = null;
    private ActivityMainLandBinding landBinding = null;
    private View mainBinding;

    private Queue<Character> keyQ;

    private ProgressBar progress;

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
    private boolean txFullStarted = false;
    public static WifiManager.MulticastLock wifiLock;

    public static FencingBoxList boxList;

    public MainActivity() {
        Log.d(TAG, "initialising broadcast receiver");
        thisActivity = this;
        final BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                Log.d(TAG, "broadcast receiver intent " + intent);
                if (com.robinterry.fencingboxapp.Constants.INTENT_ACTION_GRANT_USB.equals(intent.getAction())) {
                    Boolean granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false);
                    Log.d(TAG, "GRANT_USB intent " + granted + " received, trying to connect");
                    connect(granted);
                }
            }
        };

        /* Keypress queue */
        keyQ = new LinkedList<Character>();

        /* List of other fencing boxes on the network */
        boxList = new FencingBoxList();

        /* Tell the box list class which piste number we are */
        boxList.setMyPiste(piste);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "onCreate start");
        super.onCreate(savedInstanceState);

        try {
            bc = new NetworkBroadcast();

            /* Get the Wifi multicast lock to allow UDP broadcasts/multicasts to be received */
            WifiManager wifi = (WifiManager) getSystemService(Context.WIFI_SERVICE);
            if (wifi != null) {
                wifiLock = wifi.createMulticastLock(TAG);
                wifiLock.acquire();
            }
        } catch (IOException e) {
            Log.d(TAG, "Unable to create broadcast socket, error " + e);
            bc = null;
        }

        // Get the view bindings for each orientation layout
        landBinding = ActivityMainLandBinding.inflate(getLayoutInflater());
        portBinding = ActivityMainBinding.inflate(getLayoutInflater());

        // Get the current orientation
        orientation = getCurrentOrientation();
        if (orientation == Orientation.Landscape) {
            Log.d(TAG, "initial orientation is landscape");
            mainBinding = landBinding.getRoot();
            layout = (ConstraintLayout) mainBinding;
        } else {
            Log.d(TAG, "initial orientation is portrait");
            mainBinding = portBinding.getRoot();
            layout = (ConstraintLayout) mainBinding;
        }

        // Set the content view from the view binding for the new orientation
        setContentView(mainBinding);

        // Set up the display
        setupText(orientation);

        try {
            hitLightA = new HitLightView(this, layout, HitLightView.HitLight.HitA, Color.RED);
        } catch (Exception e) {
            Log.e(TAG, "Unable to open hit light A view " + e);
        }
        try {
            hitLightB = new HitLightView(this, layout, HitLightView.HitLight.HitB, Color.GREEN);
        } catch (Exception e) {
            Log.e(TAG, "Unable to open hit light B view " + e);
        }
        try {
            cardLightA = new CardLightView(this, layout, CardLightView.CardLight.CardA);
        } catch (Exception e) {
            Log.e(TAG, "Unable to open card light A view " + e);
        }
        try {
            cardLightB = new CardLightView(this, layout, CardLightView.CardLight.CardB);
        } catch (Exception e) {
            Log.e(TAG, "Unable to open card box B view " + e);
        }
        layout.setBackgroundColor(Color.BLACK);

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
        showUI();

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
        progress.setIndeterminate(true);
        progress.setVisibility(View.INVISIBLE);
        Log.d(TAG, "onCreate end");
    }

    @Override
    protected void onStart() {
        Log.d(TAG, "onStart start");
        super.onStart();

        /* Start RX and TX broadcast threads */
        bc.start();

        displayPaused = false;
        startService(new Intent(this, SerialService.class));
        orientation = getCurrentOrientation();
        setupText(orientation);
        hideUI();
        if (mode == Mode.Demo) {
            showDemo();
        } else {
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
            hideUI();
        }
        Log.d(TAG, "onWindowFocusChanged end");
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy start");
        super.onDestroy();
        if (connected != Connected.False) {
            disconnect(true);
        }
        stopService(new Intent(this, SerialService.class));
        sound.soundOff(true);
        landBinding = null;
        portBinding = null;
        try {
            wifiLock.release();
        } catch (Exception e) { }
        Log.d(TAG, "onDestroy end");
    }

    @Override
    protected void onNewIntent(Intent intent) {
        Log.d(TAG, "onNewIntent start");
        super.onNewIntent(intent);
        if ("android.hardware.usb.action.USB_DEVICE_ATTACHED".equals(intent.getAction())) {
            Log.d(TAG, "USB device attached");
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
        hideUI();
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
        setupText(orientation);
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
            Log.d(TAG, "orientation is now landscape");
            mainBinding = landBinding.getRoot();
            orientation = Orientation.Landscape;
        } else {
            Log.d(TAG, "orientation is now portrait");
            mainBinding = portBinding.getRoot();
            orientation = Orientation.Portrait;
        }

        // Set the content view from the view binding for the new orientation
        setContentView(mainBinding);
        layout = (ConstraintLayout) mainBinding;

        // Display the screen in the new orientation
        hideUI();
        setupText(orientation);
        hitLightA.setLayout(layout);
        hitLightB.setLayout(layout);
        cardLightA.setLayout(layout);
        cardLightB.setLayout(layout);
        layout.setBackgroundColor(Color.BLACK);
        if (mode == Mode.Demo) {
            showDemo();
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
        super.onTouchEvent(event);

        Log.d(TAG, "onTouchEvent " + event);
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            hideUI();
            return true;
        }
        return false;
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        Log.d(TAG, "key code " + keyCode);
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_DOWN:
            case KeyEvent.KEYCODE_D:
                keyQ.add('D');
                return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_L:
                keyQ.add('L');
                return true;
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_U:
                keyQ.add('U');
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
            case KeyEvent.KEYCODE_R:
                keyQ.add('R');
                return true;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
            case KeyEvent.KEYCODE_K:
                keyQ.add('K');
                return true;
            case KeyEvent.KEYCODE_CHANNEL_UP:
            case KeyEvent.KEYCODE_PAGE_UP:
            case KeyEvent.KEYCODE_STAR:
            case KeyEvent.KEYCODE_SEARCH:
                keyQ.add('*');
                return true;
            case KeyEvent.KEYCODE_CHANNEL_DOWN:
                keyQ.add('$');
                return true;
            case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
            case KeyEvent.KEYCODE_PAGE_DOWN:
            case KeyEvent.KEYCODE_POUND:
                keyQ.add('#');
                return true;
            case KeyEvent.KEYCODE_BACK:
            case KeyEvent.KEYCODE_B:
                keyQ.add('B');
                return true;
            case KeyEvent.KEYCODE_VOLUME_MUTE:
                soundMute = (soundMute == true) ? false:true;
                return super.onKeyUp(keyCode, event);
            case KeyEvent.KEYCODE_0:
            case KeyEvent.KEYCODE_MEDIA_RECORD:
            case KeyEvent.KEYCODE_MEDIA_REWIND:
            case KeyEvent.KEYCODE_GUIDE:
                keyQ.add('0');
                return true;
            case KeyEvent.KEYCODE_1:
                keyQ.add('1');
                return true;
            case KeyEvent.KEYCODE_2:
                keyQ.add('2');
                return true;
            case KeyEvent.KEYCODE_3:
                keyQ.add('3');
                return true;
            case KeyEvent.KEYCODE_4:
                keyQ.add('4');
                return true;
            case KeyEvent.KEYCODE_5:
                keyQ.add('5');
                return true;
            case KeyEvent.KEYCODE_6:
                keyQ.add('6');
                return true;
            case KeyEvent.KEYCODE_7:
                keyQ.add('7');
                return true;
            case KeyEvent.KEYCODE_8:
                keyQ.add('8');
                return true;
            case KeyEvent.KEYCODE_9:
                keyQ.add('9');
                return true;
            default:
                Log.d(TAG, "unrecognised keycode " + keyCode);
                return super.onKeyUp(keyCode, event);
        }
    }

    @Override
    public void onBackPressed() {
        keyQ.add('B');
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_weapon_foil:
                /* Change weapon to foil */
                if (weapon != Weapon.Foil) {
                    changeWeapon = Weapon.Foil;
                    Log.d(TAG, "Change weapon: foil");
                }
                break;

            case R.id.menu_weapon_epee:
                /* Change weapon to epee */
                if (weapon != Weapon.Epee) {
                    changeWeapon = Weapon.Epee;
                    Log.d(TAG, "Change weapon: epee");
                }
                break;

            case R.id.menu_weapon_sabre:
                /* Change weapon to sabre */
                if (weapon != Weapon.Sabre) {
                    changeWeapon = Weapon.Sabre;
                    Log.d(TAG, "Change weapon: sabre");
                }
                break;

            default:
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    public void showDemo() {
        Log.d(TAG, "demo");
        hideUI();
        displayHitLights(Hit.OnTarget, Hit.OnTarget);
        displayScore("00", "00");
        displayClock("00", "00", "00", false);
        displayCardA(true, true, true);
        displayCardB(true, true, true);
        displayPriority(true, true);
        displayPassivity(passivityMaxTime);
        displayPassivityCard(0, PassivityCard.Red2);
        displayPassivityCard(1, PassivityCard.Yellow);
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
                if (!visibleUI) {
                    BatteryManager batt = (BatteryManager) getApplicationContext().getSystemService(BATTERY_SERVICE);
                    batteryLvl = batt.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
                    if (batteryLvl >= 0 && batteryLvl <= 100) {
                        if (batteryLvl <= batteryDangerLevel) {
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
                        if (!visibleUI) {
                            if (batteryLvl >= 0 && batteryLvl <= 100) {
                                batteryLevel.setTextColor(batteryDangerFlash ? Color.BLACK : Color.WHITE);
                                batteryLevel.setText(String.valueOf(batteryLvl) + "%");
                            } else {
                                batteryLevel.setTextColor(Color.BLACK);
                                batteryLevel.setText("----");
                            }
                            time.setText(currentTime);

                            // Control the "volume muted" icon
                            muteIcon.setImageAlpha((soundMute || sound.isMuted()) ? 255 : 0);
                        } else {
                            batteryLevel.setTextColor(Color.BLACK);
                        }
                    }
                });
                handler.postDelayed(this, delayMillis);
            }
        };
        handler.postDelayed(r, delayMillis);
    }

    private Orientation getCurrentOrientation() {
        Configuration newConf = getResources().getConfiguration();
        return (newConf.orientation == Configuration.ORIENTATION_LANDSCAPE) ?
                Orientation.Landscape : Orientation.Portrait;
    }

    protected void setupText(Orientation orient) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (orient == Orientation.Landscape) {
                    textScoreA = landBinding.textScoreAL;
                    textScoreB = landBinding.textScoreBL;
                    textScoreA.setGravity(Gravity.CENTER);
                    textScoreB.setGravity(Gravity.CENTER);
                    textClock = landBinding.textClockL;
                    priorityA = landBinding.priorityAL;
                    priorityB = landBinding.priorityBL;
                    passivityClock = landBinding.passivityClockL;
                    batteryLevel = landBinding.batteryLevelL;
                    time = landBinding.timeL;
                    passCard[0] = landBinding.pCardAL;
                    passCard[1] = landBinding.pCardBL;
                    muteIcon = (ImageView) landBinding.iconMuteL;
                    progress = landBinding.priorityChooseL;
                } else {
                    textScore = portBinding.textScore;
                    textScore.setGravity(Gravity.CENTER);
                    textClock = portBinding.textClock;
                    priorityA = portBinding.priorityA;
                    priorityB = portBinding.priorityB;
                    passivityClock = portBinding.passivityClock;
                    batteryLevel = portBinding.batteryLevel;
                    time = portBinding.time;
                    passCard[0] = portBinding.pCardA;
                    passCard[1] = portBinding.pCardB;
                    muteIcon = (ImageView) portBinding.iconMute;
                    progress = portBinding.priorityChoose;
                }
                try {
                    Typeface face = Typeface.createFromAsset(getAssets(), "font/DSEG7Classic-Bold.ttf");
                    Log.d(TAG, "typeface for score " + face);
                    if (orientation == Orientation.Landscape) {
                        textScoreA.setTypeface(face);
                        textScoreA.setTextColor(Color.RED);
                        textScoreB.setTypeface(face);
                        textScoreB.setTextColor(Color.RED);
                    } else {
                        textScore.setTypeface(face);
                        textScore.setTextColor(Color.RED);
                    }
                    textClock.setTypeface(face);
                    textClock.setTextColor(Color.GREEN);
                    textClock.setGravity(Gravity.CENTER);
                    passivityClock.setTypeface(face);
                    passivityClock.setTextColor(Color.GREEN);
                    passivityClock.setGravity(Gravity.CENTER);
                    batteryLevel.setTextColor(Color.WHITE);
                    batteryLevel.setGravity(Gravity.CENTER);
                    priorityA.setTextColor(Color.BLACK);
                    priorityA.setGravity(Gravity.CENTER);
                    priorityB.setTextColor(Color.BLACK);
                    priorityB.setGravity(Gravity.CENTER);
                    time.setTextColor(Color.WHITE);
                    time.setGravity(Gravity.CENTER);
                    passCard[0].setTypeface(null, Typeface.BOLD);
                    passCard[0].setTextColor(Color.BLACK);
                    passCard[0].setGravity(Gravity.CENTER);
                    passCard[1].setTypeface(null, Typeface.BOLD);
                    passCard[1].setTextColor(Color.BLACK);
                    passCard[1].setGravity(Gravity.CENTER);
                } catch (Exception e) {
                    Log.d(TAG, "unable to find font " + e);
                }
            }
        });
    }

    private void hideUI() {
        Log.d(TAG, "hideUI " + controlUI + " mode " + mode);
        if (controlUI) {
            if (true /*mode != Mode.None*/) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            getWindow().setDecorFitsSystemWindows(false);
                            if (getWindow().getInsetsController() != null) {
                                getWindow().getInsetsController().hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                                getWindow().getInsetsController().setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
                            }
                        } else {
                            View decorView = getWindow().getDecorView();
                            decorView.setSystemUiVisibility(
                                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                                            | View.SYSTEM_UI_FLAG_LOW_PROFILE
                                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                                            | View.SYSTEM_UI_FLAG_FULLSCREEN);
                        }
                    }
                });
                visibleUI = false;
            }
        }
    }

    private void hideUIIfVisible() {
        if (visibleUI) {
            hideUI();
        }
    }

    private void showUI() {
        if (controlUI) {
            if (!visibleUI) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            getWindow().setDecorFitsSystemWindows(true);
                            if (getWindow().getInsetsController() != null) {
                                getWindow().getInsetsController().show(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                            }
                        } else {
                            View decorView = getWindow().getDecorView();
                            decorView.setSystemUiVisibility(
                                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
                        }
                    }
                });
                visibleUI = true;
            }
        }
    }

    protected void connect() {
        connect(null);
    }

    protected void connect(Boolean permissionGranted) {
        Log.d(TAG, "connecting to USB device");
        UsbDevice device = null;
        UsbManager usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
        for (UsbDevice v : usbManager.getDeviceList().values()) {
            Log.d(TAG, "found USB product: " + v.getProductName() + " vendor: "
                    + v.getVendorId() + " device: " + v.getDeviceName());
            if (v.getProductName().equals("USB Serial")) {
                device = v;
                break;
            }
        }
        if (device == null) {
            Log.d(TAG, "connection failed: device not found");
            return;
        }
        UsbSerialDriver driver = UsbSerialProber.getDefaultProber().probeDevice(device);
        if (driver == null) {
            driver = com.robinterry.fencingboxapp.CustomProber.getCustomProber().probeDevice(device);
        }
        if (driver == null) {
            Log.d(TAG, "connection failed: no driver for device");
            return;
        }
        usbSerialPort = driver.getPorts().get(portNum);
        UsbDeviceConnection usbConnection = usbManager.openDevice(driver.getDevice());
        if (usbConnection == null && permissionGranted == null && !usbManager.hasPermission(driver.getDevice())) {
            PendingIntent usbPermissionIntent = PendingIntent.getBroadcast(this, 0, new Intent(com.robinterry.fencingboxapp.Constants.INTENT_ACTION_GRANT_USB), 0);
            usbManager.requestPermission(driver.getDevice(), usbPermissionIntent);
            return;
        }
        if (usbConnection == null) {
            if (!usbManager.hasPermission(driver.getDevice()))
                Log.d(TAG, "connection failed: permission denied");
            else
                Log.d(TAG, "connection failed: open failed");
            return;
        }

        connected = Connected.Pending;
        try {
            usbSerialPort.open(usbConnection);
            usbSerialPort.setParameters(baudRate, UsbSerialPort.DATABITS_8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE);
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
        Log.d(TAG, "disconnected from USB device");
        connected = Connected.False;
        bc.connected(false);
        if (quitActivity) {
            service.disconnect();
            usbSerialPort = null;
        }
    }

    public void setHitLights() {
        setHitLights(hitA, hitB);
    }

    public void setHitLights(Hit h_A, Hit h_B) {
        hitA = h_A;
        hitB = h_B;
        displayHitLights(hitA, hitB);
    }

    public void displayHitLights(Hit h_A, Hit h_B) {
        hitLightA.showLights(h_A);
        hitLightB.showLights(h_B);
    }

    public void clearHitLights() {
        setHitLights(Hit.None, Hit.None);
    }

    public void setScoreA(String s_A) {
        scoreA = s_A;
        setScore(scoreA, scoreB);
    }

    public void setScoreB(String s_B) {
        scoreB = s_B;
        setScore(scoreA, scoreB);
    }

    public void setScore() {
        setScore(scoreA, scoreB);
    }

    public void setScore(String s_A, String s_B) {
        scoreA = s_A;
        scoreB = s_B;
        displayScore(scoreA, scoreB);
    }

    public void displayScore(String scoreA, String scoreB) {
        String score = scoreA + " " + scoreB;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (scoreHidden) {
                    clearScore();
                } else if (mode == Mode.Stopwatch || mode == Mode.None) {
                    clearScore();
                } else if (orientation == Orientation.Landscape) {
                    textScoreA.setTextColor(Color.RED);
                    textScoreA.setText(scoreA);
                    textScoreB.setTextColor(Color.RED);
                    textScoreB.setText(scoreB);
                } else {
                    textScore.setTextColor(Color.RED);
                    textScore.setText(score);
                }
            }
        });
    }

    public void clearScore() {
        scoreA = scoreB = "00";
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (orientation == Orientation.Landscape) {
                    textScoreA.setTextColor(Color.BLACK);
                    textScoreB.setTextColor(Color.BLACK);
                    textScoreA.setText("--");
                    textScoreB.setText("--");
                } else {
                    textScore.setTextColor(Color.BLACK);
                    textScore.setText("----");
                }
            }
        });
    }

    public void resetClock() {
        if (mode == Mode.Bout) {
            timeMins = "03";
        } else {
            timeMins = "00";
        }
        timeSecs = "00";
        timeHund = "00";
        if (mode == Mode.Sparring || mode == Mode.None) {
            clearClock();
        } else {
            setClock(timeMins, timeSecs, timeHund, false);
        }
    }

    public void resetScore() {
        scoreA = scoreB = "00";
        if (mode == Mode.Bout) {
            setScore(scoreA, scoreB);
        } else {
            clearScore();
        }
    }

    public boolean setClock() {
        return setClock(timeMins, timeSecs, timeHund, false);
    }

    public boolean setClock(String mins, String secs, String hund, boolean hundActive) {
        boolean clockChanged = true;

        if (mode == Mode.Sparring || mode == Mode.None) {
            clearClock();
            clockChanged = false;
        } else {
            /* Has the clock minutes and seconds changed? */
            if (mins.equals(timeMins) && secs.equals(timeSecs)) {
                clockChanged = false;
            }
            timeMins = mins;
            timeSecs = secs;
            timeHund = hund;
            displayClock(timeMins, timeSecs, timeHund, hundActive);
        }
        return clockChanged;
    }

    public void displayClock(String timeMins, String timeSecs, String timeHund, boolean hundActive) {
        String clock;

        if (hundActive) {
            clock = timeSecs + ":" + timeHund;
        } else {
            clock = timeMins + ":" + timeSecs;
        }
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                textClock.setTextColor(Color.GREEN);
                textClock.setText(clock);
            }
        });
    }

    public void clearClock() {
        clearClock(Color.BLACK);
    }

    public void clearClock(int color) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                textClock.setTextColor(color);
                textClock.setText("----");
            }
        });
    }

    public void setCard() {
        setCard("0", cardA);
        setCard("1", cardB);
    }

    public void displayCardA(boolean yellowCard, boolean redCard, boolean shortCircuit) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                cardLightA.showYellow(yellowCard);
                cardLightA.showRed(redCard);
                cardLightA.showShortCircuit(shortCircuit);
            }
        });
    }

    public void displayCardB(boolean yellowCard, boolean redCard, boolean shortCircuit) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                cardLightB.showYellow(yellowCard);
                cardLightB.showRed(redCard);
                cardLightB.showShortCircuit(shortCircuit);
            }
        });
    }

    public void setCard(String whichFencer, Integer card) {
        boolean yellowCard = ((card & yellowCardBit) != 0) ? true : false;
        boolean redCard = ((card & redCardBit) != 0) ? true : false;
        boolean shortCircuit = ((card & shortCircuitBit) != 0) ? true : false;

        /* Cards for fencer A */
        if (whichFencer.equals("0")) {
            displayCardA(yellowCard, redCard, shortCircuit);
        }

        /* Cards for fencer B */
        if (whichFencer.equals("1")) {
            displayCardB(yellowCard, redCard, shortCircuit);
        }
    }

    public void resetCard() {
        cardA = cardB = 0;
        clearCard();
    }

    public void clearCard() {
        setCard("0", 0);
        setCard("1", 0);
    }

    public void setPriorityA()
    {
        setPriority(true, false);
    }

    public void setPriorityB()
    {
        setPriority(false, true);
    }

    public void setPriority()
    {
        setPriority(this.priA, this.priB);
    }

    public void setPriority(boolean priA, boolean priB) {
        this.priA = priA;
        this.priB = priB;
        displayPriority(priA, priB);
    }

    public void displayPriority(boolean priA, boolean priB) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                priorityA.setTextColor(priA ? Color.RED : Color.BLACK);
                priorityB.setTextColor(priB ? Color.RED : Color.BLACK);
            }
        });
    }

    public void clearPriority()
    {
        setPriority(false, false);
    }

    public void restartPassivity(int pClock) {
        passivityActive = true;
        setPassivity(pClock);
    }

    public void restartPassivity() {
        passivityActive = true;
        setPassivity();
    }

    public void setPassivity() {
        setPassivity(passivityTimer);
    }

    public void setPassivity(int pClock)
    {
        if (passivityActive) {
            passivityClock.setTextColor(Color.GREEN);
            if (mode == Mode.Bout) {
                if (pClock > passivityMaxTime) {
                    displayPassivity(passivityMaxTime);
                } else {
                    displayPassivity(pClock);
                }
            } else if (mode == Mode.Stopwatch) {
                passivityTimer = pClock;
                displayPassivity(pClock);
            } else {
                clearPassivity();
            }
        } else {
            clearPassivity();
        }
    }

    public void displayPassivity(int pClock) {
        if (mode != Mode.Sparring) {
            passivityClock.setText(String.format("%02d", pClock));
        }
    }

    public void clearPassivity() {
        passivityActive = false;
        passivityTimer = passivityMaxTime;
        if (mode == Mode.Bout || mode == Mode.Stopwatch) {
            passivityClock.setTextColor(Color.GREEN);
        } else {
            passivityClock.setTextColor(Color.BLACK);
        }
        passivityClock.setText("--");
        displayPassivityCard();
    }

    public void clearPassivityCard() {
        setPassivityCard(0, PassivityCard.None);
        setPassivityCard(1, PassivityCard.None);
    }

    public void displayPassivityCard() {
        displayPassivityCard(0);
        displayPassivityCard(1);
    }

    public void displayPassivityCard(int fencer) {
        displayPassivityCard(fencer, pCard[fencer]);
    }

    public void displayPassivityCard(int fencer, PassivityCard pCard) {
        switch (pCard) {
            case None:
                passCard[fencer].setTextColor(Color.BLACK);
                passCard[fencer].setText("-");
                break;
            case Yellow:
                passCard[fencer].setTextColor(Color.YELLOW);
                passCard[fencer].setText("1");
                break;

            case Red1:
                passCard[fencer].setTextColor(Color.RED);
                passCard[fencer].setText("1");
                break;

            case Red2:
                passCard[fencer].setTextColor(Color.RED);
                passCard[fencer].setText("2");
                break;

            default:
                break;
        }
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
        pCard[fencer] = card;
        displayPassivityCard(fencer);
    }

    public synchronized void processData(byte data[]) {
        StringBuilder str = new StringBuilder();
        for (byte b: data) {
            str.append(String.format("%02X", b));
        }
        Log.d(TAG, "data: " + str.toString());

        for (int i = 0; i < data.length;) {
           if (data[i] == cmdMarker) {
               i += 1;
               String cmd = new String(data, i, 2, StandardCharsets.UTF_8);
               i += 2;
               Log.d(TAG, "cmd: " + cmd);
               processCmd(cmd);
           } else if (data[i] == scoreMarker) {
               i += 1;
               String s_A = new String(data, i, 2, StandardCharsets.UTF_8);
               i += 2;
               String s_B = new String(data, i, 2, StandardCharsets.UTF_8);
               i += 2;
               Log.d(TAG, "scoreA: " + s_A + " scoreB: " + s_B);
               processScore(s_A, s_B);
           } else if (data[i] == hitMarker) {
               i += 1;
               String hit = new String(data, i, 2, StandardCharsets.UTF_8);
               i += 2;
               Log.d(TAG, "hit: " + hit);
               processHit(hit);
           } else if (data[i] == clockMarker1) {
               i += 1;
               String min = new String(data, i, 2, StandardCharsets.UTF_8);
               i += 2;
               String sec = new String(data, i, 2, StandardCharsets.UTF_8);
               i += 2;
               Log.d(TAG, "min: " + min + " sec: " + sec);
               processClock(min, sec, "00",false);
           } else if (data[i] == clockMarker2) {
               i += 1;
               String sec = new String(data, i, 2, StandardCharsets.UTF_8);
               i += 2;
               String hund = new String(data, i, 2, StandardCharsets.UTF_8);
               i += 2;
               Log.d(TAG, "sec: " + sec + " hund: " + hund);
               processClock("00", sec, hund, true);
           } else if (data[i] == cardMarker) {
               i += 1;
               String whichFencer = new String(data, i, 1, StandardCharsets.UTF_8);
               i += 1;
               String whichCard = new String(data, i, 1, StandardCharsets.UTF_8);
               i += 1;
               Log.d(TAG, "card fencer: " + whichFencer + " card: " + whichCard);
               processCard(whichFencer, whichCard);
           } else if (data[i] == passivityCardMarker) {
               i += 1;
               String whichFencer = new String(data, i, 1, StandardCharsets.UTF_8);
               i += 1;
               String whichCard = new String(data, i, 1, StandardCharsets.UTF_8);
               i += 1;
               Log.d(TAG, "card fencer: " + whichFencer + " card: " + whichCard);
               clearPassivity();
               setPassivityCard(whichFencer, whichCard);
           } else if (data[i] == shortCircuitMarker) {
               i += 1;
               String whichFencer = new String(data, i, 1, StandardCharsets.UTF_8);
               i += 1;
               String scState = new String(data, i, 1, StandardCharsets.UTF_8);
               i += 1;
               Log.d(TAG, "card fencer: " + whichFencer + " s/c: " + scState);
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
                Log.d(TAG, "fencing box started up");
                mode = Mode.None;
                hideUI();
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
                    Log.d(TAG, "unable to respond to GO command");
                }
                break;

            case "PC":
                Log.d(TAG, "choosing priority");
                hideUI();
                Toast.makeText(getApplicationContext(), R.string.priority, Toast.LENGTH_SHORT).show();
                if (progress != null) {
                    progress.setVisibility(View.VISIBLE);
                }
                clearPriority();
                clearClock(Color.GREEN);
                setHitLights(Hit.OnTarget, Hit.OnTarget);
                break;

            case "BS":
                Log.d(TAG, "bout start");
                hideUI();
                mode = Mode.Bout;
                Toast.makeText(getApplicationContext(), R.string.mode_bout, Toast.LENGTH_SHORT).show();
                resetScore();
                resetClock();
                resetCard();
                clearPriority();
                clearPassivity();
                clearPassivityCard();
                break;

            case "BR":
                Log.d(TAG, "bout resume");
                restartPassivity();
                displayPassivityCard();
                break;

            case "BC":
                Log.d(TAG, "bout continue");
                break;

            case "BE":
                Log.d(TAG, "bout end");
                break;

            case "P0":
                if (progress != null) {
                    progress.setVisibility(View.INVISIBLE);
                }
                hideUI();
                setPriorityA();
                Log.d(TAG, "priority fencer A start");
                break;

            case "P1":
                if (progress != null) {
                    progress.setVisibility(View.INVISIBLE);
                }
                hideUI();
                setPriorityB();
                Log.d(TAG, "priority fencer B start");
                break;

            case "PE":
                hideUI();
                Log.d(TAG, "priority end");
                break;

            case "SS":
                Log.d(TAG, "sparring start");
                hideUI();
                mode = Mode.Sparring;
                Toast.makeText(getApplicationContext(), R.string.mode_spar, Toast.LENGTH_SHORT).show();
                resetScore();
                resetClock();
                resetCard();
                clearPriority();
                clearPassivity();
                clearPassivityCard();
                break;

            case "HS":
                Log.d(TAG, "hide score");
                scoreHidden = true;
                clearScore();
                break;

            case "RS":
                Log.d(TAG, "1 minute rest start");
                hideUI();
                Toast.makeText(getApplicationContext(), R.string.rest_period, Toast.LENGTH_SHORT).show();
                break;

            case "WS":
                Log.d(TAG, "stopwatch start");
                hideUI();
                mode = Mode.Stopwatch;
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
                Log.d(TAG, "stopwatch reset");
                if (mode != Mode.Stopwatch) {
                    hideUI();
                    mode = Mode.Stopwatch;
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
                Log.d(TAG, "stopwatch wrap");
                if (stopwatchHours >= 99) {
                    stopwatchHours = 0;
                } else {
                    stopwatchHours++;
                }
                setPassivity(stopwatchHours);
                break;

            case "RL":
                Log.d(TAG, "reset lights");
                hideUI();
                hitA = hitB = Hit.None;
                setHitLights(hitA, hitB);
                clearPriority();
                sound.soundOff(true);
                break;

            case "TF":
                Log.d(TAG, "weapon: foil");
                weapon = changeWeapon = Weapon.Foil;
                setScore();
                setClock();
                setCard();
                Toast.makeText(getApplicationContext(), R.string.weapon_foil, Toast.LENGTH_SHORT).show();
                break;

            case "TE":
                Log.d(TAG, "weapon: epee");
                weapon = changeWeapon = Weapon.Epee;
                setScore();
                setClock();
                setCard();
                Toast.makeText(getApplicationContext(), R.string.weapon_epee, Toast.LENGTH_SHORT).show();
                break;

            case "TS":
                Log.d(TAG, "weapon: sabre");
                weapon = changeWeapon = Weapon.Sabre;
                setScore();
                setClock();
                setCard();
                Toast.makeText(getApplicationContext(), R.string.weapon_sabre, Toast.LENGTH_SHORT).show();
                break;

            case "VS":
                Log.d(TAG, "passivity start");
                passivityActive = true;
                passivityTimer = passivityMaxTime;
                setPassivity(passivityTimer);
                displayPassivityCard();
                break;

            case "VC":
                Log.d(TAG, "passivity clear"); 
                clearPassivity();
                displayPassivityCard();
                break;

            case "VT":
                Log.d(TAG, "passivity signal");
                setPassivity(0);
                passivityActive = false;
                break;

            case "Z1":
                Log.d(TAG, "sound on");
                if (!soundMute) {
                    sound.soundOn();
                }
                break;

            case "Z0":
                Log.d(TAG, "sound off");
                sound.soundOff();
                break;

            case "CR":
                Log.d(TAG, "clock restart");
                hideUI();
                break;

            case "KC":
                processKeyClick();
                break;

            default:
                Log.d(TAG, "unknown command " + cmd);
                break;
        }
    }

    public synchronized void processScore(String s_A, String s_B) {
        scoreHidden = false;
        setScore(s_A, s_B);
    }

    public synchronized void processHit(String hit) {
        /* Process off-target hits first */
        if (weapon == Weapon.Foil) {
            if (hit.equals("O0")) {
                hideUI();
                hitA = Hit.OffTarget;
            }
            if (hit.equals("O1")) {
                hideUI();
                hitB = Hit.OffTarget;
            }
        }

        /* Process on-target hits */
        if (hit.equals("H0")) {
            hideUI();
            hitA = hitB = Hit.None;
        }
        if (hit.equals("H1")) {
            hideUI();
            hitA = Hit.OnTarget;
        }
        if (hit.equals("H2")) {
            hideUI();
            hitB = Hit.OnTarget;
        }
        if (hit.equals("H3")) {
            hideUI();
            hitA = Hit.OnTarget;
            hitB = Hit.None;
        }
        if (hit.equals("H4")) {
            hideUI();
            hitA = Hit.None;
            hitB = Hit.OnTarget;
        }
        if (hit.equals("S0")) {
            hitA = Hit.OnTarget;
            hitB = Hit.None;
        }
        if (hit.equals("S1")) {
            hitA = Hit.None;
            hitB = Hit.OnTarget;
        }
        setHitLights(hitA, hitB);
    }

    public synchronized void processCard(String whichFencer, String whichCard) {
        Log.d(TAG, "process card");
        hideUI();

        if (whichFencer.equals("0")) {
            cardA = Integer.parseInt(whichCard);
            setCard(whichFencer, cardA);
        } else if (whichFencer.equals("1")) {
            cardB = Integer.parseInt(whichCard);
            setCard(whichFencer, cardB);
        }
    }

    public synchronized void processClock(String min, String sec, String hund, boolean hundActive) {
        if (setClock(min, sec, hund, hundActive)) {
            if (mode == Mode.Bout) {
                if (passivityActive && passivityTimer > 0) {
                    passivityTimer--;
                    setPassivity(passivityTimer);
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
        if (changeWeapon != weapon) {
            switch (changeWeapon) {
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
            Iterator it = keyQ.iterator();

            while (it.hasNext()) {
                Character key = (Character) it.next();
                msg = "/" + key.toString();
                it.remove();
            }
        }

        /* If a response has to be sent, send it */
        if (!msg.isEmpty()) {
            try {
                socket.write(msg.getBytes(StandardCharsets.UTF_8));
            } catch (IOException e) {
                Log.d(TAG, "unable to respond to poll");
            }
        }
    }

    public void setShortCircuit(String fencer, String scState) {
        Log.d(TAG, "short-circuit: fencer " + fencer + " state " + scState);

        // Ignore for now, as the short-circuit LED already works
    }

    public synchronized void processKeyClick() {
        click.soundOn();
    }

    public String msgScore() {
        char hitA, hitB;

        switch (this.hitA) {
            case None:
            default:
                hitA = '-';
                break;
            case OnTarget:
                hitA = 'h';
                break;
            case OffTarget:
                hitA = 'o';
                break;
        }
        switch (this.hitB) {
            case None:
            default:
                hitB = '-';
                break;
            case OnTarget:
                hitB = 'h';
                break;
            case OffTarget:
                hitB = 'o';
                break;
        }
        String s = String.format("%02dS%c%c:%s:%s",
                piste,
                hitA,
                hitB,
                scoreA,
                scoreB);
        return s;
    }

    public String msgClock() {
        String s = String.format("T%s:%s:%s",
                timeMins,
                timeSecs,
                timeHund);
        return s;
    }

    public String cardStr(Integer card) {
        String cs = "";
        cs += ((card & yellowCardBit)   != 0) ? "y" : "-";
        cs += ((card & redCardBit)      != 0) ? "r" : "-";
        cs += ((card & shortCircuitBit) != 0) ? "s" : "-";
        return cs;
    }

    public String msgCard() {
        String s = String.format("C%s:%s",
                cardStr(cardA),
                cardStr(cardB));
        return s;
    }

    public String msgPriority() {
        String s = String.format("P%c:%c",
                priA ? 'y':'-',
                priB ? 'y':'-');
        return s;
    }

    public String msgResetLights() {
        String s = String.format("%02dR", piste);
        return s;
    }

    public String msgFull() {
        return msgScore() + msgClock() + msgPriority() + msgCard();
    }

    public void txResetLights() {
        Log.d(TAG, "txResetLights");
        String msg = msgResetLights();
        if (connected == Connected.True) {
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

    public void startTxFull() {
        Log.d(TAG, "Tx full status");
        new Thread(new Runnable() {
            @Override
            public void run() {
                for (;;) {
                    try {
                        if (connected == Connected.True) {
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
        Log.d(TAG, "connected to " + socket.getName());
        connected = Connected.True;
        bc.connected(true);
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
                progress.setVisibility(View.INVISIBLE);
            }
        });
    }

    public void reconnect() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                disconnect();
                while (thisActivity.connected != Connected.True) {
                    Log.d(TAG, "reconnecting USB device");
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        break;
                    }
                    thisActivity.connect();
                }
                Log.d(TAG, "reconnected USB device");
                bc.connected(true);
            }
        }).start();
    }

    @Override
    public void onSerialConnectError(Exception e) {
        Log.d(TAG, "connection failed: " + e.getMessage());
        bc.connected(false);
        showUI();
        clearHitLights();
        clearScore();
        clearClock();
        clearCard();
        clearPriority();
        clearPassivity();
        clearPassivityCard();
        progress.setVisibility(View.GONE);
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
        Log.d(TAG, "connection lost: " + e.getMessage());
        bc.connected(false);
        mode = Mode.None;
        showUI();
        clearHitLights();
        clearScore();
        clearClock();
        clearCard();
        clearPriority();
        clearPassivity();
        clearPassivityCard();
        progress.setVisibility(View.GONE);
        reconnect();
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder binder) {
        service = ((SerialService.SerialBinder) binder).getService();
        service.attach(this);
        if (initialStart && isResumed) {
            initialStart = false;
            Log.d(TAG, "service connected - connecting to device");
            runOnUiThread(this::connect);
        }
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        service = null;
        Log.d(TAG, "service not connected");
    }
}