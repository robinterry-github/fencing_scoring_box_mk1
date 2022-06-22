package com.robinterry.fencingboxapp;

import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Build;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.constraintlayout.widget.ConstraintLayout;
import com.robinterry.fencingboxapp.FencingBoxActivity.*;

import com.robinterry.fencingboxapp.databinding.ActivityMainBinding;
import com.robinterry.fencingboxapp.databinding.ActivityMainLandBinding;

import com.robinterry.constants.C;

@SuppressWarnings("ALL")
public class FencingBoxDisplay {
    private Box box;
    public static final String TAG = FencingBoxDisplay.class.getSimpleName();
    public TextView textScore, textScoreA, textScoreB, textClock;
    public TextView priorityA, priorityB;
    public TextView passivityClock, batteryLevel, time;
    public TextView[] passCard = new TextView[]{null, null};
    private ImageView muteIcon;
    private ImageView onlineIcon;
    private ImageView vibrateIcon;
    public ConstraintLayout layout;
    private ProgressBar progress;
    private FencingBoxActivity mainActivity;
    private Orientation orientation = Orientation.Portrait;
    private final boolean controlUI = true;
    private boolean visibleUI = true;
    private Typeface face;

    /* View bindings */
    private ActivityMainBinding portBinding = null;
    private ActivityMainLandBinding landBinding = null;

    private HitLightView hitLightA, hitLightB;
    private CardLightView cardLightA, cardLightB;

    public FencingBoxDisplay(FencingBoxActivity mainActivity,
                             Box box,
                             ConstraintLayout layout,
                             Orientation orientation,
                             ActivityMainBinding portBinding,
                             ActivityMainLandBinding landBinding) {

        this.mainActivity = mainActivity;
        this.box = box;
        this.layout = layout;
        this.orientation = orientation;
        this.portBinding = portBinding;
        this.landBinding = landBinding;
        this.face = Typeface.createFromAsset(mainActivity.getAssets(), "font/DSEG7Classic-Bold.ttf");

        // Set up the display
        setupText(this.box, this.orientation);

        // Clear progress bar
        progress.setIndeterminate(true);
        progress.setVisibility(View.INVISIBLE);
    }

    public boolean isUIVisible() { return visibleUI; }

    public void setupText(Box box, ConstraintLayout layout, FencingBoxActivity.Orientation orient) {
        hitLightA.setLayout(layout);
        hitLightB.setLayout(layout);
        cardLightA.setLayout(layout);
        cardLightB.setLayout(layout);
        layout.setBackgroundColor(Color.BLACK);
        setupText(box, orient);
    }

    public void setupText(Box box, FencingBoxActivity.Orientation orient) {
        orientation = orient;
        mainActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (orientation == FencingBoxActivity.Orientation.Landscape) {
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
                    onlineIcon = (ImageView) landBinding.iconOnlineL;
                    vibrateIcon = (ImageView) landBinding.iconVibrateL;
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
                    onlineIcon = (ImageView) portBinding.iconOnline;
                    vibrateIcon = (ImageView) portBinding.iconVibrate;
                    progress = portBinding.priorityChoose;
                }
                try {
                    if (orientation == FencingBoxActivity.Orientation.Landscape) {
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

                    /* Check if the box is connected or not */
                    setPassivityClockColor(Color.GREEN);
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
                    Log.e(TAG, "unable to find font " + e);
                }
            }
        });
    }

    public void hideUI() {
        if (controlUI) {
            if (!box.isModeNone()) {
                mainActivity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            mainActivity.getWindow().setDecorFitsSystemWindows(false);
                            if (mainActivity.getWindow().getInsetsController() != null) {
                                mainActivity.getWindow().getInsetsController().hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                                mainActivity.getWindow().getInsetsController().setSystemBarsBehavior(
                                        /*WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE*/
                                        WindowInsetsController.BEHAVIOR_SHOW_BARS_BY_SWIPE);
                            }
                        } else {
                            View decorView = mainActivity.getWindow().getDecorView();
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

    public void hideUIIfVisible() {
        if (visibleUI) {
            hideUI();
        }
    }

    public void showUI() {
        if (controlUI) {
            if (!visibleUI) {
                mainActivity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            mainActivity.getWindow().setDecorFitsSystemWindows(true);
                            if (mainActivity.getWindow().getInsetsController() != null) {
                                mainActivity.getWindow().getInsetsController().show(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                            }
                        } else {
                            View decorView = mainActivity.getWindow().getDecorView();
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

    public void displayHitLights(Box.Hit h_A, Box.Hit h_B) {
        hitLightA.showLights(h_A);
        hitLightB.showLights(h_B);
    }

    public void displayScore(String scoreA, String scoreB) {
        displayScore(scoreA, scoreB, false);
    }

    public void displayScore(String scoreA, String scoreB, boolean scoreHidden) {
        String score = scoreA + " " + scoreB;
        mainActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (scoreHidden) {
                    clearScore(orientation);
                } else if (box.isModeStopwatch()) {
                    clearScore(orientation);
                } else if (orientation == FencingBoxActivity.Orientation.Landscape) {
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

    public void clearScore(FencingBoxActivity.Orientation orient) {
        mainActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (orientation == FencingBoxActivity.Orientation.Landscape) {
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

    public void displayClock(String timeMins, String timeSecs, String timeHund, boolean hundActive) {
        String clock;
        if (hundActive) {
            clock = timeSecs + ":" + timeHund;
        } else {
            clock = timeMins + ":" + timeSecs;
        }
        mainActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                textClock.setTextColor(Color.GREEN);
                textClock.setText(clock);
            }
        });
    }

    public void clearClock(int color) {
        mainActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                textClock.setTextColor(color);
                textClock.setText("----");
            }
        });
    }

    public void displayCard(String whichFencer, Integer card) {
        boolean yellowCard = ((card & Box.yellowCardBit) != 0) ? true : false;
        boolean redCard = ((card & Box.redCardBit) != 0) ? true : false;
        boolean shortCircuit = ((card & Box.shortCircuitBit) != 0) ? true : false;

        /* Cards for fencer A */
        if (whichFencer.equals("0")) {
            displayCardA(yellowCard, redCard, shortCircuit);
        }

        /* Cards for fencer B */
        if (whichFencer.equals("1")) {
            displayCardB(yellowCard, redCard, shortCircuit);
        }
    }

    public void displayCardA(boolean yellowCard, boolean redCard, boolean shortCircuit) {
        mainActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                cardLightA.showYellow(yellowCard);
                cardLightA.showRed(redCard);
                cardLightA.showShortCircuit(shortCircuit);
            }
        });
    }

    public void displayCardB(boolean yellowCard, boolean redCard, boolean shortCircuit) {
        mainActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                cardLightB.showYellow(yellowCard);
                cardLightB.showRed(redCard);
                cardLightB.showShortCircuit(shortCircuit);
            }
        });
    }

    public void displayPriority(boolean priIndicator, boolean priA, boolean priB) {
        mainActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (C.DEBUG) {
                    Log.d(TAG, "priority indicator " + priIndicator
                            + ", fencer A " + priA + ", fencer B " + priB);
                }
                setProgressBarVisibility(priIndicator ? View.VISIBLE:View.INVISIBLE);
                priorityA.setTextColor(priA ? Color.RED : Color.BLACK);
                priorityB.setTextColor(priB ? Color.RED : Color.BLACK);
            }
        });
    }

    public void displayPassivityAsClock(int pClock) {
        mainActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                passivityClock.setTypeface(face);
                passivityClock.setText(String.format("%02d", pClock));
            }
        });
    }

    public void displayPassivityAsPiste(Box b) {
        mainActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                passivityClock.setTypeface(null);
                passivityClock.setTextColor(b.rxOk ? Color.WHITE:Color.RED);
                passivityClock.setText(b.piste.toString());
            }
        });
    }

    public void blankPassivityClock() {
        mainActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                passivityClock.setTypeface(face);
                passivityClock.setText("--");
            }
        });
    }

    public void setPassivityClockColor(int color) {
        mainActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                passivityClock.setTextColor(color);
            }
        });
    }

    public void setProgressBarVisibility(int visibility) {
        if (progress != null) {
            mainActivity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    progress.setVisibility(visibility);
                }
            });
        }
    }

    public void createLights() {
        try {
            hitLightA = new HitLightView(mainActivity, layout, HitLightView.HitLight.HitA, Color.RED);
        } catch (Exception e) {
            Log.e(TAG, "Unable to open hit light A view " + e);
        }
        try {
            hitLightB = new HitLightView(mainActivity, layout, HitLightView.HitLight.HitB, Color.GREEN);
        } catch (Exception e) {
            Log.e(TAG, "Unable to open hit light B view " + e);
        }
        try {
            cardLightA = new CardLightView(mainActivity, layout, CardLightView.CardLight.CardA);
        } catch (Exception e) {
            Log.e(TAG, "Unable to open card light A view " + e);
        }
        try {
            cardLightB = new CardLightView(mainActivity, layout, CardLightView.CardLight.CardB);
        } catch (Exception e) {
            Log.e(TAG, "Unable to open card box B view " + e);
        }
    }

    public void setBatteryLevel(int batteryLvl, boolean batteryDangerFlash) {
        mainActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (batteryLvl >= 0 && batteryLvl <= 100) {
                    batteryLevel.setTextColor(batteryDangerFlash ? Color.BLACK : Color.WHITE);
                    batteryLevel.setText(String.valueOf(batteryLvl) + "%");
                } else {
                    batteryLevel.setTextColor(Color.BLACK);
                    batteryLevel.setText("----");
                }
            }
        });
    }

    public void blankBatteryLevel() {
        mainActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                batteryLevel.setTextColor(Color.BLACK);
            }
        });
    }

    public void setVolumeMuted(boolean muted) {
        mainActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                muteIcon.setVisibility(muted ? View.VISIBLE:View.INVISIBLE);
            }
        });
    }

    public void setOnline(boolean online) {
        mainActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                onlineIcon.setVisibility(online ? View.VISIBLE:View.INVISIBLE);
            }
        });
    }

    public void setVibrate(boolean vibrating) {
        mainActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                vibrateIcon.setVisibility(vibrating ? View.VISIBLE:View.INVISIBLE);
            }
        });
    }

    public void setTime(String currentTime) {
        mainActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                time.setText(currentTime);
            }
        });
    }

    public void displayPassivityCard(Box box, int fencer) {
        displayPassivityCard(box, fencer, box.pCard[fencer]);
    }

    public void displayPassivityCard(Box box, int fencer, PassivityCard pCard) {
        mainActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
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
        });
    }

    public void displayBox(Box box) {
        synchronized (this) {
            displayClock(box.timeMins, box.timeSecs, box.timeHund, false);
            displayScore(box.scoreA, box.scoreB);
            displayHitLights(box.hitA, box.hitB);
            displayPriority(box.priIndicator, box.priA, box.priB);
            displayCard("0", box.cardA);
            displayCard("1", box.cardB);
            if (box.passivityActive) {
                setPassivityClockColor(Color.GREEN);
                displayPassivityAsClock(box.passivityTimer);
            } else {
                setPassivityClockColor(Color.WHITE);
                displayPassivityAsPiste(box);
            }
            displayPassivityCard(box, 0, box.pCard[0]);
            displayPassivityCard(box, 1, box.pCard[1]);
        }
    }

    public void displayBoxIfChanged(Box oldBox, Box newBox) {
        synchronized (this) {
            if (newBox.compareTime(oldBox)) {
                displayClock(newBox.timeMins, newBox.timeSecs, newBox.timeHund, false);
            }
            if (newBox.compareScore(oldBox)) {
                displayScore(newBox.scoreA, newBox.scoreB);
            }
            if (oldBox.hitA != newBox.hitA || oldBox.hitB != newBox.hitB) {
                displayHitLights(newBox.hitA, newBox.hitB);
            }
            if (oldBox.priA != newBox.priA ||
                    oldBox.priB != newBox.priB ||
                    oldBox.priIndicator != newBox.priIndicator) {
                displayPriority(newBox.priIndicator, newBox.priA, newBox.priB);
            }
            if (oldBox.cardA != newBox.cardA) {
                displayCard("0", newBox.cardA);
            }
            if (oldBox.cardB != newBox.cardB) {
                displayCard("1", newBox.cardB);
            }
            if (box.passivityActive) {
                if (oldBox.passivityTimer != newBox.passivityTimer) {
                    setPassivityClockColor(Color.GREEN);
                    displayPassivityAsClock(newBox.passivityTimer);
                }
            } else {
                setPassivityClockColor(Color.WHITE);
                displayPassivityAsPiste(newBox);
            }
            if (oldBox.pCard[0] != newBox.pCard[0]) {
                displayPassivityCard(box, 0, newBox.pCard[0]);
            }
            if (oldBox.pCard[1] != newBox.pCard[1]) {
                displayPassivityCard(box, 1, newBox.pCard[1]);
            }
        }
    }
}
