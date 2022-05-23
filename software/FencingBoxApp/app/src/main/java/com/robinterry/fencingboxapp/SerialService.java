package com.robinterry.fencingboxapp;

/* Original copyright (c) 2019 Kai Morich
   Modifications (c) 2021 Robin Terry (mainly the Log output) */

import android.app.Service;
import android.app.Notification;
import android.content.Intent;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import androidx.annotation.Nullable;

import java.io.IOException;
import java.util.LinkedList;
import java.util.Queue;
import android.util.Log;

/**
 * create notification and queue serial data while activity is not in the foreground
 * use listener chain: SerialSocket -> SerialService -> UI fragment
 */
public class SerialService extends Service implements SerialListener {

    public static final String TAG = FencingBoxActivity.class.getSimpleName();

    class SerialBinder extends Binder {
        SerialService getService() { return SerialService.this; }
    }

    private enum QueueType {Connect, ConnectError, Read, IoError}

    private static class QueueItem {
        QueueType type;
        byte[] data;
        Exception e;

        QueueItem(QueueType type, byte[] data, Exception e) { this.type=type; this.data=data; this.e=e; }
    }

    private final Handler mainLooper;
    private final IBinder binder;
    private final Queue<QueueItem> queue1, queue2;

    private SerialSocket socket;
    private SerialListener listener;
    private boolean connected;

    /**
     * Lifecycle
     */
    public SerialService() {
        mainLooper = new Handler(Looper.getMainLooper());
        binder = new SerialBinder();
        queue1 = new LinkedList<>();
        queue2 = new LinkedList<>();
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "service destroyed");
        disconnect();
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "service bind on " + intent);
        return binder;
    }

    /**
     * Api
     */
    public void connect(SerialSocket socket) throws IOException {
        Log.d(TAG, "service connect to " + socket);
        socket.connect(this);
        this.socket = socket;
        connected = true;
    }

    public void disconnect() {
        Log.d(TAG, "service disconnected");
        connected = false; // ignore data,errors while disconnecting
        if (socket != null) {
            socket.disconnect();
            socket = null;
        }
    }

    public void write(byte[] data) throws IOException {
        if (!connected) {
            throw new IOException("not connected");
        }
        socket.write(data);
    }

    public void attach(SerialListener listener) {
        Log.d(TAG, "service attach to listener " + listener);
        if (Looper.getMainLooper().getThread() != Thread.currentThread()) {
            throw new IllegalArgumentException("not in main thread");
        }
        // use synchronized() to prevent new items in queue2
        // new items will not be added to queue1 because mainLooper.post and attach() run in main thread
        synchronized (this) {
            this.listener = listener;
        }
        for (QueueItem item : queue1) {
            switch (item.type) {
                case Connect:       listener.onSerialConnect      (); break;
                case ConnectError:  listener.onSerialConnectError (item.e); break;
                case Read:          listener.onSerialRead         (item.data); break;
                case IoError:       listener.onSerialIoError      (item.e); break;
            }
        }
        for (QueueItem item : queue2) {
            switch (item.type) {
                case Connect:       listener.onSerialConnect      (); break;
                case ConnectError:  listener.onSerialConnectError (item.e); break;
                case Read:          listener.onSerialRead         (item.data); break;
                case IoError:       listener.onSerialIoError      (item.e); break;
            }
        }
        queue1.clear();
        queue2.clear();
    }

    public void detach() {
        Log.d(TAG, "service detach from listener " + listener);
        if (connected) {
            // items already in event queue (posted before detach() to mainLooper) will end up in queue1
            // items occurring later, will be moved directly to queue2
            // detach() and mainLooper.post run in the main thread, so all items are caught
            listener = null;
        }
    }

    /**
     * SerialListener
     */
    public void onSerialConnect() {
        Log.d(TAG, "onSerialConnect connected " + connected);
        if (connected) {
            synchronized (this) {
                if (listener != null) {
                    mainLooper.post(() -> {
                        if (listener != null) {
                            listener.onSerialConnect();
                        } else {
                            queue1.add(new QueueItem(QueueType.Connect, null, null));
                        }
                    });
                } else {
                    queue2.add(new QueueItem(QueueType.Connect, null, null));
                }
            }
        }
    }

    public void onSerialConnectError(Exception e) {
        Log.d(TAG, "onSerialConnectError connected " + connected);
        if (connected) {
            synchronized (this) {
                if (listener != null) {
                    mainLooper.post(() -> {
                        if (listener != null) {
                            listener.onSerialConnectError(e);
                        } else {
                            queue1.add(new QueueItem(QueueType.ConnectError, null, e));
                            disconnect();
                        }
                    });
                } else {
                    queue2.add(new QueueItem(QueueType.ConnectError, null, e));
                    disconnect();
                }
            }
        }
    }

    public void onSerialRead(byte[] data) {
        if (connected) {
            synchronized (this) {
                if (listener != null) {
                    mainLooper.post(() -> {
                        if (listener != null) {
                            listener.onSerialRead(data);
                        } else {
                            queue1.add(new QueueItem(QueueType.Read, data, null));
                        }
                    });
                } else {
                    queue2.add(new QueueItem(QueueType.Read, data, null));
                }
            }
        }
    }

    public void onSerialIoError(Exception e) {
        Log.d(TAG, "onSerialIoError connected value " + connected);
        if (connected) {
            synchronized (this) {
                if (listener != null) {
                    mainLooper.post(() -> {
                        if (listener != null) {
                            Log.d(TAG, "onSerialIoError listener " + listener);
                            listener.onSerialIoError(e);
                        } else {
                            Log.d(TAG, "onSerialIoError listener null");
                            queue1.add(new QueueItem(QueueType.IoError, null, e));
                            disconnect();
                        }
                    });
                } else {
                    Log.d(TAG, "onSerialIoError listener null (2)");
                    queue2.add(new QueueItem(QueueType.IoError, null, e));
                    disconnect();
                }
            }
        }
    }

}
