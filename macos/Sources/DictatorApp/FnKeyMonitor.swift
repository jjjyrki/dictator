import AppKit
import CoreGraphics
import DictatorCore
import Foundation

final class FnKeyMonitor {
    /// Marks synthetic paste events so Cmd+V is never treated as Fn.
    static let syntheticEventMarker: Int64 = 0x44494354

    private var tap: CFMachPort?
    private var source: CFRunLoopSource?
    private var localMonitor: Any?
    private var globalMonitor: Any?
    private var hid: FnHIDListener?
    private var fnDown = false
    private var previousFunctionFlag = false
    private var axTrustedWhenMonitorsInstalled = false
    private var hidSkipLine = "HID closed"
    private var cgTapDescription = "no CG listen tap"
    private let onDown: () -> Void
    private let onUp: () -> Void
    private let onEscape: () -> Void
    var onDiagnostic: (() -> Void)?

    private(set) var lastFnLabel = "no Fn event yet"
    private var nsEventCount = 0
    private var flagsChangedCount = 0
    private var nsFnCount = 0
    private var cgEventCount = 0
    private var lastFlagsLine = "none"
    private var recentFlags: [String] = []
    private var lastEscapeLabel = "no Escape yet"
    private var escapeCount = 0
    private var lastEscapeAt: TimeInterval = 0

    init(onDown: @escaping () -> Void, onUp: @escaping () -> Void, onEscape: @escaping () -> Void) {
        self.onDown = onDown
        self.onUp = onUp
        self.onEscape = onEscape
    }

    var statusDescription: String {
        let hidLine = hid?.description ?? hidSkipLine
        return "Fn watcher: \(hidLine). \(cgTapDescription). Last Fn: \(lastFnLabel)."
    }

    func start() {
        stop()
        installNSEventMonitors()
        installHIDIfAllowed()
        installListenTapIfAllowed()
    }

    /// Global NSEvent monitors installed before Accessibility is granted stay silent.
    /// Reinstall them after AX flips on. Never install a steal tap.
    func refreshFromPermissions() {
        if Permissions.accessibilityTrusted(), !axTrustedWhenMonitorsInstalled {
            reinstallNSEventMonitors()
        }
        installListenTapIfAllowed()
        if Permissions.inputMonitoringGranted() {
            if hid == nil {
                installHIDIfAllowed()
            }
        } else if Permissions.inputMonitoringDenied() {
            hid?.stop()
            hid = nil
            hidSkipLine = "HID skipped, Input Monitoring denied"
        }
    }

    func stop() {
        if let tap {
            CGEvent.tapEnable(tap: tap, enable: false)
        }
        if let source {
            CFRunLoopRemoveSource(CFRunLoopGetMain(), source, .commonModes)
        }
        if let localMonitor {
            NSEvent.removeMonitor(localMonitor)
        }
        if let globalMonitor {
            NSEvent.removeMonitor(globalMonitor)
        }
        hid?.stop()
        tap = nil
        source = nil
        localMonitor = nil
        globalMonitor = nil
        hid = nil
        fnDown = false
        previousFunctionFlag = false
        axTrustedWhenMonitorsInstalled = false
        hidSkipLine = "HID closed"
        cgTapDescription = "no CG listen tap"
        nsEventCount = 0
        flagsChangedCount = 0
        nsFnCount = 0
        cgEventCount = 0
        lastFlagsLine = "none"
        recentFlags = []
        lastFnLabel = "no Fn event yet"
        lastEscapeLabel = "no Escape yet"
        escapeCount = 0
    }

    func diagnosticLines() -> [String] {
        var lines = [
            statusDescription,
            "localMonitor=\(localMonitor != nil) globalMonitor=\(globalMonitor != nil) stealTap=false listenTap=\(tap != nil)",
            "nsEvents=\(nsEventCount) flagsChanged=\(flagsChangedCount) nsFn=\(nsFnCount) cgEvents=\(cgEventCount)",
            "lastFlags=\(lastFlagsLine)",
            "recentFlags=" + (recentFlags.isEmpty ? "none" : recentFlags.joined(separator: " | ")),
            "escapeCount=\(escapeCount) lastEscape=\(lastEscapeLabel)",
        ]
        lines.append("axTrusted=\(Permissions.accessibilityTrusted()) axWhenMonitorsInstalled=\(axTrustedWhenMonitorsInstalled)")
        if let hid {
            lines.append(contentsOf: hid.diagnosticLines())
        } else {
            lines.append("hid=nil \(hidSkipLine)")
        }
        return lines
    }

    private func installHIDIfAllowed() {
        if Permissions.inputMonitoringDenied() {
            hidSkipLine = "HID skipped, Input Monitoring denied"
            return
        }
        let listener = FnHIDListener { [weak self] down in
            self?.noteFn(down: down, source: "HID")
        }
        listener.start()
        hid = listener
        hidSkipLine = listener.description
    }

    private func installListenTapIfAllowed() {
        if tap != nil { return }
        if !Permissions.accessibilityTrusted(), !Permissions.inputMonitoringGranted() {
            cgTapDescription = "CG listen tap waiting for Accessibility"
            return
        }
        let mask =
            CGEventMask(1 << CGEventType.flagsChanged.rawValue)
            | CGEventMask(1 << CGEventType.keyDown.rawValue)
        var attempts: [(CGEventTapLocation, String)] = [
            (.cgSessionEventTap, "CG session listen-only"),
            (.cgAnnotatedSessionEventTap, "CG annotated listen-only"),
        ]
        if Permissions.inputMonitoringGranted() {
            attempts.insert((.cghidEventTap, "CG HID listen-only"), at: 0)
        }
        for (location, label) in attempts {
            guard let created = CGEvent.tapCreate(
                tap: location,
                place: .headInsertEventTap,
                options: FnWatchContract.stealTapAllowed ? .defaultTap : .listenOnly,
                eventsOfInterest: mask,
                callback: { _, type, event, refcon in
                    guard let refcon else {
                        return Unmanaged.passUnretained(event)
                    }
                    let monitor = Unmanaged<FnKeyMonitor>.fromOpaque(refcon).takeUnretainedValue()
                    return monitor.handleListenTap(type: type, event: event)
                },
                userInfo: Unmanaged.passUnretained(self).toOpaque()
            ) else {
                continue
            }
            tap = created
            cgTapDescription = label
            source = CFMachPortCreateRunLoopSource(kCFAllocatorDefault, created, 0)
            CFRunLoopAddSource(CFRunLoopGetMain(), source, .commonModes)
            CGEvent.tapEnable(tap: created, enable: true)
            onDiagnostic?()
            return
        }
        cgTapDescription = "CG listen tap create failed"
    }

    private func reinstallNSEventMonitors() {
        if let localMonitor {
            NSEvent.removeMonitor(localMonitor)
        }
        if let globalMonitor {
            NSEvent.removeMonitor(globalMonitor)
        }
        localMonitor = nil
        globalMonitor = nil
        installNSEventMonitors()
    }

    private func installNSEventMonitors() {
        let mask: NSEvent.EventTypeMask = [.flagsChanged, .keyDown]
        localMonitor = NSEvent.addLocalMonitorForEvents(matching: mask) { [weak self] event in
            self?.handleNSEvent(event)
            return event
        }
        globalMonitor = NSEvent.addGlobalMonitorForEvents(matching: mask) { [weak self] event in
            self?.handleNSEvent(event)
        }
        axTrustedWhenMonitorsInstalled = Permissions.accessibilityTrusted()
    }

    private func handleListenTap(type: CGEventType, event: CGEvent) -> Unmanaged<CGEvent>? {
        if type == .tapDisabledByTimeout || type == .tapDisabledByUserInput {
            if let tap { CGEvent.tapEnable(tap: tap, enable: true) }
            return Unmanaged.passUnretained(event)
        }
        if event.getIntegerValueField(.eventSourceUserData) == Self.syntheticEventMarker {
            return Unmanaged.passUnretained(event)
        }
        if type == .keyDown {
            let keyCode = UInt16(truncatingIfNeeded: event.getIntegerValueField(.keyboardEventKeycode))
            if keyCode == FnHardware.escapeKeyCode {
                DispatchQueue.main.async { [weak self] in
                    self?.noteEscape(source: "CG listen")
                }
            }
            return Unmanaged.passUnretained(event)
        }
        guard type == .flagsChanged else {
            return Unmanaged.passUnretained(event)
        }
        let keyCode = UInt16(truncatingIfNeeded: event.getIntegerValueField(.keyboardEventKeycode))
        let functionFlag = event.flags.contains(.maskSecondaryFn)
        let flagsRaw = event.flags.rawValue
        DispatchQueue.main.async { [weak self] in
            self?.cgEventCount += 1
            self?.applyFlags(
                keyCode: keyCode,
                functionFlag: functionFlag,
                flagsRaw: UInt64(flagsRaw),
                source: "CG listen"
            )
        }
        return Unmanaged.passUnretained(event)
    }

    private func handleNSEvent(_ event: NSEvent) {
        if event.isARepeat { return }
        if event.type == .keyDown {
            guard event.keyCode == FnHardware.escapeKeyCode else { return }
            noteEscape(source: "NSEvent key")
            return
        }
        nsEventCount += 1
        guard event.type == .flagsChanged else { return }
        applyFlags(
            keyCode: event.keyCode,
            functionFlag: event.modifierFlags.contains(.function),
            flagsRaw: UInt64(event.modifierFlags.rawValue),
            source: "NSEvent flags"
        )
    }

    private func applyFlags(keyCode: UInt16, functionFlag: Bool, flagsRaw: UInt64, source: String) {
        flagsChangedCount += 1
        let line = "key=\(keyCode) fn=\(functionFlag) flags=0x\(String(flagsRaw, radix: 16)) via \(source)"
        lastFlagsLine = line
        recentFlags.append(line)
        if recentFlags.count > 8 {
            recentFlags.removeFirst()
        }
        let isFn = FnHardware.isFnFlagsEvent(
            keyCode: keyCode,
            functionFlag: functionFlag,
            previousFunctionFlag: previousFunctionFlag
        )
        previousFunctionFlag = functionFlag
        guard isFn else { return }
        nsFnCount += 1
        noteFn(down: functionFlag, source: source)
    }

    private func noteEscape(source: String) {
        let now = ProcessInfo.processInfo.systemUptime
        if now - lastEscapeAt < FnWatchContract.escapeDebounceSeconds { return }
        lastEscapeAt = now
        escapeCount += 1
        lastEscapeLabel = "Escape via \(source)"
        DispatchQueue.main.async { [weak self] in
            self?.onEscape()
            self?.onDiagnostic?()
        }
    }

    private func noteFn(down: Bool, source: String) {
        if down == fnDown { return }
        fnDown = down
        lastFnLabel = "\(down ? "down" : "up") via \(source)"
        let callback = down ? onDown : onUp
        DispatchQueue.main.async { [weak self] in
            callback()
            self?.onDiagnostic?()
        }
    }
}
