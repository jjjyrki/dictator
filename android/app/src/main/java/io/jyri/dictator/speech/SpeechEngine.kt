package io.jyri.dictator.speech

interface SpeechEngine {
    fun start(): SpeechSession
}

interface SpeechSession {
    /** Streams one frame of 16 kHz mono PCM into the session. */
    fun feedAudio(pcm16kMono: FloatArray)

    /** Receives normalized input level (0..1) per frame, called from the capture thread. */
    fun setLevelListener(listener: ((Float) -> Unit)?)

    /**
     * Receives interim transcription text while recording. Only engines that
     * support partials call it; the default no-op keeps other sessions intact.
     */
    fun setPartialListener(listener: ((String) -> Unit)?) {}

    fun finish(): String

    fun cancel()
}
