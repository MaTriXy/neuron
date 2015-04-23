package com.afollestad.neuron;

import org.json.JSONException;
import org.json.JSONObject;

import java.lang.reflect.Method;

/**
 * @author Aidan Follestad (afollestad)
 */
class ElectronParser {

    private ElectronParser() {
    }

    public static class Result {
        public Result(JSONObject json, String cls, int id) {
            JSON = json;
            CLASS = cls;
            ID = id;
        }

        public final JSONObject JSON;
        public final String CLASS;
        public final int ID;
    }

    public static Result parse(StringBuilder builder, Axon.CommunicationThread thread) {
//        StringBuilder mBuilder = new StringBuilder("\0[Header/length=5]\0HelloTest");
        final int headerStart = builder.indexOf("\0[Header/");
        if (headerStart == -1) {
            Logger.d(ElectronParser.class, "No header found in this message, waiting for more.");
            return null;
        }

        final int paramsStart = headerStart + 9;
        final int paramsEnd = builder.indexOf("]\0", paramsStart);
        final String params = builder.substring(paramsStart, paramsEnd);
        final String[] splitParams = params.split(",");

        final int headerLiteralLength = builder.substring(headerStart, paramsEnd + 2).length();
        int lengthHeader = -1;
        String classHeader = null;
        int id = -1;

        for (String p : splitParams) {
            String[] sp = p.split("=");
            switch (sp[0]) {
                case "length":
                    lengthHeader = Integer.parseInt(sp[1]);
                    break;
                case "class":
                    classHeader = sp[1];
                    break;
                case "id":
                    id = Integer.parseInt(sp[1]);
                    break;
                default:
                    break;
            }
        }

        if (lengthHeader == -1) {
            Logger.e(ElectronParser.class, "No length header found.");
            return null;
        } else if (classHeader == null) {
            Logger.e(ElectronParser.class, "No class header found.");
            return null;
        }

        final int contentStart = paramsEnd + 2;
        final int contentEnd = contentStart + lengthHeader;
        if (contentEnd > builder.length()) {
            Logger.e(ElectronParser.class, "Content size doesn't match length header, waiting for more.");
            thread.mNextExpectedLength = headerLiteralLength + lengthHeader;
            Logger.v(ElectronParser.class, "Header text length: " + headerLiteralLength +
                    ", length header: " + lengthHeader + ", expected length: " + thread.mNextExpectedLength);
            if (thread.mNextExpectedLength > Axon.CommunicationThread.MAX_EXPECTED_LENGTH) {
                Logger.v(ElectronParser.class, "Expected length is too large, reducing to max.");
                thread.mNextExpectedLength = Axon.CommunicationThread.MAX_EXPECTED_LENGTH;
            }
            return null;
        }

        // Reset expected length to optimize memory usage again
        thread.mNextExpectedLength = Axon.CommunicationThread.DEFAULT_EXPECTED_LENGTH;

        final String content = builder.substring(contentStart, contentEnd);
        builder.delete(headerStart, contentEnd);

        JSONObject json = null;
        try {
            json = new JSONObject(content);
        } catch (JSONException e) {
            Logger.e(ElectronParser.class, "Unable to parse the message content, invalid JSON.");
        }

        Logger.v(ElectronParser.class, "Received " + classHeader + " object.");
        thread.trimBuffer();
        return new Result(json, classHeader, id);
    }

    public static String generateMessage(Electron electron, int id) {
        final String json = electron.toJson().toString();
        String data = "\0[Header/length=" + json.length() + ",class=" + electron.getClass().getSimpleName();
        if (id != -1)
            data += ",id=" + id;
        data += "]\0" + json;
        return data;
    }

    public static Object loadElectron(ElectronParser.Result result, Class<?> type) {
        try {
            Method load = type.getDeclaredMethod("loadJson", JSONObject.class);
            Object obj = type.newInstance();
            load.invoke(obj, result.JSON);
            ((Electron) obj).ID = result.ID;
            return obj;
        } catch (Exception e) {
            throw new RuntimeException("No default constructor found for " + type.getName());
        }
    }
}