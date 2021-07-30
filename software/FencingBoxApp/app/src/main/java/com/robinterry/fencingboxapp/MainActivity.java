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
import java.lang.String;
import java.lang.Integer;
import java.nio.charset.StandardCharsets;

import android.util.Log;

@SuppressWarnings("ALL")
public class MainActivity extends AppCompatActivity implements ServiceConnection, SerialListener {

    public static enum Orientation { Portrait, Landscape }
    private static Orientation orientation = Orientation.Portrait;
    public static Orientation getOrientation() { return orientation; }

    public TextView textScore, textScoreA, textScoreB, textClock;
    public TextView priorityA, priorityB;
    public String scoreA = "00", scoreB = "00";
    public String timeMins = "00", timeSecs = "00";
    private static Integer cardA = 0;
    private static Integer cardB = 0;
    public boolean hitA = false, hitB = false;
    public boolean priA = false, priB = false;
    private static final String TAG = "FencingBoxApp";
    public static final Integer hitAColor       = 0xFFFF0000;
    public static final Integer hitBColor       = 0xFF00FF00;
    public static final Integer inactiveColor   = 0xFFE0E0E0;
    public static final Integer yellowCardColor = 0xFFFFFF00;
    public static final Integer redCardColor    = 0xFFFF0000;
    public static final Integer yellowCardBit   = 0x01;
    public static final Integer redCardBit      = 0x02;
    public static final Integer shortCircuitBit = 0x04;
    public MainActivity thisActivity;
    public static ConstraintLayout layout;

    private enum Connected { False, Pending, True }
    private enum Weapon { Foil, Epee, Sabre }
    private enum Mode { Sparring, Bout, Stopwatch }

    private Connected connected = Connected.False;
    private Weapon weapon = Weapon.Foil;
    private Mode mode = Mode.Sparring;
    private final Integer portNum = 0;
    private final Integer baudRate = 230400;
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

    private final byte cmdMarker = '!';
    private final byte clockMarker = '@';
    private final byte scoreMarker = '*';
    private final byte hitMarker = '$';
    private final byte cardMarker = '?';

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
        setHitLights();
        setScore();
        setClock();
        setCard();
        setPriority();
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
        setHitLights();
        setScore();
        setClock();
        setCard();
        setPriority();
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

    private Orientation getCurrentOrientation() {
        Configuration newConf = getResources().getConfiguration();
        return (newConf.orientation == Configuration.ORIENTATION_LANDSCAPE) ?
                Orientation.Landscape:Orientation.Portrait;
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
        } else {
            textScore = findViewById(R.id.textScore);
            textScore.setGravity(Gravity.CENTER);
            textClock = findViewById(R.id.textClock);
            priorityA = findViewById(R.id.priorityA);
            priorityB = findViewById(R.id.priorityB);
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
            priorityA.setTextColor(Color.BLACK);
            priorityA.setGravity(Gravity.CENTER);
            priorityB.setTextColor(Color.BLACK);
            priorityB.setTextColor(Gravity.CENTER);
        } catch (Exception e) {
            Log.d(TAG, "unable to find font " + e);
        }
    }

    private void hideUI() {
        if (controlUI) {
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
            Log.d(TAG, "Found USB device " + v.getProductName());
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

    public void setHitLights(boolean l_A, boolean l_B) {
        Log.d(TAG, "setHitLights: " + (l_A ? "ON":"OFF") + ":" + (l_B ? "ON":"OFF"));
        hitA = l_A;
        hitB = l_B;
        hitLightA.showLights(hitA);
        hitLightB.showLights(hitB);
    }

    public void clearHitLights() {
        Log.d(TAG, "clearing hit lights");
        hitA = hitB = false;
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
        String score = scoreA + " " + scoreB;
        Log.d(TAG, "setScore: " + score);
        if (mode != Mode.Bout) {
            clearScore();
        } else {
            if (orientation == Orientation.Landscape) {
                textScoreA.setText(scoreA);
                textScoreB.setText(scoreB);
            } else {
                textScore.setText(score);
            }
        }
    }

    public void clearScore() {
        Log.d(TAG, "clear score");
        scoreA = scoreB = "00";
        if (orientation == Orientation.Landscape) {
            textScoreA.setText("--");
            textScoreB.setText("--");
        } else {
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
        if (mode == Mode.Sparring) {
            clearClock();
        } else {
            setClock(timeMins, timeSecs);
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

    public void setClock() {
        setClock(timeMins, timeSecs);
    }

    public void setClock(String mins, String secs) {
        timeMins = mins;
        timeSecs = secs;
        String clock = timeMins + ":" + timeSecs;
        if (mode == Mode.Sparring) {
            clearClock();
        } else {
            Log.d(TAG, "setClock: " + clock);
            textClock.setText(clock);
        }
    }

    public void clearClock() {
        Log.d(TAG, "clear clock");
        textClock.setText("----");
    }

    public void setCard() {
        setCard("0", cardA);
        setCard("1", cardB);
    }

    public void setCard(String whichFencer, Integer card) {
        /* Cards for fencer A */
        if (whichFencer.equals("0")) {
            cardLightA.showYellow(((card & yellowCardBit) != 0) ? true : false);
            cardLightA.showRed(((card & redCardBit) != 0) ? true : false);
        }

        /* Cards for fencer B */
        if (whichFencer.equals("1")) {
            cardLightB.showYellow(((card & yellowCardBit) != 0) ? true : false);
            cardLightB.showRed(((card & redCardBit) != 0) ? true : false);
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

    public void setPriority(boolean priA, boolean priB)
    {
        this.priA = priA;
        this.priB = priB;
        priorityA.setTextColor(this.priA ? Color.RED:Color.BLACK);
        priorityB.setTextColor(this.priB ? Color.RED:Color.BLACK);
    }

    public void clearPriority()
    {
        setPriority(false, false);
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
           } else if (data[i] == clockMarker) {
               i++;
               String min = new String(data, i, 2, StandardCharsets.UTF_8);
               i += 2;
               String sec = new String(data, i, 2, StandardCharsets.UTF_8);
               i += 2;
               processClock(min, sec);
           } else if (data[i] == cardMarker) {
               i++;
               String whichFencer = new String(data, i, 1, StandardCharsets.UTF_8);
               i++;
               String whichCard = new String(data, i, 1, StandardCharsets.UTF_8);
               i++;
               processCard(whichFencer, whichCard);
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
                break;

            case "PC":
                Log.d(TAG, "choosing priority");
                hideUI();
                Toast.makeText(getApplicationContext(), R.string.choose_priority, Toast.LENGTH_SHORT).show();
                setHitLights(true, true);
                break;

            case "BS":
                Log.d(TAG, "bout starting");
                hideUI();
                mode = Mode.Bout;
                Toast.makeText(getApplicationContext(), R.string.mode_bout, Toast.LENGTH_SHORT).show();
                resetScore();
                resetClock();
                resetCard();
                clearPriority();
                break;

            case "BC":
                Log.d(TAG, "bout continuing");
                break;

            case "BE":
                Log.d(TAG, "bout ending");
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
                break;

            case "WR":
                Log.d(TAG, "stopwatch reset");
                break;

            case "RL":
                Log.d(TAG, "reset lights");
                hideUI();
                hitA = hitB = false;
                setHitLights(hitA, hitB);
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

            default:
                Log.d(TAG, "unknown command " + cmd);
                break;
        }
    }

    public void processScore(String s_A, String s_B) {
        setScore(s_A, s_B);
    }

    public void processHit(String hit) {
        hideUI();
        if (hit.equals("H0")) {
            hitA = hitB = false;
        }
        if (hit.equals("H1")) {
            hitA = true;
        }
        if (hit.equals("H2")) {
            hitB = true;
        }
        if (hit.equals("H3")) {
            hitA = true;
            hitB = false;
        }
        if (hit.equals("H4")) {
            hitA = false;
            hitB = true;
        }
        setHitLights(hitA, hitB);

        /* Ignore the off-target hits for now */
    }

    public void processCard(String whichFencer, String whichCard) {
        hideUI();
        if (whichFencer.equals("0")) {
            cardA = Integer.parseInt(whichCard);
            setCard(whichFencer, cardA);
        }
        if (whichFencer.equals("1")) {
            cardB = Integer.parseInt(whichCard);
            setCard(whichFencer, cardB);
        }
    }

    public void processClock(String min, String sec) {
        setClock(min, sec);
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
        clearHitLights();
        clearScore();
        clearClock();
        clearCard();
        clearPriority();
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
        clearHitLights();
        clearScore();
        clearClock();
        clearCard();
        clearPriority();
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