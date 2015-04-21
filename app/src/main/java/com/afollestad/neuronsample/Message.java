package com.afollestad.neuronsample;

import com.afollestad.neuron.Electron;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * @author Aidan Follestad (afollestad)
 */
public class Message extends Electron {

    private String mMessage;

    public Message() {
        // Needed for internal initialization
    }

    public Message(String content) {
        // Used by sample project to create a new Message object with the content filled in
        mMessage = content;
    }

    public String getContent() {
        return mMessage;
    }

    public void setContent(String content) {
        mMessage = content;
    }

    @Override
    public void loadJson(JSONObject json) {
        mMessage = json.optString("content");
    }

    @Override
    public JSONObject toJson() {
        JSONObject json = new JSONObject();
        try {
            json.put("content", mMessage);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return json;
    }
}