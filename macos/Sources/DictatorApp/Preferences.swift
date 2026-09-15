import DictatorCore
import Foundation

final class Preferences {
    static let shared = Preferences()

    private let defaults = UserDefaults.standard
    private enum Key {
        static let profile = "model_profile"
        static let languages = "spoken_language_codes"
        static let insertMode = "insert_mode"
        static let partials = "partials_enabled"
    }

    var profile: SttModelProfile {
        get {
            defaults.string(forKey: Key.profile)
                .flatMap(SttModelProfile.init(rawValue:)) ?? .default
        }
        set { defaults.set(newValue.rawValue, forKey: Key.profile) }
    }

    var spokenLanguages: Set<SpokenLanguage> {
        get {
            let stored = defaults.stringArray(forKey: Key.languages) ?? []
            let selected = stored.compactMap(SpokenLanguage.from(whisperCode:))
            let limited = Array(selected.prefix(SpokenLanguage.maxSelected))
            return limited.isEmpty ? SpokenLanguage.defaultSelection : Set(limited)
        }
        set {
            let codes = SpokenLanguage.catalog
                .filter { newValue.contains($0) }
                .prefix(SpokenLanguage.maxSelected)
                .map(\.whisperCode)
            defaults.set(Array(codes), forKey: Key.languages)
        }
    }

    var insertMode: InsertMode {
        get {
            defaults.string(forKey: Key.insertMode).flatMap(InsertMode.init(rawValue:)) ?? .merge
        }
        set { defaults.set(newValue.rawValue, forKey: Key.insertMode) }
    }

    var partialsEnabled: Bool {
        get { defaults.bool(forKey: Key.partials) }
        set { defaults.set(newValue, forKey: Key.partials) }
    }

    var modelsRoot: URL {
        let root = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask).first!
        return root.appendingPathComponent("Dictator/models", isDirectory: true)
    }

    func modelFile(for profile: SttModelProfile) -> URL {
        modelsRoot
            .appendingPathComponent(profile.asset.directoryName, isDirectory: true)
            .appendingPathComponent(profile.asset.fileName)
    }

    func isInstalled(_ profile: SttModelProfile) -> Bool {
        let file = modelFile(for: profile)
        guard let size = try? FileManager.default.attributesOfItem(atPath: file.path)[.size] as? NSNumber else {
            return false
        }
        return size.int64Value == profile.asset.sizeBytes
    }
}
