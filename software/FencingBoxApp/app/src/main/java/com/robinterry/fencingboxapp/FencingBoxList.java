package com.robinterry.fencingboxapp;

import java.util.List;
import java.util.ArrayList;
import android.util.Log;

@SuppressWarnings("ALL")
public class FencingBoxList {
    private static final String TAG = FencingBoxList.class.getSimpleName();

    private final List<Box> boxList = new ArrayList<Box>() {
        /* Override the add method to add boxes in piste order */
        public boolean add(Box b) {
            for (Box c: boxList) {
                int idx = boxList.indexOf(c);
                if (c.piste > b.piste) {
                    super.add(idx, b);
                    return true;
                }
            }

            /* Add at end */
            super.add(b);
            return true;
        }
    };
    private Integer myPiste = 1;
    private Box thisBox;
    private int idx = 0;

    public FencingBoxList(Box thisBox, Integer piste) {
        this.thisBox = thisBox;
        this.myPiste = piste;
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

        /* Don't process this message if this is us */
        if (newBox.piste == myPiste) {
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
                newBox.timeMins = msg.substring(12, 14);
                newBox.timeSecs = msg.substring(15, 17);
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

        /* Check that the box already exists in the list */
        for (Box b : boxList) {
            if (b.piste.equals(newBox.piste)) {
                int idx = boxList.indexOf(b);
                if (idx >= 0) {
                    if (!b.equals(newBox)) {
                        newBox.changed = true;
                        boxList.set(idx, newBox);
                    }
                    return;
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
        if (idx+1 < boxList.size()) {
            ++idx;
        } else {
            idx = 0;
        }
        return boxList.get(idx);
    }

    public Box prevBox() throws IndexOutOfBoundsException {
        if (idx > 0) {
            --idx;
        } else {
            idx = boxList.size()-1;
        }
        return boxList.get(idx);
    }

    public Box currentBox() throws IndexOutOfBoundsException {
        return boxList.get(idx);
    }
}