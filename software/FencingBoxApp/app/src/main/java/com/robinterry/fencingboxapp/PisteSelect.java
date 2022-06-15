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
    public static final int ACTIVITY_CODE = 1;
    private NumberPicker pisteSelectPicker;
    private Button pisteSelectButton;
    private FencingBoxKeys keyHandler;
    private Integer piste = 1;

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

        Intent intent = getIntent();
        piste = intent.getIntExtra("piste", 1);
        pisteSelectPicker.setValue(piste);

        pisteSelectPicker.setOnValueChangedListener(new NumberPicker.OnValueChangeListener() {
            @Override
            public void onValueChange(NumberPicker numberPicker, int oldValue, int newValue) {
                piste = pisteSelectPicker.getValue();
            }
        });

        pisteSelectButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                /* Send the piste value to FencingBoxApp */
                sendIntent();
                closeActivity();
            }
        });
    }

    @Override
    protected void onStart() {
        pisteSelectPicker.setFocusable(true);
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
        switch (keyCode) {
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
                piste = pisteSelectPicker.getValue();
                sendIntent();
                closeActivity();
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (++piste > C.MAX_PISTE) {
                    piste = 1;
                }
                pisteSelectPicker.setValue(piste);
                return true;
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_DPAD_LEFT:
                if (--piste < 1) {
                    piste = C.MAX_PISTE;
                }
                pisteSelectPicker.setValue(piste);
                return true;
            default:
                break;
        }
        return super.onKeyUp(keyCode, event);
    }

    void sendIntent() {
        Log.i(TAG, "Final value of piste " + piste);
        Intent pisteResult = new Intent();
        pisteResult.putExtra("piste", piste);
        setResult(Activity.RESULT_OK, pisteResult);
    }

    private void closeActivity() {
        pisteSelectPicker.cancelPendingInputEvents();
        pisteSelectButton.cancelPendingInputEvents();
        finish();
    }
}