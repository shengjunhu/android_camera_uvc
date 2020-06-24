package com.serenegiant.usb;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;

public class USBThreadHandler extends Handler {

    private static final String TAG = "USBThreadHandler";

    private USBThreadHandler(Looper looper) {
        super(looper);
    }

    private USBThreadHandler(Looper looper, Callback callback) {
        super(looper, callback);
    }

    public static USBThreadHandler createHandler() {
        return createHandler("USBThreadHandler");
    }

    public static USBThreadHandler createHandler(String name) {
        HandlerThread thread = new HandlerThread(name);
        thread.start();
        return new USBThreadHandler(thread.getLooper());
    }

    public static USBThreadHandler createHandler(Callback callback) {
        return createHandler("USBThreadHandler", callback);
    }

    public static USBThreadHandler createHandler(String name, Callback callback) {
        HandlerThread thread = new HandlerThread(name);
        thread.start();
        return new USBThreadHandler(thread.getLooper(), callback);
    }

}
