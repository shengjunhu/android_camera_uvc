package com.serenegiant.usb;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;

public class USBHandler extends Handler {

    private static final String TAG = "USBHandler";

    private USBHandler(Looper looper) {
        super(looper);
    }

    private USBHandler(Looper looper, Callback callback) {
        super(looper, callback);
    }

    public static USBHandler createHandler() {
        return createHandler("USBHandler");
    }

    public static USBHandler createHandler(String name) {
        HandlerThread thread = new HandlerThread(name);
        thread.start();
        return new USBHandler(thread.getLooper());
    }

    public static USBHandler createHandler(Callback callback) {
        return createHandler("USBHandler", callback);
    }

    public static USBHandler createHandler(String name, Callback callback) {
        HandlerThread thread = new HandlerThread(name);
        thread.start();
        return new USBHandler(thread.getLooper(), callback);
    }

}

