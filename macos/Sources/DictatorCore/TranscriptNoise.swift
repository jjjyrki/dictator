import Foundation

/// Whisper often emits bracket tags instead of speech when the clip is silence or music.
public enum TranscriptNoise {
    private static let tags: Set<String> = [
        "BLANK_AUDIO",
        "BLANK AUDIO",
        "MUSIC",
        "SILENCE",
        "NOISE",
        "INAUDIBLE",
        "COUGH",
        "COUGHING",
        "APPLAUSE",
        "LAUGHTER",
        "HUMMING",
        "SINGING",
        "STATIC",
        "BLANK",
    ]

    private static let tokenPattern = try! NSRegularExpression(
        pattern: #"\[([^\[\]]+)\]|\(([^()]+)\)"#,
        options: []
    )

    /// Speech left after dropping silence/music tags, or `nil` if nothing usable remains.
    public static func usableSpeech(_ raw: String) -> String? {
        var text = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        if text.isEmpty { return nil }

        let ns = text as NSString
        let range = NSRange(location: 0, length: ns.length)
        let matches = tokenPattern.matches(in: text, options: [], range: range)
        for match in matches.reversed() {
            let innerRange = match.range(at: 1).location != NSNotFound
                ? match.range(at: 1)
                : match.range(at: 2)
            guard innerRange.location != NSNotFound else { continue }
            let inner = ns.substring(with: innerRange)
            if isNoiseTag(inner) {
                text = (text as NSString).replacingCharacters(in: match.range, with: " ")
            }
        }

        text = text.replacingOccurrences(of: "♪", with: " ")
        text = text.replacingOccurrences(
            of: #"\s+"#,
            with: " ",
            options: .regularExpression
        )
        text = text.trimmingCharacters(in: .whitespacesAndNewlines)
        if text.isEmpty { return nil }

        let compact = text
            .replacingOccurrences(of: "_", with: " ")
            .trimmingCharacters(in: .whitespacesAndNewlines)
        if isNoiseTag(compact) { return nil }

        let leftover = text.trimmingCharacters(in: CharacterSet.punctuationCharacters.union(.whitespacesAndNewlines))
        if leftover.isEmpty { return nil }
        return text
    }

    private static func isNoiseTag(_ raw: String) -> Bool {
        let folded = raw
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .replacingOccurrences(of: "_", with: " ")
            .uppercased()
        if tags.contains(folded) { return true }
        let squeezed = folded.replacingOccurrences(of: " ", with: "")
        return squeezed == "BLANKAUDIO" || tags.contains(squeezed)
    }
}
