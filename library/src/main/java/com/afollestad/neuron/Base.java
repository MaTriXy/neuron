package com.afollestad.neuron;

import android.os.Handler;

/**
 * @author Aidan Follestad (afollestad)
 */
class Base {

    protected Handler mHandler;

    protected Base(Handler handler) {
        mHandler = handler;
    }

    protected synchronized void invoke(final NeuronFuture future, final Object argument, final Exception e) {
        if (future != null) {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    // noinspection unchecked
                    future.on(argument, e);
                }
            });
        }
    }

    protected synchronized void invoke(final NeuronFuture2 future, final Object argument, final Object argument2, final Exception e) {
        if (future != null) {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    // noinspection unchecked
                    future.on(argument, argument2, e);
                }
            });
        }
    }
}
