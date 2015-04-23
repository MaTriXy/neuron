package com.afollestad.neuronsamplereceiver;

import android.app.Application;

import com.afollestad.neuron.Neuron;

/**
 * @author Aidan Follestad (afollestad)
 */
public class App extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        Neuron.setLoggingEnabled(true);
    }

    @Override
    public void onTerminate() {
        super.onTerminate();
        Neuron.endAll();
    }
}
