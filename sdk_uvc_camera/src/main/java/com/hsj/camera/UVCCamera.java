package com.hsj.camera;

import java.util.ArrayList;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.graphics.SurfaceTexture;
import android.hardware.usb.UsbDevice;
import android.text.TextUtils;
import android.view.Surface;
import android.view.SurfaceHolder;

import com.hsj.camera.BuildConfig;

public class UVCCamera {

    private static final String TAG = "UVCCamera";
    private static final String DEFAULT_USBFS = "/dev/bus/usb";

    //call method result status
    public static final int ACTION_SUCCESS = 0;
    public static final int ACTION_ERROR = -1;
    //preview oritation
    public static final int ROTATE_0 = 0;
    public static final int ROTATE_90 = 90;
    public static final int ROTATE_180 = 180;
    public static final int ROTATE_270 = 270;
    //preview flip
    public static final int FLIP_H = 1;
    public static final int FLIP_DEFAULT = 0;
    public static final int FLIP_W = -1;
    //preview width and height
    public static final int DEFAULT_PREVIEW_WIDTH = 640;
    public static final int DEFAULT_PREVIEW_HEIGHT = 480;
    public static final int DEFAULT_PREVIEW_MODE = 0;
    public static final int DEFAULT_PREVIEW_MIN_FPS = 1;
    public static final int DEFAULT_PREVIEW_MAX_FPS = 30;
    public static final float DEFAULT_BANDWIDTH = 1.0f;

    //Frame Format
    public static final int FRAME_FORMAT_YUYV = 0;
    public static final int FRAME_FORMAT_MJPEG = 1;
    //Pixel Format
    public static final int PIXEL_FORMAT_RAW = 0;
    public static final int PIXEL_FORMAT_YUV = 1;
    public static final int PIXEL_FORMAT_RGB565 = 2;
    public static final int PIXEL_FORMAT_RGBX = 3;
    public static final int PIXEL_FORMAT_YUV420SP = 4;
    public static final int PIXEL_FORMAT_NV21 = 5;             //YVU420SemiPlanar

//--------------------------------------------------------------------------------------------------

    public static final int CTRL_SCANNING = 0x00000001;         // D0:  Scanning Mode
    public static final int CTRL_AE = 0x00000002;               // D1:  Auto-Exposure Mode
    public static final int CTRL_AE_PRIORITY = 0x00000004;      // D2:  Auto-Exposure Priority
    public static final int CTRL_AE_ABS = 0x00000008;           // D3:  Exposure Time (Absolute)
    public static final int CTRL_AR_REL = 0x00000010;           // D4:  Exposure Time (Relative)
    public static final int CTRL_FOCUS_ABS = 0x00000020;        // D5:  Focus (Absolute)
    public static final int CTRL_FOCUS_REL = 0x00000040;        // D6:  Focus (Relative)
    public static final int CTRL_IRIS_ABS = 0x00000080;         // D7:  Iris (Absolute)
    public static final int CTRL_IRIS_REL = 0x00000100;         // D8:  Iris (Relative)
    public static final int CTRL_ZOOM_ABS = 0x00000200;         // D9:  Zoom (Absolute)
    public static final int CTRL_ZOOM_REL = 0x00000400;         // D10: Zoom (Relative)
    public static final int CTRL_PANTILT_ABS = 0x00000800;      // D11: PanTilt (Absolute)
    public static final int CTRL_PANTILT_REL = 0x00001000;      // D12: PanTilt (Relative)
    public static final int CTRL_ROLL_ABS = 0x00002000;         // D13: Roll (Absolute)
    public static final int CTRL_ROLL_REL = 0x00004000;         // D14: Roll (Relative)
    public static final int CTRL_FOCUS_AUTO = 0x00020000;       // D17: Focus, Auto
    public static final int CTRL_PRIVACY = 0x00040000;          // D18: Privacy
    public static final int CTRL_FOCUS_SIMPLE = 0x00080000;     // D19: Focus, Simple
    public static final int CTRL_WINDOW = 0x00100000;           // D20: Window

    public static final int PU_BRIGHTNESS = 0x80000001;         // D0: Brightness
    public static final int PU_CONTRAST = 0x80000002;           // D1: Contrast
    public static final int PU_HUE = 0x80000004;                // D2: Hue
    public static final int PU_SATURATION = 0x80000008;         // D3: Saturation
    public static final int PU_SHARPNESS = 0x80000010;          // D4: Sharpness
    public static final int PU_GAMMA = 0x80000020;              // D5: Gamma
    public static final int PU_WB_TEMP = 0x80000040;            // D6: White Balance Temperature
    public static final int PU_WB_COMPO = 0x80000080;           // D7: White Balance Component
    public static final int PU_BACKLIGHT = 0x80000100;          // D8: Backlight Compensation
    public static final int PU_GAIN = 0x80000200;               // D9: Gain
    public static final int PU_POWER_LF = 0x80000400;           // D10: Power Line Frequency
    public static final int PU_HUE_AUTO = 0x80000800;           // D11: Hue, Auto
    public static final int PU_WB_TEMP_AUTO = 0x80001000;       // D12: White Balance Temperature, Auto
    public static final int PU_WB_COMPO_AUTO = 0x80002000;      // D13: White Balance Component, Auto
    public static final int PU_DIGITAL_MULT = 0x80004000;       // D14: Digital Multiplier
    public static final int PU_DIGITAL_LIMIT = 0x80008000;      // D15: Digital Multiplier Limit
    public static final int PU_AVIDEO_STD = 0x80010000;         // D16: Analog Video Standard
    public static final int PU_AVIDEO_LOCK = 0x80020000;        // D17: Analog Video Lock Status
    public static final int PU_CONTRAST_AUTO = 0x80040000;      // D18: Contrast, Auto

    // uvc_status_class from libuvc.h
    public static final int STATUS_CLASS_CONTROL = 0x10;
    public static final int STATUS_CLASS_CONTROL_CAMERA = 0x11;
    public static final int STATUS_CLASS_CONTROL_PROCESSING = 0x12;

    // uvc_status_attribute from libuvc.h
    public static final int STATUS_ATTRIBUTE_VALUE_CHANGE = 0x00;
    public static final int STATUS_ATTRIBUTE_INFO_CHANGE = 0x01;
    public static final int STATUS_ATTRIBUTE_FAILURE_CHANGE = 0x02;
    public static final int STATUS_ATTRIBUTE_UNKNOWN = 0xff;

//--------------------------------------------------------------------------------------------------

    private USBMonitor.UsbControlBlock mCtrlBlock;
    //Feature flags supported by camera controls
    protected long mControlSupports;
    //Function flags supported by the processing unit
    protected long mProcSupports;
    protected int mCurrentFrameFormat = FRAME_FORMAT_MJPEG;
    protected int mCurrentWidth = DEFAULT_PREVIEW_WIDTH, mCurrentHeight = DEFAULT_PREVIEW_HEIGHT;
    protected float mCurrentBandwidthFactor = DEFAULT_BANDWIDTH;
    protected String mSupportedSize;
    protected List<Size> mCurrentSizeList;
    // these fields from here are accessed from native code and do not change name and remove
    protected long mNativePtr;
    protected int mScanningModeMin, mScanningModeMax, mScanningModeDef;
    protected int mExposureModeMin, mExposureModeMax, mExposureModeDef;
    protected int mExposurePriorityMin, mExposurePriorityMax, mExposurePriorityDef;
    protected int mExposureMin, mExposureMax, mExposureDef;
    protected int mAutoFocusMin, mAutoFocusMax, mAutoFocusDef;
    protected int mFocusMin, mFocusMax, mFocusDef;
    protected int mFocusRelMin, mFocusRelMax, mFocusRelDef;
    protected int mFocusSimpleMin, mFocusSimpleMax, mFocusSimpleDef;
    protected int mIrisMin, mIrisMax, mIrisDef;
    protected int mIrisRelMin, mIrisRelMax, mIrisRelDef;
    protected int mPanMin, mPanMax, mPanDef;
    protected int mTiltMin, mTiltMax, mTiltDef;
    protected int mRollMin, mRollMax, mRollDef;
    protected int mPanRelMin, mPanRelMax, mPanRelDef;
    protected int mTiltRelMin, mTiltRelMax, mTiltRelDef;
    protected int mRollRelMin, mRollRelMax, mRollRelDef;
    protected int mPrivacyMin, mPrivacyMax, mPrivacyDef;
    protected int mAutoWhiteBalanceMin, mAutoWhiteBalanceMax, mAutoWhiteBalanceDef;
    protected int mAutoWhiteBalanceCompoMin, mAutoWhiteBalanceCompoMax, mAutoWhiteBalanceCompoDef;
    protected int mWhiteBalanceMin, mWhiteBalanceMax, mWhiteBalanceDef;
    protected int mWhiteBalanceCompoMin, mWhiteBalanceCompoMax, mWhiteBalanceCompoDef;
    protected int mWhiteBalanceRelMin, mWhiteBalanceRelMax, mWhiteBalanceRelDef;
    protected int mBacklightCompMin, mBacklightCompMax, mBacklightCompDef;
    protected int mBrightnessMin, mBrightnessMax, mBrightnessDef;
    protected int mContrastMin, mContrastMax, mContrastDef;
    protected int mSharpnessMin, mSharpnessMax, mSharpnessDef;
    protected int mGainMin, mGainMax, mGainDef;
    protected int mGammaMin, mGammaMax, mGammaDef;
    protected int mSaturationMin, mSaturationMax, mSaturationDef;
    protected int mHueMin, mHueMax, mHueDef;
    protected int mZoomMin, mZoomMax, mZoomDef;
    protected int mZoomRelMin, mZoomRelMax, mZoomRelDef;
    protected int mPowerlineFrequencyMin, mPowerlineFrequencyMax, mPowerlineFrequencyDef;
    protected int mMultiplierMin, mMultiplierMax, mMultiplierDef;
    protected int mMultiplierLimitMin, mMultiplierLimitMax, mMultiplierLimitDef;
    protected int mAnalogVideoStandardMin, mAnalogVideoStandardMax, mAnalogVideoStandardDef;
    protected int mAnalogVideoLockStateMin, mAnalogVideoLockStateMax, mAnalogVideoLockStateDef;
    // until here

//--------------------------------------------------------------------------------------------------

    static {
        try {
            System.loadLibrary("camera");
            System.loadLibrary("jpeg_turbo_1500");
            System.loadLibrary("usb_100");
            System.loadLibrary("uvc");
            System.loadLibrary("yuv");
        } catch (UnsatisfiedLinkError e) {
            e.printStackTrace();
        }
    }

    /**
     * The constructor of this class should be call within the thread that has a looper
     * (UI thread or a thread that called Looper.prepare)
     */
    public UVCCamera() {
        mNativePtr = nativeCreate();
        mSupportedSize = null;
    }

    /**
     * Connect to a UVC camera
     * USB permission is necessary before this method is called
     *
     * @param ctrlBlock
     */
    public synchronized void open(final USBMonitor.UsbControlBlock ctrlBlock) throws UnsupportedOperationException {
        try {
            mCtrlBlock = ctrlBlock.clone();
            nativeConnect(mNativePtr,
                    mCtrlBlock.getVendorId(), mCtrlBlock.getProductId(),
                    mCtrlBlock.getFileDescriptor(),
                    mCtrlBlock.getBusNum(),
                    mCtrlBlock.getDevNum(),
                    getUSBFSName(mCtrlBlock));
        } catch (Exception e) {
            Logger.w(TAG, e);
            throw new UnsupportedOperationException("open failed:result=-1");
        }
        if (mNativePtr != 0 && TextUtils.isEmpty(mSupportedSize)) {
            mSupportedSize = nativeGetSupportedSize(mNativePtr);
        }
        nativeSetPreviewSize(mNativePtr,
                DEFAULT_PREVIEW_WIDTH, DEFAULT_PREVIEW_HEIGHT,
                DEFAULT_PREVIEW_MIN_FPS, DEFAULT_PREVIEW_MAX_FPS,
                DEFAULT_PREVIEW_MODE, DEFAULT_BANDWIDTH);
    }

    /**
     * set status callback
     *
     * @param callback
     */
    public void setStatusCallback(final IStatusCallback callback) {
        if (mNativePtr != 0) {
            nativeSetStatusCallback(mNativePtr, callback);
        }
    }

    /**
     * set button callback
     *
     * @param callback
     */
    public void setButtonCallback(final IButtonCallback callback) {
        if (mNativePtr != 0) {
            nativeSetButtonCallback(mNativePtr, callback);
        }
    }

    /**
     * close and release UVC camera
     */
    public synchronized void close() {
        stopPreview();
        if (mNativePtr != 0) {
            nativeRelease(mNativePtr);
            //Don't set 0
            //mNativePtr = 0;	
        }
        if (mCtrlBlock != null) {
            mCtrlBlock.close();
            mCtrlBlock = null;
        }
        mControlSupports = mProcSupports = 0;
        mCurrentFrameFormat = -1;
        mCurrentBandwidthFactor = 0;
        mSupportedSize = null;
        mCurrentSizeList = null;
        Logger.v(TAG, "close:finished");
    }

    public UsbDevice getDevice() {
        return mCtrlBlock != null ? mCtrlBlock.getDevice() : null;
    }

    public String getDeviceName() {
        return mCtrlBlock != null ? mCtrlBlock.getDeviceName() : null;
    }

    public USBMonitor.UsbControlBlock getUsbControlBlock() {
        return mCtrlBlock;
    }

    public synchronized String getSupportedSize() {
        return !TextUtils.isEmpty(mSupportedSize) ?
                mSupportedSize : (mSupportedSize = nativeGetSupportedSize(mNativePtr));
    }

    public Size getPreviewSize() {
        Size result = null;
        final List<Size> list = getSupportedSizeList();
        for (final Size sz : list) {
            if ((sz.width == mCurrentWidth) || (sz.height == mCurrentHeight)) {
                result = sz;
                break;
            }
        }
        return result;
    }

    /**
     * Add by hsj
     * The method priority is higher than {@link UVCCamera#setPreviewFlip}
     *
     * @param orientation {@link UVCCamera#ROTATE_90}、{@link UVCCamera#ROTATE_180}、
     *                    {@link UVCCamera#ROTATE_270}、 other value are default {@link UVCCamera#ROTATE_0}
     * @return true is success
     */
    public boolean setPreviewOrientation(int orientation) {
        if (orientation == ROTATE_90 || orientation == ROTATE_270
                || orientation == ROTATE_0 || orientation == ROTATE_180) {
            return nativeSetPreviewOrientation(mNativePtr, orientation) == ACTION_SUCCESS;
        } else {
            return false;
        }
    }

    /**
     * Add by hsj
     * The method priority is lower than {@link UVCCamera#setPreviewOrientation}
     *
     * @param flip -1 is horizontal flip, 1 is vertical flip,other value are not flip
     * @return true is success
     */
    public boolean setPreviewFlip(int flip) {
        if (flip == FLIP_H || flip == FLIP_W || flip == FLIP_DEFAULT) {
            return nativeSetPreviewFlip(mNativePtr, flip) == ACTION_SUCCESS;
        } else {
            return false;
        }
    }

    /**
     * Set preview size and preview mode
     *
     * @param width  frame width
     * @param height frame height
     */
    public void setPreviewSize(final int width, final int height) throws IllegalArgumentException {
        setPreviewSize(width, height, DEFAULT_PREVIEW_MIN_FPS, DEFAULT_PREVIEW_MAX_FPS,
                mCurrentFrameFormat, mCurrentBandwidthFactor);
    }

    /**
     * Set preview size and preview mode
     *
     * @param width       frame width
     * @param height      frame height
     * @param frameFormat either FRAME_FORMAT_YUYV(0) or FRAME_FORMAT_MJPEG(1)
     */
    public void setPreviewSize(final int width, final int height,
                               final int frameFormat) throws IllegalArgumentException {
        setPreviewSize(width, height, DEFAULT_PREVIEW_MIN_FPS, DEFAULT_PREVIEW_MAX_FPS,
                frameFormat, mCurrentBandwidthFactor);
    }

    /**
     * Set preview size and preview mode
     *
     * @param width       frame width
     * @param height      frame height
     * @param frameFormat either FRAME_FORMAT_YUYV(0) or FRAME_FORMAT_MJPEG(1)
     * @param bandwidth   [0.0f,1.0f]
     */
    public void setPreviewSize(final int width, final int height, final int frameFormat,
                               final float bandwidth) throws IllegalArgumentException {
        setPreviewSize(width, height, DEFAULT_PREVIEW_MIN_FPS, DEFAULT_PREVIEW_MAX_FPS,
                frameFormat, bandwidth);
    }

    /**
     * Set preview size and preview mode
     *
     * @param width           frame width
     * @param height          frame height
     * @param min_fps         frame min fps
     * @param max_fps         frame max fps
     * @param frameFormat     either FRAME_FORMAT_YUYV(0) or FRAME_FORMAT_MJPEG(1)
     * @param bandwidthFactor [0.0f,1.0f]
     */
    public void setPreviewSize(final int width, final int height,
                               final int min_fps, final int max_fps, final int frameFormat,
                               final float bandwidthFactor) throws IllegalArgumentException {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("invalid preview size");
        }
        if (mNativePtr != 0) {
            final int result = nativeSetPreviewSize(mNativePtr, width, height,
                    min_fps, max_fps, frameFormat, bandwidthFactor);
            if (result != 0) {
                throw new IllegalArgumentException("Failed to set preview size");
            }
            mCurrentFrameFormat = frameFormat;
            mCurrentWidth = width;
            mCurrentHeight = height;
            mCurrentBandwidthFactor = bandwidthFactor;
        }
    }

    /**
     * getSupportedSizeList
     *
     * @return
     */
    public List<Size> getSupportedSizeList() {
        return getSupportedSize((mCurrentFrameFormat > 0) ? 6 : 4, mSupportedSize);
    }

    /**
     * get Supported Size
     *
     * @param type
     * @param supportedSize
     * @return
     */
    public static List<Size> getSupportedSize(final int type, final String supportedSize) {
        List<Size> result = new ArrayList<>();
        if (!TextUtils.isEmpty(supportedSize))
            try {
                final JSONObject json = new JSONObject(supportedSize);
                final JSONArray formats = json.getJSONArray("formats");
                final int format_nums = formats.length();
                for (int i = 0; i < format_nums; i++) {
                    final JSONObject format = formats.getJSONObject(i);
                    if (format.has("type") && format.has("size")) {
                        final int format_type = format.getInt("type");
                        if ((format_type == type) || (type == -1)) {
                            addSize(format, format_type, 0, result);
                        }
                    }
                }
            } catch (final JSONException e) {
                e.printStackTrace();
            }
        return result;
    }

    /**
     * add Size
     *
     * @param format
     * @param formatType
     * @param frameType
     * @param size_list
     * @throws JSONException
     */
    private static void addSize(final JSONObject format, final int formatType,
                                final int frameType, final List<Size> size_list) throws JSONException {
        final JSONArray size = format.getJSONArray("size");
        final int size_nums = size.length();
        for (int j = 0; j < size_nums; j++) {
            final String[] sz = size.getString(j).split("x");
            try {
                size_list.add(new Size(formatType, frameType, j, Integer.parseInt(sz[0]), Integer.parseInt(sz[1])));
            } catch (final Exception e) {
                break;
            }
        }
    }

    /**
     * set preview surface with SurfaceHolder</br>
     * you can use SurfaceHolder came from SurfaceView/GLSurfaceView
     *
     * @param holder
     */
    public synchronized void setPreviewDisplay(final SurfaceHolder holder) {
        nativeSetPreviewDisplay(mNativePtr, holder.getSurface());
    }

    /**
     * set preview surface with SurfaceTexture.
     * this method require API >= 14
     *
     * @param texture
     */
    public synchronized void setPreviewTexture(final SurfaceTexture texture) {
        nativeSetPreviewDisplay(mNativePtr, new Surface(texture));
    }

    /**
     * set preview surface with Surface
     *
     * @param surface
     */
    public synchronized void setPreviewDisplay(final Surface surface) {
        nativeSetPreviewDisplay(mNativePtr, surface);
    }

    /**
     * set frame callback
     *
     * @param callback
     * @param pixelFormat
     */
    public void setFrameCallback(final IFrameCallback callback, final int pixelFormat) {
        if (mNativePtr != 0) {
            nativeSetFrameCallback(mNativePtr, callback, pixelFormat);
        }
    }

    /**
     * start preview
     */
    public synchronized void startPreview() {
        if (mCtrlBlock != null) {
            nativeStartPreview(mNativePtr);
        }
    }

    /**
     * stop preview
     */
    public synchronized void stopPreview() {
        setFrameCallback(null, 0);
        if (mCtrlBlock != null) {
            nativeStopPreview(mNativePtr);
        }
    }

    /**
     * destroy UVCCamera object
     */
    public synchronized void destroy() {
        close();
        if (mNativePtr != 0) {
            nativeDestroy(mNativePtr);
            mNativePtr = 0;
        }
    }

    // wrong result may return when you call this just after camera open.
    // it is better to wait several hundreads millseconds.
    public boolean checkSupportFlag(final long flag) {
        updateCameraParams();
        if ((flag & 0x80000000) == 0x80000000) {
            return ((mProcSupports & flag) == (flag & 0x7ffffffF));
        } else {
            return (mControlSupports & flag) == flag;
        }
    }

//==================================================================================================

    public synchronized boolean setAutoFocus(final boolean autoFocus) {
        if (mNativePtr != 0) {
            return nativeSetAutoFocus(mNativePtr, autoFocus) > ACTION_SUCCESS;
        } else {
            return false;
        }
    }

    public synchronized boolean getAutoFocus() {
        if (mNativePtr != 0) {
            return nativeGetAutoFocus(mNativePtr) > ACTION_SUCCESS;
        } else {
            return false;
        }
    }

    /**
     * setFocus
     *
     * @param focus [%]
     */
    public synchronized void setFocus(final int focus) {
        if (mNativePtr != 0) {
            final float range = Math.abs(mFocusMax - mFocusMin);
            if (range > 0) {
                nativeSetFocus(mNativePtr, (int) (focus / 100.f * range) + mFocusMin);
            }
        }
    }

    /**
     * getFocus
     *
     * @param focus_abs
     * @return focus[%]
     */
    public synchronized int getFocus(final int focus_abs) {
        int result = 0;
        if (mNativePtr != 0) {
            nativeUpdateFocusLimit(mNativePtr);
            final float range = Math.abs(mFocusMax - mFocusMin);
            if (range > 0) {
                result = (int) ((focus_abs - mFocusMin) * 100.f / range);
            }
        }
        return result;
    }

    /**
     * getFocus
     *
     * @return focus[%]
     */
    public synchronized int getFocus() {
        return getFocus(nativeGetFocus(mNativePtr));
    }

    /**
     * resetFocus
     */
    public synchronized void resetFocus() {
        if (mNativePtr != 0) {
            nativeSetFocus(mNativePtr, mFocusDef);
        }
    }

//==================================================================================================

    /**
     * set Auto White Balance
     *
     * @param autoWhiteBalance
     */
    public synchronized void setAutoWhiteBalance(final boolean autoWhiteBalance) {
        if (mNativePtr != 0) {
            nativeSetAutoWhiteBalance(mNativePtr, autoWhiteBalance);
        }
    }

    /**
     * get Auto White Balance
     *
     * @return
     */
    public synchronized boolean getAutoWhiteBalance() {
        if (mNativePtr != 0) {
            return nativeGetAutoWhiteBalance(mNativePtr) > ACTION_SUCCESS;
        } else {
            return false;
        }
    }

    /**
     * setWhiteBalance
     *
     * @param whiteBalance [%]
     */
    public synchronized void setWhiteBalance(final int whiteBalance) {
        if (mNativePtr != 0) {
            final float range = Math.abs(mWhiteBalanceMax - mWhiteBalanceMin);
            if (range > 0) {
                nativeSetWhiteBalance(mNativePtr, (int) (whiteBalance / 100.f * range) + mWhiteBalanceMin);
            }
        }
    }

    /**
     * getWhiteBalance
     *
     * @param whiteBalance_abs
     * @return whiteBalance[%]
     */
    public synchronized int getWhiteBalance(final int whiteBalance_abs) {
        int result = 0;
        if (mNativePtr != 0) {
            nativeUpdateWhiteBalanceLimit(mNativePtr);
            final float range = Math.abs(mWhiteBalanceMax - mWhiteBalanceMin);
            if (range > 0) {
                result = (int) ((whiteBalance_abs - mWhiteBalanceMin) * 100.f / range);
            }
        }
        return result;
    }

    /**
     * getWhiteBalance
     *
     * @return white balance[%]
     */
    public synchronized int getWhiteBalance() {
        return getFocus(nativeGetWhiteBalance(mNativePtr));
    }

    /**
     * resetWhiteBalance
     */
    public synchronized void resetWhiteBalance() {
        if (mNativePtr != 0) {
            nativeSetWhiteBalance(mNativePtr, mWhiteBalanceDef);
        }
    }

//==================================================================================================

    /**
     * @param brightness [%]
     */
    public synchronized void setBrightness(final int brightness) {
        if (mNativePtr != 0) {
            final float range = Math.abs(mBrightnessMax - mBrightnessMin);
            if (range > 0) {
                nativeSetBrightness(mNativePtr, (int) (brightness / 100.f * range) + mBrightnessMin);
            }
        }
    }

    /**
     * @param brightness_abs
     * @return brightness[%]
     */
    public synchronized int getBrightness(final int brightness_abs) {
        int result = 0;
        if (mNativePtr != 0) {
            nativeUpdateBrightnessLimit(mNativePtr);
            final float range = Math.abs(mBrightnessMax - mBrightnessMin);
            if (range > 0) {
                result = (int) ((brightness_abs - mBrightnessMin) * 100.f / range);
            }
        }
        return result;
    }

    /**
     * @return brightness[%]
     */
    public synchronized int getBrightness() {
        return getBrightness(nativeGetBrightness(mNativePtr));
    }

    public synchronized void resetBrightness() {
        if (mNativePtr != 0) {
            nativeSetBrightness(mNativePtr, mBrightnessDef);
        }
    }

//==================================================================================================

    /**
     * set Contrast
     *
     * @param contrast [%]
     */
    public synchronized void setContrast(final int contrast) {
        if (mNativePtr != 0) {
            nativeUpdateContrastLimit(mNativePtr);
            final float range = Math.abs(mContrastMax - mContrastMin);
            if (range > 0) {
                nativeSetContrast(mNativePtr, (int) (contrast / 100.f * range) + mContrastMin);
            }
        }
    }

    /**
     * get Contrast
     *
     * @param contrast_abs
     * @return contrast[%]
     */
    public synchronized int getContrast(final int contrast_abs) {
        int result = 0;
        if (mNativePtr != 0) {
            final float range = Math.abs(mContrastMax - mContrastMin);
            if (range > 0) {
                result = (int) ((contrast_abs - mContrastMin) * 100.f / range);
            }
        }
        return result;
    }

    /**
     * get Contrast
     *
     * @return contrast[%]
     */
    public synchronized int getContrast() {
        return getContrast(nativeGetContrast(mNativePtr));
    }

    /**
     * reset Contrast
     */
    public synchronized void resetContrast() {
        if (mNativePtr != 0) {
            nativeSetContrast(mNativePtr, mContrastDef);
        }
    }

//==================================================================================================

    /**
     * set Sharpness
     *
     * @param sharpness [%]
     */
    public synchronized void setSharpness(final int sharpness) {
        if (mNativePtr != 0) {
            final float range = Math.abs(mSharpnessMax - mSharpnessMin);
            if (range > 0) {
                nativeSetSharpness(mNativePtr, (int) (sharpness / 100.f * range) + mSharpnessMin);
            }
        }
    }

    /**
     * get Sharpness
     *
     * @param sharpness_abs
     * @return sharpness[%]
     */
    public synchronized int getSharpness(final int sharpness_abs) {
        int result = 0;
        if (mNativePtr != 0) {
            nativeUpdateSharpnessLimit(mNativePtr);
            final float range = Math.abs(mSharpnessMax - mSharpnessMin);
            if (range > 0) {
                result = (int) ((sharpness_abs - mSharpnessMin) * 100.f / range);
            }
        }
        return result;
    }

    /**
     * get Sharpness
     *
     * @return sharpness[%]
     */
    public synchronized int getSharpness() {
        return getSharpness(nativeGetSharpness(mNativePtr));
    }

    /**
     * reset Sharpness
     */
    public synchronized void resetSharpness() {
        if (mNativePtr != 0) {
            nativeSetSharpness(mNativePtr, mSharpnessDef);
        }
    }

//==================================================================================================

    /**
     * set Gain
     *
     * @param gain [%]
     */
    public synchronized void setGain(final int gain) {
        if (mNativePtr != 0) {
            final float range = Math.abs(mGainMax - mGainMin);
            if (range > 0) {
                nativeSetGain(mNativePtr, (int) (gain / 100.f * range) + mGainMin);
            }
        }
    }

    /**
     * get Gain
     *
     * @param gain_abs
     * @return gain[%]
     */
    public synchronized int getGain(final int gain_abs) {
        int result = 0;
        if (mNativePtr != 0) {
            nativeUpdateGainLimit(mNativePtr);
            final float range = Math.abs(mGainMax - mGainMin);
            if (range > 0) {
                result = (int) ((gain_abs - mGainMin) * 100.f / range);
            }
        }
        return result;
    }

    /**
     * get Gain
     *
     * @return gain[%]
     */
    public synchronized int getGain() {
        return getGain(nativeGetGain(mNativePtr));
    }

    /**
     * reset Gain
     */
    public synchronized void resetGain() {
        if (mNativePtr != 0) {
            nativeSetGain(mNativePtr, mGainDef);
        }
    }

//==================================================================================================

    /**
     * set Gamma
     *
     * @param gamma [%]
     */
    public synchronized void setGamma(final int gamma) {
        if (mNativePtr != 0) {
            final float range = Math.abs(mGammaMax - mGammaMin);
            if (range > 0) {
                nativeSetGamma(mNativePtr, (int) (gamma / 100.f * range) + mGammaMin);
            }
        }
    }

    /**
     * get Gamma
     *
     * @param gamma_abs
     * @return gamma[%]
     */
    public synchronized int getGamma(final int gamma_abs) {
        int result = 0;
        if (mNativePtr != 0) {
            nativeUpdateGammaLimit(mNativePtr);
            final float range = Math.abs(mGammaMax - mGammaMin);
            if (range > 0) {
                result = (int) ((gamma_abs - mGammaMin) * 100.f / range);
            }
        }
        return result;
    }

    /**
     * get Gamma
     *
     * @return gamma[%]
     */
    public synchronized int getGamma() {
        return getGamma(nativeGetGamma(mNativePtr));
    }

    /**
     * reset Gamma
     */
    public synchronized void resetGamma() {
        if (mNativePtr != 0) {
            nativeSetGamma(mNativePtr, mGammaDef);
        }
    }

//==================================================================================================

    /**
     * set Saturation
     *
     * @param saturation [%]
     */
    public synchronized void setSaturation(final int saturation) {
        if (mNativePtr != 0) {
            final float range = Math.abs(mSaturationMax - mSaturationMin);
            if (range > 0) {
                nativeSetSaturation(mNativePtr, (int) (saturation / 100.f * range) + mSaturationMin);
            }
        }
    }

    /**
     * get Saturation
     *
     * @param saturation_abs
     * @return saturation[%]
     */
    public synchronized int getSaturation(final int saturation_abs) {
        int result = 0;
        if (mNativePtr != 0) {
            nativeUpdateSaturationLimit(mNativePtr);
            final float range = Math.abs(mSaturationMax - mSaturationMin);
            if (range > 0) {
                result = (int) ((saturation_abs - mSaturationMin) * 100.f / range);
            }
        }
        return result;
    }

    /**
     * get Saturation
     *
     * @return saturation[%]
     */
    public synchronized int getSaturation() {
        return getSaturation(nativeGetSaturation(mNativePtr));
    }

    /**
     * reset Saturation
     */
    public synchronized void resetSaturation() {
        if (mNativePtr != 0) {
            nativeSetSaturation(mNativePtr, mSaturationDef);
        }
    }

//==================================================================================================

    /**
     * set Hue
     *
     * @param hue [%]
     */
    public synchronized void setHue(final int hue) {
        if (mNativePtr != 0) {
            final float range = Math.abs(mHueMax - mHueMin);
            if (range > 0) {
                nativeSetHue(mNativePtr, (int) (hue / 100.f * range) + mHueMin);
            }
        }
    }

    /**
     * get Hue
     *
     * @param hue_abs
     * @return hue[%]
     */
    public synchronized int getHue(final int hue_abs) {
        int result = 0;
        if (mNativePtr != 0) {
            nativeUpdateHueLimit(mNativePtr);
            final float range = Math.abs(mHueMax - mHueMin);
            if (range > 0) {
                result = (int) ((hue_abs - mHueMin) * 100.f / range);
            }
        }
        return result;
    }

    /**
     * set Hue
     *
     * @return hue[%]
     */
    public synchronized int getHue() {
        return getHue(nativeGetHue(mNativePtr));
    }

    /**
     * reset Hue
     */
    public synchronized void resetHue() {
        if (mNativePtr != 0) {
            nativeSetHue(mNativePtr, mSaturationDef);
        }
    }

//==================================================================================================

    /**
     * set Power line Frequency
     *
     * @param frequency
     */
    public void setPowerlineFrequency(final int frequency) {
        if (mNativePtr != 0) {
            nativeSetPowerlineFrequency(mNativePtr, frequency);
        }
    }

    /**
     * get Power line Frequency
     *
     * @return
     */
    public int getPowerlineFrequency() {
        return nativeGetPowerlineFrequency(mNativePtr);
    }

//==================================================================================================

    /**
     * this may not work well with some combination of camera and device
     *
     * @param zoom [%]
     */
    public synchronized void setZoom(final int zoom) {
        if (mNativePtr != 0) {
            final float range = Math.abs(mZoomMax - mZoomMin);
            if (range > 0) {
                final int z = (int) (zoom / 100.f * range) + mZoomMin;
                Logger.i(TAG, "setZoom:zoom=" + zoom + " ,value=" + z);
                nativeSetZoom(mNativePtr, z);
            }
        }
    }

    /**
     * @param zoom_abs
     * @return zoom[%]
     */
    public synchronized int getZoom(final int zoom_abs) {
        int result = 0;
        if (mNativePtr != 0) {
            nativeUpdateZoomLimit(mNativePtr);
            final float range = Math.abs(mZoomMax - mZoomMin);
            if (range > 0) {
                result = (int) ((zoom_abs - mZoomMin) * 100.f / range);
            }
        }
        return result;
    }

    /**
     * @return zoom[%]
     */
    public synchronized int getZoom() {
        return getZoom(nativeGetZoom(mNativePtr));
    }

    /**
     * reset Zoom
     */
    public synchronized void resetZoom() {
        if (mNativePtr != 0) {
            nativeSetZoom(mNativePtr, mZoomDef);
        }
    }

//==================================================================================================

    /**
     * Set exposure mode
     *
     * @param exposureMode exposure mode
     */
    public synchronized void setExposureMode(int exposureMode) {
        if (this.mNativePtr != 0L) {
            nativeSetExposureMode(this.mNativePtr, exposureMode);
        }
    }

    /**
     * Get exposure mode
     *
     * @return exposure mode
     */
    public synchronized int getExposureMode() {
        return nativeGetExposureMode(this.mNativePtr);
    }

    /**
     * set exposure level (must numerical correspondence with usb camera sensor driver)
     *
     * @param exposureLevel for usb camera sensor driver
     */
    public synchronized void setExposureLevel(int exposureLevel) {
        if (this.mNativePtr != 0L) {
            Logger.i(TAG, "setExposureValue = " + exposureLevel);
            nativeSetExposure(this.mNativePtr, exposureLevel);
        }
    }

    /**
     * get exposure level
     *
     * @return
     */
    public synchronized int getExposureLevel() {
        return nativeGetExposure(this.mNativePtr);
    }

//==================================================================================================

    /**
     * update Camera Params
     */
    public synchronized void updateCameraParams() {
        if (mNativePtr != 0) {
            if ((mControlSupports == 0) || (mProcSupports == 0)) {
                // サポートしている機能フラグを取得
                if (mControlSupports == 0) {
                    mControlSupports = nativeGetCtrlSupports(mNativePtr);
                }
                if (mProcSupports == 0) {
                    mProcSupports = nativeGetProcSupports(mNativePtr);
                }
                // 設定値を取得
                if ((mControlSupports != 0) && (mProcSupports != 0)) {
                    nativeUpdateBrightnessLimit(mNativePtr);
                    nativeUpdateContrastLimit(mNativePtr);
                    nativeUpdateSharpnessLimit(mNativePtr);
                    nativeUpdateGainLimit(mNativePtr);
                    nativeUpdateGammaLimit(mNativePtr);
                    nativeUpdateSaturationLimit(mNativePtr);
                    nativeUpdateHueLimit(mNativePtr);
                    nativeUpdateZoomLimit(mNativePtr);
                    nativeUpdateWhiteBalanceLimit(mNativePtr);
                    nativeUpdateFocusLimit(mNativePtr);
                    //增加曝光 add by hsj
                    nativeUpdateExposureLimit(mNativePtr);
                    nativeUpdateExposureModeLimit(mNativePtr);
                    nativeUpdateExposurePriorityLimit(mNativePtr);
                }
                if (BuildConfig.DEBUG) {
                    dumpControls(mControlSupports);
                    dumpProc(mProcSupports);
                    Logger.v(TAG, String.format("Brightness:min=%d,max=%d,def=%d", mBrightnessMin, mBrightnessMax, mBrightnessDef));
                    Logger.v(TAG, String.format("Contrast:min=%d,max=%d,def=%d", mContrastMin, mContrastMax, mContrastDef));
                    Logger.v(TAG, String.format("Sharpness:min=%d,max=%d,def=%d", mSharpnessMin, mSharpnessMax, mSharpnessDef));
                    Logger.v(TAG, String.format("Gain:min=%d,max=%d,def=%d", mGainMin, mGainMax, mGainDef));
                    Logger.v(TAG, String.format("Gamma:min=%d,max=%d,def=%d", mGammaMin, mGammaMax, mGammaDef));
                    Logger.v(TAG, String.format("Saturation:min=%d,max=%d,def=%d", mSaturationMin, mSaturationMax, mSaturationDef));
                    Logger.v(TAG, String.format("Hue:min=%d,max=%d,def=%d", mHueMin, mHueMax, mHueDef));
                    Logger.v(TAG, String.format("Zoom:min=%d,max=%d,def=%d", mZoomMin, mZoomMax, mZoomDef));
                    Logger.v(TAG, String.format("WhiteBalance:min=%d,max=%d,def=%d", mWhiteBalanceMin, mWhiteBalanceMax, mWhiteBalanceDef));
                    Logger.v(TAG, String.format("Focus:min=%d,max=%d,def=%d", mFocusMin, mFocusMax, mFocusDef));
                }
            }
        } else {
            mControlSupports = mProcSupports = 0;
        }
    }

    private static final String[] SUPPORTS_CTRL = {
            "D0:  Scanning Mode",
            "D1:  Auto-Exposure Mode",
            "D2:  Auto-Exposure Priority",
            "D3:  Exposure Time (Absolute)",
            "D4:  Exposure Time (Relative)",
            "D5:  Focus (Absolute)",
            "D6:  Focus (Relative)",
            "D7:  Iris (Absolute)",
            "D8:  Iris (Relative)",
            "D9:  Zoom (Absolute)",
            "D10: Zoom (Relative)",
            "D11: PanTilt (Absolute)",
            "D12: PanTilt (Relative)",
            "D13: Roll (Absolute)",
            "D14: Roll (Relative)",
            "D15: Reserved",
            "D16: Reserved",
            "D17: Focus, Auto",
            "D18: Privacy",
            "D19: Focus, Simple",
            "D20: Window",
            "D21: Region of Interest",
            "D22: Reserved, set to zero",
            "D23: Reserved, set to zero",
    };

    private static final String[] SUPPORTS_PROC = {
            "D0: Brightness",
            "D1: Contrast",
            "D2: Hue",
            "D3: Saturation",
            "D4: Sharpness",
            "D5: Gamma",
            "D6: White Balance Temperature",
            "D7: White Balance Component",
            "D8: Backlight Compensation",
            "D9: Gain",
            "D10: Power Line Frequency",
            "D11: Hue, Auto",
            "D12: White Balance Temperature, Auto",
            "D13: White Balance Component, Auto",
            "D14: Digital Multiplier",
            "D15: Digital Multiplier Limit",
            "D16: Analog Video Standard",
            "D17: Analog Video Lock Status",
            "D18: Contrast, Auto",
            "D19: Reserved. Set to zero",
            "D20: Reserved. Set to zero",
            "D21: Reserved. Set to zero",
            "D22: Reserved. Set to zero",
            "D23: Reserved. Set to zero",
    };

    private static void dumpControls(final long controlSupports) {
        Logger.i(TAG, String.format("controlSupports=%x", controlSupports));
        for (int i = 0; i < SUPPORTS_CTRL.length; i++) {
            Logger.i(TAG, SUPPORTS_CTRL[i] + ((controlSupports & (0x1 << i)) != 0 ? "=enabled" : "=disabled"));
        }
    }

    private static void dumpProc(final long procSupports) {
        Logger.i(TAG, String.format("procSupports=%x", procSupports));
        for (int i = 0; i < SUPPORTS_PROC.length; i++) {
            Logger.i(TAG, SUPPORTS_PROC[i] + ((procSupports & (0x1 << i)) != 0 ? "=enabled" : "=disabled"));
        }
    }

    private String getUSBFSName(final USBMonitor.UsbControlBlock ctrlBlock) {
        String result = null;
        final String name = ctrlBlock.getDeviceName();
        final String[] v = !TextUtils.isEmpty(name) ? name.split("/") : null;
        if ((v != null) && (v.length > 2)) {
            StringBuilder sb = new StringBuilder(v[0]);
            for (int i = 1; i < v.length - 2; i++) {
                sb.append("/").append(v[i]);
            }
            result = sb.toString();
        }
        if (TextUtils.isEmpty(result)) {
            Logger.w(TAG, "failed to get USBFS path, try to use default path:" + name);
            result = DEFAULT_USBFS;
        }
        return result;
    }

//==================================================================================================

    //nativeCreate is not static methods.
    private native long nativeCreate();

    //nativeDestroy is not static methods.
    private native void nativeDestroy(final long id_camera);

    private native int nativeConnect(long id_camera, int venderId, int productId, int fileDescriptor, int busNum, int devAddr, String usbfs);

    private static native int nativeRelease(final long id_camera);

    private static native int nativeSetStatusCallback(final long mNativePtr, final IStatusCallback callback);

    private static native int nativeSetButtonCallback(final long mNativePtr, final IButtonCallback callback);

    private static native int nativeSetPreviewSize(final long id_camera, final int width, final int height, final int min_fps, final int max_fps, final int mode, final float bandwidth);

    private static native String nativeGetSupportedSize(final long id_camera);

    private static native int nativeStartPreview(final long id_camera);

    private static native int nativeStopPreview(final long id_camera);

    private static native int nativeSetPreviewOrientation(final long id_camera, final int orientation);

    private static native int nativeSetPreviewFlip(final long id_camera, final int flipH);

    private static native int nativeSetPreviewDisplay(final long id_camera, final Surface surface);

    private static native int nativeSetFrameCallback(final long mNativePtr, final IFrameCallback callback, final int pixelFormat);

//==================================================================================================

    /**
     * start movie capturing(this should call while previewing)
     *
     * @param surface
     */
    public void startCapture(final Surface surface) throws NullPointerException {
        if (mCtrlBlock == null || surface == null) {
            throw new NullPointerException("startCapture");
        } else {
            nativeSetCaptureDisplay(mNativePtr, surface);
        }
    }

    /**
     * stop movie capturing
     */
    public void stopCapture() {
        if (mCtrlBlock != null) {
            nativeSetCaptureDisplay(mNativePtr, null);
        }
    }

//==================================================================================================

    private static native int nativeSetCaptureDisplay(final long id_camera, final Surface surface);

    private static native long nativeGetCtrlSupports(final long id_camera);

    private static native long nativeGetProcSupports(final long id_camera);

    private native int nativeUpdateScanningModeLimit(final long id_camera);

    private static native int nativeSetScanningMode(final long id_camera, final int scanning_mode);

    private static native int nativeGetScanningMode(final long id_camera);

    private native int nativeUpdateExposureModeLimit(final long id_camera);

    private static native int nativeSetExposureMode(final long id_camera, final int exposureMode);

    private static native int nativeGetExposureMode(final long id_camera);

    private native int nativeUpdateExposurePriorityLimit(final long id_camera);

    private static native int nativeSetExposurePriority(final long id_camera, final int priority);

    private static native int nativeGetExposurePriority(final long id_camera);

    private native int nativeUpdateExposureLimit(final long id_camera);

    private static native int nativeSetExposure(final long id_camera, final int exposure);

    private static native int nativeGetExposure(final long id_camera);

    private native int nativeUpdateExposureRelLimit(final long id_camera);

    private static native int nativeSetExposureRel(final long id_camera, final int exposure_rel);

    private static native int nativeGetExposureRel(final long id_camera);

    private native int nativeUpdateAutoFocusLimit(final long id_camera);

    private static native int nativeSetAutoFocus(final long id_camera, final boolean autofocus);

    private static native int nativeGetAutoFocus(final long id_camera);

    private native int nativeUpdateFocusLimit(final long id_camera);

    private static native int nativeSetFocus(final long id_camera, final int focus);

    private static native int nativeGetFocus(final long id_camera);

    private native int nativeUpdateFocusRelLimit(final long id_camera);

    private static native int nativeSetFocusRel(final long id_camera, final int focus_rel);

    private static native int nativeGetFocusRel(final long id_camera);

    private native int nativeUpdateIrisLimit(final long id_camera);

    private static native int nativeSetIris(final long id_camera, final int iris);

    private static native int nativeGetIris(final long id_camera);

    private native int nativeUpdateIrisRelLimit(final long id_camera);

    private static native int nativeSetIrisRel(final long id_camera, final int iris_rel);

    private static native int nativeGetIrisRel(final long id_camera);

    private native int nativeUpdatePanLimit(final long id_camera);

    private static native int nativeSetPan(final long id_camera, final int pan);

    private static native int nativeGetPan(final long id_camera);

    private native int nativeUpdatePanRelLimit(final long id_camera);

    private static native int nativeSetPanRel(final long id_camera, final int pan_rel);

    private static native int nativeGetPanRel(final long id_camera);

    private native int nativeUpdateTiltLimit(final long id_camera);

    private static native int nativeSetTilt(final long id_camera, final int tilt);

    private static native int nativeGetTilt(final long id_camera);

    private native int nativeUpdateTiltRelLimit(final long id_camera);

    private static native int nativeSetTiltRel(final long id_camera, final int tilt_rel);

    private static native int nativeGetTiltRel(final long id_camera);

    private native int nativeUpdateRollLimit(final long id_camera);

    private static native int nativeSetRoll(final long id_camera, final int roll);

    private static native int nativeGetRoll(final long id_camera);

    private native int nativeUpdateRollRelLimit(final long id_camera);

    private static native int nativeSetRollRel(final long id_camera, final int roll_rel);

    private static native int nativeGetRollRel(final long id_camera);

    private native int nativeUpdateAutoWhiteBalanceLimit(final long id_camera);

    private static native int nativeSetAutoWhiteBalance(final long id_camera, final boolean autoWhiteBalance);

    private static native int nativeGetAutoWhiteBalance(final long id_camera);

    private native int nativeUpdateAutoWhiteBalanceCompoLimit(final long id_camera);

    private static native int nativeSetAutoWhiteBalanceCompo(final long id_camera, final boolean autoWhiteBalanceCompo);

    private static native int nativeGetAutoWhiteBalanceCompo(final long id_camera);

    private native int nativeUpdateWhiteBalanceLimit(final long id_camera);

    private static native int nativeSetWhiteBalance(final long id_camera, final int whiteBalance);

    private static native int nativeGetWhiteBalance(final long id_camera);

    private native int nativeUpdateWhiteBalanceCompoLimit(final long id_camera);

    private static native int nativeSetWhiteBalanceCompo(final long id_camera, final int whiteBalance_compo);

    private static native int nativeGetWhiteBalanceCompo(final long id_camera);

    private native int nativeUpdateBacklightCompLimit(final long id_camera);

    private static native int nativeSetBacklightComp(final long id_camera, final int backlight_comp);

    private static native int nativeGetBacklightComp(final long id_camera);

    private native int nativeUpdateBrightnessLimit(final long id_camera);

    private static native int nativeSetBrightness(final long id_camera, final int brightness);

    private static native int nativeGetBrightness(final long id_camera);

    private native int nativeUpdateContrastLimit(final long id_camera);

    private static native int nativeSetContrast(final long id_camera, final int contrast);

    private static native int nativeGetContrast(final long id_camera);

    private native int nativeUpdateAutoContrastLimit(final long id_camera);

    private static native int nativeSetAutoContrast(final long id_camera, final boolean autocontrast);

    private static native int nativeGetAutoContrast(final long id_camera);

    private native int nativeUpdateSharpnessLimit(final long id_camera);

    private static native int nativeSetSharpness(final long id_camera, final int sharpness);

    private static native int nativeGetSharpness(final long id_camera);

    private native int nativeUpdateGainLimit(final long id_camera);

    private static native int nativeSetGain(final long id_camera, final int gain);

    private static native int nativeGetGain(final long id_camera);

    private native int nativeUpdateGammaLimit(final long id_camera);

    private static native int nativeSetGamma(final long id_camera, final int gamma);

    private static native int nativeGetGamma(final long id_camera);

    private native int nativeUpdateSaturationLimit(final long id_camera);

    private static native int nativeSetSaturation(final long id_camera, final int saturation);

    private static native int nativeGetSaturation(final long id_camera);

    private native int nativeUpdateHueLimit(final long id_camera);

    private static native int nativeSetHue(final long id_camera, final int hue);

    private static native int nativeGetHue(final long id_camera);

    private native int nativeUpdateAutoHueLimit(final long id_camera);

    private static native int nativeSetAutoHue(final long id_camera, final boolean autohue);

    private static native int nativeGetAutoHue(final long id_camera);

    private native int nativeUpdatePowerlineFrequencyLimit(final long id_camera);

    private static native int nativeSetPowerlineFrequency(final long id_camera, final int frequency);

    private static native int nativeGetPowerlineFrequency(final long id_camera);

    private native int nativeUpdateZoomLimit(final long id_camera);

    private static native int nativeSetZoom(final long id_camera, final int zoom);

    private static native int nativeGetZoom(final long id_camera);

    private native int nativeUpdateZoomRelLimit(final long id_camera);

    private static native int nativeSetZoomRel(final long id_camera, final int zoom_rel);

    private static native int nativeGetZoomRel(final long id_camera);

    private native int nativeUpdateDigitalMultiplierLimit(final long id_camera);

    private static native int nativeSetDigitalMultiplier(final long id_camera, final int multiplier);

    private static native int nativeGetDigitalMultiplier(final long id_camera);

    private native int nativeUpdateDigitalMultiplierLimitLimit(final long id_camera);

    private static native int nativeSetDigitalMultiplierLimit(final long id_camera, final int multiplier_limit);

    private static native int nativeGetDigitalMultiplierLimit(final long id_camera);

    private native int nativeUpdateAnalogVideoStandardLimit(final long id_camera);

    private static native int nativeSetAnalogVideoStandard(final long id_camera, final int standard);

    private static native int nativeGetAnalogVideoStandard(final long id_camera);

    private native int nativeUpdateAnalogVideoLockStateLimit(final long id_camera);

    private static native int nativeSetAnalogVideoLoackState(final long id_camera, final int state);

    private static native int nativeGetAnalogVideoLoackState(final long id_camera);

    private native int nativeUpdatePrivacyLimit(final long id_camera);

    private static native int nativeSetPrivacy(final long id_camera, final boolean privacy);

    private static native int nativeGetPrivacy(final long id_camera);
}

