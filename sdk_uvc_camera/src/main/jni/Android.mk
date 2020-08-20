#include $(call all-subdir-makefiles)
PRO_PATH	:= $(call my-dir)
include $(CLEAR_VARS)
include $(PRO_PATH)/libjpeg-turbo-1.5.0/Android.mk
include $(PRO_PATH)/libusb/android/jni/Android.mk
include $(PRO_PATH)/libuvc/android/jni/Android.mk
include $(PRO_PATH)/libcamera/Android.mk
include $(PRO_PATH)/libyuv/Android.mk
