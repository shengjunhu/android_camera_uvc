package com.hsj.camera;

import java.io.UnsupportedEncodingException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.os.Handler;
import android.text.TextUtils;
import android.util.SparseArray;

public final class USBMonitor {

    private static final String TAG = "USBMonitor";
    private static final String ACTION_USB_PERMISSION_BASE = "com.hsj.USB_PERMISSION.";
    private final String ACTION_USB_PERMISSION = ACTION_USB_PERMISSION_BASE + hashCode();
    public static final String ACTION_USB_DEVICE_ATTACHED = "android.hardware.usb.action.USB_DEVICE_ATTACHED";

    /**
     * UsbControlBlock that is open
     */
    private final ConcurrentHashMap<UsbDevice, UsbControlBlock> mCtrlBlocks = new ConcurrentHashMap<>();
    private final SparseArray<WeakReference<UsbDevice>> mHasPermissions = new SparseArray<>();

    private final WeakReference<Context> mWeakContext;
    private final UsbManager mUsbManager;
    private final OnDeviceConnectListener mOnDeviceConnectListener;
    private PendingIntent mPermissionIntent = null;
    private List<DeviceFilter> mDeviceFilters = new ArrayList<>();

    /**
     * Handler for calling callback on worker thread
     */
    private final Handler mAsyncHandler;
    private volatile boolean destroyed;

    /**
     * Callback listener when changing the status of a USB device
     */
    public interface OnDeviceConnectListener {
        /**
         * called when device attached
         *
         * @param device {@link UsbDevice}
         */
        void onAttach(UsbDevice device);

        /**
         * called after device opened
         *
         * @param device {@link UsbDevice}
         * @param ctrlBlock {@link UsbControlBlock}
         * @param createNew true is new
         */
        void onConnect(UsbDevice device, UsbControlBlock ctrlBlock, boolean createNew);

        /**
         * called when USB device removed or its power off (this callback is called after device closing)
         *
         * @param device {@link UsbDevice}
         * @param ctrlBlock {@link UsbControlBlock}
         */
        void onDisconnect(UsbDevice device, UsbControlBlock ctrlBlock);

        /**
         * called when canceled or could not get permission from user
         *
         * @param device {@link UsbDevice}
         */
        void onCancel(UsbDevice device);

        /**
         * called when device detach(after onDisconnect)
         *
         * @param device {@link UsbDevice}
         */
        void onDetach(UsbDevice device);
    }

    public USBMonitor(final Context context, final OnDeviceConnectListener listener) throws IllegalArgumentException {
        Logger.v(TAG, "USBMonitor:Constructor");
        if (listener == null) {
            throw new IllegalArgumentException("OnDeviceConnectListener should not null.");
        }
        mWeakContext = new WeakReference<>(context);
        mUsbManager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
        mOnDeviceConnectListener = listener;
        mAsyncHandler = USBHandler.createHandler(TAG);
        destroyed = false;
        Logger.v(TAG, "USBMonitor:mUsbManager=" + mUsbManager);
    }

    /**
     * Release all related resources,
     * never reuse again
     */
    public void destroy() {
        Logger.i(TAG, ">>destroy");
        unregister();
        if (!destroyed) {
            destroyed = true;
            // Close all monitored USB devices
            Set<UsbDevice> keys = mCtrlBlocks.keySet();
            if (keys != null) {
                UsbControlBlock ctrlBlock;
                try {
                    for (final UsbDevice key : keys) {
                        ctrlBlock = mCtrlBlocks.remove(key);
                        if (ctrlBlock != null) {
                            ctrlBlock.close();
                        }
                    }
                } catch (final Exception e) {
                    Logger.e(TAG, ">>destroy", e);
                }
            }
            mCtrlBlocks.clear();
            try {
                mAsyncHandler.getLooper().quit();
            } catch (final Exception e) {
                Logger.e(TAG, ">>destroy:", e);
            }
        }
    }

    /**
     * register BroadcastReceiver to monitor USB events
     *
     * @throws IllegalStateException
     */
    public synchronized void register() throws IllegalStateException {
        if (destroyed) throw new IllegalStateException("already destroyed");
        if (mPermissionIntent == null) {
            Logger.i(TAG, ">>register");
            final Context context = mWeakContext.get();
            if (context != null) {
                mPermissionIntent = PendingIntent.getBroadcast(context, 0, new Intent(ACTION_USB_PERMISSION), 0);
                final IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
                // ACTION_USB_DEVICE_ATTACHED never comes on some devices so it should not be added here
                filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
                context.registerReceiver(mUsbReceiver, filter);
            }
            // start connection check
            mDeviceCounts = 0;
            // 1000ms -> 100ms
            mAsyncHandler.postDelayed(mDeviceCheckRunnable, 100);
        }
    }

    /**
     * unregister BroadcastReceiver
     *
     * @throws IllegalStateException
     */
    public synchronized void unregister() throws IllegalStateException {
        // Removed Runnable for connection check
        mDeviceCounts = 0;
        if (!destroyed) {
            mAsyncHandler.removeCallbacks(mDeviceCheckRunnable);
        }
        if (mPermissionIntent != null) {
            final Context context = mWeakContext.get();
            try {
                if (context != null) {
                    context.unregisterReceiver(mUsbReceiver);
                }
            } catch (final Exception e) {
                Logger.w(TAG, e);
            }
            mPermissionIntent = null;
        }
    }

    public synchronized boolean isRegistered() {
        return !destroyed && (mPermissionIntent != null);
    }

    /**
     * set device filter
     *
     * @param filter
     * @throws IllegalStateException
     */
    public void setDeviceFilter(final DeviceFilter filter) throws IllegalStateException {
        if (destroyed) throw new IllegalStateException("already destroyed");
        mDeviceFilters.clear();
        mDeviceFilters.add(filter);
    }

    /**
     * Add device filter
     *
     * @param filter
     * @throws IllegalStateException
     */
    public void addDeviceFilter(final DeviceFilter filter) throws IllegalStateException {
        if (destroyed) throw new IllegalStateException("already destroyed");
        mDeviceFilters.add(filter);
    }

    /**
     * Remove device filter
     *
     * @param filter {@link DeviceFilter}
     * @throws IllegalStateException
     */
    public void removeDeviceFilter(final DeviceFilter filter) throws IllegalStateException {
        if (destroyed) throw new IllegalStateException("already destroyed");
        mDeviceFilters.remove(filter);
    }

    /**
     * set device filters
     *
     * @param filters
     * @throws IllegalStateException
     */
    public void setDeviceFilter(final List<DeviceFilter> filters) throws IllegalStateException {
        if (destroyed) throw new IllegalStateException("already destroyed");
        mDeviceFilters.clear();
        mDeviceFilters.addAll(filters);
    }

    /**
     * add device filters
     *
     * @param filters
     * @throws IllegalStateException
     */
    public void addDeviceFilter(final List<DeviceFilter> filters) throws IllegalStateException {
        if (destroyed) throw new IllegalStateException("already destroyed");
        mDeviceFilters.addAll(filters);
    }

    /**
     * remove device filters
     *
     * @param filters
     */
    public void removeDeviceFilter(final List<DeviceFilter> filters) throws IllegalStateException {
        if (destroyed) throw new IllegalStateException("already destroyed");
        mDeviceFilters.removeAll(filters);
    }

    /**
     * return the number of connected USB devices that matched device filter
     *
     * @return
     * @throws IllegalStateException
     */
    public int getDeviceCount() throws IllegalStateException {
        if (destroyed) throw new IllegalStateException("already destroyed");
        return getDeviceList().size();
    }

    /**
     * return device list, return empty list if no device matched
     *
     * @return
     * @throws IllegalStateException
     */
    public List<UsbDevice> getDeviceList() throws IllegalStateException {
        if (destroyed) throw new IllegalStateException("already destroyed");
        return getDeviceList(mDeviceFilters);
    }

    /**
     * return device list, return empty list if no device matched
     *
     * @param filters
     * @return
     * @throws IllegalStateException
     */
    public List<UsbDevice> getDeviceList(final List<DeviceFilter> filters) throws IllegalStateException {
        if (destroyed) throw new IllegalStateException("already destroyed");
        //Add by Hsj for fix NullPointerException
        //final HashMap<String, UsbDevice> deviceList = mUsbManager.getDeviceList();
        final HashMap<String, UsbDevice> deviceList = getDeviceList(mUsbManager);
        final List<UsbDevice> result = new ArrayList<>();
        if (deviceList != null) {
            if ((filters == null) || filters.isEmpty()) {
                result.addAll(deviceList.values());
            } else {
                for (final UsbDevice device : deviceList.values()) {
                    for (final DeviceFilter filter : filters) {
                        if ((filter != null) && filter.matches(device)) {
                            // when filter matches
                            if (!filter.isExclude) {
                                result.add(device);
                            }
                            break;
                        }
                    }
                }
            }
        }
        return result;
    }

    /**
     * return device list, return empty list if no device matched
     *
     * @param filter
     * @return List<UsbDevice>
     * @throws {@link IllegalStateException}
     */
    public List<UsbDevice> getDeviceList(final DeviceFilter filter) throws IllegalStateException {
        if (destroyed) throw new IllegalStateException("already destroyed");
        final HashMap<String, UsbDevice> deviceList = mUsbManager.getDeviceList();
        final List<UsbDevice> result = new ArrayList<>();
        if (deviceList != null) {
            for (final UsbDevice device : deviceList.values()) {
                if ((filter == null) || (filter.matches(device) && !filter.isExclude)) {
                    result.add(device);
                }
            }
        }
        return result;
    }

    private static HashMap<String, UsbDevice> getDeviceList(UsbManager usbManager) {
        HashMap<String, UsbDevice> list = null;
        if (null != usbManager) {
            try {
                list = usbManager.getDeviceList();
            } catch (Throwable e) {
                Logger.w(TAG, e);
            }
        }
        return list;
    }

    /**
     * get USB device list, without filter
     *
     * @return Iterator<UsbDevice>
     * @throws {@link IllegalStateException}
     */
    public Iterator<UsbDevice> getDevices() throws IllegalStateException {
        if (destroyed) throw new IllegalStateException("already destroyed");
        Iterator<UsbDevice> iterator = null;
        final HashMap<String, UsbDevice> list = mUsbManager.getDeviceList();
        if (list != null)
            iterator = list.values().iterator();
        return iterator;
    }

    /**
     * output device list to LogCat
     */
    public final void dumpDevices() {
        //Add by Hsj for fix NullPointerException
        //final HashMap<String, UsbDevice> list = mUsbManager.getDeviceList();
        final HashMap<String, UsbDevice> list = getDeviceList(mUsbManager);
        if (list != null) {
            final Set<String> keys = list.keySet();
            if (keys != null && keys.size() > 0) {
                final StringBuilder sb = new StringBuilder();
                for (final String key : keys) {
                    final UsbDevice device = list.get(key);
                    final int num_interface = device != null ? device.getInterfaceCount() : 0;
                    sb.setLength(0);
                    for (int i = 0; i < num_interface; i++) {
                        sb.append(String.format(Locale.US, "interface%d:%s", i, device.getInterface(i).toString()));
                    }
                    Logger.i(TAG, "key=" + key + ":" + device + ":" + sb.toString());
                }
            } else {
                Logger.i(TAG, "no device");
            }
        } else {
            Logger.i(TAG, "no device");
        }
    }

    /**
     * return whether the specific Usb device has permission
     *
     * @param device
     * @return true: The specified UsbDevice has permissions
     * @throws IllegalStateException
     */
    public final boolean hasPermission(final UsbDevice device) throws IllegalStateException {
        if (destroyed) throw new IllegalStateException("already destroyed");
        return updatePermission(device, device != null && mUsbManager.hasPermission(device));
    }

    /**
     * Update the permission status held internally
     *
     * @param device
     * @param hasPermission
     * @return hasPermission
     */
    private boolean updatePermission(final UsbDevice device, final boolean hasPermission) {
        final int deviceKey = getDeviceKey(device, true);
        synchronized (mHasPermissions) {
            if (hasPermission) {
                if (mHasPermissions.get(deviceKey) == null) {
                    mHasPermissions.put(deviceKey, new WeakReference<>(device));
                }
            } else {
                mHasPermissions.remove(deviceKey);
            }
        }
        return hasPermission;
    }

    /**
     * request permission to access to USB device
     *
     * @param device
     * @return true if fail to request permission
     */
    public synchronized boolean requestPermission(final UsbDevice device) {
        Logger.v(TAG, "requestPermission:device=" + device);
        boolean result = false;
        if (isRegistered()) {
            if (device != null) {
                if (mUsbManager.hasPermission(device)) {
                    // call onConnect if app already has permission
                    processConnect(device);
                } else {
                    try {
                        // Request if you don't have permission
                        mUsbManager.requestPermission(device, mPermissionIntent);
                    } catch (final Exception e) {
                        //It seems to generate an unknown exception called android.permission.sec.MDM_APP_MGMT in the GALAXY system of Android 5.1.x
                        Logger.w(TAG, e.getMessage());
                        processCancel(device);
                        result = true;
                    }
                }
            } else {
                processCancel(device);
                result = true;
            }
        } else {
            processCancel(device);
            result = true;
        }
        return result;
    }

    /**
     * Open the specified UsbDevice
     *
     * @param device
     * @return UsbControlBlock
     * @throws SecurityException Throw a SecurityException if you don't have permissions
     */
    public UsbControlBlock openDevice(final UsbDevice device) throws SecurityException {
        if (hasPermission(device)) {
            UsbControlBlock result = mCtrlBlocks.get(device);
            if (result == null) {
                // OpenDevice in this
                result = new UsbControlBlock(USBMonitor.this, device);
                mCtrlBlocks.put(device, result);
            }
            return result;
        } else {
            throw new SecurityException("has no permission");
        }
    }

    /**
     * BroadcastReceiver for USB permission
     */
    private final BroadcastReceiver mUsbReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(final Context context, final Intent intent) {
            if (destroyed) return;
            final String action = intent.getAction();
            if (ACTION_USB_PERMISSION.equals(action)) {
                // when received the result of requesting USB permission
                synchronized (USBMonitor.this) {
                    final UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        if (device != null) {
                            // get permission, call onConnect
                            processConnect(device);
                        }
                    } else {
                        // failed to get permission
                        processCancel(device);
                    }
                }
            } else if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action)) {
                final UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                updatePermission(device, hasPermission(device));
                processAttach(device);
            } else if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
                // when device removed
                final UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                if (device != null) {
                    UsbControlBlock ctrlBlock = mCtrlBlocks.remove(device);
                    if (ctrlBlock != null) {
                        // cleanup
                        ctrlBlock.close();
                    }
                    mDeviceCounts = 0;
                    processDetach(device);
                }
            }
        }
    };

    /**
     * number of connected & detected devices
     */
    private volatile int mDeviceCounts = 0;
    /**
     * periodically check connected devices and if it changed, call onAttach
     */
    private final Runnable mDeviceCheckRunnable = new Runnable() {
        @Override
        public void run() {
            if (destroyed) return;
            final List<UsbDevice> devices = getDeviceList();
            final int n = devices.size();
            final int hasPermissionCounts;
            final int m;
            synchronized (mHasPermissions) {
                hasPermissionCounts = mHasPermissions.size();
                mHasPermissions.clear();
                for (final UsbDevice device : devices) {
                    hasPermission(device);
                }
                m = mHasPermissions.size();
            }
            if ((n > mDeviceCounts) || (m > hasPermissionCounts)) {
                mDeviceCounts = n;
                if (mOnDeviceConnectListener != null) {
                    for (int i = 0; i < n; i++) {
                        final UsbDevice device = devices.get(i);
                        mAsyncHandler.post(() -> mOnDeviceConnectListener.onAttach(device));
                    }
                }
            }
            // confirm every 2 seconds
            mAsyncHandler.postDelayed(this, 2000);
        }
    };

    private void processAttach(final UsbDevice device) {
        if (destroyed) return;
        Logger.v(TAG, ">>processAttach");
        if (mOnDeviceConnectListener != null) {
            mAsyncHandler.post(() -> mOnDeviceConnectListener.onAttach(device));
        }
    }

    /**
     * open specific USB device
     *
     * @param device
     */
    private void processConnect(final UsbDevice device) {
        if (destroyed) return;
        updatePermission(device, true);
        mAsyncHandler.post(() -> {
            Logger.v(TAG, "processConnect:device=" + device);
            UsbControlBlock ctrlBlock;
            final boolean createNew;
            ctrlBlock = mCtrlBlocks.get(device);
            if (ctrlBlock == null) {
                ctrlBlock = new UsbControlBlock(USBMonitor.this, device);
                mCtrlBlocks.put(device, ctrlBlock);
                createNew = true;
            } else {
                createNew = false;
            }
            if (mOnDeviceConnectListener != null) {
                mOnDeviceConnectListener.onConnect(device, ctrlBlock, createNew);
            }
        });
    }

    private void processCancel(final UsbDevice device) {
        if (destroyed) return;
        Logger.v(TAG, ">>processCancel");
        updatePermission(device, false);
        if (mOnDeviceConnectListener != null) {
            mAsyncHandler.post(() -> mOnDeviceConnectListener.onCancel(device));
        }
    }

    private void processDetach(final UsbDevice device) {
        if (destroyed) return;
        Logger.v(TAG, ">>processDetach");
        if (mOnDeviceConnectListener != null) {
            mAsyncHandler.post(() -> mOnDeviceConnectListener.onDetach(device));
        }
    }

    /**
     * Generate a device key name for saving settings for each USB device.
     * Generated from vendor ID, product ID, device class, device subclass, device protocol
     * Note that the same key name will be used for products of the same type.
     *
     * @param device Returns an empty string if null
     * @return device key name
     */
    public static String getDeviceKeyName(final UsbDevice device) {
        return getDeviceKeyName(device, null, false);
    }

    /**
     * Generate a device key name for saving settings for each USB device.
     * Note that useNewAPI = false will result in the same device key for similar products.
     *
     * @param device
     * @param useNewAPI
     * @return device key name
     */
    public static String getDeviceKeyName(final UsbDevice device, final boolean useNewAPI) {
        return getDeviceKeyName(device, null, useNewAPI);
    }

    /**
     * Generate a device key name for saving settings for each USB device. Use this device name as the HashMap key
     * Valid only when UsbDevice is open
     * Generated from vendor ID, product ID, device class, device subclass, device protocol
     * Generate a device key name including serial if serial is not null or empty string
     * If useNewAPI = true meets the API level, the manufacturer name, version, and configuration count are also used.
     *
     * @param device    Returns an empty string if null
     * @param serial    UsbDeviceConnection#getSerialPass the serial number obtained in, null and useNewAPI = true and API> = 21 to get it internally
     * @param useNewAPI Also use methods that can only be used with API> = 21 or API> = 23 (however, depending on the device, null is returned, so it depends on the device)
     * @return device key name
     */
    @SuppressLint("NewApi")
    public static String getDeviceKeyName(final UsbDevice device, final String serial, final boolean useNewAPI) {
        if (device == null) return "";
        final StringBuilder sb = new StringBuilder();
        sb.append(device.getVendorId());
        sb.append("#");    // API >= 12
        sb.append(device.getProductId());
        sb.append("#");    // API >= 12
        sb.append(device.getDeviceClass());
        sb.append("#");    // API >= 12
        sb.append(device.getDeviceSubclass());
        sb.append("#");    // API >= 12
        sb.append(device.getDeviceProtocol());// API >= 12
        if (!TextUtils.isEmpty(serial)) {
            sb.append("#");
            sb.append(serial);
        }
        if (useNewAPI && BuildCheck.isAndroid5()) {
            sb.append("#");
            if (TextUtils.isEmpty(serial)) {
                sb.append(device.getSerialNumber());
                sb.append("#");    // API >= 21
            }
            sb.append(device.getManufacturerName());
            sb.append("#");    // API >= 21
            sb.append(device.getConfigurationCount());
            sb.append("#");    // API >= 21
            if (BuildCheck.isMarshmallow()) {
                sb.append(device.getVersion());
                sb.append("#");    // API >= 23
            }
        }
        //Logger.v(TAG, "getDeviceKeyName:" + sb.toString());
        return sb.toString();
    }

    /**
     * Get device key as an integer
     * Get the hasCode of the string obtained by getDeviceKeyName
     * Generated from vendor ID, product ID, device class, device subclass, device protocol
     * Note that the same device key will be used for products of the same type.
     *
     * @param device Returns 0 if null
     * @return device key
     */
    public static int getDeviceKey(final UsbDevice device) {
        return device != null ? getDeviceKeyName(device, null, false).hashCode() : 0;
    }

    /**
     * Get device key as an integer
     * getDeviceKeyNameGet the hasCode of the string obtained in
     * useNewAPI=false Note that the same device key will be used for products of the same type.
     *
     * @param device
     * @param useNewAPI
     * @return device key
     */
    public static int getDeviceKey(final UsbDevice device, final boolean useNewAPI) {
        return device != null ? getDeviceKeyName(device, null, useNewAPI).hashCode() : 0;
    }

    /**
     * Get device key as an integer
     * getDeviceKeyNameGet the hasCode of the string obtained in
     * Note that if serial is null and useNewAPI = false, it will be the same device key for similar products.
     *
     * @param device    Returns 0 if null
     * @param serial    UsbDeviceConnection#getSerialPass the serial number obtained in, null and useNewAPI = true and API> = 21 to get it internally
     * @param useNewAPI Also use methods that can only be used with API> = 21 or API> = 23 (however, depending on the device, null is returned, so it depends on the device)
     * @return int
     */
    public static int getDeviceKey(final UsbDevice device, final String serial, final boolean useNewAPI) {
        return device != null ? getDeviceKeyName(device, serial, useNewAPI).hashCode() : 0;
    }

    public static class UsbDeviceInfo {
        public String usb_version;
        public String manufacturer;
        public String product;
        public String version;
        public String serial;

        private void clear() {
            usb_version = manufacturer = product = version = serial = null;
        }

        @Override
        public String toString() {
            return String.format("UsbDevice:usb_version=%s,manufacturer=%s,product=%s,version=%s,serial=%s",
                    usb_version != null ? usb_version : "",
                    manufacturer != null ? manufacturer : "",
                    product != null ? product : "",
                    version != null ? version : "",
                    serial != null ? serial : "");
        }
    }

    private static final int USB_DIR_OUT = 0;
    private static final int USB_DIR_IN = 0x80;
    private static final int USB_TYPE_MASK = (0x03 << 5);
    private static final int USB_TYPE_STANDARD = (0x00 << 5);
    private static final int USB_TYPE_CLASS = (0x01 << 5);
    private static final int USB_TYPE_VENDOR = (0x02 << 5);
    private static final int USB_TYPE_RESERVED = (0x03 << 5);
    private static final int USB_RECIP_MASK = 0x1f;
    private static final int USB_RECIP_DEVICE = 0x00;
    private static final int USB_RECIP_INTERFACE = 0x01;
    private static final int USB_RECIP_ENDPOINT = 0x02;
    private static final int USB_RECIP_OTHER = 0x03;
    private static final int USB_RECIP_PORT = 0x04;
    private static final int USB_RECIP_RPIPE = 0x05;
    private static final int USB_REQ_GET_STATUS = 0x00;
    private static final int USB_REQ_CLEAR_FEATURE = 0x01;
    private static final int USB_REQ_SET_FEATURE = 0x03;
    private static final int USB_REQ_SET_ADDRESS = 0x05;
    private static final int USB_REQ_GET_DESCRIPTOR = 0x06;
    private static final int USB_REQ_SET_DESCRIPTOR = 0x07;
    private static final int USB_REQ_GET_CONFIGURATION = 0x08;
    private static final int USB_REQ_SET_CONFIGURATION = 0x09;
    private static final int USB_REQ_GET_INTERFACE = 0x0A;
    private static final int USB_REQ_SET_INTERFACE = 0x0B;
    private static final int USB_REQ_SYNCH_FRAME = 0x0C;
    private static final int USB_REQ_SET_SEL = 0x30;
    private static final int USB_REQ_SET_ISOCH_DELAY = 0x31;
    private static final int USB_REQ_SET_ENCRYPTION = 0x0D;
    private static final int USB_REQ_GET_ENCRYPTION = 0x0E;
    private static final int USB_REQ_RPIPE_ABORT = 0x0E;
    private static final int USB_REQ_SET_HANDSHAKE = 0x0F;
    private static final int USB_REQ_RPIPE_RESET = 0x0F;
    private static final int USB_REQ_GET_HANDSHAKE = 0x10;
    private static final int USB_REQ_SET_CONNECTION = 0x11;
    private static final int USB_REQ_SET_SECURITY_DATA = 0x12;
    private static final int USB_REQ_GET_SECURITY_DATA = 0x13;
    private static final int USB_REQ_SET_WUSB_DATA = 0x14;
    private static final int USB_REQ_LOOPBACK_DATA_WRITE = 0x15;
    private static final int USB_REQ_LOOPBACK_DATA_READ = 0x16;
    private static final int USB_REQ_SET_INTERFACE_DS = 0x17;

    private static final int USB_REQ_STANDARD_DEVICE_SET = (USB_DIR_OUT | USB_TYPE_STANDARD | USB_RECIP_DEVICE);        // 0x10
    private static final int USB_REQ_STANDARD_DEVICE_GET = (USB_DIR_IN | USB_TYPE_STANDARD | USB_RECIP_DEVICE);            // 0x90
    private static final int USB_REQ_STANDARD_INTERFACE_SET = (USB_DIR_OUT | USB_TYPE_STANDARD | USB_RECIP_INTERFACE);    // 0x11
    private static final int USB_REQ_STANDARD_INTERFACE_GET = (USB_DIR_IN | USB_TYPE_STANDARD | USB_RECIP_INTERFACE);    // 0x91
    private static final int USB_REQ_STANDARD_ENDPOINT_SET = (USB_DIR_OUT | USB_TYPE_STANDARD | USB_RECIP_ENDPOINT);    // 0x12
    private static final int USB_REQ_STANDARD_ENDPOINT_GET = (USB_DIR_IN | USB_TYPE_STANDARD | USB_RECIP_ENDPOINT);        // 0x92

    private static final int USB_REQ_CS_DEVICE_SET = (USB_DIR_OUT | USB_TYPE_CLASS | USB_RECIP_DEVICE);                // 0x20
    private static final int USB_REQ_CS_DEVICE_GET = (USB_DIR_IN | USB_TYPE_CLASS | USB_RECIP_DEVICE);                    // 0xa0
    private static final int USB_REQ_CS_INTERFACE_SET = (USB_DIR_OUT | USB_TYPE_CLASS | USB_RECIP_INTERFACE);            // 0x21
    private static final int USB_REQ_CS_INTERFACE_GET = (USB_DIR_IN | USB_TYPE_CLASS | USB_RECIP_INTERFACE);            // 0xa1
    private static final int USB_REQ_CS_ENDPOINT_SET = (USB_DIR_OUT | USB_TYPE_CLASS | USB_RECIP_ENDPOINT);                // 0x22
    private static final int USB_REQ_CS_ENDPOINT_GET = (USB_DIR_IN | USB_TYPE_CLASS | USB_RECIP_ENDPOINT);                // 0xa2

    private static final int USB_REQ_VENDER_DEVICE_SET = (USB_DIR_OUT | USB_TYPE_CLASS | USB_RECIP_DEVICE);                // 0x40
    private static final int USB_REQ_VENDER_DEVICE_GET = (USB_DIR_IN | USB_TYPE_CLASS | USB_RECIP_DEVICE);                // 0xc0
    private static final int USB_REQ_VENDER_INTERFACE_SET = (USB_DIR_OUT | USB_TYPE_CLASS | USB_RECIP_INTERFACE);        // 0x41
    private static final int USB_REQ_VENDER_INTERFACE_GET = (USB_DIR_IN | USB_TYPE_CLASS | USB_RECIP_INTERFACE);        // 0xc1
    private static final int USB_REQ_VENDER_ENDPOINT_SET = (USB_DIR_OUT | USB_TYPE_CLASS | USB_RECIP_ENDPOINT);            // 0x42
    private static final int USB_REQ_VENDER_ENDPOINT_GET = (USB_DIR_IN | USB_TYPE_CLASS | USB_RECIP_ENDPOINT);            // 0xc2

    private static final int USB_DT_DEVICE = 0x01;
    private static final int USB_DT_CONFIG = 0x02;
    private static final int USB_DT_STRING = 0x03;
    private static final int USB_DT_INTERFACE = 0x04;
    private static final int USB_DT_ENDPOINT = 0x05;
    private static final int USB_DT_DEVICE_QUALIFIER = 0x06;
    private static final int USB_DT_OTHER_SPEED_CONFIG = 0x07;
    private static final int USB_DT_INTERFACE_POWER = 0x08;
    private static final int USB_DT_OTG = 0x09;
    private static final int USB_DT_DEBUG = 0x0a;
    private static final int USB_DT_INTERFACE_ASSOCIATION = 0x0b;
    private static final int USB_DT_SECURITY = 0x0c;
    private static final int USB_DT_KEY = 0x0d;
    private static final int USB_DT_ENCRYPTION_TYPE = 0x0e;
    private static final int USB_DT_BOS = 0x0f;
    private static final int USB_DT_DEVICE_CAPABILITY = 0x10;
    private static final int USB_DT_WIRELESS_ENDPOINT_COMP = 0x11;
    private static final int USB_DT_WIRE_ADAPTER = 0x21;
    private static final int USB_DT_RPIPE = 0x22;
    private static final int USB_DT_CS_RADIO_CONTROL = 0x23;
    private static final int USB_DT_PIPE_USAGE = 0x24;
    private static final int USB_DT_SS_ENDPOINT_COMP = 0x30;
    private static final int USB_DT_CS_DEVICE = (USB_TYPE_CLASS | USB_DT_DEVICE);
    private static final int USB_DT_CS_CONFIG = (USB_TYPE_CLASS | USB_DT_CONFIG);
    private static final int USB_DT_CS_STRING = (USB_TYPE_CLASS | USB_DT_STRING);
    private static final int USB_DT_CS_INTERFACE = (USB_TYPE_CLASS | USB_DT_INTERFACE);
    private static final int USB_DT_CS_ENDPOINT = (USB_TYPE_CLASS | USB_DT_ENDPOINT);
    private static final int USB_DT_DEVICE_SIZE = 18;

    /**
     * Gets a string from the String descriptor with the specified ID. Null if not available
     *
     * @param connection
     * @param id
     * @param languageCount
     * @param languages
     * @return String
     */
    private static String getString(final UsbDeviceConnection connection, final int id, final int languageCount, final byte[] languages) {
        final byte[] work = new byte[256];
        String result = null;
        for (int i = 1; i <= languageCount; i++) {
            int ret = connection.controlTransfer(
                    USB_REQ_STANDARD_DEVICE_GET, // USB_DIR_IN | USB_TYPE_STANDARD | USB_RECIP_DEVICE
                    USB_REQ_GET_DESCRIPTOR,
                    (USB_DT_STRING << 8) | id, languages[i], work, 256, 0);
            if ((ret > 2) && (work[0] == ret) && (work[1] == USB_DT_STRING)) {
                // skip first two bytes(bLength & bDescriptorType), and copy the rest to the string
                try {
                    result = new String(work, 2, ret - 2, "UTF-16LE");
                    if (!"Љ".equals(result)) {    // 変なゴミが返ってくる時がある
                        break;
                    } else {
                        result = null;
                    }
                } catch (final UnsupportedEncodingException e) {
                    Logger.w(e.getMessage());
                }
            }
        }
        return result;
    }

    /**
     * Get vendor name / product name / version / serial
     *
     * @param device
     * @return UsbDeviceInfo
     */
    public UsbDeviceInfo getDeviceInfo(final UsbDevice device) {
        return updateDeviceInfo(mUsbManager, device, null);
    }

    /**
     * Get vendor name / product name / version / serial
     * #updateDeviceInfo(final UsbManager, final UsbDevice, final UsbDeviceInfo)のヘルパーメソッド
     *
     * @param context
     * @param device
     * @return UsbDeviceInfo
     */
    public static UsbDeviceInfo getDeviceInfo(final Context context, final UsbDevice device) {
        return updateDeviceInfo((UsbManager) context.getSystemService(Context.USB_SERVICE), device, new UsbDeviceInfo());
    }

    /**
     * Get vendor name / product name / version / serial
     *
     * @param manager
     * @param device
     * @param _info
     * @return UsbDeviceInfo
     */
    public static UsbDeviceInfo updateDeviceInfo(final UsbManager manager, final UsbDevice device, final UsbDeviceInfo _info) {
        final UsbDeviceInfo info = _info != null ? _info : new UsbDeviceInfo();
        info.clear();

        if (device != null) {
            if (BuildCheck.isLollipop()) {
                info.manufacturer = device.getManufacturerName();
                info.product = device.getProductName();
                info.serial = device.getSerialNumber();
            }
            if (BuildCheck.isMarshmallow()) {
                info.usb_version = device.getVersion();
            }
            if ((manager != null) && manager.hasPermission(device)) {
                final UsbDeviceConnection connection = manager.openDevice(device);
                if (connection != null) {//Add by Hsj
                    final byte[] desc = connection.getRawDescriptors();

                    if (TextUtils.isEmpty(info.usb_version)) {
                        info.usb_version = String.format("%x.%02x", ((int) desc[3] & 0xff), ((int) desc[2] & 0xff));
                    }
                    if (TextUtils.isEmpty(info.version)) {
                        info.version = String.format("%x.%02x", ((int) desc[13] & 0xff), ((int) desc[12] & 0xff));
                    }
                    if (TextUtils.isEmpty(info.serial)) {
                        info.serial = connection.getSerial();
                    }

                    final byte[] languages = new byte[256];
                    int languageCount = 0;
                    // controlTransfer(int requestType, int request, int value, int index, byte[] buffer, int length, int timeout)
                    try {
                        int result = connection.controlTransfer(
                                USB_REQ_STANDARD_DEVICE_GET, // USB_DIR_IN | USB_TYPE_STANDARD | USB_RECIP_DEVICE
                                USB_REQ_GET_DESCRIPTOR,
                                (USB_DT_STRING << 8) | 0, 0, languages, 256, 0);
                        if (result > 0) {
                            languageCount = (result - 2) / 2;
                        }
                        if (languageCount > 0) {
                            if (TextUtils.isEmpty(info.manufacturer)) {
                                info.manufacturer = getString(connection, desc[14], languageCount, languages);
                            }
                            if (TextUtils.isEmpty(info.product)) {
                                info.product = getString(connection, desc[15], languageCount, languages);
                            }
                            if (TextUtils.isEmpty(info.serial)) {
                                info.serial = getString(connection, desc[16], languageCount, languages);
                            }
                        }
                    } finally {
                        connection.close();
                    }
                }
            }
            if (TextUtils.isEmpty(info.manufacturer)) {
                info.manufacturer = USBVendorId.vendorName(device.getVendorId());
            }
            if (TextUtils.isEmpty(info.manufacturer)) {
                info.manufacturer = String.format("%04x", device.getVendorId());
            }
            if (TextUtils.isEmpty(info.product)) {
                info.product = String.format("%04x", device.getProductId());
            }
        }
        return info;
    }

    /**
     * control class
     * never reuse the instance when it closed
     */
    public static final class UsbControlBlock implements Cloneable {
        private final WeakReference<USBMonitor> mWeakMonitor;
        private final WeakReference<UsbDevice> mWeakDevice;
        protected UsbDeviceConnection mConnection;
        protected final UsbDeviceInfo mInfo;
        private final int mBusNum;
        private final int mDevNum;
        private final SparseArray<SparseArray<UsbInterface>> mInterfaces = new SparseArray<SparseArray<UsbInterface>>();

        /**
         * this class needs permission to access USB device before constructing
         *
         * @param monitor
         * @param device
         */
        private UsbControlBlock(final USBMonitor monitor, final UsbDevice device) {
            Logger.i(TAG, "UsbControlBlock:constructor");
            mWeakMonitor = new WeakReference<USBMonitor>(monitor);
            mWeakDevice = new WeakReference<UsbDevice>(device);
            mConnection = monitor.mUsbManager.openDevice(device);
            mInfo = updateDeviceInfo(monitor.mUsbManager, device, null);
            final String name = device.getDeviceName();
            final String[] v = !TextUtils.isEmpty(name) ? name.split("/") : null;
            int busnum = 0;
            int devnum = 0;
            if (v != null) {
                busnum = Integer.parseInt(v[v.length - 2]);
                devnum = Integer.parseInt(v[v.length - 1]);
            }
            mBusNum = busnum;
            mDevNum = devnum;
            if (mConnection != null) {
                try {
                    final int desc = mConnection.getFileDescriptor();
                    final byte[] rawDesc = mConnection.getRawDescriptors();
                    Logger.i(TAG, String.format(
                            Locale.US, "name=%s,desc=%d,busnum=%d,devnum=%d,rawDesc=",
                            name, desc, busnum, devnum) + rawDesc);
                } catch (Throwable e) {
                    e.printStackTrace();
                }
            } else {
                Logger.e(TAG, "could not connect to device " + name);
            }
        }

        /**
         * copy constructor
         *
         * @param src
         * @throws IllegalStateException
         */
        private UsbControlBlock(final UsbControlBlock src) throws IllegalStateException {
            final USBMonitor monitor = src.getUSBMonitor();
            final UsbDevice device = src.getDevice();
            if (device == null) {
                throw new IllegalStateException("device may already be removed");
            }
            mConnection = monitor.mUsbManager.openDevice(device);
            if (mConnection == null) {
                throw new IllegalStateException("device may already be removed or have no permission");
            }
            mInfo = updateDeviceInfo(monitor.mUsbManager, device, null);
            mWeakMonitor = new WeakReference<USBMonitor>(monitor);
            mWeakDevice = new WeakReference<UsbDevice>(device);
            mBusNum = src.mBusNum;
            mDevNum = src.mDevNum;
            // FIXME USBMonitor.mCtrlBlocks(since HashMap is now replaced, add it to the list or HashMap)
        }

        /**
         * duplicate by clone
         * need permission
         * USBMonitor never handle cloned UsbControlBlock, you should release it after using it.
         *
         * @return UsbControlBlock
         * @throws CloneNotSupportedException
         */
        @Override
        public UsbControlBlock clone() throws CloneNotSupportedException {
            final UsbControlBlock ctrlBlock;
            try {
                ctrlBlock = new UsbControlBlock(this);
            } catch (final IllegalStateException e) {
                throw new CloneNotSupportedException(e.getMessage());
            }
            return ctrlBlock;
        }

        public USBMonitor getUSBMonitor() {
            return mWeakMonitor.get();
        }

        public final UsbDevice getDevice() {
            return mWeakDevice.get();
        }

        /**
         * get device name
         *
         * @return deviceName
         */
        public String getDeviceName() {
            final UsbDevice device = mWeakDevice.get();
            return device != null ? device.getDeviceName() : "";
        }

        /**
         * get device id
         *
         * @return deviceId
         */
        public int getDeviceId() {
            final UsbDevice device = mWeakDevice.get();
            return device != null ? device.getDeviceId() : 0;
        }

        /**
         * get device key string
         *
         * @return same value if the devices has same vendor id, product id, device class, device subclass and device protocol
         */
        public String getDeviceKeyName() {
            return USBMonitor.getDeviceKeyName(mWeakDevice.get());
        }

        /**
         * get device key string
         *
         * @param useNewAPI if true, try to use serial number
         * @return String
         * @throws IllegalStateException
         */
        public String getDeviceKeyName(final boolean useNewAPI) throws IllegalStateException {
            if (useNewAPI) checkConnection();
            return USBMonitor.getDeviceKeyName(mWeakDevice.get(), mInfo.serial, useNewAPI);
        }

        /**
         * get device key
         *
         * @return int
         * @throws IllegalStateException
         */
        public int getDeviceKey() throws IllegalStateException {
            checkConnection();
            return USBMonitor.getDeviceKey(mWeakDevice.get());
        }

        /**
         * get device key
         *
         * @param useNewAPI if true, try to use serial number
         * @return int
         * @throws IllegalStateException
         */
        public int getDeviceKey(final boolean useNewAPI) throws IllegalStateException {
            if (useNewAPI) checkConnection();
            return USBMonitor.getDeviceKey(mWeakDevice.get(), mInfo.serial, useNewAPI);
        }

        /**
         * get device key string
         * if device has serial number, use it
         *
         * @return String
         */
        public String getDeviceKeyNameWithSerial() {
            return USBMonitor.getDeviceKeyName(mWeakDevice.get(), mInfo.serial, false);
        }

        /**
         * get device key
         * if device has serial number, use it
         *
         * @return int
         */
        public int getDeviceKeyWithSerial() {
            return getDeviceKeyNameWithSerial().hashCode();
        }

        /**
         * get UsbDeviceConnection
         *
         * @return UsbDeviceConnection
         */
        public synchronized UsbDeviceConnection getConnection() {
            return mConnection;
        }

        /**
         * get file descriptor to access USB device
         *
         * @return int
         * @throws IllegalStateException
         */
        public synchronized int getFileDescriptor() throws IllegalStateException {
            checkConnection();
            return mConnection.getFileDescriptor();
        }

        /**
         * get raw descriptor for the USB device
         *
         * @return byte[]
         * @throws IllegalStateException
         */
        public synchronized byte[] getRawDescriptors() throws IllegalStateException {
            checkConnection();
            return mConnection.getRawDescriptors();
        }

        /**
         * get vendor id
         *
         * @return VendorId
         */
        public int getVendorId() {
            final UsbDevice device = mWeakDevice.get();
            return device != null ? device.getVendorId() : 0;
        }

        /**
         * get product id
         *
         * @return ProductId
         */
        public int getProductId() {
            final UsbDevice device = mWeakDevice.get();
            return device != null ? device.getProductId() : 0;
        }

        /**
         * get version string of USB
         *
         * @return String
         */
        public String getUsbVersion() {
            return mInfo.usb_version;
        }

        /**
         * get manufacture
         *
         * @return String
         */
        public String getManufacture() {
            return mInfo.manufacturer;
        }

        /**
         * get product name
         *
         * @return String
         */
        public String getProductName() {
            return mInfo.product;
        }

        /**
         * get version
         *
         * @return String
         */
        public String getVersion() {
            return mInfo.version;
        }

        /**
         * get serial number
         *
         * @return String
         */
        public String getSerial() {
            return mInfo.serial;
        }

        public int getBusNum() {
            return mBusNum;
        }

        public int getDevNum() {
            return mDevNum;
        }

        /**
         * get interface
         *
         * @param interface_id
         * @throws IllegalStateException
         */
        public synchronized UsbInterface getInterface(final int interface_id) throws IllegalStateException {
            return getInterface(interface_id, 0);
        }

        /**
         * get interface
         *
         * @param interface_id
         * @param altsetting
         * @return UsbInterface
         * @throws IllegalStateException
         */
        @SuppressLint("NewApi")
        public synchronized UsbInterface getInterface(final int interface_id, final int altsetting) throws IllegalStateException {
            checkConnection();
            SparseArray<UsbInterface> intfs = mInterfaces.get(interface_id);
            if (intfs == null) {
                intfs = new SparseArray<>();
                mInterfaces.put(interface_id, intfs);
            }
            UsbInterface intf = intfs.get(altsetting);
            if (intf == null) {
                final UsbDevice device = mWeakDevice.get();
                final int n = device.getInterfaceCount();
                for (int i = 0; i < n; i++) {
                    UsbInterface temp = device.getInterface(i);
                    if ((temp.getId() == interface_id) && (temp.getAlternateSetting() == altsetting)) {
                        intf = temp;
                        break;
                    }
                }
                if (intf != null) {
                    intfs.append(altsetting, intf);
                }
            }
            return intf;
        }

        /**
         * open specific interface
         *
         * @param intf
         */
        public synchronized void claimInterface(final UsbInterface intf) {
            claimInterface(intf, true);
        }

        public synchronized void claimInterface(final UsbInterface intf, final boolean force) {
            checkConnection();
            mConnection.claimInterface(intf, force);
        }

        /**
         * close interface
         *
         * @param intf
         * @throws IllegalStateException
         */
        public synchronized void releaseInterface(final UsbInterface intf) throws IllegalStateException {
            checkConnection();
            final SparseArray<UsbInterface> intfs = mInterfaces.get(intf.getId());
            if (intfs != null) {
                final int index = intfs.indexOfValue(intf);
                intfs.removeAt(index);
                if (intfs.size() == 0) {
                    mInterfaces.remove(intf.getId());
                }
            }
            mConnection.releaseInterface(intf);
        }

        /**
         * Close device
         * This also close interfaces if they are opened in Java side
         */
        public synchronized void close() {
            Logger.i(TAG, "UsbControlBlock#close:");
            if (mConnection != null) {
                final int n = mInterfaces.size();
                for (int i = 0; i < n; i++) {
                    final SparseArray<UsbInterface> intfs = mInterfaces.valueAt(i);
                    if (intfs != null) {
                        final int m = intfs.size();
                        for (int j = 0; j < m; j++) {
                            final UsbInterface intf = intfs.valueAt(j);
                            mConnection.releaseInterface(intf);
                        }
                        intfs.clear();
                    }
                }
                mInterfaces.clear();
                mConnection.close();
                mConnection = null;
                final USBMonitor monitor = mWeakMonitor.get();
                if (monitor != null) {
                    if (monitor.mOnDeviceConnectListener != null) {
                        monitor.mOnDeviceConnectListener.onDisconnect(mWeakDevice.get(), UsbControlBlock.this);
                    }
                    monitor.mCtrlBlocks.remove(getDevice());
                }
            }
        }

        @Override
        public boolean equals(final Object o) {
            if (o == null) return false;
            if (o instanceof UsbControlBlock) {
                final UsbDevice device = ((UsbControlBlock) o).getDevice();
                return device == null ? mWeakDevice.get() == null
                        : device.equals(mWeakDevice.get());
            } else if (o instanceof UsbDevice) {
                return o.equals(mWeakDevice.get());
            }
            return super.equals(o);
        }

        //@Override
        //protected void finalize() throws Throwable {
        //    close();
        //    super.finalize();
        //}

        private synchronized void checkConnection() throws IllegalStateException {
            if (mConnection == null) {
                throw new IllegalStateException("already closed");
            }
        }
    }

}

