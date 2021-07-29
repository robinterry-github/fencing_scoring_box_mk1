package com.robinterry.fencingboxapp;

import androidx.constraintlayout.widget.ConstraintLayout;
import android.content.Context;
import android.view.View;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import java.lang.String;

import android.util.Log;

public class CardLightView extends View {

    private static final String TAG = "CardLightView";
    private ConstraintLayout layout;
    private Paint paintYellow, paintRed;
    private int areaXSize = 0;
    private int areaYSize = 0;
    private int leftPos, topPos;
    private boolean redCardOn = false;
    private boolean yellowCardOn = false;
    public enum CardLight { CardA, CardB }
    private CardLight cardLight = CardLight.CardA;
    private final int offColor = Color.BLACK;

    public CardLightView(Context context) {
        super(context);
    }

    public CardLightView(Context context, ConstraintLayout layout, CardLight cardLight) {
        super(context);
        Log.d(TAG, "CardLightView constructor start, left " + leftPos);
        if (MainActivity.getOrientation() == MainActivity.Orientation.Portrait) {
            Log.d(TAG, "Orientation: portrait");
        } else {
            Log.d(TAG, "Orientation: landscape");
        }
        Log.d(TAG, "layout " + layout);

        // Yellow LED
        paintYellow = new Paint();
        paintYellow.setStyle(Paint.Style.FILL);
        paintYellow.setColor(offColor);
        paintYellow.setAntiAlias(true);

        // Red LED
        paintRed = new Paint();
        paintRed.setStyle(Paint.Style.FILL);
        paintRed.setColor(offColor);
        paintRed.setAntiAlias(true);
        this.layout = layout;
        this.cardLight = cardLight;

        layout.addView(this);

        Log.d(TAG, "CardLightView constructor end " + this);
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
        Log.d(TAG, "widthSize " + widthSize + " heightSize " + heightSize);

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

        if (MainActivity.getOrientation() == MainActivity.Orientation.Portrait) {
            areaXSize = screenWidth / HitLightView.LED_SIZE_X_DIV_PORT;
            areaYSize = screenHeight / CARD_SIZE_Y_DIV_PORT;
            topPos = HitLightView.coords.bottomPos;

            if (cardLight == CardLight.CardB) {
                leftPos = screenWidth - (screenWidth / LEFT_MARGIN_DIV);
                leftPos -= areaXSize;
            } else {
                leftPos = screenWidth / LEFT_MARGIN_DIV;
            }
        } else {
            areaXSize = screenHeight / HitLightView.LED_SIZE_X_DIV_LAND;
            areaYSize = screenHeight / CARD_SIZE_Y_DIV_LAND;
            topPos = HitLightView.coords.bottomPos;

            if (cardLight == CardLight.CardB) {
                leftPos = screenWidth - (screenWidth / LEFT_MARGIN_DIV);
                leftPos -= areaXSize;
            } else {
                leftPos = screenWidth / LEFT_MARGIN_DIV;
            }
        }
        Log.d(TAG, "areaXSize " + areaXSize + "  areaYSize " +
                    areaYSize + " leftPos " + leftPos + " topPos " + topPos);
    }

    public void setLayout(ConstraintLayout layout) {
        try {
            this.layout.removeView(this);
            this.layout = layout;
            this.layout.addView(this);
        } catch (Exception e) {
            Log.d(TAG, "Cannot remove view " + this + " exception " + e);
        }
        showYellow(this.yellowCardOn);
        showRed(this.redCardOn);
    }

    public void showYellow(boolean yellowCardOn) {
        this.yellowCardOn = yellowCardOn;
        invalidate();
    }

    public void showRed(boolean redCardOn) {
        this.redCardOn = redCardOn;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        Log.d(TAG, "onDraw called on " + this);
        super.onDraw(canvas);
        setLeft(leftPos);
        setRight(leftPos+areaXSize);
        setTop(topPos);
        setBottom(topPos+areaYSize);

        canvas.drawColor(Color.TRANSPARENT);

        float cx = (float) getWidth()/4;
        float cy = (float) getHeight()/2;
        float radius = (float) areaYSize/3;

        // Plot yellow LED
        paintYellow.setColor(yellowCardOn ? Color.YELLOW:offColor);
        canvas.drawCircle(cx, cy, radius, paintYellow);

        // Change position and plot red LED
        cx = (float) getWidth()*3/4;
        paintRed.setColor(redCardOn ? Color.RED:offColor);
        canvas.drawCircle(cx, cy, radius, paintRed);
    }
}