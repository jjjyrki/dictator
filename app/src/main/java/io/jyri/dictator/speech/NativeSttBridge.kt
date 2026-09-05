package io.jyri.dictator.speech

/** JNI bridge for the persistent Rust Mimi plus Kyutai STT runtime. */
object NativeSttBridge {
    init {
        System.loadLibrary("dictator_stt")
    }

    external fun nativeCreate(modelDirectory: String): Long

    external fun nativeStart(handle: Long)

    external fun nativeFeed(handle: Long, pcm24kMono: FloatArray): String

    external fun nativeFinish(handle: Long): String

    external fun nativeClose(handle: Long)
}
