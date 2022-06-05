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
    /* Enables quantisation of the clock in network messages */
    public static final boolean QUANTISE_CLOCK = true;
    /* Clock quantisation factor in seconds */
    public static final int QUANTISE_FACTOR_SECS = 5;
    /* Network receive timeout in milliseconds */
    public static final int RX_TIMEOUT = 2000;
    /* Multicast IP address */
    public static final String IPMCADDR = "224.0.0.1";
    /* Multicast IP port */
    public static final int IPMCPORT = 28888;
    /* Limit on count of received messages */
    public static final int MAX_RXMESSAGES = 20;
    /* The boost to the received message count when one first comes in */
    public static final int BOOST_RXMESSAGES = (MAX_RXMESSAGES/2);
}

