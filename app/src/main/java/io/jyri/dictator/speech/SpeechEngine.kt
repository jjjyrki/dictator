package io.jyri.dictator.speech

interface SpeechEngine {
    fun start(): SpeechSession
}

interface SpeechSession {
    fun finish(): String

    fun cancel()
}
