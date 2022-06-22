package com.robinterry.fencingboxapp;

import java.net.DatagramSocket;
import java.net.DatagramPacket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.io.IOException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.NetworkInterface;
import java.net.MulticastSocket;
import java.net.UnknownHostException;
import java.util.Enumeration;
import java.util.NoSuchElementException;
import java.util.concurrent.ArrayBlockingQueue;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.util.Log;

import com.robinterry.constants.C;

@SuppressWarnings("ALL")
public class NetworkBroadcast {
    public static final String TAG = NetworkBroadcast.class.getSimpleName();
    private DatagramSocket txSocket = null;
    private DatagramSocket rxSocket = null;
    private enum SocketConnection { None, Multicast, Broadcast };
    SocketConnection conn = SocketConnection.None;

    private InetAddress bcAddr = null;
    private Inet4Address ip4Addr = null;
    private int port;
    private ArrayBlockingQueue<String> txMsgs;
    private Thread txThread, rxThread;
    private boolean isTx = false;
    private boolean isThreadRunning = false;
    private boolean connected = false;
    private boolean networkOnline = false;
    private FencingBoxActivity mainActivity;

    public NetworkBroadcast(FencingBoxActivity mainActivity) throws IOException {
        this(mainActivity, C.IPMCPORT);
    }

    public NetworkBroadcast(FencingBoxActivity mainActivity, int port) throws IOException {
        this.mainActivity = mainActivity;
        this.port = port;
        txMsgs = new ArrayBlockingQueue<String>(5);
        if (C.DEBUG) {
            Log.d(TAG, "Initial connection");
        }
        tryConnect();
    }

    public void tryConnect() throws IOException {
        if (!networkOnline) {
            Log.i(TAG, "Trying to connect");
            /* Try opening a multicast socket first */
            try {
                openMulticastSocket();
                joinMulticastGroup();
                conn = SocketConnection.Multicast;
                if (C.DEBUG) {
                    Log.d(TAG, "Network connected");
                }
            } catch (SocketException e1) {
                if (!e1.getMessage().contains("EADDRINUSE")) {
                    Log.e(TAG, "Unable to read multicast address, error " + e1);
                    networkOnline = false;
                } else if (C.DEBUG) {
                    Log.d(TAG, "EADDRINUSE returned, error " + e1);
                }
            } catch (UnknownHostException e2) {
                Log.e(TAG, "Unable to find host " + C.IPMCADDR + ", error " + e2);
            }
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        ip4Addr = getIPAddress();
                    } catch (SocketException e1) {
                        /* Ignore */
                    }
                    try {
                        if (C.DEBUG) {
                            Log.d(TAG, "Network online " + networkOnline);
                        }
                        networkOnline = true;
                        rxSocket.setSoTimeout(C.RX_TIMEOUT);
                        joinMulticastGroup();
                    } catch (SocketException e2) {
                        if (!e2.getMessage().contains("EADDRINUSE")) {
                            Log.e(TAG, "Unable to get IP address, error " + e2);
                            networkOnline = false;
                        } else if (C.DEBUG) {
                            Log.d(TAG, "EADDRINUSE returned, error " + e2);
                        }
                    } catch (IOException e3) {
                        Log.e(TAG, "Unable to get IP address, error " + e3);
                        networkOnline = false;
                    }
                }
            }).start();
        }
    }

    private void openMulticastSocket() throws SocketException, UnknownHostException, IOException {
        bcAddr = InetAddress.getByName(C.IPMCADDR);

        if (txSocket == null) {
            if (C.DEBUG) {
                Log.d(TAG, "Opening TX multicast socket " + bcAddr);
            }
            txSocket = new MulticastSocket(C.IPMCPORT);
        }
        if (rxSocket == null) {
            if (C.DEBUG) {
                Log.d(TAG, "Opening RX multicast socket " + bcAddr);
            }
            rxSocket = new MulticastSocket(C.IPMCPORT);
        }
    }

    private void joinMulticastGroup() throws IOException {
        if (txSocket != null) {
            if (C.DEBUG) {
                Log.d(TAG, "Joining TX multicast group " + bcAddr);
            }
            ((MulticastSocket) txSocket).joinGroup(bcAddr);
        }
        if (rxSocket != null) {
            if (C.DEBUG) {
                Log.d(TAG, "Joining RX multicast group " + bcAddr);
            }
            ((MulticastSocket) rxSocket).joinGroup(bcAddr);
        }
    }

    private void closeSocket() {
        if (txSocket != null) {
            try {
                ((MulticastSocket) txSocket).leaveGroup(bcAddr);
            } catch (IOException e) {
                /* Ignore */
            } finally {
                txSocket.close();
            }
        }
        if (rxSocket != null) {
            try {
                ((MulticastSocket) rxSocket).leaveGroup(bcAddr);
            } catch (IOException e) {
                /* Ignore */
            } finally {
                rxSocket.close();
            }
        }
        networkOnline = false;
        conn = SocketConnection.None;
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
                            networkOnline = true;
                            return if4Addr;
                        }
                    }
                }
            }
        }
        networkOnline = false;
        throw new SocketException("No IP address found");
    }

    public void send(String msg) {
        try {
            if (C.DEBUG) {
                Log.d(TAG, "Add TX message " + msg);
            }
            txMsgs.add(msg);
        } catch (Exception e) {
            return;
        }
    }

    public void connected(boolean c) { connected = c; }

    public boolean isNetworkOnline() {
        return networkOnline;
    }

    public void start() {
        if (!isThreadRunning) {
            txThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    if (C.DEBUG) {
                        Log.d(TAG, "TX thread running");
                    }
                    while (true) {
                        /* If there's a message, fetch it */
                        while (txMsgs.peek() != null) {
                            String msg;
                            try {
                                msg = txMsgs.remove();
                            } catch (NoSuchElementException e) {
                                break;
                            }

                            /* Only send the message if we are connected to a box, otherwise junk it */
                            if (FencingBoxActivity.isSerialConnected()) {
                                DatagramPacket p = new DatagramPacket(msg.getBytes(), msg.length(), bcAddr, port);
                                /* Keep sending this message if this is the only one */
                                if (C.DEBUG) {
                                    Log.d(TAG, "Connection state " +
                                            FencingBoxActivity.isSerialConnected() + ", TX socket " + txSocket);
                                }
                                if (!txSocket.isClosed()) {
                                    do {
                                        try {
                                            if (FencingBoxActivity.isSerialConnected()) {
                                                if (C.DEBUG) {
                                                    Log.d(TAG, "TX message " + msg);
                                                }
                                                txSocket.send(p);
                                                networkOnline = true;
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
                                            Log.e(TAG, "Unable to TX message, error " + e);
                                            networkOnline = false;
                                            /* Wait a second before trying again */
                                            try {
                                                Thread.sleep(1000);
                                            } catch (Exception f) {
                                                /* Ignore */
                                            }
                                        }
                                    } while (txMsgs.peek() == null);
                                }
                            }
                        }
                    }
                }
            });

            rxThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    byte[] buf = new byte[100];
                    if (C.DEBUG) {
                        Log.d(TAG, "RX thread running");
                    }
                    while (true) {
                        DatagramPacket p = new DatagramPacket(buf, 100);
                        while (true) {
                            if (networkOnline) {
                                try {
                                    rxSocket.receive(p);
                                    String msg = new String(p.getData(), p.getOffset(), p.getLength());
                                    if (C.DEBUG) {
                                        Log.d(TAG, "RX message " + msg);
                                    }
                                    try {
                                        /* Add this box to the list, if it is not already there */
                                        mainActivity.boxList.updateBox(msg, p.getAddress().getHostAddress());
                                    } catch (IllegalStateException e) {
                                        /* Ignore (queue full) */
                                    }
                                } catch (SocketTimeoutException e) {
                                    /* Ignore - wait for network to be reconnected */
                                    if (C.DEBUG) {
                                        Log.d(TAG, "RX socket timeout, ignored " + e);
                                    }
                                    //networkOnline = false;
                                } catch (NullPointerException e) {
                                    return;
                                } catch (IOException e) {
                                    Log.e(TAG, "Unable to RX message, error " + e);
                                    networkOnline = false;
                                }
                            } else {
                                try {
                                    if (C.DEBUG) {
                                        Log.d(TAG, "Waiting to connect RX");
                                    }
                                    Thread.sleep(500);
                                    tryConnect();
                                } catch (Exception e) {
                                    if (C.DEBUG) {
                                        Log.d(TAG, "RX connect failed, error " + e);
                                    }
                                    /* Ignore */
                                }
                            }
                        }
                    }
                }
            });

            /* Start the TX and RX threads */
            rxThread.start();
            txThread.start();
            isThreadRunning = true;
        }
    }

    public boolean checkNetworkConnection(Context context) {
        final ConnectivityManager connMgr = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connMgr != null) {
            NetworkInfo activeNetwork = connMgr.getActiveNetworkInfo();

            if (activeNetwork != null) {
                if (activeNetwork.getType() == ConnectivityManager.TYPE_WIFI) {
                    if (C.DEBUG) {
                        Log.d(TAG, "Wifi connection valid");
                    }
                    return true;
                } else if (activeNetwork.getType() == ConnectivityManager.TYPE_MOBILE) {
                    /* Drop through, as this is not a valid connection */
                }
            }
        }
        /* Not connected to Wifi (mobile is not counted) */
        networkOnline = false;
        return false;
    }
}
