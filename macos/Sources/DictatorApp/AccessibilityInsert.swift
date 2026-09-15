import AppKit
import ApplicationServices
import DictatorCore
import Foundation

struct FocusedEditor {
    let element: AXUIElement
    let pid: pid_t
    let role: String

    static var selfPid: pid_t { pid_t(ProcessInfo.processInfo.processIdentifier) }

    /// Frontmost app that is not Dictator. Used when Settings is key or AX cannot name a field.
    static func frontmostForeign() -> FocusedEditor? {
        let ours = FocusedEditor.selfPid
        if let app = NSWorkspace.shared.frontmostApplication, app.processIdentifier != ours {
            return pasteTarget(pid: app.processIdentifier)
        }
        return nil
    }

    static func current() -> FocusedEditor? {
        let ours = FocusedEditor.selfPid
        let system = AXUIElementCreateSystemWide()
        if let focused = copyElement(system, kAXFocusedUIElementAttribute as CFString),
           let editor = wrap(focused, skipping: ours)
        {
            return editor
        }
        if let app = copyElement(system, kAXFocusedApplicationAttribute as CFString) {
            var appPid: pid_t = 0
            AXUIElementGetPid(app, &appPid)
            if appPid != ours {
                if let focused = copyElement(app, kAXFocusedUIElementAttribute as CFString),
                   let editor = wrap(focused, skipping: ours)
                {
                    return editor
                }
                return pasteTarget(pid: appPid)
            }
        }
        return frontmostForeign()
    }

    static func pasteTarget(pid: pid_t) -> FocusedEditor {
        FocusedEditor(
            element: AXUIElementCreateApplication(pid),
            pid: pid,
            role: "AXApplication"
        )
    }

    func matches(_ other: FocusedEditor) -> Bool {
        pid == other.pid
    }
}

enum AccessibilityInserter {
    enum Outcome {
        case direct
        case pastePosted(previousClipboard: String?, before: String, expected: String, chunk: String)
        case failed
    }

    static func insert(into editor: FocusedEditor, transcript: String, mode: InsertMode) -> Outcome {
        let current = stringValue(editor.element, kAXValueAttribute as CFString) ?? ""
        let utf16Length = (current as NSString).length
        let selection = selectedRange(editor.element) ?? CFRange(location: utf16Length, length: 0)
        let next: String
        let cursor: Int
        let pasteChunk: String
        if mode == .replace {
            let text = InsertionText.withSeparator(prefix: "", insert: transcript)
            next = text
            cursor = (text as NSString).length
            pasteChunk = text
        } else {
            let result = InsertionText.apply(
                current: current,
                selectionStart: selection.location,
                selectionEnd: selection.location + selection.length,
                insert: transcript
            )
            next = result.text
            cursor = result.cursor
            let prefix = (current as NSString).substring(
                with: NSRange(location: 0, length: min(max(selection.location, 0), utf16Length))
            )
            pasteChunk = InsertionText.withSeparator(prefix: prefix, insert: transcript)
        }

        if mode == .merge, setString(editor.element, kAXSelectedTextAttribute as CFString, pasteChunk) {
            if valueLooksInserted(element: editor.element, before: current, expected: next, chunk: pasteChunk) {
                setSelectedRange(editor.element, CFRange(location: cursor, length: 0))
                return .direct
            }
        }
        if setString(editor.element, kAXValueAttribute as CFString, next) {
            if valueLooksInserted(element: editor.element, before: current, expected: next, chunk: pasteChunk) {
                setSelectedRange(editor.element, CFRange(location: cursor, length: 0))
                return .direct
            }
        }
        let previous = NSPasteboard.general.string(forType: .string)
        NSPasteboard.general.clearContents()
        guard NSPasteboard.general.setString(pasteChunk, forType: .string) else {
            return .failed
        }
        postCommandV(to: editor.pid)
        return .pastePosted(previousClipboard: previous, before: current, expected: next, chunk: pasteChunk)
    }

    static func valueLooksInserted(
        element: AXUIElement,
        before: String,
        expected: String,
        chunk: String
    ) -> Bool {
        let after = stringValue(element, kAXValueAttribute as CFString) ?? ""
        return InsertVerify.looksInserted(
            before: before,
            after: after,
            expected: expected,
            chunk: chunk
        )
    }

    static func restoreClipboard(_ previous: String?) {
        NSPasteboard.general.clearContents()
        if let previous {
            NSPasteboard.general.setString(previous, forType: .string)
        }
    }

    private static func postCommandV(to pid: pid_t) {
        let source = CGEventSource(stateID: .combinedSessionState)
        source?.userData = FnKeyMonitor.syntheticEventMarker
        guard
            let down = CGEvent(keyboardEventSource: source, virtualKey: 0x09, keyDown: true),
            let up = CGEvent(keyboardEventSource: source, virtualKey: 0x09, keyDown: false)
        else {
            return
        }
        down.flags = .maskCommand
        up.flags = .maskCommand
        down.setIntegerValueField(.eventSourceUserData, value: FnKeyMonitor.syntheticEventMarker)
        up.setIntegerValueField(.eventSourceUserData, value: FnKeyMonitor.syntheticEventMarker)
        down.postToPid(pid)
        up.postToPid(pid)
    }

}

private func wrap(_ element: AXUIElement, skipping selfPid: pid_t) -> FocusedEditor? {
    var pid: pid_t = 0
    AXUIElementGetPid(element, &pid)
    if pid == selfPid { return nil }
    let usable = nearestEditable(element) ?? element
    return FocusedEditor(
        element: usable,
        pid: pid,
        role: stringValue(usable, kAXRoleAttribute as CFString) ?? ""
    )
}

private func nearestEditable(_ start: AXUIElement) -> AXUIElement? {
    var current: AXUIElement? = start
    for _ in 0..<10 {
        guard let el = current else { return nil }
        if isEditable(el) { return el }
        current = copyElement(el, kAXParentAttribute as CFString)
    }
    return nil
}

private func copyElement(_ owner: AXUIElement, _ attribute: CFString) -> AXUIElement? {
    var value: CFTypeRef?
    guard AXUIElementCopyAttributeValue(owner, attribute, &value) == .success,
          let value,
          CFGetTypeID(value) == AXUIElementGetTypeID()
    else {
        return nil
    }
    return unsafeBitCast(value, to: AXUIElement.self)
}

private func isEditable(_ element: AXUIElement) -> Bool {
    if isDisabled(element) { return false }
    let role = stringValue(element, kAXRoleAttribute as CFString) ?? ""
    if AXTextRole.isEditable(role) {
        return true
    }
    var valueSettable: DarwinBoolean = false
    if AXUIElementIsAttributeSettable(element, kAXValueAttribute as CFString, &valueSettable) == .success,
       valueSettable.boolValue
    {
        return true
    }
    var selectedSettable: DarwinBoolean = false
    if AXUIElementIsAttributeSettable(element, kAXSelectedTextAttribute as CFString, &selectedSettable) == .success,
       selectedSettable.boolValue
    {
        return true
    }
    return false
}

private func isDisabled(_ element: AXUIElement) -> Bool {
    var enabled: CFTypeRef?
    guard AXUIElementCopyAttributeValue(element, kAXEnabledAttribute as CFString, &enabled) == .success,
          let enabled,
          CFGetTypeID(enabled) == CFBooleanGetTypeID()
    else {
        return false
    }
    return !(enabled as! CFBoolean as NSNumber).boolValue
}

private func stringValue(_ element: AXUIElement, _ attribute: CFString) -> String? {
    var value: CFTypeRef?
    guard AXUIElementCopyAttributeValue(element, attribute, &value) == .success, let value else { return nil }
    if let text = value as? String { return text }
    if let text = value as? NSAttributedString { return text.string }
    return nil
}

private func selectedRange(_ element: AXUIElement) -> CFRange? {
    var value: CFTypeRef?
    guard AXUIElementCopyAttributeValue(element, kAXSelectedTextRangeAttribute as CFString, &value) == .success,
          let ax = value, CFGetTypeID(ax) == AXValueGetTypeID()
    else {
        return nil
    }
    var range = CFRange()
    AXValueGetValue(ax as! AXValue, .cfRange, &range)
    return range
}

private func setString(_ element: AXUIElement, _ attribute: CFString, _ value: String) -> Bool {
    AXUIElementSetAttributeValue(element, attribute, value as CFTypeRef) == .success
}

private func setSelectedRange(_ element: AXUIElement, _ range: CFRange) {
    var mutable = range
    if let ax = AXValueCreate(.cfRange, &mutable) {
        AXUIElementSetAttributeValue(element, kAXSelectedTextRangeAttribute as CFString, ax)
    }
}
