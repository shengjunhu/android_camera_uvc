# android_sample_uvccamera
android sample of uvccamera,
developed based on the [saki4510t/UVCCamera](https://github.com/saki4510t/UVCCamera)

### Improve
* 1-fix memery leak on addCaptureFrame(uvc_frame_t *frame) of UVCpreview.cpp
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
