package com.robinterry.fencingboxapp;

import android.app.Activity;
import android.content.Intent;
import android.view.KeyEvent;
import android.view.View;
import android.widget.NumberPicker;
import android.widget.Button;
import android.os.Bundle;
import android.util.Log;

/* Import constant values */
import com.robinterry.constants.C;

@SuppressWarnings("ALL")
public class PisteSelect extends Activity {

    private static final String TAG = PisteSelect.class.getSimpleName();
    public static final int ACTIVITY_CODE = 0;
    private NumberPicker pisteSelectPicker;
    private Button pisteSelectButton;
    private FencingBoxKeys keyHandler;
    private static Integer piste = 1;


    public PisteSelect() {
        keyHandler = new FencingBoxKeys() {
            @Override
            public String processKey(Character c) {
                return "";
            }
        };
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_piste_select);
        pisteSelectPicker = findViewById(R.id.piste_select);
        pisteSelectButton = findViewById(R.id.piste_select_button);

        pisteSelectPicker.setMinValue(1);
        pisteSelectPicker.setMaxValue(C.MAX_PISTE);
        pisteSelectPicker.setValue(piste);
        pisteSelectPicker.setOnValueChangedListener(new NumberPicker.OnValueChangeListener() {
            @Override
            public void onValueChange(NumberPicker numberPicker, int oldValue, int newValue) {
                piste = pisteSelectPicker.getValue();
                Log.d(TAG,"Selected piste " + piste);
            }
        });

        pisteSelectButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                /* Send the piste value to FencingBoxApp */
                Log.d(TAG,"Final value of piste " + piste);
                Intent pisteResult = new Intent();
                pisteResult.putExtra("piste", piste);
                setResult(Activity.RESULT_OK, pisteResult);
                finish();
            }
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
    }

    @Override
    protected void onStop() {
        super.onStop();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        Log.i(TAG, "key code " + keyCode);
        return super.onKeyUp(keyCode, event);
    }
}