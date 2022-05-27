package com.robinterry.fencingboxapp;

import java.util.LinkedList;
import java.util.Queue;
import java.util.Iterator;
import java.lang.String;

public abstract class FencingBoxKeys {
    /* Key queue */
    private Queue<Character> keyQ;
    private Iterator it;

    void FencingBoxKeys() {
        /* Keypress queue */
        keyQ = new LinkedList<Character>();
        it = keyQ.iterator();
    }

    void addKey(Character c) {
        keyQ.add(c);
    }

    boolean keyPresent() {
        return it.hasNext();
    }

    Character getKey() {
        Character c = (Character) it.next();
        it.remove();
        return c;
    }

    /* Override this to process the key */
    abstract String processKey(Character c);
}
