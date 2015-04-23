package com.afollestad.neuronsamplereceiver;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.widget.Toast;

import com.afollestad.neuron.Axon;
import com.afollestad.neuron.Neuron;
import com.afollestad.neuron.NeuronFuture;
import com.afollestad.neuron.NeuronFuture2;
import com.afollestad.neuron.Terminal;

/**
 * @author Aidan Follestad (afollestad)
 */
public class EchoService extends Service {

    private static final String READY_ACTION = "com.afollestad.neuronsample.READY";
    private static final int PORT = 45421;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private final NeuronFuture<Terminal> mReadyCallback = new NeuronFuture<Terminal>() {
        @Override
        protected void on(Terminal result, Exception e) {
            Toast.makeText(EchoService.this, "Terminal running on port " + PORT, Toast.LENGTH_SHORT).show();
            sendBroadcast(new Intent(READY_ACTION));
        }
    };

    private final NeuronFuture<Axon> mAxonCallback = new NeuronFuture<Axon>() {
        @Override
        protected void on(Axon result, Exception e) {
            if (e != null)
                Toast.makeText(EchoService.this, "Server failed to accept client: " + e.getLocalizedMessage(), Toast.LENGTH_SHORT).show();
            else
                result.receival(Message.class, mReceiveCallback);
        }
    };

    private final NeuronFuture2<Axon, Message> mReceiveCallback = new NeuronFuture2<Axon, Message>() {
        @Override
        protected void on(Axon parent, Message result, Exception e) {
            if (e != null)
                Toast.makeText(EchoService.this, "Server failed to receive: " + e.getLocalizedMessage(), Toast.LENGTH_SHORT).show();
            else {
                try {
                    result.setContent(result.getContent().toUpperCase());
                    parent.reply(result, result);
                } catch (Exception e1) {
                    Toast.makeText(EchoService.this, "Server failed to receive: " + e1.getLocalizedMessage(), Toast.LENGTH_SHORT).show();
                }
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        Neuron.endAll();
        Neuron.with(PORT)
                .terminal()
                .ready(mReadyCallback)
                .axon(mAxonCallback);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Neuron.endAll();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (Neuron.with(PORT).terminal().isRunning()) {
            sendBroadcast(new Intent(READY_ACTION));
            Toast.makeText(getApplicationContext(), "Terminal is already ready", Toast.LENGTH_SHORT).show();
        }
        else
            Toast.makeText(getApplicationContext(), "Terminal is not yet ready", Toast.LENGTH_SHORT).show();
        return START_STICKY;
    }
}