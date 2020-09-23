# android_sample_uvccamera
android sample of uvccamera,
developed based on the [saki4510t/UVCCamera](https://github.com/saki4510t/UVCCamera)

### Image
<img src="doc/file/screenshot_1.png" width="200" height="320"/>
<img src="doc/file/screenshot_2.png" width="200" height="320"/>
<img src="doc/file/screenshot_3.png" width="200" height="320"/>

<figure class="third">
    <img src="doc/file/screenshot_1.png">
    <img src="doc/file/screenshot_2.png">
    <img src="doc/file/screenshot_3.png">
</figure>

### APK file
![LOGO](doc/file/android_logo.png)
[APK](doc/file/UsbCamera_v20092316.apk)

### Add Function

* 1-Add UVCCamera API with previewRotate(int rotate);
```
public boolean setPreviewRotate(@PREVIEW_ROTATE int rotate)
```

* 2-Add UVCCamera API with previewFlip(int flipH);
```
public boolean setPreviewFlip(@PREVIEW_FLIP int flip)
```

### Fix bug

* 1-fix memory leak on addCaptureFrame(uvc_frame_t *frame) of UVCpreview.cpp
```
void UVCPreview::addCaptureFrame(uvc_frame_t *frame) {
	pthread_mutex_lock(&capture_mutex);
	if (LIKELY(isRunning())) {
		// keep only latest one
		if (captureQueu) {
			recycle_frame(captureQueu);
		}
		captureQueu = frame;
		pthread_cond_broadcast(&capture_sync);
	}else{
	    //Add By Hsj
	    recycle_frame(frame);
	}
	pthread_mutex_unlock(&capture_mutex);
}
```

* 2-fix end of pthread_join() for stopPreview() of UVCPreview.cpp
```
int UVCPreview::stopPreview() {
    bool b = isRunning();
    if (LIKELY(b)) {
       mIsRunning = false;
        //Add lock for fix pthread_join() can't be end
       pthread_mutex_lock(&preview_mutex);
       pthread_cond_signal(&preview_sync);
       pthread_mutex_unlock(&preview_mutex);
       pthread_cond_signal(&capture_sync);
       if (pthread_join(capture_thread, NULL) != EXIT_SUCCESS) {
           LOGW("UVCPreview::terminate capture thread: pthread_join failed");
       }
       if (pthread_join(preview_thread, NULL) != EXIT_SUCCESS) {
           LOGW("UVCPreview::terminate preview thread: pthread_join failed");
       }
       clearDisplay();
    }
    ...
}
```

* 3-fix NullPointerException for do_capture_callback() of UVCPreview.cpp
```
void UVCPreview::do_capture_callback(JNIEnv *env, uvc_frame_t *frame) {
    ...
    //mFrameCallbackObj or iframecallback_fields.onFrame maybe null
    if (isCapturing()) {
        jobject buf = env->NewDirectByteBuffer(callback_frame->data, callbackPixelBytes);
        env->CallVoidMethod(mFrameCallbackObj, iframecallback_fields.onFrame, buf);
        env->DeleteLocalRef(buf);
    }
    ...
}
```

### About Author:
* Author: shengjunhu
* Date  : 2020/5/1
* E-Mail: shengjunhu@foxmail.com
* GitHub: https://github.com/hushengjun

### About License
```
Copyright (c) 2020 shengjunhu
Please compliance with the UVCCamera license
```
