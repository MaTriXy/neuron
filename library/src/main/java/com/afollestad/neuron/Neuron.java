package com.afollestad.neuron;

import android.os.Handler;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Aidan Follestad (afollestad)
 */
public class Neuron {

    protected int mPort;
    protected Handler mHandler;
    private Axon mAxon;
    private Terminal mTerminal;

    private static Map<Integer, Neuron> mSingletons;

    private Neuron(int port) {
        mPort = port;
        mHandler = new Handler();
    }

    public static Neuron with(int port) {
        if (mSingletons == null)
            mSingletons = new HashMap<>();
        if (mSingletons.containsKey(port))
            return mSingletons.get(port);
        Neuron n = new Neuron(port);
        mSingletons.put(port, n);
        return n;
    }

    public synchronized Terminal terminal() {
        if (mTerminal == null)
            mTerminal = new Terminal(this);
        return mTerminal;
    }

    public synchronized Axon axon() {
        if (mAxon == null)
            mAxon = new Axon(this);
        return mAxon;
    }

    public synchronized void end() {
        if (mTerminal != null)
            mTerminal.end();
        if (mAxon != null)
            mAxon.end();
        if (mSingletons != null) {
            mSingletons.remove(mPort);
            if (mSingletons.size() == 0)
                mSingletons = null;
        }
    }

    public static void endAll() {
        if (mSingletons == null)
            return;
        for (Integer key : mSingletons.keySet())
            mSingletons.get(key).end();
    }

    @Override
    protected void finalize() throws Throwable {
        end();
        super.finalize();
    }
}