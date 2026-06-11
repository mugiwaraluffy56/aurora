#include <jni.h>

extern "C" JNIEXPORT jstring JNICALL
Java_com_aurora_cinema_core_nativebridge_NativeCore_engineName(JNIEnv* env, jobject) {
    return env->NewStringUTF("aurora_native");
}
