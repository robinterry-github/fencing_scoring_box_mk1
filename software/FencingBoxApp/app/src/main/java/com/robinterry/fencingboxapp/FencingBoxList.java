package com.robinterry.fencingboxapp;

import java.util.List;
import java.util.ArrayList;
import android.util.Log;
import androidx.annotation.NonNull;
import com.robinterry.fencingboxapp.MainActivity.*;

public class FencingBoxList {
    private static final String TAG = "FencingBoxList";

    private class Box {
        public Integer piste = 0;
        public Hit hitA = Hit.None, hitB = Hit.None;
        public String scoreA = "00", scoreB = "00";
        public String timeMins = "00", timeSecs = "00", timeHund = "00";
        public String cardA = "---", cardB = "---";
        public boolean priA = false, priB = false;

        @NonNull
        public String toString() {
            return "piste=" + piste
                    + ",hitA=" + hitA
                    + ",hitB=" + hitB
                    + ",timeMins=" + timeMins
                    + ",timeSecs=" + timeSecs
                    + ",timeHund=" + timeHund
                    + ",scoreA=" + scoreA
                    + ",scoreB=" + scoreB
                    + ",cardA=" + cardA
                    + ",cardB=" + cardB
                    + ",priA=" + priA
                    + ",priB=" + priB;
        }
    }

    private final List<Box> boxList = new ArrayList<>();
    private Integer myPiste = 1;

    public void setMyPiste(Integer piste) {
        myPiste = piste;
    }

    public void updateBox(String msg) {
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
           01Sh-02:01T02:25:00Cy--:-r-P-:-
         */
        Box newBox = new Box();
        try {
            newBox.piste = Integer.valueOf(msg.substring(0, 2));
        } catch (NumberFormatException e) {
            return;
        }

        /* Don't process this message if this is us */
        if (newBox.piste == myPiste) {
            return;
        }

        /* Read hits */
        if (msg.charAt(2) != 'S') {
            return;
        } else {
            String hA = msg.substring(3, 4);
            String hB = msg.substring(4, 5);
            switch (hA) {
                case "H":
                    newBox.hitA = Hit.OnTarget;
                    break;
                case "O":
                    newBox.hitA = Hit.OffTarget;
                    break;
                default:
                    newBox.hitA = Hit.None;
                    break;
            }
            switch (hB) {
                case "H":
                    newBox.hitB = Hit.OnTarget;
                    break;
                case "O":
                    newBox.hitB = Hit.OffTarget;
                    break;
                default:
                    newBox.hitB = Hit.None;
                    break;
            }

            /* Read score */
            newBox.scoreA = msg.substring(5, 7);
            newBox.scoreB = msg.substring(8, 10);
        }

        /* Read clock */
        if (msg.charAt(10) != 'T') {
            return;
        } else {
            newBox.timeMins = msg.substring(11, 13);
            newBox.timeSecs = msg.substring(14, 16);
            newBox.timeHund = msg.substring(17, 19);
        }

        /* Read cards */
        if (msg.charAt(19) != 'C') {
            return;
        } else {
            newBox.cardA = msg.substring(20, 23);
            newBox.cardB = msg.substring(24, 27);
        }

        /* Read priority */
        if (msg.charAt(27) != 'P') {
            return;
        } else {
            String pA = msg.substring(28, 29);
            String pB = msg.substring(30, 31);
            newBox.priA = pA.contains("y");
            newBox.priB = pB.contains("y");
        }

        /* Check that the box already exists in the list */
        for (Box b : boxList) {
            if (b.piste.equals(newBox.piste)) {
                int idx = boxList.indexOf(b);
                if (idx >= 0) {
                    Log.d(TAG, "Updating " + newBox);
                    boxList.set(idx, newBox);
                    return;
                }
            }
        }

        /* This is a new box, so add to the list */
        Log.d(TAG, "Adding " + newBox);
        boxList.add(newBox);
    }
}