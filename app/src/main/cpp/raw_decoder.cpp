// Minimal self-contained DNG / RAW decoder bridge.
//
// Strategy: we do NOT bundle libraw (heavy, license/NDK complexity). Instead we
// hand raw DNG bytes to Android's own BitmapFactory / ImageDecoder on the Kotlin
// side for standard DNGs (Android 10+ can often decode DNG via the media stack),
// and this native layer provides a fast linear-light "develop" pass that the
// Kotlin pipeline calls for basic demosaic-free normalization.
//
// This file intentionally keeps the native surface tiny and dependency-free so
// it always compiles in CI. Real sensor demosaic can be added later.

#include <jni.h>
#include <android/log.h>
#include <cstdint>
#include <vector>

#define LOG_TAG "RawDecoder"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

// Applies a global exposure gain and clamps into 8-bit RGBA output.
// `src` is RGBA8888 (as produced by BitmapFactory for a DNG or a plain image).
extern "C" JNIEXPORT jobject JNICALL
Java_com_raweditor_app_RawDecoder_nativeDevelop(
        JNIEnv *env,
        jobject /* this */,
        jobject srcBitmap,
        jfloat exposure,
        jfloat contrast) {

    // We operate on the Java Bitmap via JNI-less approach: the Kotlin side passes
    // a Bitmap, we read its pixels through JNI calls.
    jclass bitmapClass = env->GetObjectClass(srcBitmap);
    jmethodID getWidth = env->GetMethodID(bitmapClass, "getWidth", "()I");
    jmethodID getHeight = env->GetMethodID(bitmapClass, "getHeight", "()I");
    jmethodID getPixels = env->GetMethodID(bitmapClass, "getPixels", "([IIIIIII)V");

    jint width = env->CallIntMethod(srcBitmap, getWidth);
    jint height = env->CallIntMethod(srcBitmap, getHeight);

    jintArray pixels = env->NewIntArray(width * height);
    env->CallVoidMethod(srcBitmap, getPixels, pixels, 0, width, 0, 0, width, height);

    jint *buf = env->GetIntArrayElements(pixels, nullptr);
    if (buf == nullptr) {
        env->DeleteLocalRef(pixels);
        return srcBitmap;
    }

    for (int i = 0; i < width * height; i++) {
        jint px = buf[i];
        int a = (px >> 24) & 0xFF;
        int r = (px >> 16) & 0xFF;
        int g = (px >> 8) & 0xFF;
        int b = px & 0xFF;

        auto apply = [&](int c) -> int {
            float v = c / 255.0f;
            v *= exposure;
            // S-curve-ish contrast around 0.5
            v = (v - 0.5f) * contrast + 0.5f;
            if (v < 0.0f) v = 0.0f;
            if (v > 1.0f) v = 1.0f;
            return (int) (v * 255.0f + 0.5f);
        };

        r = apply(r);
        g = apply(g);
        b = apply(b);
        buf[i] = (a << 24) | (r << 16) | (g << 8) | b;
    }

    env->ReleaseIntArrayElements(pixels, buf, 0);

    jclass bitmapConfig = env->FindClass("android/graphics/Bitmap$Config");
    jmethodID valueOf = env->GetStaticMethodID(
            bitmapConfig, "valueOf",
            "(Ljava/lang/String;)Landroid/graphics/Bitmap$Config;");
    jstring argb = env->NewStringUTF("ARGB_8888");
    jobject config = env->CallStaticObjectMethod(bitmapConfig, valueOf, argb);

    jclass bmpClass = env->FindClass("android/graphics/Bitmap");
    jmethodID createBitmap = env->GetStaticMethodID(
            bmpClass, "createBitmap",
            "(IILandroid/graphics/Bitmap$Config;)Landroid/graphics/Bitmap;");

    jobject out = env->CallStaticObjectMethod(
            bmpClass, createBitmap, width, height, config);

    jmethodID setPixels = env->GetMethodID(bmpClass, "setPixels", "([IIIIIII)V");
    env->CallVoidMethod(out, setPixels, pixels, 0, width, 0, 0, width, height);

    env->DeleteLocalRef(argb);
    env->DeleteLocalRef(pixels);
    return out;
}
