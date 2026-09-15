import AppKit
import Foundation

final class FnTapController {
    private var monitor: FnKeyMonitor?
    private var poll: Timer?
    private let onDown: () -> Void
    private let onUp: () -> Void
    private let onEscape: () -> Void
    private(set) var statusLine = "Fn watcher: starting"
    var onStatus: (() -> Void)?

    init(onDown: @escaping () -> Void, onUp: @escaping () -> Void, onEscape: @escaping () -> Void) {
        self.onDown = onDown
        self.onUp = onUp
        self.onEscape = onEscape
    }

    func start() {
        Permissions.promptAccessibility()
        if !Permissions.microphoneGranted() {
            Permissions.requestMicrophone { _ in }
        }
        Permissions.requestInputMonitoring()
        if !Permissions.accessibilityTrusted() {
            Permissions.openAccessibilitySettings()
        }
        installIfNeeded()
        ensurePolling()
    }

    func retryNow() {
        monitor?.stop()
        monitor = nil
        Permissions.promptAccessibility()
        Permissions.requestInputMonitoring()
        if !Permissions.accessibilityTrusted() {
            Permissions.openAccessibilitySettings()
        } else if Permissions.inputMonitoringDenied() {
            Permissions.openInputMonitoringSettings()
        }
        installIfNeeded()
        ensurePolling()
    }

    private func installIfNeeded() {
        if monitor == nil {
            let next = FnKeyMonitor(onDown: onDown, onUp: onUp, onEscape: onEscape)
            next.onDiagnostic = { [weak self] in
                self?.publish()
            }
            next.start()
            monitor = next
        }
        publish()
    }

    private func ensurePolling() {
        if poll != nil { return }
        poll = Timer.scheduledTimer(withTimeInterval: 1.5, repeats: true) { [weak self] timer in
            guard let self else {
                timer.invalidate()
                return
            }
            self.monitor?.refreshFromPermissions()
            self.publish()
        }
    }

    func diagnosticDump(lastLoad: String, lastOutcome: String) -> String {
        Diagnostics.dump(
            watcher: monitor?.diagnosticLines() ?? ["monitor=nil", statusLine],
            lastLoad: lastLoad,
            lastOutcome: lastOutcome
        )
    }

    private func publish() {
        statusLine = monitor?.statusDescription ?? statusLine
        onStatus?()
    }
}
