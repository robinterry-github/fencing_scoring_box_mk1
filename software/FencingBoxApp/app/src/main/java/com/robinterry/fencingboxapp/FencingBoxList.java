package com.robinterry.fencingboxapp;

import java.util.List;
import java.util.ArrayList;

import android.content.Context;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;

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
        this.thisBox      = thisBox;
        this.myPiste      = piste;
        this.mainActivity = mainActivity;
    }

    public void setMyPiste(Integer piste) {
        myPiste = piste;
    }

    public void updateBox(String msg, String host) {
        /* Updates box data to the box list:

           the message format is as follows:
           <index>|<piste>S<hitA><hitB><scoreA>:<scoreB>T<mins>:<secs>:<hund>C<cardA>:<cardB>P<priA>:<priB>

           where:
           <index> is 4 digits, 0-9999 inclusive, and incremented for each new message
           <piste> is 2 digits, >= 1
           <hitA>, <hitB> are '-', 'h' for hit, 'o' for off-target
           <scoreA>, <scoreB> are 2 digits
           <mins> is 2-digit minutes
           <secs> is 2-digit seconds
           <hund> is 2-digit hundredths
           <cardA>, <cardB> are three-character strings, one each for yellow, red and s/c:
              '-' or 'y', '-' or 'r', '-' or 's'
           <priA>, <priB> are '-', '?' or 'y' ('?' means that priority selection is active)

           an example is:
           2345|01Sh-:02:01T02:25:00P-:-Cy--:-r-

        */
        int offset = 0;

        Box newBox = new Box();
        if (C.DEBUG) {
            Log.d(TAG, "Creating (" + myPiste + ") a new box " + newBox);
        }
        try {
            newBox.msgIndex = Integer.valueOf(msg.substring(offset, offset+4));
            offset += 4;
        } catch (NumberFormatException e) {
            return;
        }
        if (msg.charAt(offset) != '|') {
            return;
        }
        offset++;
        try {
            newBox.piste = Integer.valueOf(msg.substring(offset, offset+2));
            offset += 2;
        } catch (NumberFormatException e) {
            return;
        }

        /* Don't process this message if this is us, and we're connected to the box */
        if (newBox.piste == myPiste && FencingBoxActivity.isSerialConnected()) {
            if (C.DEBUG) {
                Log.d(TAG, "Piste " + myPiste + " message received - ignore");
            }
            return;
        }
        newBox.host = host;
        try {
            /* Read hits */
            if (msg.charAt(offset) != 'S') {
                return;
            }
            offset++;
            String hA = msg.substring(offset, offset+1);
            offset++;
            String hB = msg.substring(offset, offset+1);
            offset += 2;
            switch (hA) {
                case "h":
                case "p":
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
                case "p":
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
            newBox.scoreA = msg.substring(offset, offset+2);
            offset += 3;
            newBox.scoreB = msg.substring(offset, offset+2);
            offset += 2;

            /* Read clock */
            if (msg.charAt(offset) != 'T') {
                return;
            }
            offset++;
            if (C.QUANTISE_CLOCK) {
                int mins = Integer.parseInt(msg.substring(offset, offset+2));
                offset += 3;
                int secs = Integer.parseInt(msg.substring(offset, offset+2));
                offset += 3;
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
                /* Non-quantised clock */
                newBox.timeMins = msg.substring(offset, offset+2);
                offset += 3;
                newBox.timeSecs = msg.substring(offset, offset+2);
                offset += 3;
            }
            newBox.timeHund = msg.substring(offset, offset+2);
            offset += 2;

            /* Read priority */
            if (msg.charAt(offset) != 'P') {
                return;
            }
            offset++;
            String pA = msg.substring(offset, offset+1);
            offset += 2;
            String pB = msg.substring(offset, offset+1);
            offset++;
            if (pA.contains("?") && pB.contains("?")) {
                newBox.priIndicator = true;
            } else {
                newBox.priIndicator = false;
                newBox.priA = pA.contains("y");
                newBox.priB = pB.contains("y");
            }

            /* Read cards */
            if (msg.charAt(offset) != 'C') {
                return;
            }
            offset++;
            newBox.sCardA = msg.substring(offset, offset+3);
            offset += 4;
            newBox.sCardB = msg.substring(offset, offset+3);
            offset += 3;
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
                    if (C.DEBUG) {
                        Log.d(TAG, "Found box " + newBox + " at index " + i);
                    }
                    if (i >= 0) {
                        if (!b.equals(newBox) && b.msgIndex != newBox.msgIndex) {
                            newBox.changed = true;

                            /* Check for a new hit on the currently-displayed box */
                            if (isNewHit(b, newBox)) {
                                if (i == currentBoxIndex) {
                                    vibrateForHit(b);
                                }
                            }
                            if (C.DEBUG) {
                                Log.d(TAG, "Storing (" + myPiste + ") new box " + newBox);
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

    public void saveCurrentMode(Box.Mode mode) {
        Box b = boxList.get(currentBoxIndex);
        b.setMode(mode);
        boxList.set(currentBoxIndex, b);
    }

    private boolean isNewHit(Box oldBox, Box newBox) {
        return ((oldBox.hitA == Box.Hit.None && newBox.hitA != Box.Hit.None)
                ||
                (oldBox.hitB == Box.Hit.None && newBox.hitB != Box.Hit.None)) ? true:false;
    }

    private void vibrateForHit(Box b) {
        /* Don't vibrate if there is no display */
        Vibrator v = (Vibrator) mainActivity.getSystemService(Context.VIBRATOR_SERVICE);

        if (mainActivity.isVibrationOn()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                v.vibrate(VibrationEffect.createOneShot(C.VIBRATE_PERIOD, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                v.vibrate(C.VIBRATE_PERIOD);
            }
        }
    }
}