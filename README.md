# Neuron

An simple, efficient, thread-safe, IPC framework for Android.
 
##### Note

If you can, use Intents, service binding, or other forms of communication in the Android framework.
This library is only intended to speed up and simplify passing objects between different apps.

# How It Works

This library does IPC using the common method of local TCP. The IPC server creates a local TCP server,
the IPC client connects to it with the localhost loopback address over the port of your choosing.

The library adds to that by using a simple JSON-based protocol. When you transmit an Electron (message object),
the object is converted to JSON and sent with a header. This header tells the receiving axon how long the message
was supposed to be. This length header lets the library receive large fragmented TCP messages and put them all
back together. The library will intelligently increase or decrease the receiving buffer size to optimize memory usage
and receive the whole message as fast as possible.

# Gradle Dependency (jCenter)

Easily reference the library in your Android projects using this dependency in your module's `build.gradle` file:

```Gradle
dependencies {
    compile 'com.afollestad:neuron:0.1.1'
}
```

[ ![Download](https://api.bintray.com/packages/drummer-aidan/maven/neuron/images/download.svg) ](https://bintray.com/drummer-aidan/maven/neuron/_latestVersion)

# Client Side Coding

```java
Axon client = Neuron.with(12345)
    .axon()
    .connection(new NeuronFuture<Axon>() {
        @Override
        public void on(Axon result, Exception e) {
            if (e != null) {
                // Connect Error
            } else {
                // Connected
            }
        }
    })
    .receival(Message.class, new NeuronFuture2<Axon, Message>() {
        @Override
        public void on(Axon parent, Message result, Exception e) {
            if (e != null) {
                // Receival error
            } else {
                // Received Message object
            }
        }
    })
    .disconnection(new NeuronFuture<Axon>() {
        @Override
        public void on(Axon result, Exception e) {
            // e will always be null here, for now
        }
    });
    
Electron transmission = // ... create instance of an Electron subclass here
client.transmit(transmission);
```

This code creates a new connection over port 12345, to any listening terminal (server) on the same port.

`Neuron.with(12345)` returns a singleton reference, so you get a reference to the same `Neuron` object every
in your app with that code. `axon()` returns the client for that port, and it will also be the same instance per Neuron object.

`connection` and `disconnection` are callbacks that notify you of the associated events. `receival` sets
up a callback to receive `Message` objects from the terminal.

# Server Side Coding

```java
Terminal terminal = Neuron.with(12345)
    .terminal()
    .ready(new NeuronFuture<Terminal>() {
        @Override
        public void on(Terminal result, Exception e) {
            // Server is initialized and ready to accept clients
        }
    })
    .axon(new NeuronFuture<Axon>() {
        @Override
        public void on(Axon result, Exception e) {
            // New client (axon)
        }
    });
```

Like the client code, this gets a reference to the `Neuron` singleton for port 12345. It retrieves the Terminal
for this port and setups up the `ready` and `axon` callbacks. `ready` is called when the server is finished
initializing and is ready to accept clients. `axon` is called when the server receives a new client. You
can setup a `receival` callback and `transmit` data to clients.

`Terminal`'s `axons` method gets a list of all connected clients.

# Closing Connections

Neuron has two cleanup methods:

```java
// This one closes axons and terminals for port 12345 in the current app.
Neuron.with(12345)
    end();
    
// This one closes all axons and terminals for all ports for the current app.
Neuron.endAll();
```

Axon and Terminal also have their own individual cleanup methods:

```java
Terminal terminal = // ... get it from somewhere
terminal.end();

Axon axon = // ... get it from somewhere
axon.end();
```

Basically, the end methods close connections and cleanup any running threads so that your app can cleanly terminate.

# Electron Objects

`Electron` is an abstract interface that you can implement with your own sub-classes. An `Electron` is an object that
can be sent and received through axons. When you implement `Electron`, you instruct `Neuron` on how
 to convert your object to and from JSON automatically. Here's an implementation from the sample project:
 
```java
public class Message extends Electron {

    private String mMessage;

    // Needed for internal initialization
    public Message() {
    }

    // Used by sample project to create a new Message object with the content filled in
    public Message(String content) {
        mMessage = content;
    }

    // This is used by the sample to retrieve and display received content
    public String getContent() {
        return mMessage;
    }

    // This is used by the terminal in the sample to update the content
    public void setContent(String content) {
        mMessage = content;
    }

    // This is used internally to load JSON into a new instance of your Electron object
    @Override
    public void loadJson(JSONObject json) {
        mMessage = json.optString("content");
    }

    // This is used by the library when you transmit this object, it converts itself to JSON
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
```

As long as you implement a default (no parameters) constructor and the two overridden methods, you're all set.

Now, notice how the `receival` callback was setup in the client example:

```java
.receival(Message.class, new NeuronFuture2<Axon, Message>() {
        @Override
        public void on(Axon parent, Message result, Exception e) {
                            
        }
})
```

`Message.class` is passed as the first parameter, `Message` is passed as the second generic type in the future callback.
The first parameter is used to match up received objects with objects you're interested in. If an axon receives
an object with the name `Message`, it will construct a new `Message` object and pass it to this callback. The
generic type just allows the callback to assume the received type will be a `Message` (as long as the first parameter matches
it, there'll be no issues.

# Async Transmissions and Replies

If you want to send an Electron and wait for a response specifically related to that message, you can use the async
form of `Axon#transmit`.

```java
Message electron = new Message("Hey!");
axon.transmit(electron, new NeuronFuture3<Axon, Message>() {
    @Override
    public void on(Axon parent, Message result, Exception e) {
        
    }
});
```

Basically, an ID is assigned to your sent message and a temporary callback is created and associated with that ID.
The client or server can respond with the `Axon#reply` method in order to reply with that same ID which will
be treated as a reply and sent to the `transmit` callback.

```java
Message received = // message from callback...
Message reply = new Message("I got your message!");

axon.reply(received, reply);
```

Currently, replies to replies aren't supported. You shouldn't really need that anyways.