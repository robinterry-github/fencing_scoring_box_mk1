package com.robinterry.fencingboxapp;

import java.net.DatagramSocket;
import java.net.DatagramPacket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.io.IOException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.NetworkInterface;
import java.net.InterfaceAddress;
import java.util.Enumeration;
import java.util.concurrent.ArrayBlockingQueue;
import android.util.Log;

@SuppressWarnings("ALL")
public class NetworkBroadcast {
    public static final String TAG = "NetworkBroadcast";
    private DatagramSocket txSocket = null;
    private DatagramSocket rxSocket = null;
    private static final int PORT = 28888;
    private InetAddress bcAddr;
    private Inet4Address ip4Addr = null;
    private int port;
    private ArrayBlockingQueue<String> txMsgs;
    private Thread txThread, rxThread;
    private int piste = 0;
    private boolean isTx = false;
    private boolean isThreadRunning = false;
    private boolean connected = false;

    public NetworkBroadcast() throws IOException {
        this(PORT);
    }

    public NetworkBroadcast(int port) throws IOException {
        this.port = port;
        try {
            openBroadcastSocket();
        } catch (SocketException e) {
            Log.d(TAG, "Unable to read broadcast address, error ", e);
        }
        txMsgs = new ArrayBlockingQueue<String>(5);

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    ip4Addr = getIPAddress();
                } catch (SocketException e) {
                    Log.e(TAG, "Unable to get IP address, error " + e);
                }
            }
        }).start();
    }

    private void openBroadcastSocket() throws IOException {
        bcAddr = getBroadcastAddress();
        if (txSocket == null) {
            /* The IP address/port are set when the datagram is constructed */
            txSocket = new DatagramSocket();
            txSocket.setBroadcast(true);
        }
        if (rxSocket == null) {
            /* Bind to wildcard address and fixed port for receive */
            rxSocket = new DatagramSocket(port);
            rxSocket.setBroadcast(true);
        }
    }

    private void closeBroadcastSocket() {
        if (txSocket != null) {
            txSocket.close();
            txSocket = null;
        }
        if (rxSocket != null) {
            rxSocket.close();
            rxSocket = null;
        }
    }

    private static InetAddress getBroadcastAddress() throws SocketException {
        Enumeration<NetworkInterface> ifs = NetworkInterface.getNetworkInterfaces();
        while (ifs.hasMoreElements()) {
            NetworkInterface netIf = ifs.nextElement();
            if (netIf.isLoopback() || !netIf.supportsMulticast()) {
                continue;
            }
            for (InterfaceAddress ifAddr : netIf.getInterfaceAddresses()) {
                InetAddress bcAddr = ifAddr.getBroadcast();
                if (bcAddr != null) {
                    Log.d(TAG, "Broadcast address " + bcAddr);
                    return bcAddr;
                }
            }
        }
        throw new SocketException("No broadcast address found");
    }

    private Inet4Address getIPAddress() throws SocketException {
        Enumeration<NetworkInterface> netIfs = NetworkInterface.getNetworkInterfaces();
        while (netIfs.hasMoreElements()) {
            NetworkInterface netIf = netIfs.nextElement();
            if (netIf.getName().contains("wlan0")) {
                Enumeration<InetAddress> ifAddrs = netIf.getInetAddresses();
                while (ifAddrs.hasMoreElements()) {
                    InetAddress ifAddr = ifAddrs.nextElement();

                    /* Only look at it if it is an IPV4 address */
                    if (!ifAddr.isLoopbackAddress()) {
                        if (ifAddr instanceof Inet4Address) {
                            Inet4Address if4Addr = (Inet4Address) ifAddr;
                            Log.d(TAG, "IP address " + if4Addr.getHostName());
                            return if4Addr;
                        }
                    }
                }
            }
        }
        throw new SocketException("No IP address found");
    }

    public void send(String msg) {
        try {
            txMsgs.add(msg);
        } catch (Exception e) {
            return;
        }
    }

    public void connected(boolean c) { connected = c; }

    public void start() {
        txThread = new Thread(new Runnable() {
            @Override
            public void run() {
                while (true) {
                    /* If there's a message, fetch it */
                    while (txMsgs.peek() != null) {
                        String str = txMsgs.remove();
                        /* Only send the message if we are connected, otherwise junk it */
                        if (connected) {
                            DatagramPacket p = new DatagramPacket(str.getBytes(), str.length(), bcAddr, port);
                            /* Keep sending this message if this is the only one */
                            do {
                                try {
                                    if (connected) {
                                        txSocket.send(p);
                                        if (txMsgs.peek() != null) {
                                            break;
                                        } else {
                                            /* If there are no more messages, wait a bit before resending */
                                            Thread.sleep(250);
                                        }
                                    } else {
                                        break;
                                    }
                                } catch (Exception e) {
                                    Log.e(TAG, "Unable to send broadcast message, error " + e);
                                    return;
                                }
                            } while (txMsgs.peek() == null);
                        }
                    }
                }
            }
        });

        rxThread = new Thread(new Runnable() {
            @Override
            public void run() {
                byte[] buf = new byte[100];
                while (true) {
                    DatagramPacket p = new DatagramPacket(buf, 100);
                    while (true) {
                        try {
                            rxSocket.setBroadcast(true);
                            rxSocket.receive(p);
                            String msg = new String(p.getData(), p.getOffset(), p.getLength());
                            if (!msg.contains(ip4Addr.getHostName())) {
                                try {
                                    /* Add this box to the list, if it is not already there */
                                    MainActivity.boxList.updateBox(msg);
                                } catch (IllegalStateException e) { /* Ignore (queue full) */ }
                            }
                        } catch (SocketTimeoutException e) {
                            return;
                        } catch (IOException e) {
                            Log.d(TAG, "Unable to receive, error " + e);
                        }
                    }
                }
            }
        });

        /* Start the TX and RX threads */
        rxThread.start();
        txThread.start();
    }
}
