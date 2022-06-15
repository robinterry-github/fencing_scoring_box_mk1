package com.robinterry.fencingboxapp;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.RadioButton;
import android.widget.RadioGroup;

@SuppressWarnings("ALL")
public class WeaponSelect extends Activity {

    private static final String TAG = WeaponSelect.class.getSimpleName();
    public static final int ACTIVITY_CODE = 2;
    private enum SelectDirection { None, Down, Up };
    private Button weaponSelectButton;
    private RadioGroup weaponGroup;
    private RadioButton weaponSelectFoil, weaponSelectEpee, weaponSelectSabre;
    private FencingBoxKeys keyHandler;
    private Box.Weapon weapon = Box.Weapon.Foil;
    private Box.Weapon oldWp = Box.Weapon.Foil;
    private static boolean buttonInvisible = false;
    SelectDirection dir = SelectDirection.None;

    public WeaponSelect() {
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
        setContentView(R.layout.activity_weapon_select);
        weaponSelectButton = findViewById(R.id.weapon_select_button);
        weaponSelectButton.setVisibility(buttonInvisible ? View.INVISIBLE:View.VISIBLE);
        weaponGroup = findViewById(R.id.weapon_select_group);

        Intent intent = getIntent();
        String weaponName = intent.getStringExtra("weapon");
        switch (weaponName) {
            case "Foil":
            default:
                weapon = Box.Weapon.Foil;
                break;

            case "Epee":
                weapon = Box.Weapon.Epee;
                break;

            case "Sabre":
                weapon = Box.Weapon.Sabre;
                break;
        }

        /* Check the previously selected weapon */
        weaponSelectFoil = findViewById(R.id.weapon_select_foil);
        weaponSelectEpee = findViewById(R.id.weapon_select_epee);
        weaponSelectSabre = findViewById(R.id.weapon_select_sabre);

        selectWeapon();

        weaponGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            private boolean isChecked(RadioGroup group, int viewId) {
                if (viewId != -1) {
                    View v = group.findViewById(viewId);
                    if (v instanceof RadioButton) {
                        return ((RadioButton) v).isChecked();
                    }
                }
                return true;
            }

            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                if (!isChecked(group, checkedId)) {
                    return;
                } else if (checkedId == R.id.weapon_select_foil) {
                    /* This is foil */
                    oldWp = weapon;
                    weapon = Box.Weapon.Foil;
                } else if (checkedId == R.id.weapon_select_epee) {
                    /* This is epee */
                    oldWp = weapon;
                    weapon = Box.Weapon.Epee;
                } else if (checkedId == R.id.weapon_select_sabre) {
                    /* This is sabre */
                    oldWp = weapon;
                    weapon = Box.Weapon.Sabre;
                }
            }
        });

        weaponSelectButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                /* Send the weapon value as an intent to FencingBoxActivity */
                sendIntent();
                closeActivity();
            }
        });
    }

    void sendIntent() {
        Intent weaponResult = new Intent();
        switch (weapon) {
            case Foil:
                Log.i(TAG, "FOIL selected");
                weapon = Box.Weapon.Foil;
                weaponResult.putExtra("weapon", "FOIL");
                setResult(Activity.RESULT_OK, weaponResult);
                break;

            case Epee:
                Log.i(TAG, "EPEE selected");
                weapon = Box.Weapon.Epee;
                weaponResult.putExtra("weapon", "EPEE");
                setResult(Activity.RESULT_OK, weaponResult);
                break;

            case Sabre:
                Log.i(TAG, "SABRE selected");
                weapon = Box.Weapon.Sabre;
                weaponResult.putExtra("weapon", "SABRE");
                setResult(Activity.RESULT_OK, weaponResult);
                break;
        }
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
        switch (keyCode) {
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
                /* This overcomes an odd bug in the RadioGroup
                   widget where it spuriously selects another
                   radio button when the keyboard/handset is
                   used, and the ENTER button is pressed to select */
                weapon = oldWp;
                sendIntent();
                closeActivity();
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                nextWeapon(SelectDirection.Down);
                return true;
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_DPAD_LEFT:
                nextWeapon(SelectDirection.Up);
                return true;
            default:
                break;
        }
        return super.onKeyUp(keyCode, event);
    }

    private void getWeapon() {
        switch (weaponGroup.getCheckedRadioButtonId()) {
            case R.id.weapon_select_foil:
                weapon = Box.Weapon.Foil;
                break;

            case R.id.weapon_select_epee:
                weapon = Box.Weapon.Epee;
                break;

            case R.id.weapon_select_sabre:
                weapon = Box.Weapon.Sabre;
                break;

            default:
                break;
        }
    }

    static void setButtonInvisible(boolean invis) {
        buttonInvisible = invis;
    }

    private void selectWeapon() {
        switch (weapon) {
            case Foil:
                weaponGroup.check(R.id.weapon_select_foil);
                weaponSelectFoil.setChecked(true);
                weaponSelectEpee.setChecked(false);
                weaponSelectSabre.setChecked(false);
                break;
            case Epee:
                weaponGroup.check(R.id.weapon_select_epee);
                weaponSelectFoil.setChecked(false);
                weaponSelectEpee.setChecked(true);
                weaponSelectSabre.setChecked(false);
                break;
            case Sabre:
                weaponGroup.check(R.id.weapon_select_sabre);
                weaponSelectFoil.setChecked(false);
                weaponSelectEpee.setChecked(false);
                weaponSelectSabre.setChecked(true);
                break;
        }
        weaponGroup.cancelPendingInputEvents();
        weaponSelectButton.cancelPendingInputEvents();
    }

    private void nextWeapon(SelectDirection dir) {
        switch (dir) {
            case Down:
                switch (weapon) {
                    case Foil:
                        weapon = Box.Weapon.Epee;
                        break;

                    case Epee:
                        weapon = Box.Weapon.Sabre;
                        break;

                    case Sabre:
                        weapon = Box.Weapon.Foil;
                        break;
                }
                break;

            case Up:
                switch (weapon) {
                    case Foil:
                        weapon = Box.Weapon.Sabre;
                        break;

                    case Epee:
                        weapon = Box.Weapon.Foil;
                        break;

                    case Sabre:
                        weapon = Box.Weapon.Epee;
                        break;
                }
                break;
        }
        selectWeapon();
    }

    private void closeActivity() {
        weaponGroup.cancelPendingInputEvents();
        weaponSelectButton.cancelPendingInputEvents();
        finish();
    }
}