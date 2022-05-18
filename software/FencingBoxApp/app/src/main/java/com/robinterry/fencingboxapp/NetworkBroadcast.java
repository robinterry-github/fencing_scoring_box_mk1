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
import java.net.MulticastSocket;
import java.net.UnknownHostException;
import java.util.Enumeration;
import java.util.NoSuchElementException;
import java.util.concurrent.ArrayBlockingQueue;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.util.Log;

@SuppressWarnings("ALL")
public class NetworkBroadcast {
    public static final String TAG = "NetworkBroadcast";
    private DatagramSocket txSocket = null;
    private DatagramSocket rxSocket = null;
    private static final String MCADDR = "224.0.0.1";
    private static final int PORT = 28888;
    private InetAddress bcAddr = null;
    private Inet4Address ip4Addr = null;
    private int port;
    private ArrayBlockingQueue<String> txMsgs;
    private Thread txThread, rxThread;
    private boolean isTx = false;
    private boolean isThreadRunning = false;
    private boolean connected = false;
    private boolean networkOnline = false;

    public NetworkBroadcast() throws IOException {
        this(PORT);
    }

    public NetworkBroadcast(int port) throws IOException {
        this.port = port;
        txMsgs = new ArrayBlockingQueue<String>(5);
        tryConnect();
    }

    public void tryConnect() throws IOException {
        if (!networkOnline) {
            Log.d(TAG, "Trying to reconnect");
            /* Try opening a multicast socket first */
            try {
                openMulticastSocket();
                Log.d(TAG, "Opened multicast socket on " + MCADDR + ", port " + PORT);
            } catch (SocketException e1) {
                Log.d(TAG, "Unable to read multicast address, error ", e1);
                try {
                    openBroadcastSocket();
                    Log.d(TAG, "Opened broadcast socket on " + bcAddr + ", port " + PORT);
                } catch (SocketException e) {
                    Log.d(TAG, "Unable to read broadcast address, error ", e);
                }
            } catch (UnknownHostException e2) {
                Log.d(TAG, "Unable to find host " + MCADDR + ", error " + e2);
                try {
                    openBroadcastSocket();
                    Log.d(TAG, "Opened broadcast socket on " + bcAddr + ", port " + PORT);
                } catch (SocketException e) {
                    Log.d(TAG, "Unable to read broadcast address, error ", e);
                }
            }
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        ip4Addr = getIPAddress();
                        networkOnline = true;
                        Log.d(TAG, "IP address " + ip4Addr.getHostName());
                    } catch (SocketException e) {
                        Log.e(TAG, "Unable to get IP address, error " + e);
                    }
                }
            }).start();
        }
    }

    private void openBroadcastSocket() throws IOException, SocketException {
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

    private void openMulticastSocket() throws SocketException, UnknownHostException, IOException {
        bcAddr = InetAddress.getByName(MCADDR);
        txSocket = new MulticastSocket(PORT);
        ((MulticastSocket) txSocket).joinGroup(bcAddr);
        rxSocket = new MulticastSocket(PORT);
        ((MulticastSocket) rxSocket).joinGroup(bcAddr);
    }

    private void closeSocket() {
        if (txSocket != null) {
            txSocket.close();
            txSocket = null;
        }
        if (rxSocket != null) {
            rxSocket.close();
            rxSocket = null;
        }
        networkOnline = false;
    }

    private InetAddress getBroadcastAddress() throws SocketException {
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
        networkOnline = false;
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
                    while (true) {
                        /* If there's a message, fetch it */
                        while (txMsgs.peek() != null) {
                            String str;
                            try {
                                str = txMsgs.remove();
                            } catch (NoSuchElementException e) {
                                break;
                            }

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
                                        networkOnline = false;
                                        break;
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
                                try {
                                    /* Add this box to the list, if it is not already there */
                                    MainActivity.boxList.updateBox(msg);
                                } catch (IllegalStateException e) { /* Ignore (queue full) */ }
                            } catch (SocketTimeoutException e) {
                                /* Ignore - wait for network to be reconnected */
                            } catch (NullPointerException e) {
                                return;
                            } catch (IOException e) {
                                Log.d(TAG, "Unable to receive, error " + e);
                                networkOnline = false;
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
