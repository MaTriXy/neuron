package com.afollestad.neuron;

/**
 * @author Aidan Follestad (afollestad)
 */
public abstract class NeuronFuture2<P, T> extends NeuronFutureBase {

    protected abstract void on(P parent, T result, Exception e);
}
