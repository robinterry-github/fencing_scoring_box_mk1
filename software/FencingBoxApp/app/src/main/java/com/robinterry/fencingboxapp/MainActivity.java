package com.robinterry.fencingboxapp;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;

import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;

import android.app.PendingIntent;
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
import android.os.IBinder;
import android.widget.TextView;
import android.widget.Toast;
import android.view.View;
import android.view.WindowManager;
import android.view.Window;
import android.view.Gravity;
import android.view.MotionEvent;
import android.os.Bundle;
import android.os.Handler;
import android.os.BatteryManager;
import java.lang.String;
import java.lang.Integer;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.io.IOException;

import android.util.Log;

@SuppressWarnings("ALL")
public class MainActivity extends AppCompatActivity implements ServiceConnection, SerialListener {

    private enum Connected {False, Pending, True}
    private enum Weapon {Foil, Epee, Sabre}
    private enum Mode {None, Sparring, Bout, Stopwatch, Demo}
    private enum PassivityCard {None, Yellow, Red1, Red2  }
    public static enum Orientation {Portrait, Landscape}
    public static enum Hit {None, OnTarget, OffTarget}

    private Mode mode = Mode.None; /* Mode.Demo */
    private static Orientation orientation = Orientation.Portrait;
    private Weapon weapon = Weapon.Foil;

    public static Orientation getOrientation() {
        return orientation;
    }

    public TextView textScore, textScoreA, textScoreB, textClock;
    public TextView priorityA, priorityB;
    public TextView passivityClock, batteryLevel, time;
    public TextView[] passCard = new TextView[]{null, null};
    private boolean batteryDangerActive = false;
    private boolean batteryDangerFlash = false;
    public String scoreA = "00", scoreB = "00";
    public String timeMins = "00", timeSecs = "00", timeHund = "00";
    public int passivityTimer = 0;
    public boolean passivityActive = false;
    private static Integer cardA = 0;
    private static Integer cardB = 0;
    public int stopwatchHours = 0;
    public Hit hitA = Hit.None, hitB = Hit.None;
    public boolean priA = false, priB = false;
    private boolean scoreHidden = false;
    private static final String TAG = "FencingBoxApp";
    private final int passivityMaxTime = 60;
    private final int batteryDangerLevel = 10;
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
    private PassivityCard[] pCard = new PassivityCard[]{PassivityCard.None, PassivityCard.None};
    private final Integer portNum = 0;
    private final Integer baudRate = 500000;
    private UsbSerialPort usbSerialPort;
    private SerialService service;
    private boolean initialStart = true;
    private boolean isResumed = false;
    private SerialSocket socket;
    private HitLightView hitLightA, hitLightB;
    private CardLightView cardLightA, cardLightB;
    private final int ledSize = 200;
    private final boolean controlUI = true;
    private boolean visibleUI = false;

    /* Commands from the fencing scoring box */
    private final byte cmdMarker = '!';
    private final byte clockMarker1 = '@';
    private final byte clockMarker2 = ':';
    private final byte scoreMarker = '*';
    private final byte hitMarker = '$';
    private final byte cardMarker = '?';
    private final byte passivityCardMarker = '+';

    public MainActivity() {
        Log.d(TAG, "Initialising broadcast receiver");
        thisActivity = this;
        final BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                Log.d(TAG, "Broadcast receiver intent " + intent);
                if (com.robinterry.fencingboxapp.Constants.INTENT_ACTION_GRANT_USB.equals(intent.getAction())) {
                    Boolean granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false);
                    Log.d(TAG, "GRANT_USB intent " + granted + " received, trying to connect");
                    connect(granted);
                }
            }
        };
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "onCreate start");
        super.onCreate(savedInstanceState);
        orientation = getCurrentOrientation();
        if (orientation == Orientation.Landscape) {
            Log.d(TAG, "initial orientation is landscape");
            setContentView(R.layout.activity_main_land);
            setupText(orientation);
            layout = (ConstraintLayout) findViewById(R.id.activity_main_land);
        } else {
            Log.d(TAG, "initial orientation is portrait");
            setContentView(R.layout.activity_main);
            setupText(orientation);
            layout = (ConstraintLayout) findViewById(R.id.activity_main);
        }

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
        showUI();
        Log.d(TAG, "onCreate end");
    }

    @Override
    protected void onStart() {
        Log.d(TAG, "onStart start");
        super.onStart();
        startService(new Intent(this, SerialService.class));
        orientation = getCurrentOrientation();
        setupText(orientation);
        hideUIIfVisible();
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
        startBatteryMonitorAndTime();
        Log.d(TAG, "onStart end");
    }

    @Override
    protected void onStop() {
        Log.d(TAG, "onStop start");
        super.onStop();
        Log.d(TAG, "onStop end");
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        Log.d(TAG, "onWindowFocusChanged hasFocus " + hasFocus);
        if (hasFocus) {
            hideUI();
        }
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy start");
        if (connected != Connected.False)
            disconnect(true);
        stopService(new Intent(this, SerialService.class));
        super.onDestroy();
        Log.d(TAG, "onDestroy end");
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if ("android.hardware.usb.action.USB_DEVICE_ATTACHED".equals(intent.getAction())) {
            Log.d(TAG, "USB device attached");
        }
    }

    @Override
    protected void onResume() {
        Log.d(TAG, "onResume start");
        super.onResume();
        if (!isResumed) {
            isResumed = true;
            bindService(new Intent(this, SerialService.class), this, Context.BIND_AUTO_CREATE);
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        hideUIIfVisible();
        Log.d(TAG, "onResume end");
    }

    @Override
    protected void onPause() {
        Log.d(TAG, "onPause start");
        super.onPause();
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
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
        Log.d(TAG, "onRestart end");
    }

    @Override
    public void onConfigurationChanged(Configuration newConf) {
        super.onConfigurationChanged(newConf);
        /* Checks the orientation of the screen */
        orientation = getCurrentOrientation();
        if (orientation == Orientation.Landscape) {
            Log.d(TAG, "orientation is now landscape");
            setContentView(R.layout.activity_main_land);
            layout = (ConstraintLayout) findViewById(R.id.activity_main_land);
            orientation = Orientation.Landscape;
        } else {
            Log.d(TAG, "orientation is now portrait");
            setContentView(R.layout.activity_main);
            layout = (ConstraintLayout) findViewById(R.id.activity_main);
            orientation = Orientation.Portrait;
        }
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
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        super.onTouchEvent(event);

        Log.d(TAG, "onTouchEvent " + event);
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            hideUI();
            return true;
        }
        return false;
    }

    public void showDemo() {
        hideUI();
        displayHitLights(Hit.OnTarget, Hit.OnTarget);
        displayScore("00", "00");
        displayClock("00", "00", "00", false);
        displayCardA(true, true);
        displayCardB(true, true);
        displayPriority(true, true);
        displayPassivity(passivityMaxTime);
        displayPassivityCard(0, PassivityCard.Red2);
        displayPassivityCard(1, PassivityCard.Yellow);
    }

    public void startBatteryMonitorAndTime() {
        final int delayMillis = 500;

        Handler handler = new Handler();
        final Runnable r = new Runnable() {
            public void run() {
                if (!visibleUI) {
                    BatteryManager batt = (BatteryManager) getApplicationContext().getSystemService(BATTERY_SERVICE);
                    int level = batt.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
                    if (level < batteryDangerLevel) {
                        if (!batteryDangerActive) {
                            batteryDangerActive = batteryDangerFlash = true;
                        } else {
                            batteryDangerFlash = batteryDangerFlash ? false : true;
                        }
                    } else {
                        batteryDangerActive = batteryDangerActive = false;
                    }
                    batteryLevel.setTextColor(batteryDangerFlash ? Color.BLACK : Color.WHITE);
                    batteryLevel.setText(String.valueOf(level) + "%");

                    Date curTime = Calendar.getInstance().getTime();
                    SimpleDateFormat dateFormat = new SimpleDateFormat("hh:mm");
                    String tm = dateFormat.format(curTime);
                    time.setText(tm);
                } else {
                    batteryLevel.setTextColor(Color.BLACK);
                }
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
        if (orient == Orientation.Landscape) {
            textScoreA = findViewById(R.id.textScoreA_l);
            textScoreB = findViewById(R.id.textScoreB_l);
            textScoreA.setGravity(Gravity.CENTER);
            textScoreB.setGravity(Gravity.CENTER);
            textClock = findViewById(R.id.textClock_l);
            priorityA = findViewById(R.id.priorityA_l);
            priorityB = findViewById(R.id.priorityB_l);
            passivityClock = findViewById(R.id.passivityClock_l);
            batteryLevel = findViewById(R.id.battery_level_land);
            time = findViewById(R.id.time_land);
            passCard[0] = findViewById(R.id.pCardA_land);
            passCard[1] = findViewById(R.id.pCardB_land);
        } else {
            textScore = findViewById(R.id.textScore);
            textScore.setGravity(Gravity.CENTER);
            textClock = findViewById(R.id.textClock);
            priorityA = findViewById(R.id.priorityA);
            priorityB = findViewById(R.id.priorityB);
            passivityClock = findViewById(R.id.passivityClock);
            batteryLevel = findViewById(R.id.battery_level);
            time = findViewById(R.id.time);
            passCard[0] = findViewById(R.id.pCardA);
            passCard[1] = findViewById(R.id.pCardB);
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
            //passCard[0].setTypeface(face);
            passCard[0].setTypeface(null, Typeface.BOLD);
            passCard[0].setTextColor(Color.BLACK);
            passCard[0].setGravity(Gravity.CENTER);
            //passCard[1].setTypeface(face);
            passCard[1].setTypeface(null, Typeface.BOLD);
            passCard[1].setTextColor(Color.BLACK);
            passCard[1].setGravity(Gravity.CENTER);
        } catch (Exception e) {
            Log.d(TAG, "unable to find font " + e);
        }
    }

    private void hideUI() {
        if (controlUI) {
            if (mode != Mode.None) {
                View decorView = getWindow().getDecorView();
                decorView.setSystemUiVisibility(
                        View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                                | View.SYSTEM_UI_FLAG_LOW_PROFILE
                                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                                | View.SYSTEM_UI_FLAG_FULLSCREEN);
                Log.d(TAG, "hide UI");
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
                View decorView = getWindow().getDecorView();
                decorView.setSystemUiVisibility(
                        View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
                Log.d(TAG, "show UI");
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
            Log.d(TAG, "Found USB device");
            Log.d(TAG, "Product: " + v.getProductName() + " Vendor: "
                    + v.getVendorId() + " Device: " + v.getDeviceName());
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

    private void disconnect() {
        disconnect(false);
    }

    private void disconnect(boolean quitActivity) {
        Log.d(TAG, "disconnected from USB device");
        connected = Connected.False;
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
        Log.d(TAG, "clearing hit lights");
        setHitLights(Hit.None, Hit.None);
    }

    public void setScoreA(String s_A) {
        scoreA = s_A;
        Log.d(TAG, "set score A " + s_A);
        setScore(scoreA, scoreB);
    }

    public void setScoreB(String s_B) {
        scoreB = s_B;
        Log.d(TAG, "set score B " + s_B);
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
        Log.d(TAG, "setScore: " + score);

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

    public void clearScore() {
        Log.d(TAG, "clear score");
        scoreA = scoreB = "00";
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
        Log.d(TAG, "setClock: " + clock);
        textClock.setTextColor(Color.GREEN);
        textClock.setText(clock);
    }

    public void clearClock() {
        Log.d(TAG, "clear clock");
        textClock.setTextColor(Color.BLACK);
        textClock.setText("----");
    }

    public void setCard() {
        setCard("0", cardA);
        setCard("1", cardB);
    }

    public void displayCardA(boolean yellowCard, boolean redCard) {
        cardLightA.showYellow(yellowCard);
        cardLightA.showRed(redCard);
    }

    public void displayCardB(boolean yellowCard, boolean redCard) {
        cardLightB.showYellow(yellowCard);
        cardLightB.showRed(redCard);
    }

    public void setCard(String whichFencer, Integer card) {
        boolean yellowCard = ((card & yellowCardBit) != 0) ? true : false;
        boolean redCard = ((card & redCardBit) != 0) ? true : false;

        /* Cards for fencer A */
        if (whichFencer.equals("0")) {
            displayCardA(yellowCard, redCard);
        }

        /* Cards for fencer B */
        if (whichFencer.equals("1")) {
            displayCardB(yellowCard, redCard);
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
        priorityA.setTextColor(priA ? Color.RED:Color.BLACK);
        priorityB.setTextColor(priB ? Color.RED:Color.BLACK);
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
        }
    }

    public void displayPassivity(int pClock) {
        passivityClock.setText(String.format("%02d", pClock));
    }

    public void clearPassivity() {
        passivityActive = false;
        passivityTimer = passivityMaxTime;
        passivityClock.setTextColor((mode == Mode.Bout) ? Color.GREEN:Color.BLACK);
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

    public void processData(byte data[]) {
        for (int i = 0; i < data.length; i++) {
           if (data[i] == cmdMarker) {
               i++;
               String cmd = new String(data, i, 2, StandardCharsets.UTF_8);
               i += 2;
               processCmd(cmd);
           } else if (data[i] == scoreMarker) {
               i++;
               String s_A = new String(data, i, 2, StandardCharsets.UTF_8);
               i += 2;
               String s_B = new String(data, i, 2, StandardCharsets.UTF_8);
               i += 2;
               processScore(s_A, s_B);
           } else if (data[i] == hitMarker) {
               i++;
               String hit = new String(data, i, 2, StandardCharsets.UTF_8);
               i += 2;
               processHit(hit);
           } else if (data[i] == clockMarker1) {
               i++;
               String min = new String(data, i, 2, StandardCharsets.UTF_8);
               i += 2;
               String sec = new String(data, i, 2, StandardCharsets.UTF_8);
               processClock(min, sec, "00",false);
           } else if (data[i] == clockMarker2) {
               i++;
               String sec = new String(data, i, 2, StandardCharsets.UTF_8);
               i += 2;
               String hund = new String(data, i, 2, StandardCharsets.UTF_8);
               processClock("00", sec, hund, true);
           } else if (data[i] == cardMarker) {
               i++;
               String whichFencer = new String(data, i, 1, StandardCharsets.UTF_8);
               i++;
               String whichCard = new String(data, i, 1, StandardCharsets.UTF_8);
               i++;
               processCard(whichFencer, whichCard);
           } else if (data[i] == passivityCardMarker) {
               i++;
               String whichFencer = new String(data, i, 1, StandardCharsets.UTF_8);
               i++;
               String whichCard = new String(data, i, 1, StandardCharsets.UTF_8);
               i++;
               clearPassivity();
               setPassivityCard(whichFencer, whichCard);
           }
        }
    }

    public void processCmd(String cmd) {
        Log.d(TAG, "Command: " + cmd);
        switch (cmd) {
            case "GO":
                Log.d(TAG, "fencing box started up");
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
                    Log.d(TAG, "Unable to respond to fencing scoring box");
                }
                break;

            case "PC":
                Log.d(TAG, "choosing priority");
                hideUI();
                clearPriority();
                Toast.makeText(getApplicationContext(), R.string.choose_priority, Toast.LENGTH_SHORT).show();
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
                hideUI();
                setPriorityA();
                Log.d(TAG, "priority fencer A start");
                break;

            case "P1":
                hideUI();
                setPriorityB();
                Log.d(TAG, "priority fencer B start");

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
                Log.d(TAG, "Hide score");
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
                clearPassivity();
                break;

            case "TF":
                Log.d(TAG, "weapon: foil");
                weapon = Weapon.Foil;
                setScore();
                setClock();
                setCard();
                Toast.makeText(getApplicationContext(), R.string.weapon_foil, Toast.LENGTH_SHORT).show();
                break;

            case "TE":
                Log.d(TAG, "weapon: epee");
                weapon = Weapon.Epee;
                setScore();
                setClock();
                setCard();
                Toast.makeText(getApplicationContext(), R.string.weapon_epee, Toast.LENGTH_SHORT).show();
                break;

            case "TS":
                Log.d(TAG, "weapon: sabre");
                weapon = Weapon.Sabre;
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

            default:
                Log.d(TAG, "unknown command " + cmd);
                break;
        }
    }

    public void processScore(String s_A, String s_B) {
        scoreHidden = false;
        setScore(s_A, s_B);
    }

    public void processHit(String hit) {
        hideUI();

        /* Process off-target hits first */
        if (weapon == Weapon.Foil) {
            if (hit.equals("O0")) {
                hitA = Hit.OffTarget;
            }
            if (hit.equals("O1")) {
                hitB = Hit.OffTarget;
            }
        }

        /* Process on-target hits */
        if (hit.equals("H0")) {
            hitA = hitB = Hit.None;
        }
        if (hit.equals("H1")) {
            hitA = Hit.OnTarget;
        }
        if (hit.equals("H2")) {
            hitB = Hit.OnTarget;
        }
        if (hit.equals("H3")) {
            hitA = Hit.OnTarget;
            hitB = Hit.None;
        }
        if (hit.equals("H4")) {
            hitA = Hit.None;
            hitB = Hit.OnTarget;
        }
        setHitLights(hitA, hitB);
    }

    public void processCard(String whichFencer, String whichCard) {
        hideUI();

        if (whichFencer.equals("0")) {
            cardA = Integer.parseInt(whichCard);
            setCard(whichFencer, cardA);
        } else if (whichFencer.equals("1")) {
            cardB = Integer.parseInt(whichCard);
            setCard(whichFencer, cardB);
        }
    }

    public void processClock(String min, String sec, String hund, boolean hundActive) {
        if (setClock(min, sec, hund, hundActive)) {
            if (mode == Mode.Bout) {
                if (passivityActive && passivityTimer > 0) {
                    passivityTimer--;
                    setPassivity(passivityTimer);
                }
            }
        }
    }

    /*
     * SerialListener
     */
    @Override
    public void onSerialConnect() {
        Log.d(TAG, "connected to " + socket.getName());
        connected = Connected.True;
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
            }
        });
    }

    public void reconnect() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                Log.d(TAG, "reconnecting USB device");
                disconnect();
                while (thisActivity.connected != Connected.True) {
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        break;
                    }
                    thisActivity.connect();
                }
                Log.d(TAG, "reconnected USB device");
            }
        }).start();
    }

    @Override
    public void onSerialConnectError(Exception e) {
        Log.d(TAG, "connection failed: " + e.getMessage());
        showUI();
        clearHitLights();
        clearScore();
        clearClock();
        clearCard();
        clearPriority();
        clearPassivity();
        clearPassivityCard();
        reconnect();
    }

    @Override
    public void onSerialRead(byte[] data) {
        /* Process the incoming data here */
        String s = new String(data, StandardCharsets.UTF_8);
        Log.d(TAG, "data: " + s);
        processData(data);
    }

    @Override
    public void onSerialIoError(Exception e) {
        Log.d(TAG, "connection lost: " + e.getMessage());
        mode = Mode.None;
        showUI();
        clearHitLights();
        clearScore();
        clearClock();
        clearCard();
        clearPriority();
        clearPassivity();
        clearPassivityCard();
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