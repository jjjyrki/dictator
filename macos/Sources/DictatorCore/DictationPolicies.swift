import Foundation

/// Escape cancels only while a gesture is open or Whisper is still running.
public enum EscapeGate {
    public static func shouldCancel(phaseIsIdle: Bool, busy: Bool) -> Bool {
        !phaseIsIdle || busy
    }
}

/// Insert never targets Dictator. Live focus wins, then the pid captured at
/// record start, then the frontmost foreign app.
public enum InsertDestination {
    public static func choose(
        selfPid: Int32,
        livePid: Int32?,
        storedPid: Int32?,
        frontmostPid: Int32?
    ) -> Int32? {
        [livePid, storedPid, frontmostPid]
            .compactMap { $0 }
            .first { $0 != selfPid }
    }
}

/// AX roles we treat as text without probing settable attributes.
public enum AXTextRole {
    public static let editable: Set<String> = [
        "AXTextField",
        "AXTextArea",
        "AXComboBox",
        "AXSearchField",
        "AXWebArea",
        "AXTextEntry",
    ]

    public static func isEditable(_ role: String) -> Bool {
        editable.contains(role)
    }
}

/// Same rule Accessibility uses after an AX set or a Cmd+V post.
public enum InsertVerify {
    public static func looksInserted(
        before: String,
        after: String,
        expected: String,
        chunk: String
    ) -> Bool {
        if after == expected { return true }
        let needle = chunk.trimmingCharacters(in: .whitespacesAndNewlines)
        if needle.isEmpty { return after != before }
        return after != before && after.contains(needle)
    }
}

/// Listen-only watcher contract. A steal tap is a regression.
public enum FnWatchContract {
    public static let stealTapAllowed = false
    public static let listensToFlagsChanged = true
    public static let listensToKeyDownForEscape = true
    public static let swallowsEscape = false
    public static let escapeDebounceSeconds: TimeInterval = 0.2
}
