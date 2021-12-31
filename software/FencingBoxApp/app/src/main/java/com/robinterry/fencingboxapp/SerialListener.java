package com.robinterry.fencingboxapp;

/* Original copyright (c) 2019 Kai Morich */

interface SerialListener {
    void onSerialConnect      ();
    void onSerialConnectError (Exception e);
    void onSerialRead         (byte[] data);
    void onSerialIoError      (Exception e);
}
