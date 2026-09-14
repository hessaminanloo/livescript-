// JNI bridge for ai.z.livescript.stt.WhisperTranscriber.
// Mirrors the pattern used by whisper.cpp's own examples/whisper.android sample:
// https://github.com/ggerganov/whisper.cpp/tree/master/examples/whisper.android
//
// This file intentionally stays small — all the heavy lifting is whisper.cpp's own
// whisper_full() call. Error handling is deliberately defensive since this runs on
// arbitrary user devices/ABIs.

#include <jni.h>
#include <android/log.h>
#include <string>
#include <vector>
#include "whisper.h"

#define LOG_TAG "whisper_jni"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

extern "C" JNIEXPORT jlong JNICALL
Java_ai_z_livescript_stt_WhisperTranscriber_nativeInit(JNIEnv *env, jobject /*thiz*/, jstring modelPath) {
    const char *path = env->GetStringUTFChars(modelPath, nullptr);

    whisper_context_params cparams = whisper_context_default_params();
    // Set to true if the build links a GPU/NNAPI backend; CPU-only is the safe default.
    cparams.use_gpu = false;

    struct whisper_context *ctx = whisper_init_from_file_with_params(path, cparams);
    env->ReleaseStringUTFChars(modelPath, path);

    if (ctx == nullptr) {
        LOGE("Failed to load whisper model");
        return 0;
    }
    return reinterpret_cast<jlong>(ctx);
}

extern "C" JNIEXPORT jstring JNICALL
Java_ai_z_livescript_stt_WhisperTranscriber_nativeTranscribe(
        JNIEnv *env, jobject /*thiz*/, jlong contextPtr, jfloatArray samples, jstring language) {

    auto *ctx = reinterpret_cast<struct whisper_context *>(contextPtr);
    if (ctx == nullptr) return env->NewStringUTF("");

    jsize n = env->GetArrayLength(samples);
    std::vector<float> pcm(n);
    env->GetFloatArrayRegion(samples, 0, n, pcm.data());

    const char *lang = env->GetStringUTFChars(language, nullptr);

    whisper_full_params wparams = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    wparams.language = lang;
    wparams.translate = false;
    wparams.print_progress = false;
    wparams.print_special = false;
    wparams.print_realtime = false;
    wparams.single_segment = true;      // we feed one already-segmented Vosk utterance at a time
    wparams.n_threads = 4;

    int rc = whisper_full(ctx, wparams, pcm.data(), (int) pcm.size());
    env->ReleaseStringUTFChars(language, lang);

    if (rc != 0) {
        LOGE("whisper_full failed rc=%d", rc);
        return env->NewStringUTF("");
    }

    std::string result;
    const int nSegments = whisper_full_n_segments(ctx);
    for (int i = 0; i < nSegments; i++) {
        result += whisper_full_get_segment_text(ctx, i);
    }
    return env->NewStringUTF(result.c_str());
}

extern "C" JNIEXPORT void JNICALL
Java_ai_z_livescript_stt_WhisperTranscriber_nativeFree(JNIEnv * /*env*/, jobject /*thiz*/, jlong contextPtr) {
    auto *ctx = reinterpret_cast<struct whisper_context *>(contextPtr);
    if (ctx != nullptr) {
        whisper_free(ctx);
    }
}
