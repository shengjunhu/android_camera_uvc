package com.serenegiant.usb;

import java.nio.ByteBuffer;

/**
 * Callback interface for UVCCamera class
 * If you need frame data as ByteBuffer, you can use this callback interface with UVCCamera#setFrameCallback
 */
public interface IFrameCallback {
	/**
	 * This method is called from native library via JNI on the same thread as UVCCamera#startCapture.
	 * You can use both UVCCamera#startCapture and #setFrameCallback
	 * but it is better to use either for better performance.
	 * You can also pass pixel format type to UVCCamera#setFrameCallback for this method.
	 * Some frames may drops if this method takes a time.
	 * When you use some color format like NV21, this library never execute color space conversion,
	 * just execute pixel format conversion. If you want to get same result as on screen, please try to
	 * consider to get images via texture(SurfaceTexture) and read pixel buffer from it using OpenGL|ES2/3
	 * instead of using IFrameCallback(this way is much efficient in most case than using IFrameCallback).
	 * @param frame this is direct ByteBuffer from JNI layer and you should handle it's byte order and limitation.
	 */
	void onFrame(ByteBuffer frame);
}
