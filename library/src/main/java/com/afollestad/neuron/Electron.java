package com.afollestad.neuron;

import org.json.JSONObject;

/**
 * @author Aidan Follestad (afollestad)
 */
public abstract class Electron {

    public Electron() {
    }

    protected int ID = -1;

    public abstract void loadJson(JSONObject json);

    public abstract JSONObject toJson();
}