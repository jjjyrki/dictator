package io.jyri.dictator.session

enum class DictationState {
    Idle,
    MicrophonePermissionRequired,
    Recording,
    Processing,
    Done,
    Error,
}
