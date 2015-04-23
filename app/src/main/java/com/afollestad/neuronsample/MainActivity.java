package com.afollestad.neuronsample;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import com.afollestad.neuron.Axon;
import com.afollestad.neuron.Neuron;
import com.afollestad.neuron.NeuronFuture;
import com.afollestad.neuron.NeuronFuture2;
import com.afollestad.neuron.NeuronFuture3;

public class MainActivity extends AppCompatActivity {

    private static final String READY_ACTION = "com.afollestad.neuronsample.READY";

    private TextView log;
    private EditText mInput;
    private Button mSend;
    private Axon mAxon;

    private void log(String message) {
        log.append(message + "\n");
    }

    private BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            // Service signalled it's ready
            mAxon = Neuron.with(App.PORT)
                    .axon()
                    .connection(new NeuronFuture<Axon>() {
                        @Override
                        public void on(Axon result, Exception e) {
                            if (e != null) {
                                log("CONNECT ERROR: " + e.getLocalizedMessage());
                            } else {
                                log("[CONNECTED].");
                                mInput.setEnabled(true);
                            }
                        }
                    })
                    .receival(Message.class, new NeuronFuture2<Axon, Message>() {
                        @Override
                        public void on(Axon parent, Message result, Exception e) {
                            if (e != null)
                                log("RECEIVE ERROR: " + e.getLocalizedMessage());
                            else
                                log("[RECEIVED]: " + result.getContent());
                        }
                    })
                    .disconnection(new NeuronFuture<Axon>() {
                        @Override
                        public void on(Axon result, Exception e) {
                            log("[DISCONNECTED]: " + result);
                        }
                    });
            unregisterReceiver(this);
        }
    };

    private Intent getServiceIntent() {
        return new Intent()
                .setComponent(new ComponentName(
                        "com.afollestad.neuronsamplereceiver",
                        "com.afollestad.neuronsamplereceiver.EchoService"));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        startService(getServiceIntent());
        registerReceiver(mReceiver, new IntentFilter(READY_ACTION));

        log = (TextView) findViewById(R.id.content);

        mSend = (Button) findViewById(R.id.sendButton);
        mSend.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String msg = mInput.getText().toString().trim();
                log("[SEND]: " + msg);
                mAxon.transmit(new Message(msg), new NeuronFuture3<Axon, Message>() {
                    @Override
                    public void on(Axon parent, Message result, Exception e) {
                        if (e != null) {
                            log("[SEND ERROR]: " + e.getLocalizedMessage());
                        } else {
                            log("[SERVER REPLY]: " + result.getContent());
                        }
                    }
                });
                mInput.setText("");
            }
        });

        mInput = (EditText) findViewById(R.id.input);
        mInput.setImeActionLabel(getString(R.string.transmit), EditorInfo.IME_ACTION_SEND);
        mInput.setEnabled(false);
        mInput.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_SEND) {
                    mSend.performClick();
                    return true;
                }
                return false;
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopService(getServiceIntent());
        try {
            unregisterReceiver(mReceiver);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}