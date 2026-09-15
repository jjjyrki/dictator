import AppKit
import ApplicationServices
import AVFoundation
import CoreGraphics
import IOKit.hid

enum Permissions {
    static func microphoneGranted() -> Bool {
        AVCaptureDevice.authorizationStatus(for: .audio) == .authorized
    }

    static func requestMicrophone(_ completion: @escaping (Bool) -> Void) {
        AVCaptureDevice.requestAccess(for: .audio, completionHandler: completion)
    }

    static func accessibilityTrusted() -> Bool {
        AXIsProcessTrusted()
    }

    static func promptAccessibility() {
        let options = [kAXTrustedCheckOptionPrompt.takeUnretainedValue() as String: true] as CFDictionary
        AXIsProcessTrustedWithOptions(options)
    }

    static func inputMonitoringGranted() -> Bool {
        IOHIDCheckAccess(kIOHIDRequestTypeListenEvent) == kIOHIDAccessTypeGranted
            || CGPreflightListenEventAccess()
    }

    static func inputMonitoringDenied() -> Bool {
        IOHIDCheckAccess(kIOHIDRequestTypeListenEvent) == kIOHIDAccessTypeDenied
    }

    static func identityLine() -> String {
        let id = Bundle.main.bundleIdentifier ?? "unknown-id"
        let path = Bundle.main.bundlePath
        return "This binary is \(id) at \(path)"
    }

    /// Ask TCC once while the state is unknown. A denied answer stays denied
    /// until the user toggles Input Monitoring. Re-requesting that deny every
    /// 1.5s never shows a prompt and can keep the row empty.
    static func requestInputMonitoring() {
        let listen = IOHIDCheckAccess(kIOHIDRequestTypeListenEvent)
        if listen == kIOHIDAccessTypeUnknown {
            _ = IOHIDRequestAccess(kIOHIDRequestTypeListenEvent)
        }
        let post = IOHIDCheckAccess(kIOHIDRequestTypePostEvent)
        if post == kIOHIDAccessTypeUnknown {
            _ = IOHIDRequestAccess(kIOHIDRequestTypePostEvent)
        }
        if listen != kIOHIDAccessTypeDenied, !CGPreflightListenEventAccess() {
            _ = CGRequestListenEventAccess()
        }
        if post != kIOHIDAccessTypeDenied, !CGPreflightPostEventAccess() {
            _ = CGRequestPostEventAccess()
        }
    }

    static func openMicrophoneSettings() {
        open("x-apple.systempreferences:com.apple.preference.security?Privacy_Microphone")
    }

    static func openAccessibilitySettings() {
        if let modern = URL(string: "x-apple.systempreferences:com.apple.settings.PrivacySecurity.extension?Privacy_Accessibility") {
            NSWorkspace.shared.open(modern)
            return
        }
        open("x-apple.systempreferences:com.apple.preference.security?Privacy_Accessibility")
    }

    static func openInputMonitoringSettings() {
        requestInputMonitoring()
        if let modern = URL(string: "x-apple.systempreferences:com.apple.settings.PrivacySecurity.extension?Privacy_ListenEvent") {
            NSWorkspace.shared.open(modern)
            return
        }
        open("x-apple.systempreferences:com.apple.preference.security?Privacy_ListenEvent")
    }

    static func openKeyboardSettings() {
        open("x-apple.systempreferences:com.apple.preference.keyboard")
    }

    private static func open(_ url: String) {
        if let parsed = URL(string: url) {
            NSWorkspace.shared.open(parsed)
        }
    }
}
