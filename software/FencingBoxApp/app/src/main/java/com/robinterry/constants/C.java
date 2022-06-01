package com.robinterry.constants;

/* Class of configuration constants */

public class C {
    /* If true, then when the serial is lost, the app switches to display mode */
    public static final boolean DISPLAY_AFTER_CONNECT_ERROR = false;
    /* USB port number */
    public static final int USB_PORT_NUMBER = 0;
    /* USB serial baud rate */
    public static final int USB_BAUD_RATE = 115200;
    /* Passivity timer maximum time in seconds */
    public static final int PASSIVITY_MAX_TIME = 60;
    /* Low battery danger level in percent */
    public static final int BATTERY_DANGER_LEVEL = 15;
    /* Maximum number of supported pistes */
    public static final int MAX_PISTE = 50;
    /* The fling distance as a proportion of X or Y axis */
    public static final int FLING_RATIO = 5;
}
