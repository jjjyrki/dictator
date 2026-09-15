import Foundation

/// MERGE inserts at the caret. REPLACE overwrites the whole field.
public enum InsertMode: String, Sendable, CaseIterable {
    case merge
    case replace
}
