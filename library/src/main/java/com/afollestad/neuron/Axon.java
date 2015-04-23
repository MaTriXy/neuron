package com.afollestad.neuron;

import android.os.Handler;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Aidan Follestad (afollestad)
 */
public class Axon extends Base {

    private int mId;
    private int mPort;
    private Terminal mServer;
    private Socket mSocket;
    private InputStream mInput;
    private OutputStream mOutput;
    private ConnectionThread conn;
    private CommunicationThread comm;
    private boolean mRunning = true;
    private NeuronFuture<Axon> mConnectionFuture;
    private final Map<Integer, NeuronFuture2<Axon, ? extends Electron>> mReceiveFutures;
    private int mReceiveFutureCounter;
    private NeuronFuture<Axon> mDisconnectionFuture;

    private Axon(Handler handler, int id) {
        super(handler);
        mId = id;
        mReceiveFutures = new HashMap<>();
    }

    protected Axon(Neuron neuron, int id, Socket socket, Terminal server, InputStream is, OutputStream os) {
        this(neuron.mHandler, id);
        mSocket = socket;
        mServer = server;
        mInput = is;
        mOutput = os;
        startCommunciationThread();
    }

    protected Axon(Neuron neuron) {
        this(neuron.mHandler, -1);
        mPort = neuron.mPort;
    }

    public int getId() {
        return mId;
    }

    private void startConnection() {
        conn = new ConnectionThread(mPort);
        new Thread(conn).start();
    }

    private void startCommunciationThread() {
        comm = new CommunicationThread();
        new Thread(comm).start();
    }

    private synchronized void incrementCounter() {
        synchronized (mReceiveFutures) {
            mReceiveFutureCounter++;
            if (mReceiveFutureCounter == Integer.MAX_VALUE) {
                mReceiveFutureCounter = 0;
            }
        }
    }

    private void write(String data) throws Exception {
        final byte[] bytes = data.getBytes("UTF-8");
        mOutput.write(bytes);
        mOutput.flush();
    }

    public synchronized void transmit(Electron obj) throws Exception {
        if (mOutput == null)
            return;
        final String str = ElectronParser.generateMessage(obj, -1);
        write(str);
    }

    public synchronized void reply(Electron replyTo, Electron response) throws Exception {
        if (mOutput == null)
            return;
        final String str = ElectronParser.generateMessage(response, replyTo.ID);
        write(str);
    }

    public synchronized void transmit(Electron obj, NeuronFuture3<Axon, ? extends Electron> future) {
        synchronized (mReceiveFutures) {
            if (mOutput == null)
                return;
            incrementCounter();
            future.ID = mReceiveFutureCounter;
            future.CLASS = obj.getClass();
            mReceiveFutures.put(mReceiveFutureCounter, future);
            final String str = ElectronParser.generateMessage(obj, future.ID);
            try {
                final byte[] bytes = str.getBytes("UTF-8");
                mOutput.write(bytes);
                mOutput.flush();
            } catch (IOException e) {
                future.on(Axon.this, null, e);
            }
        }
    }

    public synchronized void end() {
        if (comm != null)
            comm.end();
        mRunning = false;
    }

    public synchronized Axon connection(NeuronFuture<Axon> future) {
        if (getId() > 0)
            throw new IllegalStateException("Server Axons do not accept a connection callback.");
        mConnectionFuture = future;
        if (conn == null)
            startConnection();
        return this;
    }

    public synchronized Axon receival(Class<? extends Electron> cls, NeuronFuture2<Axon, ? extends Electron> future) {
        synchronized (mReceiveFutures) {
            future.CLASS = cls;
            incrementCounter();
            mReceiveFutures.put(mReceiveFutureCounter, future);
            if (conn == null && getId() <= 0)
                startConnection();
        }
        return this;
    }

    public synchronized Axon disconnection(NeuronFuture<Axon> future) {
        mDisconnectionFuture = future;
        return this;
    }

    class ConnectionThread implements Runnable {

        protected int mPort;

        public ConnectionThread(int port) {
            mPort = port;
        }

        @Override
        public void run() {
            try {
                mSocket = new Socket("localhost", mPort);
                mInput = mSocket.getInputStream();
                mOutput = mSocket.getOutputStream();
                if (!mRunning) return;
                startCommunciationThread();
                Logger.v(Axon.this, "Axon connected to server on port " + mPort);
            } catch (IOException e) {
                Logger.e(Axon.this, "Failed to start the connection: " + e.getLocalizedMessage());
                invoke(mConnectionFuture, Axon.this, e);
            }
        }
    }

    protected class CommunicationThread implements Runnable {

        protected static final int DEFAULT_EXPECTED_LENGTH = 2048;
        protected static final int MAX_EXPECTED_LENGTH = 5012;

        private final StringBuilder mBuilder;
        protected int mNextExpectedLength = DEFAULT_EXPECTED_LENGTH;

        public CommunicationThread() {
            mBuilder = new StringBuilder();
        }

        public void end() {
            mRunning = false;
            if (!Thread.currentThread().isInterrupted())
                Thread.currentThread().interrupt();
            if (mSocket != null) {
                try {
                    mSocket.shutdownOutput();
                    mSocket.shutdownInput();
                    mSocket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        protected void trimBuffer() {
            int length = mBuilder.length();
            for (int i = mBuilder.length() - 1; i >= 0; i--) {
                char c = mBuilder.charAt(i);
                if (c == '\0' || Character.isWhitespace(c))
                    length--;
                else break;
            }
            if (length < mBuilder.length())
                mBuilder.setLength(length);
        }

        private synchronized void checkReceiveReady() {
            synchronized (mReceiveFutures) {
                if (mBuilder.length() == 0)
                    return;
                else if (mReceiveFutures.size() == 0) {
                    Logger.d(Axon.this, "No receival futures available to send receival callback.");
                    return;
                }
                ElectronParser.Result result = ElectronParser.parse(mBuilder, CommunicationThread.this);
                if (result != null && result.JSON != null && result.CLASS != null) {
                    List<Integer> removeKeys = new ArrayList<>();
                    for (Integer key : mReceiveFutures.keySet()) {
                        NeuronFuture2<Axon, ? extends Electron> future = mReceiveFutures.get(key);
                        if (future.CLASS.getSimpleName().equals(result.CLASS)) {
                            if (future instanceof NeuronFuture3) {
                                if (result.ID == -1) {
                                    // This message is a not a reply, this callback isn't interested
                                    continue;
                                } else if (result.ID != ((NeuronFuture3) future).ID) {
                                    // ID of reply doesn't match up with callback
                                    continue;
                                }
                            } else if (result.ID != -1 && getId() == -1) {
                                // This message is a reply for a specific future
                                continue;
                            }
                            final Object electron = ElectronParser.loadElectron(result, future.CLASS);
                            invoke(future, Axon.this, electron, null);
                            if (future instanceof NeuronFuture3)
                                removeKeys.add(((NeuronFuture3) future).ID);
                        }
                    }
                    if (removeKeys.size() > 0) {
                        for (Integer key : removeKeys)
                            mReceiveFutures.remove(key);
                    }
                }
            }
        }

        @Override
        public void run() {
            if (mInput == null)
                return;
            Logger.v(Axon.this, "Communication thread (mId = " + mId + ") started");
            invoke(mConnectionFuture, Axon.this, null);

            try {
                mSocket.setSoTimeout(10000);
            } catch (SocketException e) {
                e.printStackTrace();
            }

            if (mServer != null)
                invoke(mServer.mAxonCallback, Axon.this, null);

            while (!mSocket.isClosed() && mRunning && !Thread.currentThread().isInterrupted()) {
                try {
                    Logger.v(Axon.this, "Attempting to receive " + mNextExpectedLength + " bytes...");
                    byte[] received = new byte[mNextExpectedLength];
                    int readCount = mInput.read(received);
                    if (readCount > 0) {
                        Logger.v(Axon.this, "Received " + readCount + " bytes.");
                        String receivedStr = new String(received, 0, readCount, "UTF-8");
                        if (receivedStr.length() == 1 && receivedStr.equals("\0")) {
                            Logger.v(Axon.this, "Received connection check character. Ignoring.");
                            continue;
                        }

                        mBuilder.append(receivedStr);
                        trimBuffer();
                        checkReceiveReady();
                    } else if (readCount == -1) {
                        Logger.d(Axon.this, "Received -1 bytes, connection is closed (mId = " + mId + ")");
                        break;
                    }
                } catch (SocketTimeoutException e) {
                    Logger.v(Axon.this, "Haven't received anything for 10 seconds, sending connection check.");
                    try {
                        write("\0");
                    } catch (Exception e1) {
                        e1.printStackTrace();
                        Logger.e(Axon.this, "Failed to send connection check message.");
                    }
                } catch (IOException e) {
                    if (mSocket.isClosed() || !mRunning || Thread.currentThread().isInterrupted())
                        break;
                    Logger.e(Axon.this, "Failed to read from the input stream: " + e.getLocalizedMessage());
                }
            }

            Logger.v(Axon.this, "Communication thread (mId = " + mId + ") quit");
            if (mServer != null) {
                synchronized (mServer.mConnections) {
                    mServer.mConnections.remove(mId);
                }
            }
            invoke(mDisconnectionFuture, Axon.this, null);
            if (!mSocket.isClosed()) {
                try {
                    mSocket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            mOutput = null;
            mInput = null;
            mSocket = null;
        }

    }

    @Override
    protected void finalize() throws Throwable {
        end();
        super.finalize();
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof Axon && ((Axon) obj).mId == mId;
    }

    @Override
    public String toString() {
        return "(Axon) id = " + mId;
    }
}