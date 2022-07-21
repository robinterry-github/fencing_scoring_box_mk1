package com.robinterry.fencingboxapp;

import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;

import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;

import com.robinterry.constants.C;

public class FencingBoxLaunch extends AppCompatActivity {
    public static final String TAG = "FencingBoxLaunch";
    private ConstraintLayout layout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_fencing_box_launch);
        layout = (ConstraintLayout) findViewById(R.id.activity_launch);
        layout.setBackgroundColor(Color.BLACK);
        getSupportActionBar().hide();

        if (C.DEBUG) {
            Log.d(TAG, "Launching Fencing Box splash screen");
        }
    }

    @Override protected void onStart() {
        super.onStart();
        /* Hide the bars */
        runOnUiThread(new Runnable() {
            public void run() {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    final WindowInsetsController controller = getWindow().getInsetsController();

                    if (controller != null) {
                        controller.hide(WindowInsets.Type.statusBars());
                    }
                } else {
                    getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                            WindowManager.LayoutParams.FLAG_FULLSCREEN);
                }
            }
        });
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                if (C.DEBUG) {
                    Log.d(TAG, "Launching Fencing Box activity");
                }
                startActivity(new Intent(FencingBoxLaunch.this, FencingBoxActivity.class));
                finish();

            }
        }, C.LAUNCH_SCREEN_DELAY);
    }
}