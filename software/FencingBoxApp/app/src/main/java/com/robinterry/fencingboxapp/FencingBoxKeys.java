package com.robinterry.fencingboxapp;

import java.util.LinkedList;
import java.util.Queue;
import java.util.Iterator;
import java.lang.String;
import android.util.Log;

public abstract class FencingBoxKeys {
    /* Key queue */
    private Queue<Character> keyQ;
    public FencingBoxKeys() {
        /* Key press queue */
        keyQ = new LinkedList<Character>();
    }

    void addKey(Character c) {
        synchronized (this) {
            keyQ.add(c);
        }
    }

    boolean keyPresent() {
        synchronized (this) {
            Iterator<Character> it = keyQ.iterator();
            return it.hasNext();
        }
    }

    Character getKey() {
        synchronized (this) {
            Iterator<Character> it = keyQ.iterator();
            Character c = (Character) it.next();
            it.remove();
            return c;
        }
    }

    /* Override this to process the key */
    abstract String processKey(Character c);
}
