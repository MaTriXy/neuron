package com.afollestad.neuron;

/**
 * @author Aidan Follestad (afollestad)
 */
public abstract class NeuronFuture<T> extends NeuronFutureBase {

    protected abstract void on(T result, Exception e);
}
