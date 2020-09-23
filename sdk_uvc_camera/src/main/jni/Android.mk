PRO_PATH := $(call my-dir)
include $(CLEAR_VARS)
include $(PRO_PATH)/libyuv/Android.mk
include $(PRO_PATH)/libjpeg-turbo-150/Android.mk
include $(PRO_PATH)/libusb/android/jni/Android.mk
include $(PRO_PATH)/libuvc/Android.mk
include $(PRO_PATH)/libcamera/Android.mk
