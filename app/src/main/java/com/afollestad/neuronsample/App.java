package com.afollestad.neuronsample;

import android.app.Application;

import com.afollestad.neuron.Neuron;

/**
 * @author Aidan Follestad (afollestad)
 */
public class App extends Application {

    public final static int PORT = 45421;

    @Override
    public void onTerminate() {
        super.onTerminate();
        Neuron.endAll();
    }
}
