package io.jyri.dictator.speech

class StubSpeechEngine : SpeechEngine {
    override fun start(): SpeechSession = StubSpeechSession()

    companion object {
        const val CANNED_TRANSCRIPT = "Can you send me the document tomorrow morning?"
    }
}

private class StubSpeechSession : SpeechSession {
    private var cancelled = false

    override fun feedAudio(pcm16kMono: FloatArray) = Unit

    override fun setLevelListener(listener: ((Float) -> Unit)?) = Unit

    override fun finish(): String {
        if (cancelled) return ""
        return StubSpeechEngine.CANNED_TRANSCRIPT
    }

    override fun cancel() {
        cancelled = true
    }
}
