LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)


LOCAL_SRC_FILES := myapp.cpp \

LOCAL_MODULE := myapp

LOCAL_LDLIBS += -llog

include ${ANDROID_GRADLE_NATIVE_BUNDLE_PLUGIN_MK} #must followed by "include $(BUILD_SHARED_LIBRARY)" or "include $(BUILD_STATIC_LIBRARY)"
include $(BUILD_SHARED_LIBRARY)

