package com.robinterry.fencingboxapp;

import androidx.constraintlayout.widget.ConstraintLayout;

import android.content.Context;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.View;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import java.lang.String;
import com.robinterry.fencingboxapp.FencingBoxActivity.*;
import com.robinterry.constants.C;

import android.util.Log;
import android.view.WindowManager;

@SuppressWarnings("ALL")
public class HitLightView extends View {

    private static final String TAG = HitLightView.class.getSimpleName();
    private FencingBoxActivity mainActivity;
    private ConstraintLayout layout;
    private Paint paint;
    private Box.Hit hit = Box.Hit.None;
    private int leftPos;
    public enum HitLight { HitA, HitB }
    private HitLight hitLight = HitLight.HitA;
    private int onTargetColor = Color.BLACK; // either red or green in practice
    private final int offTargetColor = Color.WHITE;
    private final int offColor = Color.TRANSPARENT;
    public static final double LED_SIZE_X_DIV_PORT = 3.0;
    public static final double LED_SIZE_Y_DIV_PORT = 4.0;
    public static final double LED_SIZE_X_DIV_LAND = 3.5;
    public static final double LED_SIZE_Y_DIV_LAND = 4.0;

    public static class FixedCoords {
        public int ledSizeX, ledSizeY, topPos, bottomPos;
    }

    public static FixedCoords coords = new FixedCoords();

    public HitLightView(FencingBoxActivity mainActivity) {
        super(mainActivity.getBaseContext());
        this.mainActivity = mainActivity;
    }

    public HitLightView(FencingBoxActivity mainActivity, ConstraintLayout layout, HitLight hitLight, int color) {
        super(mainActivity.getBaseContext());
        this.mainActivity = mainActivity;

        if (mainActivity.getOrientation() == Orientation.Portrait) {
            Log.i(TAG, "Orientation: portrait");
        } else {
            Log.i(TAG, "Orientation: landscape");
        }

        this.layout = layout;
        this.hitLight = hitLight;
        layout.addView(this);

        paint = new Paint();
        paint.setStyle(Paint.Style.FILL);

        /* onTargetColor can be either red or green, depending on the fencer */
        onTargetColor = color;
        paint.setColor(offColor);
        paint.setAntiAlias(true);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);

        int width, height, screenWidth, screenHeight;
        final int LEFT_MARGIN_DIV = 10;
        final int TOP_MARGIN_DIV  = 12;

        int widthSize  = MeasureSpec.getSize(widthMeasureSpec);
        int heightSize = MeasureSpec.getSize(heightMeasureSpec);
        int widthMode  = MeasureSpec.getMode(widthMeasureSpec);
        int heightMode = MeasureSpec.getMode(heightMeasureSpec);

        screenWidth    = widthSize;
        screenHeight   = heightSize;

        coords.topPos = screenHeight/TOP_MARGIN_DIV;
        if (mainActivity.getOrientation() == Orientation.Portrait) {
            coords.ledSizeX = (int) (((double) screenWidth)/LED_SIZE_X_DIV_PORT);
            coords.ledSizeY = (int) (((double) screenHeight)/LED_SIZE_Y_DIV_PORT);
        } else {
            coords.ledSizeX = (int) (((double) screenWidth)/LED_SIZE_X_DIV_LAND);
            coords.ledSizeY = (int) (((double) screenHeight)/LED_SIZE_Y_DIV_LAND);
        }
        coords.bottomPos = coords.topPos + coords.ledSizeY;

        int desiredWidth  = coords.ledSizeX;
        int desiredHeight = coords.ledSizeY;

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

        if (hitLight == HitLight.HitB) {
            leftPos = screenWidth - (screenWidth/LEFT_MARGIN_DIV);
            leftPos -= coords.ledSizeX;
        } else {
            leftPos = screenWidth/LEFT_MARGIN_DIV;
        }
        setMeasuredDimension(width, height);
    }

    public void setLayout(ConstraintLayout layout) {
        try {
            this.layout.removeView(this);
            this.layout = layout;
            this.layout.addView(this);
        } catch (Exception e) {
            Log.e(TAG, "Cannot remove view " + this + " error " + e);
        }
        showLights(this.hit);
    }

    public void showLights(Box.Hit hit) {
        this.hit = hit;
        requestLayout();
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        mainActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                setLeft(leftPos);
                setRight(leftPos + coords.ledSizeX);
                setTop(coords.topPos);
                setBottom(coords.bottomPos);

                int color;

                switch (hit) {
                    case None:
                    default:
                        color = offColor;
                        break;

                    case OnTarget:
                        color = onTargetColor;
                        break;

                    case OffTarget:
                        color = offTargetColor;
                        break;
                }
                paint.setColor(color);
                canvas.drawRect(
                        0.0f,
                        0.0f,
                        (float) getWidth(),
                        (float) getHeight(),
                        paint);
            }
        });
    }
}