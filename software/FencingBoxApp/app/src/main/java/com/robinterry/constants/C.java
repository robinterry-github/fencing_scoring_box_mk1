package com.robinterry.constants;

/* Class of configuration constants */

public final class C {
    /* Debug enable flag */
    public static final boolean DEBUG = true;
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
    public static final int MAX_PISTE = 30;
    /* The fling distance as a proportion of X or Y axis */
    public static final int FLING_RATIO = 5;
    /* Enables quantisation of the clock in network messages */
    public static final boolean QUANTISE_CLOCK = true;
    /* Clock quantisation factor in seconds */
    public static final int QUANTISE_FACTOR_SECS = 5;
    /* Network receive timeout in milliseconds */
    public static final int RX_TIMEOUT = 2000;
    /* IP multicast message transmit interval in milliseconds */
    public static final int TX_INTERVAL = 250;
    /* Multicast IP address */
    public static final String IPMCADDR = "224.0.0.1";
    /* Multicast IP port */
    public static final int IPMCPORT = 28888;
    /* Limit on count of received messages before a disconnection is reported */
    public static final int MAX_RXMESSAGES = 20;
    /* Vibrate period in milliseconds when a hit is detected */
    public static final int VIBRATE_PERIOD = 500;
    /* Maximum value of the network message index */
    public static final int MAX_MSGINDEX = 9999;
    /* Send Bluetooth keys to fencing scoring box when connected, otherwise process locally */
    public static final boolean SEND_KEYS_TO_BOX = false;
    /* Documentation display (should only be enabled for screenshots) */
    public static final boolean DOC_DISPLAY = false;
    /* Box monitor thread interval in milliseconds */
    public static final int BOX_MONITOR_INTERVAL = 250;
    /* System monitor thread interval in milliseconds */
    public static final int SYSTEM_MONITOR_INTERVAL = 500;
    /* USB reconnect delay in milliseconds */
    public static final int USB_RECONNECT_DELAY = 1000;
}

