package com.afollestad.neuron;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * @author Aidan Follestad (afollestad)
 */
public class Terminal extends Base {

    public Terminal(Neuron parent) {
        super(parent.mHandler);
        mNeuron = parent;
        mPort = parent.mPort;
        mConnections = new HashMap<>();
    }

    private Neuron mNeuron;
    private int mPort;
    protected Thread mServerThread;
    protected ServerSocket mServerSocket;
    protected final HashMap<Integer, Axon> mConnections;
    protected NeuronFuture<Axon> mAxonCallback;
    protected NeuronFuture<Terminal> mReadyCallback;
    protected boolean mRunning = true;
    private boolean mIsReady = false;

    private void createServerThread() {
        mServerThread = new Thread(new Runnable() {
            @Override
            public void run() {
                Logger.d(getClass(), "Server thread is running");
                try {
                    mServerSocket = new ServerSocket(mPort);
                } catch (Exception e) {
                    Logger.e(Terminal.this, "Failed to start server: " + e.getLocalizedMessage());
                    invoke(mReadyCallback, Terminal.this, e);
                    return;
                }
                mIsReady = true;
                invoke(mReadyCallback, Terminal.this, null);
                while (!mServerSocket.isClosed() && mRunning && !Thread.currentThread().isInterrupted()) {
                    try {
                        int id;
                        synchronized (mConnections) {
                            id = mConnections.size() + 1;
                        }
                        Logger.v(Terminal.this, "Waiting for another client (id = " + (id + 1) + ")...");
                        final Socket socket = mServerSocket.accept();
                        final InputStream is = socket.getInputStream();
                        final OutputStream os = socket.getOutputStream();
                        onConnection(id, socket, is, os);
                    } catch (IOException e) {
                        if (mServerSocket.isClosed() || !mRunning || Thread.currentThread().isInterrupted())
                            break;
                        Logger.e(Terminal.this, "Failed to accept client: " + e.getLocalizedMessage());
                        invoke(mAxonCallback, null, e);
                    }
                }
                mIsReady = false;
                Logger.d(Terminal.this, "Server thread quit");
                if (!mServerSocket.isClosed()) {
                    try {
                        mServerSocket.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        });
        mServerThread.start();
    }

    private synchronized void onConnection(int id, Socket socket, InputStream is, OutputStream os) {
        synchronized (mConnections) {
            Axon axon = new Axon(mNeuron, id, socket, this, is, os);
            mConnections.put(id, axon);
            Logger.d(Terminal.this, "New client (id = " + id + "): " + socket.getInetAddress());
        }
    }

    public synchronized Terminal ready(NeuronFuture<Terminal> future) {
        mReadyCallback = future;
        if (mServerThread == null)
            createServerThread();
        else if (mServerSocket != null && !mServerSocket.isClosed() && future != null)
            future.on(Terminal.this, null);
        return this;
    }

    public synchronized Terminal axon(NeuronFuture<Axon> future) {
        mAxonCallback = future;
        if (mServerThread == null)
            createServerThread();
        return this;
    }

    public synchronized List<Axon> axons() {
        synchronized (mConnections) {
            List<Axon> results = new ArrayList<>();
            for (Integer key : mConnections.keySet())
                results.add(mConnections.get(key));
            return results;
        }
    }

    public synchronized void resumeAcceptingClients() {
        if (isAcceptingClients())
            throw new IllegalStateException("The Terminal is already accepting clients.");
        createServerThread();
    }

    public synchronized final boolean isReady() {
        return mIsReady;
    }

    public synchronized boolean isAcceptingClients() {
        return mRunning && !mServerThread.isInterrupted() &&
                (mServerSocket == null || mServerSocket.isClosed());
    }

    public synchronized void endAcceptingClients() {
        mRunning = false;
        if (mServerThread != null)
            mServerThread.interrupt();
        if (mServerSocket != null) {
            try {
                mServerSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public synchronized void end() {
        endAcceptingClients();
        synchronized (mConnections) {
            for (Integer key : mConnections.keySet())
                mConnections.get(key).end();
            mConnections.clear();
        }
    }

    @Override
    protected void finalize() throws Throwable {
        end();
        super.finalize();
    }
}