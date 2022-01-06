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
public final class MainActivity extends AppCompatActivity implements Handler.Callback, SurfaceHolder.Callback {

    private static final String TAG = "MainActivity";
    //TODO Set your usb camera display width and height
    private static int PREVIEW_WIDTH = 640;
    private static int PREVIEW_HEIGHT = 480;

    private static final int CAMERA_CREATE = 1;
    private static final int CAMERA_PREVIEW = 2;
    private static final int CAMERA_START = 3;
    private static final int CAMERA_STOP = 4;
    private static final int CAMERA_DESTROY = 5;

    private int index;
    private Context context;
    private LinearLayout ll_action;
    private USBMonitor mUSBMonitor;
    private Handler cameraHandler;
    private HandlerThread cameraThread;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        SurfaceView sv = findViewById(R.id.sv);
        sv.setZOrderOnTop(true);
        sv.setZOrderMediaOverlay(true);
        sv.getHolder().addCallback(this);

        this.ll_action = findViewById(R.id.ll_action);
        this.context = getApplicationContext();
        this.cameraThread = new HandlerThread("thread_uvc_camera");
        this.cameraThread.start();
        this.cameraHandler = new Handler(cameraThread.getLooper(), this);

        if (hasPermissions(Manifest.permission.CAMERA)) {
            createUsbMonitor();
        }
    }

    @Override
    protected void onDestroy() {
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
        super.onDestroy();
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

//==========================================Menu====================================================

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.item_camera) {
            showSingleChoiceDialog();
        }
        return super.onOptionsItemSelected(item);
    }

    private void showSingleChoiceDialog() {
        if (this.mUSBMonitor == null) {
            showToast("Wait USBMonitor created success");
        } else {
            List<UsbDevice> deviceList = mUSBMonitor.getDeviceList();
            if (deviceList.size() == 0) {
                showToast("No Usb Camera to be found");
            } else {
                //Close Usb Camera
                this.ll_action.setVisibility(View.GONE);
                this.cameraHandler.obtainMessage(CAMERA_DESTROY).sendToTarget();
                String[] items = new String[deviceList.size()];
                for (int i = 0; i < deviceList.size(); ++i) {
                    UsbDevice device = deviceList.get(i);
                    items[i] = device.getProductName();
                }
                AlertDialog.Builder selectDialog = new AlertDialog.Builder(this);
                selectDialog.setTitle(R.string.select_camera);
                selectDialog.setSingleChoiceItems(items, index, (dialog, which) -> {
                    index = which;
                });
                selectDialog.setPositiveButton(R.string.btn_confirm, (dialog, which) -> {
                    this.ll_action.setVisibility(View.VISIBLE);
                    UsbDevice device = deviceList.get(index);
                    if (this.mUSBMonitor.hasPermission(device)){
                        USBMonitor.UsbControlBlock ctrlBlock = this.mUSBMonitor.openDevice(device);
                        this.cameraHandler.obtainMessage(CAMERA_CREATE, ctrlBlock).sendToTarget();
                    }else {
                        this.mUSBMonitor.requestPermission(device);
                    }
                });
                selectDialog.show();
            }
        }
    }

//==========================================Button Click============================================

    public void startPreview(View view) {
        cameraHandler.obtainMessage(CAMERA_START).sendToTarget();
    }

    public void stopPreview(View view) {
        cameraHandler.obtainMessage(CAMERA_STOP).sendToTarget();
    }

    public void destroyCamera(View view) {
        cameraHandler.obtainMessage(CAMERA_DESTROY).sendToTarget();
    }

//===================================Surface========================================================

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        Log.d(TAG, "->surfaceCreated");
        cameraHandler.obtainMessage(CAMERA_PREVIEW, holder.getSurface()).sendToTarget();
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {

    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        Log.d(TAG, "->surfaceDestroyed");
        cameraHandler.obtainMessage(CAMERA_DESTROY, holder.getSurface()).sendToTarget();
    }

//====================================UsbDevice Status==============================================

    private final USBMonitor.OnDeviceConnectListener dcl = new USBMonitor.OnDeviceConnectListener() {
        @Override
        public void onAttach(UsbDevice device) {
            Log.d(TAG, "Usb->onAttach->" + device.getProductId());
        }

        @Override
        public void onConnect(UsbDevice device, USBMonitor.UsbControlBlock ctrlBlock, boolean createNew) {
            Log.d(TAG, "Usb->onConnect->" + device.getProductId());
            cameraHandler.obtainMessage(CAMERA_CREATE, ctrlBlock).sendToTarget();
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
    private UVCCamera camera;

    @Override
    public boolean handleMessage(@NonNull Message msg) {
        switch (msg.what) {
            case CAMERA_CREATE:
                initCamera((USBMonitor.UsbControlBlock) msg.obj);
                break;
            case CAMERA_PREVIEW:
                setSurface((Surface) msg.obj);
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

    private void initCamera(@NonNull USBMonitor.UsbControlBlock block) {
        long t = System.currentTimeMillis();
        if (camera != null) {
            destroyCamera();
            camera = null;
        }
        Log.d(TAG, "camera create start");
        try {
            camera = new UVCCamera();
            camera.open(block);
            camera.setPreviewRotate(UVCCamera.PREVIEW_ROTATE.ROTATE_90);
            camera.setPreviewFlip(UVCCamera.PREVIEW_FLIP.FLIP_H);
            checkSupportSize(camera);
            camera.setPreviewSize(PREVIEW_WIDTH, PREVIEW_HEIGHT,
                    UVCCamera.FRAME_FORMAT_YUYV, 1.0f);
        } catch (UnsupportedOperationException | IllegalArgumentException e) {
            e.printStackTrace();
            camera.destroy();
            camera = null;
            return;
        }
        Log.d(TAG, "camera create time=" + (System.currentTimeMillis() - t));
        if (surface != null) {
            startCamera();
        }
    }

    private void checkSupportSize(UVCCamera mCamera) {
        List<Size> sizes = mCamera.getSupportedSizeList();
        //Most UsbCamera support 640x480
        //A few UsbCamera may fail to obtain the supported resolution
        if (sizes == null || sizes.size() == 0) return;
        Log.d(TAG, mCamera.getSupportedSize());
        boolean isSupport = false;
        for (Size size : sizes) {
            if (size.width == PREVIEW_WIDTH && size.height == PREVIEW_HEIGHT) {
                isSupport = true;
                break;
            }
        }
        if (!isSupport) {
            //Use intermediate support size
            Size size = sizes.get(sizes.size() / 2);
            PREVIEW_WIDTH = size.width;
            PREVIEW_HEIGHT = size.height;
        }
        Log.d(TAG, String.format("SupportSize->with=%d,height=%d", PREVIEW_WIDTH, PREVIEW_HEIGHT));
    }

    private void setSurface(Surface surface) {
        this.surface = surface;
        if (isStart) {
            stopCamera();
            startCamera();
        } else if (camera != null) {
            startCamera();
        }
    }

    private void startCamera() {
        long start = System.currentTimeMillis();
        if (!isStart && camera != null) {
            isStart = true;
            if (surface != null) {
                //Call this method when you need show preview
                Log.d(TAG, "setPreviewDisplay()");
                camera.setPreviewDisplay(surface);
            }
            //TODO Camera frame callback
            camera.setFrameCallback(frame -> {
                Log.d(TAG,"frameSize="+frame.capacity());
                //saveFile("/sdcard/640x400.NV21",frame);
            }, UVCCamera.PIXEL_FORMAT_NV21);
            camera.startPreview();
        }
        Log.d(TAG, "camera start time=" + (System.currentTimeMillis() - start));
    }

    private void stopCamera() {
        long start = System.currentTimeMillis();
        if (isStart && camera != null) {
            isStart = false;
            camera.stopPreview();
        }
        Log.d(TAG, "camera stop time=" + (System.currentTimeMillis() - start));
    }

    private void destroyCamera() {
        long start = System.currentTimeMillis();
        stopCamera();
        if (camera != null) {
            camera.destroy();
            camera = null;
        }
        Log.d(TAG, "camera destroy time=" + (System.currentTimeMillis() - start));
    }

//=========================================Utils Action=============================================

    public static boolean saveFile(String dstFile, ByteBuffer data) {
        boolean result = true;
        FileChannel fc = null;
        try {
            fc = new FileOutputStream(dstFile).getChannel();
            fc.write(data);
        } catch (IOException e) {
            e.printStackTrace();
            result = false;
        } finally {
            ioClose(fc);
        }
        return result;
    }

    private static void ioClose(Closeable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}


