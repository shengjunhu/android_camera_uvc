package com.hsj.sample.uvc;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.SurfaceTexture;
import android.hardware.usb.UsbDevice;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.util.Log;
import android.view.Surface;
import android.view.TextureView;
import android.widget.Toast;

import com.serenegiant.usb.IFrameCallback;
import com.serenegiant.usb.Logger;
import com.serenegiant.usb.USBMonitor;
import com.serenegiant.usb.UVCCamera;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import java.nio.ByteBuffer;

/**
 * @Author:hsj
 * @Date:2020-06-22 16:50
 * @Class:MainActivity
 * @Desc:Sample of UVCCamera
 */
public final class MainActivity extends AppCompatActivity implements TextureView.SurfaceTextureListener,
        Handler.Callback, USBMonitor.OnDeviceConnectListener {

    private static final String TAG = "MainActivity";

    private static final int CAMERA_ID_RGB = 12384;
    private static final int RGB_PREVIEW_WIDTH = 640;
    private static final int RGB_PREVIEW_HEIGHT = 480;

    private static final int CAMERA_CREATE = 1;
    private static final int CAMERA_START = 2;
    private static final int CAMERA_STOP = 3;
    private static final int CAMERA_DESTROY = 4;

    private Surface surface;
    private SurfaceTexture surfaceTexture;

    private Handler cameraHandler;
    private HandlerThread cameraThread;

    private boolean isStartRGB;
    private UVCCamera rgbCamera;
    private USBMonitor mUSBMonitor;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        TextureView tv = findViewById(R.id.tv);
        tv.setSurfaceTextureListener(this);

        if (hasPermissions(Manifest.permission.CAMERA)) {
            this.mUSBMonitor = new USBMonitor(this, this);
            this.mUSBMonitor.register();
        }

        this.cameraThread = new HandlerThread("camera_uvc_thread");
        this.cameraThread.start();
        this.cameraHandler = new Handler(cameraThread.getLooper(), this);

        findViewById(R.id.btn_start).setOnClickListener(v ->
                cameraHandler.obtainMessage(CAMERA_START, surfaceTexture).sendToTarget());
        findViewById(R.id.btn_stop).setOnClickListener(v ->
                cameraHandler.obtainMessage(CAMERA_STOP).sendToTarget());
    }

    public boolean hasPermissions(String... permissions) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true;
        if (permissions == null || permissions.length == 0) return true;
        boolean allGranted = true;
        for (String permission : permissions) {
            if (ActivityCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                allGranted = false;
                //弹窗申请
                ActivityCompat.requestPermissions(this, permissions, 0x01);
            }
        }
        return allGranted;
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (mUSBMonitor != null && !mUSBMonitor.isRegistered()) {
            mUSBMonitor.register();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (cameraHandler != null) {
            cameraHandler.obtainMessage(CAMERA_DESTROY).sendToTarget();
        }
        if (mUSBMonitor != null && mUSBMonitor.isRegistered()) {
            mUSBMonitor.unregister();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mUSBMonitor != null) {
            mUSBMonitor.destroy();
        }
        if (cameraThread != null) {
            cameraThread.quitSafely();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        boolean hasAllPermissions = true;
        for (int granted : grantResults) {
            hasAllPermissions &= (granted == PackageManager.PERMISSION_GRANTED);
        }
        if (hasAllPermissions) {
            this.mUSBMonitor = new USBMonitor(this, this);
            this.mUSBMonitor.register();
        }
    }

//===================================SurfaceTexture=================================================

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture st, int width, int height) {
        Log.i(TAG, "onSurfaceTextureAvailable");
        this.surfaceTexture = st;
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture st, int width, int height) {
        Log.i(TAG, "onSurfaceTextureSizeChanged");
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture st) {
        //Log.i(TAG, "onSurfaceTextureUpdated");
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture st) {
        Log.i(TAG, "onSurfaceTextureDestroyed");
        return false;
    }

//====================================Usb Status====================================================

    @Override
    public void onAttach(UsbDevice device) {
        int productId = device.getProductId();
        Logger.i(TAG, "Usb productId = " + productId);
        if (productId == CAMERA_ID_RGB) {
            Toast.makeText(this, "Usb device attach->" + productId, Toast.LENGTH_LONG).show();
            if (mUSBMonitor != null) {
                mUSBMonitor.requestPermission(device);
            }
        }
    }

    @Override
    public void onConnect(UsbDevice device, USBMonitor.UsbControlBlock block, boolean createNew) {
        Toast.makeText(this, "Usb device connect", Toast.LENGTH_LONG).show();
        int productId = device.getProductId();
        if (productId == CAMERA_ID_RGB) {
            Message msg = cameraHandler.obtainMessage(CAMERA_CREATE);
            msg.what = CAMERA_CREATE;
            msg.arg1 = productId;
            msg.obj = block;
            msg.sendToTarget();
        }
    }

    @Override
    public void onDisconnect(UsbDevice device, USBMonitor.UsbControlBlock ctrlBlock) {
        Toast.makeText(this, "Usb device disconnect", Toast.LENGTH_LONG).show();
        if (cameraHandler != null) {
            cameraHandler.obtainMessage(CAMERA_DESTROY).sendToTarget();
        }
    }

    @Override
    public void onCancel(UsbDevice device) {
        Toast.makeText(this, "Usb device cancel", Toast.LENGTH_LONG).show();
    }

    @Override
    public void onDetach(UsbDevice device) {
        Toast.makeText(this, "Usb device detach", Toast.LENGTH_LONG).show();
    }

//=====================================UVCCamera Action=============================================

    @Override
    public boolean handleMessage(@NonNull Message msg) {
        switch (msg.what) {
            case CAMERA_CREATE:
                initCameraRGB((USBMonitor.UsbControlBlock) msg.obj);
                break;
            case CAMERA_START:
                startCameraRGB((SurfaceTexture) msg.obj);
                break;
            case CAMERA_STOP:
                stopCameraRGB();
                break;
            case CAMERA_DESTROY:
                destroyCameraRGB();
                break;
            default:
                break;
        }
        return true;
    }

    private void initCameraRGB(@NonNull USBMonitor.UsbControlBlock blockRGB) {
        long t = System.currentTimeMillis();
        if (rgbCamera != null) {
            stopCameraRGB();
            destroyCameraRGB();
        }
        try {
            rgbCamera = new UVCCamera();
            rgbCamera.open(blockRGB);
            Logger.i(TAG, "rgb camera supported size = " + rgbCamera.getSupportedSize());
            rgbCamera.setPreviewSize(RGB_PREVIEW_WIDTH, RGB_PREVIEW_HEIGHT,
                    UVCCamera.FRAME_FORMAT_MJPEG, 1.0f);
        } catch (Exception e) {
            e.printStackTrace();
        }
        Log.i(TAG, "rgb camera create time=" + (System.currentTimeMillis() - t));
    }

    private void startCameraRGB(SurfaceTexture surfaceTexture) {
        long t = System.currentTimeMillis();
        if (rgbCamera != null && !isStartRGB) {
            isStartRGB = true;
            if (surfaceTexture != null) {
                surface = new Surface(surfaceTexture);
                rgbCamera.setPreviewDisplay(surface);
            }
            rgbCamera.setFrameCallback(bb -> {
            }, UVCCamera.PIXEL_FORMAT_RAW);
            rgbCamera.startPreview();
        }
        Log.i(TAG, "rgb camera start time=" + (System.currentTimeMillis() - t));
    }

    private void stopCameraRGB() {
        long t = System.currentTimeMillis();
        if (rgbCamera != null && isStartRGB) {
            isStartRGB = false;
            rgbCamera.stopPreview();
            if (surface != null) {
                surface.release();
            }
        }
        Log.i(TAG, "rgb camera stop time=" + (System.currentTimeMillis() - t));
    }

    private void destroyCameraRGB() {
        long t = System.currentTimeMillis();
        if (rgbCamera != null) {
            rgbCamera.close();
            rgbCamera.destroy();
            rgbCamera = null;
        }
        Log.i(TAG, "rgb camera destroy time=" + (System.currentTimeMillis() - t));
    }

}
