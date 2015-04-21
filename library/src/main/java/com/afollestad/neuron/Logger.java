package com.afollestad.neuron;

import android.util.Log;

/**
 * @author Aidan Follestad (afollestad)
 */
class Logger {

    private static String getTag(Object context) {
        if (context instanceof Class)
            return ((Class) context).getSimpleName();
        else return context.getClass().getSimpleName();
    }

    public static void v(Object context, String message) {
        Log.v(getTag(context), message);
    }

    public static void d(Object context, String message) {
        Log.d(getTag(context), message);
    }

    public static void e(Object context, String message) {
        Log.e(getTag(context), message);
    }
}
