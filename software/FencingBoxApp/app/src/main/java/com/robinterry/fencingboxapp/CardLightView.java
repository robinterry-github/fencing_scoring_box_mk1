package com.robinterry.fencingboxapp;

import androidx.constraintlayout.widget.ConstraintLayout;
import android.view.View;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import java.lang.String;
import com.robinterry.fencingboxapp.FencingBoxActivity.*;

import android.util.Log;

@SuppressWarnings("ALL")
public class CardLightView extends View {

    private static final String TAG = CardLightView.class.getSimpleName();
    private FencingBoxActivity mainActivity;
    private ConstraintLayout layout;
    private Paint yellowLed, redLed, whiteLed;
    private int areaXSize = 0;
    private int areaYSize = 0;
    private int leftPos, topPos;
    private boolean redCardOn = false;
    private boolean yellowCardOn = false;
    private boolean shortCircuitOn = false;
    public enum CardLight { CardA, CardB }
    private CardLight cardLight = CardLight.CardA;
    private final int offColor = Color.BLACK;

    public CardLightView(FencingBoxActivity mainActivity) {
        super(mainActivity.getBaseContext());
        this.mainActivity = mainActivity;
    }

    public CardLightView(FencingBoxActivity mainActivity, ConstraintLayout layout, CardLight cardLight) {
        super(mainActivity.getBaseContext());
        this.mainActivity = mainActivity;
        if (mainActivity.getOrientation() == Orientation.Portrait) {
            Log.i(TAG, "Orientation: portrait");
        } else {
            Log.i(TAG, "Orientation: landscape");
        }

        // Yellow LED
        yellowLed = new Paint();
        yellowLed.setStyle(Paint.Style.FILL);
        yellowLed.setColor(offColor);
        yellowLed.setAntiAlias(true);

        // Red LED
        redLed = new Paint();
        redLed.setStyle(Paint.Style.FILL);
        redLed.setColor(offColor);
        redLed.setAntiAlias(true);

        // White LED
        whiteLed = new Paint();
        whiteLed.setStyle(Paint.Style.FILL);
        whiteLed.setColor(offColor);
        whiteLed.setAntiAlias(true);
        
        this.layout = layout;
        this.cardLight = cardLight;
        layout.addView(this);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        int screenWidth, screenHeight;
        int desiredWidth = areaXSize;
        int desiredHeight = areaYSize;
        int width, height;
        final int LEFT_MARGIN_DIV = 10;
        final int CARD_SIZE_Y_DIV_PORT = 14;
        final int CARD_SIZE_Y_DIV_LAND = 8;

        int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        int widthSize = MeasureSpec.getSize(widthMeasureSpec);
        int heightMode = MeasureSpec.getMode(heightMeasureSpec);
        int heightSize = MeasureSpec.getSize(heightMeasureSpec);

        screenWidth  = widthSize;
        screenHeight = heightSize;

        if (widthMode == MeasureSpec.EXACTLY) {
            width = widthSize;
        } else if (widthMode == MeasureSpec.AT_MOST) {
            width = Math.min(desiredWidth, widthSize);
        } else {
            width = desiredWidth;
        }

        if (heightMode == MeasureSpec.EXACTLY) {
            height = heightSize;
        } else if (heightMode == MeasureSpec.AT_MOST) {
            height = Math.min(desiredHeight, heightSize);
        } else {
            height = desiredHeight;
        }
        setMeasuredDimension(width, height);

        if (mainActivity.getOrientation() == Orientation.Portrait) {
            areaXSize = HitLightView.coords.ledSizeX;
            areaYSize = screenHeight / CARD_SIZE_Y_DIV_PORT;
            topPos = HitLightView.coords.bottomPos;

            if (cardLight == CardLight.CardB) {
                leftPos = screenWidth - (screenWidth / LEFT_MARGIN_DIV);
                leftPos -= areaXSize;
            } else {
                leftPos = screenWidth / LEFT_MARGIN_DIV;
            }
        } else {
            areaXSize = HitLightView.coords.ledSizeX;
            areaYSize = screenHeight / CARD_SIZE_Y_DIV_LAND;
            topPos = HitLightView.coords.bottomPos;

            if (cardLight == CardLight.CardB) {
                leftPos = screenWidth - (screenWidth / LEFT_MARGIN_DIV);
                leftPos -= areaXSize;
            } else {
                leftPos = screenWidth / LEFT_MARGIN_DIV;
            }
        }
    }

    public void setLayout(ConstraintLayout layout) {
        try {
            this.layout.removeView(this);
            this.layout = layout;
            this.layout.addView(this);
        } catch (Exception e) {
            Log.e(TAG, "Cannot remove view " + this + " exception " + e);
        }
        showYellow(this.yellowCardOn);
        showRed(this.redCardOn);
        showShortCircuit(this.shortCircuitOn);
    }

    public void showYellow(boolean yellowCardOn) {
        this.yellowCardOn = yellowCardOn;
        invalidate();
    }

    public void showRed(boolean redCardOn) {
        this.redCardOn = redCardOn;
        invalidate();
    }

    public void showShortCircuit(boolean scOn) {
        this.shortCircuitOn = scOn;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        mainActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                setLeft(leftPos);
                setRight(leftPos + areaXSize);
                setTop(topPos);
                setBottom(topPos + areaYSize);

                canvas.drawColor(Color.TRANSPARENT);

                float cx;
                float cy = (float) getHeight() / 2;
                float radius;
                if (mainActivity.getOrientation() == Orientation.Landscape) {
                    radius = (float) areaXSize / 16;
                } else {
                    radius = (float) areaXSize / 8;
                }

                /* We want to keep the yellow and red LEDs in the same order,
                   so for  fencer A: yellow, red, white
                   and for fencer B: white, yellow red
                */
                if (cardLight == CardLight.CardA) {
                    // For fencer A, plot the LEDs forwards
                    cx = radius;

                    // Plot yellow LED first
                    yellowLed.setColor(yellowCardOn ? Color.YELLOW : offColor);
                    canvas.drawCircle(cx, cy, radius, yellowLed);

                    // Change position and plot red LED second
                    cx += (float) (radius * 3);
                    redLed.setColor(redCardOn ? Color.RED : offColor);
                    canvas.drawCircle(cx, cy, radius, redLed);

                    // Change position and plot white LED third
                    cx += (float) (radius * 3);
                    whiteLed.setColor(shortCircuitOn ? Color.WHITE : offColor);
                    canvas.drawCircle(cx, cy, radius, whiteLed);
                } else {
                    // For fencer B, plot the LEDs backwards
                    cx = areaXSize - radius;

                    // Plot red LED first
                    redLed.setColor(redCardOn ? Color.RED : offColor);
                    canvas.drawCircle(cx, cy, radius, redLed);

                    // Change position and plot yellow LED second
                    cx -= (float) (radius * 3);
                    yellowLed.setColor(yellowCardOn ? Color.YELLOW : offColor);
                    canvas.drawCircle(cx, cy, radius, yellowLed);

                    // Change position and plot white LED third
                    cx -= (float) (radius * 3);
                    whiteLed.setColor(shortCircuitOn ? Color.WHITE : offColor);
                    canvas.drawCircle(cx, cy, radius, whiteLed);
                }
            }
        });
    }
}