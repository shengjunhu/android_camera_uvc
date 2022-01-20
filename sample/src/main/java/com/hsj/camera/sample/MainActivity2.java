package com.hsj.camera.sample;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.usb.UsbDevice;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.hsj.camera.Size;
import com.hsj.camera.USBMonitor;
import com.hsj.camera.UVCCamera;

import java.io.Closeable;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.List;

/**
 * @Author:Hsj
 * @Date:2020-06-22
 * @Class:MainActivity
 * @Desc: Sample of UVCCamera
 */
public final class MainActivity2 extends AppCompatActivity implements Handler.Callback {

    private static final String TAG = "MainActivity2";
    //TODO Set your usb camera display width and height
    private static final int PREVIEW_WIDTH = 1920;
    private static final int PREVIEW_HEIGHT = 1080;

    private static final int CAMERA_CREATE = 1;
    private static final int CAMERA_CREATE2 = 2;
    private static final int CAMERA_START = 3;
    private static final int CAMERA_STOP = 4;
    private static final int CAMERA_DESTROY = 5;

    private Context context;
    private USBMonitor mUSBMonitor;
    private Handler cameraHandler;
    private HandlerThread cameraThread;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main2);

        SurfaceView sv = findViewById(R.id.sv);
        sv.getHolder().addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder holder) {
                surface = holder.getSurface();
            }

            @Override
            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {

            }

            @Override
            public void surfaceDestroyed(SurfaceHolder holder) {
                surface = null;
            }
        });

        SurfaceView sv2 = findViewById(R.id.sv2);
        sv2.getHolder().addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder holder) {
                surface2 = holder.getSurface();
            }

            @Override
            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {

            }

            @Override
            public void surfaceDestroyed(SurfaceHolder holder) {
                surface2 = null;
            }
        });

        this.context = getApplicationContext();
        this.cameraThread = new HandlerThread("thread_uvc_camera");
        this.cameraThread.start();
        this.cameraHandler = new Handler(cameraThread.getLooper(), this);

        if (hasPermissions(Manifest.permission.CAMERA)) {
            createUsbMonitor();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (cameraHandler != null) {
            cameraHandler.obtainMessage(CAMERA_STOP).sendToTarget();
        }
    }

    @Override
    protected void onDestroy() {
        if (cameraHandler != null) {
            cameraHandler.obtainMessage(CAMERA_DESTROY).sendToTarget();
        }
        super.onDestroy();
        if (mUSBMonitor != null) {
            mUSBMonitor.destroy();
            mUSBMonitor = null;
        }
        if (cameraThread != null) {
            try {
                cameraThread.join(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            cameraThread = null;
        }
        System.exit(0);
        Log.d(TAG, "activity destroy");
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        boolean hasAllPermissions = true;
        for (int granted : grantResults) {
            hasAllPermissions &= (granted == PackageManager.PERMISSION_GRANTED);
        }
        if (hasAllPermissions) {
            createUsbMonitor();
        }
    }

    private void createUsbMonitor() {
        this.mUSBMonitor = new USBMonitor(context, dcl);
        this.mUSBMonitor.register();
    }

    private void showToast(@NonNull String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    private boolean hasPermissions(String... permissions) {
        if (permissions == null || permissions.length == 0) return true;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true;
        boolean allGranted = true;
        for (String permission : permissions) {
            if (ActivityCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                allGranted = false;
                ActivityCompat.requestPermissions(this, permissions, 0);
            }
        }
        return allGranted;
    }

//====================================UsbDevice Status==============================================

    private final USBMonitor.OnDeviceConnectListener dcl = new USBMonitor.OnDeviceConnectListener() {
        @Override
        public void onAttach(UsbDevice device) {
            Log.d(TAG, "Usb->onAttach->" + device.getProductId());
            if (device.getProductId() == 0x8801) {
                 mUSBMonitor.requestPermission(device);
                 mUSBMonitor.hasPermission(device);
            } else if (device.getProductId() == 0x8802) {
                mUSBMonitor.requestPermission(device);
                mUSBMonitor.hasPermission(device);
            }
        }

        @Override
        public void onConnect(UsbDevice device, USBMonitor.UsbControlBlock ctrlBlock, boolean createNew) {
            Log.d(TAG, "Usb->onConnect->" + device.getProductId());
            if (device.getProductId() == 0x8801) {
                cameraHandler.obtainMessage(CAMERA_CREATE, ctrlBlock).sendToTarget();
            } else if (device.getProductId() == 0x8802) {
                cameraHandler.obtainMessage(CAMERA_CREATE2, ctrlBlock).sendToTarget();
            }
        }

        @Override
        public void onDisconnect(UsbDevice device, USBMonitor.UsbControlBlock ctrlBlock) {
            Log.d(TAG, "Usb->onDisconnect->" + device.getProductId());
        }

        @Override
        public void onCancel(UsbDevice device) {
            Log.d(TAG, "Usb->onCancel->" + device.getProductId());
        }

        @Override
        public void onDetach(UsbDevice device) {
            Log.d(TAG, "Usb->onDetach->" + device.getProductId());
        }
    };

//=====================================UVCCamera Action=============================================

    private boolean isStart;
    private Surface surface;
    private Surface surface2;
    private UVCCamera camera;
    private UVCCamera camera2;

    @Override
    public boolean handleMessage(@NonNull Message msg) {
        switch (msg.what) {
            case CAMERA_CREATE:
                createCamera((USBMonitor.UsbControlBlock) msg.obj);
                break;
            case CAMERA_CREATE2:
                createCamera2((USBMonitor.UsbControlBlock) msg.obj);
                break;
            case CAMERA_START:
                startCamera();
                break;
            case CAMERA_STOP:
                stopCamera();
                break;
            case CAMERA_DESTROY:
                destroyCamera();
                break;
            default:
                break;
        }
        return true;
    }

    private void createCamera(@NonNull USBMonitor.UsbControlBlock block) {
        if (camera == null) {
            try {
                camera = new UVCCamera();
                camera.open(block);
                //camera.setPreviewRotate(UVCCamera.PREVIEW_ROTATE.ROTATE_90);
                //camera.setPreviewFlip(UVCCamera.PREVIEW_FLIP.FLIP_H);
                camera.setPreviewSize(PREVIEW_WIDTH, PREVIEW_HEIGHT,
                        UVCCamera.FRAME_FORMAT_MJPEG, 0.5f);
            } catch (UnsupportedOperationException | IllegalArgumentException e) {
                e.printStackTrace();
                destroyCamera();
                return;
            }
            Log.e(TAG, "createCamera");
            if (surface != null && surface2 != null && camera2 != null) {
                startCamera();
            }
        }
    }

    private void createCamera2(@NonNull USBMonitor.UsbControlBlock block) {
        if (camera2 == null) {
            try {
                camera2 = new UVCCamera();
                camera2.open(block);
                //camera.setPreviewRotate(UVCCamera.PREVIEW_ROTATE.ROTATE_90);
                //camera.setPreviewFlip(UVCCamera.PREVIEW_FLIP.FLIP_H);
                camera2.setPreviewSize(PREVIEW_WIDTH, PREVIEW_HEIGHT,
                        UVCCamera.FRAME_FORMAT_MJPEG, 0.5f);
            } catch (UnsupportedOperationException | IllegalArgumentException e) {
                e.printStackTrace();
                destroyCamera();
                return;
            }
            Log.e(TAG, "createCamera2");
            if (surface != null && surface2 != null && camera != null) {
                startCamera();
            }
        }
    }

    private void startCamera() {
        if (!isStart) {
            isStart = true;
            if (camera != null) {
                if (surface != null) camera.setPreviewDisplay(surface);
                camera.startPreview();
            }
            if (camera2 != null) {
                if (surface2 != null) camera2.setPreviewDisplay(surface2);
                camera2.startPreview();
            }
        }
    }

    private void stopCamera() {
        if (isStart) {
            isStart = false;
            if (camera != null) camera.stopPreview();
            if (camera2 != null) camera2.stopPreview();
        }
    }

    private void destroyCamera() {
        if (camera != null) {
            camera.destroy();
            camera = null;
        }
        if (camera2 != null) {
            camera2.destroy();
            camera2 = null;
        }
    }

}


