package com.robinterry.fencingboxapp;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.RadioButton;
import android.widget.RadioGroup;

public class WeaponSelect extends Activity {

    private static final String TAG = "WeaponSelect";
    public static final int ACTIVITY_CODE = 1;
    private Button weaponSelectButton;
    private RadioGroup weaponGroup;
    private RadioButton weaponSelectFoil, weaponSelectEpee, weaponSelectSabre;
    private static MainActivity.Weapon weapon = MainActivity.Weapon.Foil;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_weapon_select);
        weaponSelectButton = findViewById(R.id.weapon_select_button);
        weaponGroup = findViewById(R.id.weapon_select_group);

        /* Check the previously selected weapon */
        weaponSelectFoil  = findViewById(R.id.weapon_select_foil);
        weaponSelectEpee  = findViewById(R.id.weapon_select_epee);
        weaponSelectSabre = findViewById(R.id.weapon_select_sabre);

        switch (weapon) {
            case Foil:
                weaponSelectFoil.setChecked(true);
                weaponSelectEpee.setChecked(false);
                weaponSelectSabre.setChecked(false);
                break;
            case Epee:
                weaponSelectFoil.setChecked(false);
                weaponSelectEpee.setChecked(true);
                weaponSelectSabre.setChecked(false);
                break;
            case Sabre:
                weaponSelectFoil.setChecked(false);
                weaponSelectEpee.setChecked(false);
                weaponSelectSabre.setChecked(true);
                break;
        }

        weaponGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged (RadioGroup group, int checkedId) {
                if (checkedId == R.id.weapon_select_foil) {
                    /* This is foil */
                    weapon = MainActivity.Weapon.Foil;
                } else if (checkedId == R.id.weapon_select_epee) {
                    /* This is epee */
                    weapon = MainActivity.Weapon.Epee;
                } else if (checkedId == R.id.weapon_select_sabre) {
                    /* This is sabre */
                    weapon = MainActivity.Weapon.Sabre;
                }
            }
        });

        weaponSelectButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                /* Send the weapon value to MainActivity */
                Intent weaponResult = new Intent();
                switch (weapon) {
                    case Foil:
                        Log.d(TAG, "FOIL selected");
                        weapon = MainActivity.Weapon.Foil;
                        weaponResult.putExtra("weapon", "FOIL");
                        setResult(Activity.RESULT_OK, weaponResult);
                        break;

                    case Epee:
                        Log.d(TAG, "EPEE selected");
                        weapon = MainActivity.Weapon.Epee;
                        weaponResult.putExtra("weapon", "EPEE");
                        setResult(Activity.RESULT_OK, weaponResult);
                        break;

                    case Sabre:
                        Log.d(TAG, "SABRE selected");
                        weapon = MainActivity.Weapon.Sabre;
                        weaponResult.putExtra("weapon", "SABRE");
                        setResult(Activity.RESULT_OK, weaponResult);
                        break;
                }
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

    static void setWeapon(MainActivity.Weapon w) {
        weapon = w;
    }
}