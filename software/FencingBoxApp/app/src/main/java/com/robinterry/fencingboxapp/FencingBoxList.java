package com.robinterry.fencingboxapp;

import java.util.List;
import java.util.ArrayList;

import android.content.Context;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;

import com.robinterry.constants.C;

@SuppressWarnings("ALL")
public class FencingBoxList {
    private static final String TAG = FencingBoxList.class.getSimpleName();

    private final List<Box> boxList = new ArrayList<Box>() {
        /* Override the add method to add boxes in piste order */
        public boolean add(Box b) {
            synchronized (this) {
                for (Box c : boxList) {
                    int i = boxList.indexOf(c);
                    if (c.piste > b.piste) {
                        super.add(i, b);
                        return true;
                    }
                }
            }

            /* Add at end */
            super.add(b);
            return true;
        }
    };
    private Integer myPiste = 0;
    private Box thisBox;
    private int currentBoxIndex = 0;
    private FencingBoxActivity mainActivity;

    public FencingBoxList(FencingBoxActivity mainActivity, Box thisBox, Integer piste) {
        this.thisBox    = thisBox;
        this.myPiste    = piste;
        this.mainActivity = mainActivity;
    }

    public void setMyPiste(Integer piste) {
        myPiste = piste;
    }

    public void updateBox(String msg, String host) {
        /* Updates box data to the box list:

           the message format is as follows:
           <piste>S<hitA><hitB><scoreA>:<scoreB>T<mins>:<secs>:<hund>C<cardA>:<cardB>P<priA>:<priB>

           where <piste> is 2 digits, >= 1
           <hitA>, <hitB> are '-', 'h' for hit, 'o' for off-target
           <scoreA>, <scoreB> are 2 digits
           <mins> is 2-digit minutes
           <secs> is 2-digit seconds
           <hund> is 2-digit hundredths
           <cardA>, <cardB> are three-character strings, one each for yellow, red and s/c:
              '-' or 'y', '-' or 'r', '-' or 's'
           <priA>, <priB> are '-' or 'y'

           an example is:
           01Sh-02:01T02:25:00P-:-Cy--:-r-

         */
        Box newBox = new Box();
        try {
            newBox.piste = Integer.valueOf(msg.substring(0, 2));
        } catch (NumberFormatException e) {
            return;
        }

        /* Don't process this message if this is us, and we're connected to the box */
        if (newBox.piste == myPiste && mainActivity.isSerialConnected()) {
            return;
        }
        newBox.host = host;

        try {
            /* Read hits */
            if (msg.charAt(2) != 'S') {
                return;
            } else {
                String hA = msg.substring(3, 4);
                String hB = msg.substring(4, 5);
                switch (hA) {
                    case "h":
                        newBox.hitA = Box.Hit.OnTarget;
                        break;
                    case "o":
                        newBox.hitA = Box.Hit.OffTarget;
                        break;
                    default:
                        newBox.hitA = Box.Hit.None;
                        break;
                }
                switch (hB) {
                    case "h":
                        newBox.hitB = Box.Hit.OnTarget;
                        break;
                    case "o":
                        newBox.hitB = Box.Hit.OffTarget;
                        break;
                    default:
                        newBox.hitB = Box.Hit.None;
                        break;
                }

                /* Read score */
                newBox.scoreA = msg.substring(6, 8);
                newBox.scoreB = msg.substring(9, 11);
            }

            /* Read clock */
            if (msg.charAt(11) != 'T') {
                return;
            } else {
                if (C.QUANTISE_CLOCK) {
                    int mins = Integer.parseInt(msg.substring(12, 14));
                    int secs = Integer.parseInt(msg.substring(15, 17));
                    /* Round the seconds to the next highest multiple of the factor -
                       if this is 60, then increment the minutes, and set seconds to 0 */

                    /* Example (quantisation factor is 5):
                       03:00 -> 03:00
                       02:59 -> 03:00
                       02:58 -> 03:00
                       02:55 -> 02:55
                       02:54 -> 02:55
                       01:59 -> 02:00 */

                    /* The reason for quantising is due to the message loss rate for
                       multicast over Wifi - if we count down every second, the clock
                       count as displayed looks very irregular due to lost messages.
                       If we quantise the clock to more than one second (say 5) then the
                       clock count looks less irregular, which is visually more acceptable */
                    if (secs % C.QUANTISE_FACTOR_SECS != 0) {
                        if (secs > (60-C.QUANTISE_FACTOR_SECS)) {
                            secs = 0;
                            mins++;
                        } else {
                            secs = ((secs + C.QUANTISE_FACTOR_SECS) /
                                    C.QUANTISE_FACTOR_SECS) * C.QUANTISE_FACTOR_SECS;
                        }
                    }
                    newBox.timeMins = String.format("%02d", mins);
                    newBox.timeSecs = String.format("%02d", secs);
                } else {
                    newBox.timeMins = msg.substring(14, 14);
                    newBox.timeSecs = msg.substring(15, 17);
                }
                newBox.timeHund = msg.substring(18, 20);
            }

            /* Read priority */
            if (msg.charAt(20) != 'P') {
                return;
            } else {
                String pA = msg.substring(21, 22);
                String pB = msg.substring(23, 24);
                newBox.priA = pA.contains("y");
                newBox.priB = pB.contains("y");
            }

            /* Read cards */
            if (msg.charAt(24) != 'C') {
                return;
            } else {
                newBox.sCardA = msg.substring(25, 28);
                newBox.sCardB = msg.substring(29, 32);
                if (newBox.sCardA.contains("y")) {
                    newBox.cardA |= Box.yellowCardBit;
                }
                if (newBox.sCardA.contains("r")) {
                    newBox.cardA |= Box.redCardBit;
                }
                if (newBox.sCardA.contains("s")) {
                    newBox.cardA |= Box.shortCircuitBit;
                }
                if (newBox.sCardB.contains("y")) {
                    newBox.cardB |= Box.yellowCardBit;
                }
                if (newBox.sCardB.contains("r")) {
                    newBox.cardB |= Box.redCardBit;
                }
                if (newBox.sCardB.contains("s")) {
                    newBox.cardB |= Box.shortCircuitBit;
                }
            }
        } catch (StringIndexOutOfBoundsException e) {
            return;
        }
        newBox.passivityActive = false;
        newBox.passivityTimer = 0;

        /* Keep a check that messages are being received from this box */
        if (newBox.rxMessages < C.MAX_RXMESSAGES) {
            /* If a message is received and the app was previously
               not receiving messages, then boost the message counter */
            if (!newBox.rxOk) {
                newBox.rxMessages = C.BOOST_RXMESSAGES;
            } else {
                newBox.rxMessages++;
            }
            newBox.rxOk = true;
        }

        /* Check that the box already exists in the list */
        synchronized (this) {
            for (Box b : boxList) {
                if (b.piste.equals(newBox.piste)) {
                    int i = boxList.indexOf(b);
                    if (i >= 0) {
                        if (!b.equals(newBox)) {
                            newBox.changed = true;

                            /* Check for a new hit on the currently-displayed box */
                            if (isNewHit(b, newBox)) {
                                if (i == currentBoxIndex) {
                                    vibrateForHit();
                                }
                            }
                            boxList.set(i, newBox);
                        }
                        return;
                    }
                }
            }
        }

        /* This is a new box, so add to the list */
        boxList.add(newBox);
    }

    public boolean empty() {
        return boxList.size() == 0;
    }

    public Box nextBox() throws IndexOutOfBoundsException {
        if (currentBoxIndex+1 < boxList.size()) {
            ++currentBoxIndex;
        } else {
            currentBoxIndex = 0;
        }
        return boxList.get(currentBoxIndex);
    }

    public Box prevBox() throws IndexOutOfBoundsException {
        if (currentBoxIndex > 0) {
            --currentBoxIndex;
        } else {
            currentBoxIndex = boxList.size()-1;
        }
        return boxList.get(currentBoxIndex);
    }

    public Box currentBox() throws IndexOutOfBoundsException {
        return boxList.get(currentBoxIndex);
    }

    private boolean isNewHit(Box oldBox, Box newBox) {
        return ((oldBox.hitA == Box.Hit.None && newBox.hitA != Box.Hit.None)
                ||
                (oldBox.hitB == Box.Hit.None && newBox.hitB != Box.Hit.None)) ? true:false;
    }

    private void vibrateForHit() {
        Vibrator v = (Vibrator) mainActivity.getSystemService(Context.VIBRATOR_SERVICE);

        if (v.hasVibrator()) {
            /* Don't vibrate if the app is connected to the fencing scoring box */
            if (!mainActivity.isSerialConnected()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    v.vibrate(VibrationEffect.createOneShot(C.VIBRATE_PERIOD, VibrationEffect.DEFAULT_AMPLITUDE));
                } else {
                    v.vibrate(C.VIBRATE_PERIOD);
                }
            }
        }
    }
}