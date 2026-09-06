// JNI bridge for Dictator's on-device Whisper dictation engine.
#include <jni.h>
#include <string>
#include <vector>

#include "transcribe.hpp"
#include "whisper.h"

namespace {

jstring toJString(JNIEnv * env, const std::string & value) {
    return env->NewStringUTF(value.c_str());
}

jlong throwAndReturn(JNIEnv * env, const std::string & message) {
    jclass cls = env->FindClass("java/lang/IllegalStateException");
    if (cls != nullptr) {
        env->ThrowNew(cls, message.c_str());
    }
    return 0;
}

} // namespace

extern "C" JNIEXPORT jlong JNICALL
Java_io_jyri_dictator_speech_WhisperSttEngine_nativeCreate(
    JNIEnv * env,
    jobject /* thunk */,
    jstring modelPath,
    jint /* threads */) {
    const char * path = env->GetStringUTFChars(modelPath, nullptr);
    if (path == nullptr) {
        return 0;
    }
    whisper_context_params contextParams = whisper_context_default_params();
    struct whisper_context * context = whisper_init_from_file_with_params(path, contextParams);
    env->ReleaseStringUTFChars(modelPath, path);
    if (context == nullptr) {
        throwAndReturn(env, "could not load the Whisper model file");
        return 0;
    }
    return reinterpret_cast<jlong>(context);
}

extern "C" JNIEXPORT jstring JNICALL
Java_io_jyri_dictator_speech_WhisperSttEngine_nativeTranscribe(
    JNIEnv * env,
    jobject /* thunk */,
    jlong handle,
    jfloatArray pcm,
    jint threads,
    jstring language,
    jstring allowedLanguages) {
    if (handle == 0) {
        throwAndReturn(env, "the Whisper engine is closed");
        return nullptr;
    }
    const char * lang = env->GetStringUTFChars(language, nullptr);
    if (lang == nullptr) {
        // GetStringUTFChars already left an OutOfMemoryError pending.
        return nullptr;
    }
    const char * allowed = env->GetStringUTFChars(allowedLanguages, nullptr);
    if (allowed == nullptr) {
        env->ReleaseStringUTFChars(language, lang);
        return nullptr;
    }
    const jsize length = env->GetArrayLength(pcm);
    std::vector<float> samples(static_cast<size_t>(length));
    env->GetFloatArrayRegion(pcm, 0, length, samples.data());
    if (env->ExceptionCheck()) {
        env->ReleaseStringUTFChars(allowedLanguages, allowed);
        env->ReleaseStringUTFChars(language, lang);
        return nullptr;
    }
    struct whisper_context * context = reinterpret_cast<struct whisper_context *>(handle);
    const std::string text = transcribeDictation(
        context,
        samples.data(),
        samples.size(),
        static_cast<int>(threads),
        lang,
        allowed);
    env->ReleaseStringUTFChars(allowedLanguages, allowed);
    env->ReleaseStringUTFChars(language, lang);
    return toJString(env, text);
}

extern "C" JNIEXPORT void JNICALL
Java_io_jyri_dictator_speech_WhisperSttEngine_nativeClose(
    JNIEnv * /* env */,
    jobject /* thunk */,
    jlong handle) {
    if (handle != 0) {
        auto * context = reinterpret_cast<struct whisper_context *>(handle);
        whisper_free(context);
    }
}
