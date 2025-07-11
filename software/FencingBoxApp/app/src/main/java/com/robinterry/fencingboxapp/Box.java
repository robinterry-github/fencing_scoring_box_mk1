package com.robinterry.fencingboxapp;

import androidx.annotation.NonNull;

import com.robinterry.constants.C;

@SuppressWarnings("ALL")
public class Box {
    public static int counter = 1;
    public int msgIndex = 0;
    public enum Weapon {Foil, Epee, Sabre}
    public static enum Hit {None, OnTarget, OffTarget}
    public enum Mode {None, Display, Sparring, Bout, Stopwatch, WeaponTest, Demo}
    public Mode mode = Mode.None;
    private Mode oldMode = Mode.None;
    public Integer piste = 1;
    public Integer changePiste = piste;
    public Hit hitA = Hit.None;
    public Hit hitB = Hit.None;
    public String host = null;
    public String scoreA = "00";
    public String scoreB = "00";
    public String timeMins = "03";
    public String timeSecs = "00";
    public String timeHund = "00";
    public int clock = 0;
    public int period = 1;
    public String sCardA = "---";
    public String sCardB = "---";
    public Integer cardA = 0;
    public Integer cardB = 0;
    public boolean priA = false, priB = false;
    public boolean priIndicator = false;
    public int passivityTimer = 0;
    public boolean passivityActive = false;
    public Weapon weapon = Weapon.Foil;
    public Weapon changeWeapon = weapon;
    public static final Integer yellowCardBit = 0x01;
    public static final Integer redCardBit = 0x02;
    public static final Integer shortCircuitBit = 0x04;
    public FencingBoxActivity.PassivityCard[] pCard =
            new FencingBoxActivity.PassivityCard[] {FencingBoxActivity.PassivityCard.None, FencingBoxActivity.PassivityCard.None};

    public Box() {
        this.piste = 1;
    }

    public Box(int piste) {
        this.counter++;
        this.piste = piste;
    }

    public FencingBoxDisplay disp;
    public boolean changed = false;
    public int rxMessages = C.MAX_RXMESSAGES;
    public boolean rxOk = true;

    @NonNull
    public String toString() {
        return  "count " + counter
                + ",piste=" + piste
                + ",hitA=" + hitA
                + ",hitB=" + hitB
                + ",timeMins=" + timeMins
                + ",timeSecs=" + timeSecs
                + ",timeHund=" + timeHund
                + ",scoreA=" + scoreA
                + ",scoreB=" + scoreB
                + ",sCardA=" + sCardA
                + ",scardB=" + sCardB
                + ",priA=" + priA
                + ",priB=" + priB;
    }

    public boolean isModeNone() { return mode == Mode.None; }

    public boolean isModeDisplay() {
        return mode == Mode.Display;
    }

    public boolean isModeConnected() { return mode != Mode.None && mode != Mode.Display; }

    public boolean isModeBout() {
        return mode == Mode.Bout;
    }

    public boolean isModeSparring() {
        return mode == Mode.Sparring;
    }

    public boolean isModeStopwatch() {
        return mode == Mode.Stopwatch;
    }

    public boolean isModeDemo() {
        return mode == Mode.Demo;
    }

    public boolean isModeWeaponTest() { return mode == Mode.WeaponTest; }

    public void setMode(Mode mode) {
        this.mode = mode;
    }

    public void setModeNone() { this.mode = Mode.None; }

    public void setModeDisplay() { this.mode = Mode.Display; }

    public void setModeBout() {
        this.mode = Mode.Bout;
    }

    public void setModeSparring() {
        this.mode = Mode.Sparring;
    }

    public void setModeStopwatch() {
        this.mode = Mode.Stopwatch;
    }

    public void setModeWeaponTest() { this.mode = Mode.WeaponTest; }

    public void setModeDemo() {
        this.mode = Mode.Demo;
    }

    public Mode getBoxMode() {
        return mode;
    }

    public void saveMode() {
        oldMode = mode;
    }

    public void restoreMode() {
        mode = oldMode;
    }

    public boolean compareTime(Box otherBox) {
        return  timeMins != otherBox.timeMins
                ||
                timeSecs != otherBox.timeSecs;
    }

    public boolean compareScore(Box otherBox) {
        return  scoreA != otherBox.scoreA
                ||
                scoreB != otherBox.scoreB;
    }
}
