#include <jni.h>
#include <android/log.h>
#include <string>

#define LOG_TAG "LRHQ_Translator"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_livingroomhq_translate_TranslationEngine_nativeLoadModel(
    JNIEnv *env, jobject, jstring, jstring) {
    LOGI("CTranslate2 native model loading not available on this platform");
    return 0;
}

JNIEXPORT void JNICALL
Java_com_livingroomhq_translate_TranslationEngine_nativeRelease(
    JNIEnv *, jobject, jlong) {}

JNIEXPORT jstring JNICALL
Java_com_livingroomhq_translate_TranslationEngine_nativeTranslate(
    JNIEnv *env, jobject, jlong, jobjectArray, jint) {
    return env->NewStringUTF("");
}

JNIEXPORT void JNICALL
Java_com_livingroomhq_translate_TranslationEngine_nativeUnload(
    JNIEnv *, jobject, jlong) {}

}
