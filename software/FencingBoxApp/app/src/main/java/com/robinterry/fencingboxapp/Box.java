package com.robinterry.fencingboxapp;

import androidx.annotation.NonNull;

@SuppressWarnings("ALL")
public class Box {
    public enum Weapon {Foil, Epee, Sabre}
    public static enum Hit {None, OnTarget, OffTarget}
    public enum Mode {None, Display, Sparring, Bout, Stopwatch, Demo}
    public Mode mode = Mode.None;
    private Mode oldMode = Mode.None;
    public Integer piste = 0;
    public Hit hitA = Hit.None;
    public Hit hitB = Hit.None;
    public String host = null;
    public String scoreA = "00";
    public String scoreB = "00";
    public String timeMins = "00";
    public String timeSecs = "00";
    public String timeHund = "00";
    public int clock = 0;
    public String sCardA = "---";
    public String sCardB = "---";
    public Integer cardA = 0;
    public Integer cardB = 0;
    public boolean priA = false, priB = false;
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
        this.piste = 0;
    }

    public Box(int piste) {
        this.piste = piste;
    }

    public FencingBoxDisplay disp;
    public boolean changed = false;
    public int rxMessages = 5;
    public boolean rxOk = false;

    @NonNull
    public String toString() {
        return    "piste=" + piste
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
