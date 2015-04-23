package com.afollestad.neuron;

import android.util.Log;

/**
 * @author Aidan Follestad (afollestad)
 */
class Logger {

    private static boolean mLoggingEnabled = false;

    public static void setEnabled(boolean enabled) {
        mLoggingEnabled = enabled;
    }

    private static String getTag(Object context) {
        if (context instanceof Axon)
            return "Axon:" + ((Axon) context).getId();
        if (context instanceof Class)
            return ((Class) context).getSimpleName();
        else return context.getClass().getSimpleName();
    }

    public static void v(Object context, String message) {
        if (!mLoggingEnabled) return;
        Log.v(getTag(context), message);
    }

    public static void d(Object context, String message) {
        if (!mLoggingEnabled) return;
        Log.d(getTag(context), message);
    }

    public static void e(Object context, String message) {
        if (!mLoggingEnabled) return;
        Log.e(getTag(context), message);
    }
}
