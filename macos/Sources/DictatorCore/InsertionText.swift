import Foundation

public enum InsertionText {
    /// Whisper often prefixes a segment with a space. Keep it as a word
    /// separator unless the insertion sits at the start of a blank prefix.
    /// Indices match Android `String` / macOS AX: UTF-16 code units.
    public static func withSeparator(prefix: String, insert: String) -> String {
        if prefix.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            return String(insert.drop(while: \.isWhitespace))
        }
        return insert
    }

    public static func apply(
        current: String,
        selectionStart: Int,
        selectionEnd: Int,
        insert: String
    ) -> (text: String, cursor: Int) {
        let ns = current as NSString
        let start = clamp(selectionStart, 0, ns.length)
        let rawEnd = clamp(selectionEnd, 0, ns.length)
        let end = max(start, rawEnd)
        let prefix = ns.substring(with: NSRange(location: 0, length: start))
        let cleaned = withSeparator(prefix: prefix, insert: insert)
        let suffix = ns.substring(from: end)
        return (prefix + cleaned + suffix, start + (cleaned as NSString).length)
    }

    private static func clamp(_ value: Int, _ lower: Int, _ upper: Int) -> Int {
        min(max(value, lower), upper)
    }
}
