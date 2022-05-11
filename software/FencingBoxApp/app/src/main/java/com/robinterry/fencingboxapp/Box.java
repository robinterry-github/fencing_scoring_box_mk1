package com.robinterry.fencingboxapp;

import androidx.annotation.NonNull;

@SuppressWarnings("ALL")
public class Box {
    public enum Weapon {Foil, Epee, Sabre}
    public static enum Hit {None, OnTarget, OffTarget}
    public enum Mode {None, Sparring, Bout, Stopwatch, Demo}
    public Mode mode = Mode.None;
    public Integer piste = 0;
    public Hit hitA = Hit.None;
    public Hit hitB = Hit.None;
    public String scoreA = "00";
    public String scoreB = "00";
    public String timeMins = "00";
    public String timeSecs = "00";
    public String timeHund = "00";
    public String sCardA = "---";
    public String sCardB = "---";
    public Integer cardA = 0;
    public Integer cardB = 0;
    public boolean priA = false, priB = false;
    public int passivityTimer = 0;
    public boolean passivityActive = false;
    public Weapon weapon = Weapon.Foil;
    public Weapon changeWeapon = weapon;

    public Box() {
        this.piste = 0;
    }

    public Box(int piste) {
        this.piste = piste;
    }

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

    public boolean isModeNone() {
        return mode == Mode.None;
    }

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

    public void setModeNone() {
        this.mode = Mode.None;
    }

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
}
