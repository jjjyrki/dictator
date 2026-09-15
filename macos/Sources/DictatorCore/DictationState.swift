import Foundation

public enum DictationState: String, Sendable {
    case idle
    case microphonePermissionRequired
    case recording
    case processing
    case done
    case error
}
