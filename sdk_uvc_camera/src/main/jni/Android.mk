#include $(call all-subdir-makefiles)
LOCAL_PATH	:= $(call my-dir)
include $(CLEAR_VARS)
include $(LOCAL_PATH)/UVCCamera/Android.mk
include $(LOCAL_PATH)/libjpeg-turbo-1.5.0/Android.mk
include $(LOCAL_PATH)/libusb/android/jni/Android.mk
include $(LOCAL_PATH)/libuvc/android/jni/Android.mk