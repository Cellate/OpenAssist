#include <jni.h>
#include <string>
#include <vector>
#include <android/log.h>
#include "whisper.h"

#define TAG "WhisperJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_openassist_app_WhisperBridge_initContext(
        JNIEnv *env,
        jobject /* thiz */,
        jstring model_path) {
    if (!model_path) return 0;
    const char *path = env->GetStringUTFChars(model_path, nullptr);
    struct whisper_context_params cparams = whisper_context_default_params();
    struct whisper_context *ctx = whisper_init_from_file_with_params(path, cparams);
    env->ReleaseStringUTFChars(model_path, path);

    if (!ctx) {
        LOGE("Failed to initialize whisper context from %s", path ? path : "null");
        return 0;
    }
    LOGI("Successfully initialized whisper context from %s", path);
    return reinterpret_cast<jlong>(ctx);
}

JNIEXPORT void JNICALL
Java_com_openassist_app_WhisperBridge_freeContext(
        JNIEnv *env,
        jobject /* thiz */,
        jlong context_ptr) {
    auto *ctx = reinterpret_cast<struct whisper_context *>(context_ptr);
    if (ctx) {
        whisper_free(ctx);
        LOGI("Freed whisper context");
    }
}

JNIEXPORT jstring JNICALL
Java_com_openassist_app_WhisperBridge_fullTranscribe(
        JNIEnv *env,
        jobject /* thiz */,
        jlong context_ptr,
        jfloatArray samples,
        jstring language) {
    auto *ctx = reinterpret_cast<struct whisper_context *>(context_ptr);
    if (!ctx) {
        return env->NewStringUTF("");
    }

    jsize n_samples = env->GetArrayLength(samples);
    jfloat *sample_data = env->GetFloatArrayElements(samples, nullptr);
    const char *lang_str = language ? env->GetStringUTFChars(language, nullptr) : "en";

    whisper_full_params wparams = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    wparams.print_realtime = false;
    wparams.print_progress = false;
    wparams.print_timestamps = false;
    wparams.print_special = false;
    wparams.translate = false;
    wparams.language = lang_str;
    wparams.n_threads = 4;

    int ret = whisper_full(ctx, wparams, sample_data, n_samples);

    env->ReleaseFloatArrayElements(samples, sample_data, JNI_ABORT);
    if (language) {
        env->ReleaseStringUTFChars(language, lang_str);
    }

    if (ret != 0) {
        LOGE("whisper_full failed with code: %d", ret);
        return env->NewStringUTF("");
    }

    std::string result_text = "";
    int n_segments = whisper_full_n_segments(ctx);
    for (int i = 0; i < n_segments; ++i) {
        const char *text = whisper_full_get_segment_text(ctx, i);
        if (text) {
            result_text += text;
        }
    }

    LOGI("Transcription result length: %zu", result_text.length());
    return env->NewStringUTF(result_text.c_str());
}

// Aliases for com.example.whispermodels
JNIEXPORT jlong JNICALL
Java_com_example_whispermodels_WhisperBridge_initContext(
        JNIEnv *env, jobject thiz, jstring model_path) {
    return Java_com_openassist_app_WhisperBridge_initContext(env, thiz, model_path);
}

JNIEXPORT void JNICALL
Java_com_example_whispermodels_WhisperBridge_freeContext(
        JNIEnv *env, jobject thiz, jlong context_ptr) {
    Java_com_openassist_app_WhisperBridge_freeContext(env, thiz, context_ptr);
}

JNIEXPORT jstring JNICALL
Java_com_example_whispermodels_WhisperBridge_fullTranscribe(
        JNIEnv *env, jobject thiz, jlong context_ptr, jfloatArray samples, jstring language) {
    return Java_com_openassist_app_WhisperBridge_fullTranscribe(env, thiz, context_ptr, samples, language);
}

}
