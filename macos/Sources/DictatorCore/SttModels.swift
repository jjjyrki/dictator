import Foundation

public struct SpokenLanguage: Hashable, Sendable, Identifiable {
    public var whisperCode: String
    public var displayName: String
    public var id: String { whisperCode }

    public static let english = SpokenLanguage(whisperCode: "en", displayName: "English")
    public static let finnish = SpokenLanguage(whisperCode: "fi", displayName: "Finnish")
    public static let maxSelected = 4

    public static let catalog: [SpokenLanguage] = [
        .english,
        SpokenLanguage(whisperCode: "zh", displayName: "Chinese"),
        SpokenLanguage(whisperCode: "de", displayName: "German"),
        SpokenLanguage(whisperCode: "es", displayName: "Spanish"),
        SpokenLanguage(whisperCode: "ru", displayName: "Russian"),
        SpokenLanguage(whisperCode: "ko", displayName: "Korean"),
        SpokenLanguage(whisperCode: "fr", displayName: "French"),
        SpokenLanguage(whisperCode: "ja", displayName: "Japanese"),
        SpokenLanguage(whisperCode: "pt", displayName: "Portuguese"),
        SpokenLanguage(whisperCode: "tr", displayName: "Turkish"),
        SpokenLanguage(whisperCode: "pl", displayName: "Polish"),
        SpokenLanguage(whisperCode: "ca", displayName: "Catalan"),
        SpokenLanguage(whisperCode: "nl", displayName: "Dutch"),
        SpokenLanguage(whisperCode: "ar", displayName: "Arabic"),
        SpokenLanguage(whisperCode: "sv", displayName: "Swedish"),
        SpokenLanguage(whisperCode: "it", displayName: "Italian"),
        SpokenLanguage(whisperCode: "id", displayName: "Indonesian"),
        SpokenLanguage(whisperCode: "hi", displayName: "Hindi"),
        .finnish,
        SpokenLanguage(whisperCode: "vi", displayName: "Vietnamese"),
        SpokenLanguage(whisperCode: "he", displayName: "Hebrew"),
        SpokenLanguage(whisperCode: "uk", displayName: "Ukrainian"),
        SpokenLanguage(whisperCode: "el", displayName: "Greek"),
        SpokenLanguage(whisperCode: "ms", displayName: "Malay"),
        SpokenLanguage(whisperCode: "cs", displayName: "Czech"),
        SpokenLanguage(whisperCode: "ro", displayName: "Romanian"),
        SpokenLanguage(whisperCode: "da", displayName: "Danish"),
        SpokenLanguage(whisperCode: "hu", displayName: "Hungarian"),
        SpokenLanguage(whisperCode: "ta", displayName: "Tamil"),
        SpokenLanguage(whisperCode: "no", displayName: "Norwegian"),
        SpokenLanguage(whisperCode: "th", displayName: "Thai"),
        SpokenLanguage(whisperCode: "ur", displayName: "Urdu"),
        SpokenLanguage(whisperCode: "hr", displayName: "Croatian"),
        SpokenLanguage(whisperCode: "bg", displayName: "Bulgarian"),
        SpokenLanguage(whisperCode: "lt", displayName: "Lithuanian"),
        SpokenLanguage(whisperCode: "la", displayName: "Latin"),
        SpokenLanguage(whisperCode: "mi", displayName: "Maori"),
        SpokenLanguage(whisperCode: "ml", displayName: "Malayalam"),
        SpokenLanguage(whisperCode: "cy", displayName: "Welsh"),
        SpokenLanguage(whisperCode: "sk", displayName: "Slovak"),
        SpokenLanguage(whisperCode: "te", displayName: "Telugu"),
        SpokenLanguage(whisperCode: "fa", displayName: "Persian"),
        SpokenLanguage(whisperCode: "lv", displayName: "Latvian"),
        SpokenLanguage(whisperCode: "bn", displayName: "Bengali"),
        SpokenLanguage(whisperCode: "sr", displayName: "Serbian"),
        SpokenLanguage(whisperCode: "az", displayName: "Azerbaijani"),
        SpokenLanguage(whisperCode: "sl", displayName: "Slovenian"),
        SpokenLanguage(whisperCode: "kn", displayName: "Kannada"),
        SpokenLanguage(whisperCode: "et", displayName: "Estonian"),
        SpokenLanguage(whisperCode: "mk", displayName: "Macedonian"),
        SpokenLanguage(whisperCode: "br", displayName: "Breton"),
        SpokenLanguage(whisperCode: "eu", displayName: "Basque"),
        SpokenLanguage(whisperCode: "is", displayName: "Icelandic"),
        SpokenLanguage(whisperCode: "hy", displayName: "Armenian"),
        SpokenLanguage(whisperCode: "ne", displayName: "Nepali"),
        SpokenLanguage(whisperCode: "mn", displayName: "Mongolian"),
        SpokenLanguage(whisperCode: "bs", displayName: "Bosnian"),
        SpokenLanguage(whisperCode: "kk", displayName: "Kazakh"),
        SpokenLanguage(whisperCode: "sq", displayName: "Albanian"),
        SpokenLanguage(whisperCode: "sw", displayName: "Swahili"),
        SpokenLanguage(whisperCode: "gl", displayName: "Galician"),
        SpokenLanguage(whisperCode: "mr", displayName: "Marathi"),
        SpokenLanguage(whisperCode: "pa", displayName: "Punjabi"),
        SpokenLanguage(whisperCode: "si", displayName: "Sinhala"),
        SpokenLanguage(whisperCode: "km", displayName: "Khmer"),
        SpokenLanguage(whisperCode: "sn", displayName: "Shona"),
        SpokenLanguage(whisperCode: "yo", displayName: "Yoruba"),
        SpokenLanguage(whisperCode: "so", displayName: "Somali"),
        SpokenLanguage(whisperCode: "af", displayName: "Afrikaans"),
        SpokenLanguage(whisperCode: "oc", displayName: "Occitan"),
        SpokenLanguage(whisperCode: "ka", displayName: "Georgian"),
        SpokenLanguage(whisperCode: "be", displayName: "Belarusian"),
        SpokenLanguage(whisperCode: "tg", displayName: "Tajik"),
        SpokenLanguage(whisperCode: "sd", displayName: "Sindhi"),
        SpokenLanguage(whisperCode: "gu", displayName: "Gujarati"),
        SpokenLanguage(whisperCode: "am", displayName: "Amharic"),
        SpokenLanguage(whisperCode: "yi", displayName: "Yiddish"),
        SpokenLanguage(whisperCode: "lo", displayName: "Lao"),
        SpokenLanguage(whisperCode: "uz", displayName: "Uzbek"),
        SpokenLanguage(whisperCode: "fo", displayName: "Faroese"),
        SpokenLanguage(whisperCode: "ht", displayName: "Haitian Creole"),
        SpokenLanguage(whisperCode: "ps", displayName: "Pashto"),
        SpokenLanguage(whisperCode: "tk", displayName: "Turkmen"),
        SpokenLanguage(whisperCode: "nn", displayName: "Nynorsk"),
        SpokenLanguage(whisperCode: "mt", displayName: "Maltese"),
        SpokenLanguage(whisperCode: "sa", displayName: "Sanskrit"),
        SpokenLanguage(whisperCode: "lb", displayName: "Luxembourgish"),
        SpokenLanguage(whisperCode: "my", displayName: "Myanmar"),
        SpokenLanguage(whisperCode: "bo", displayName: "Tibetan"),
        SpokenLanguage(whisperCode: "tl", displayName: "Tagalog"),
        SpokenLanguage(whisperCode: "mg", displayName: "Malagasy"),
        SpokenLanguage(whisperCode: "as", displayName: "Assamese"),
        SpokenLanguage(whisperCode: "tt", displayName: "Tatar"),
        SpokenLanguage(whisperCode: "haw", displayName: "Hawaiian"),
        SpokenLanguage(whisperCode: "ln", displayName: "Lingala"),
        SpokenLanguage(whisperCode: "ha", displayName: "Hausa"),
        SpokenLanguage(whisperCode: "ba", displayName: "Bashkir"),
        SpokenLanguage(whisperCode: "jw", displayName: "Javanese"),
        SpokenLanguage(whisperCode: "su", displayName: "Sundanese"),
        SpokenLanguage(whisperCode: "yue", displayName: "Cantonese"),
    ]

    public static let defaultSelection: Set<SpokenLanguage> = [.english, .finnish]

    public static func from(whisperCode: String) -> SpokenLanguage? {
        catalog.first { $0.whisperCode == whisperCode }
    }
}

public enum SttModelFamily: String, Sendable, CaseIterable {
    case englishOnly
    case multilingual
}

public enum SttModelVariant: String, Sendable, CaseIterable {
    case tiny
    case base
    case small
    case medium
    case largeTurbo
}

public enum SttModelSource: String, Sendable {
    case futoAcft
    case whisperCppOfficial
}

public struct SttModelAsset: Equatable, Sendable {
    public var languageName: String
    public var modelName: String
    public var sizeLabel: String
    public var usageDescription: String
    public var directoryName: String
    public var fileName: String
    public var sizeBytes: Int64
    public var sha256: String
    public var source: SttModelSource = .futoAcft

    public var displayName: String {
        "\(languageName) · \(modelName) · \(sizeLabel) · \(usageDescription)"
    }

    public var downloadURL: URL {
        switch source {
        case .futoAcft:
            return URL(string: "https://voiceinput.futo.org/VoiceInput/\(fileName)")!
        case .whisperCppOfficial:
            return URL(string: "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/\(fileName)?download=true")!
        }
    }

    public var shortenAudioContext: Bool { source == .futoAcft }
}

public enum SttModelProfile: String, Sendable, CaseIterable, Identifiable {
    case englishTiny
    case englishBase
    case englishSmall
    case multilingualTiny
    case multilingualBase
    case multilingualSmall
    case multilingualMediumOfficial
    case multilingualLargeTurboOfficial

    public var id: String { rawValue }

    public static let `default`: SttModelProfile = .multilingualSmall

    public var variant: SttModelVariant {
        switch self {
        case .englishTiny, .multilingualTiny: return .tiny
        case .englishBase, .multilingualBase: return .base
        case .englishSmall, .multilingualSmall: return .small
        case .multilingualMediumOfficial: return .medium
        case .multilingualLargeTurboOfficial: return .largeTurbo
        }
    }

    public var family: SttModelFamily {
        switch self {
        case .englishTiny, .englishBase, .englishSmall: return .englishOnly
        case .multilingualTiny, .multilingualBase, .multilingualSmall,
             .multilingualMediumOfficial, .multilingualLargeTurboOfficial:
            return .multilingual
        }
    }

    public var isMultilingual: Bool { family == .multilingual }

    public var shortenAudioContext: Bool { asset.shortenAudioContext }

    public var asset: SttModelAsset {
        switch self {
        case .englishTiny:
            return SttModelAsset(
                languageName: "English",
                modelName: "Tiny",
                sizeLabel: "44 MB",
                usageDescription: "Fastest",
                directoryName: "whisper-en-acft-tiny",
                fileName: "tiny_en_acft_q8_0.bin",
                sizeBytes: 43_550_795,
                sha256: "4b5480aa1b14a7efc5b578ef176510970a898049671c3cd237285b3e3f6bfbfc"
            )
        case .englishBase:
            return SttModelAsset(
                languageName: "English",
                modelName: "Base",
                sizeLabel: "82 MB",
                usageDescription: "Balanced",
                directoryName: "whisper-en-acft-base",
                fileName: "base_en_acft_q8_0.bin",
                sizeBytes: 81_781_811,
                sha256: "e9b4b7b81b8a28769e8aa9962aa39bb9f21b622cf6a63982e93f065ed5caf1c8"
            )
        case .englishSmall:
            return SttModelAsset(
                languageName: "English",
                modelName: "Small",
                sizeLabel: "264 MB",
                usageDescription: "Best quality",
                directoryName: "whisper-en-acft-small",
                fileName: "small_en_acft_q8_0.bin",
                sizeBytes: 264_477_561,
                sha256: "58fbe949992dafed917590d58bc12ca577b08b9957f0b3e0d7ee71b64bed3aa8"
            )
        case .multilingualTiny:
            return SttModelAsset(
                languageName: "Multilingual",
                modelName: "Tiny",
                sizeLabel: "44 MB",
                usageDescription: "Fastest",
                directoryName: "whisper-acft-tiny",
                fileName: "tiny_acft_q8_0.bin",
                sizeBytes: 43_537_450,
                sha256: "07aa4d514144deacf5ffec5cacb36c93dee272fda9e64ac33a801f8cd5cbd953"
            )
        case .multilingualBase:
            return SttModelAsset(
                languageName: "Multilingual",
                modelName: "Base",
                sizeLabel: "82 MB",
                usageDescription: "Balanced",
                directoryName: "whisper-acft-base",
                fileName: "base_acft_q8_0.bin",
                sizeBytes: 81_768_602,
                sha256: "e44f352c9aa2c3609dece20c733c4ad4a75c28cd9ab07d005383df55fa96efc4"
            )
        case .multilingualSmall:
            return SttModelAsset(
                languageName: "Multilingual",
                modelName: "Small",
                sizeLabel: "264 MB",
                usageDescription: "Best quality",
                directoryName: "whisper-acft-small",
                fileName: "small_acft_q8_0.bin",
                sizeBytes: 264_464_624,
                sha256: "15ef255465a6dc582ecf1ec651a4618c7ee2c18c05570bbe46493d248d465ac4"
            )
        case .multilingualMediumOfficial:
            return SttModelAsset(
                languageName: "Multilingual",
                modelName: "Medium Q8",
                sizeLabel: "823 MB",
                usageDescription: "Mac only, official ggml",
                directoryName: "whisper-official-medium-q8",
                fileName: "ggml-medium-q8_0.bin",
                sizeBytes: 823_369_779,
                sha256: "42a1ffcbe4167d224232443396968db4d02d4e8e87e213d3ee2e03095dea6502",
                source: .whisperCppOfficial
            )
        case .multilingualLargeTurboOfficial:
            return SttModelAsset(
                languageName: "Multilingual",
                modelName: "Large v3 Turbo Q8",
                sizeLabel: "874 MB",
                usageDescription: "Mac only, official ggml",
                directoryName: "whisper-official-large-v3-turbo-q8",
                fileName: "ggml-large-v3-turbo-q8_0.bin",
                sizeBytes: 874_188_075,
                sha256: "317eb69c11673c9de1e1f0d459b253999804ec71ac4c23c17ecf5fbe24e259a1",
                source: .whisperCppOfficial
            )
        }
    }

    public var displayName: String { asset.displayName }
}
