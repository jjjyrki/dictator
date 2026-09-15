import Darwin
import Foundation

final class WhisperEngine {
    private typealias CreateFn = @convention(c) (
        UnsafePointer<CChar>?,
        Int32,
        UnsafeMutablePointer<CChar>?,
        Int32
    ) -> OpaquePointer?
    private typealias TranscribeFn = @convention(c) (
        OpaquePointer?,
        UnsafePointer<Float>?,
        Int,
        Int32,
        UnsafePointer<CChar>?,
        UnsafePointer<CChar>?
    ) -> UnsafeMutablePointer<CChar>?
    private typealias FreeStringFn = @convention(c) (UnsafeMutablePointer<CChar>?) -> Void
    private typealias CloseFn = @convention(c) (OpaquePointer?) -> Void

    private let library: UnsafeMutableRawPointer
    private let create: CreateFn
    private let transcribeFn: TranscribeFn
    private let freeString: FreeStringFn
    private let close: CloseFn
    private var handle: OpaquePointer?
    private let lock = NSLock()
    private let threads: Int32

    init(modelPath: String, shortenAudioContext: Bool = true) throws {
        guard let libraryPath = Self.libraryPath() else {
            throw EngineError.loadFailed("libdictator_api.dylib not found next to the app")
        }
        guard let loaded = dlopen(libraryPath, RTLD_NOW | RTLD_LOCAL) else {
            throw EngineError.loadFailed(String(cString: dlerror()))
        }
        guard
            let create = dlsym(loaded, "dictator_whisper_create").map({ unsafeBitCast($0, to: CreateFn.self) }),
            let transcribeFn = dlsym(loaded, "dictator_whisper_transcribe").map({
                unsafeBitCast($0, to: TranscribeFn.self)
            }),
            let freeString = dlsym(loaded, "dictator_whisper_free_string").map({
                unsafeBitCast($0, to: FreeStringFn.self)
            }),
            let close = dlsym(loaded, "dictator_whisper_close").map({ unsafeBitCast($0, to: CloseFn.self) })
        else {
            dlclose(loaded)
            throw EngineError.loadFailed("libdictator_api.dylib is missing Whisper symbols")
        }
        library = loaded
        self.create = create
        self.transcribeFn = transcribeFn
        self.freeString = freeString
        self.close = close

        var error = [CChar](repeating: 0, count: 1024)
        handle = modelPath.withCString { path in
            create(path, shortenAudioContext ? 1 : 0, &error, Int32(error.count))
        }
        if handle == nil {
            let message = String(cString: error)
            throw EngineError.loadFailed(message.isEmpty ? "could not load Whisper" : message)
        }
        threads = Int32(min(8, max(1, ProcessInfo.processInfo.activeProcessorCount)))
    }

    deinit {
        if let handle {
            close(handle)
        }
        dlclose(library)
    }

    func transcribe(
        pcm16kMono: [Float],
        language: String,
        allowedLanguages: String?
    ) -> String {
        lock.lock()
        defer { lock.unlock() }
        guard let handle, !pcm16kMono.isEmpty else { return "" }
        return pcm16kMono.withUnsafeBufferPointer { buffer in
            language.withCString { langPointer in
                func run(_ allowed: UnsafePointer<CChar>?) -> String {
                    guard let raw = transcribeFn(
                        handle,
                        buffer.baseAddress,
                        buffer.count,
                        threads,
                        langPointer,
                        allowed
                    ) else {
                        return ""
                    }
                    defer { freeString(raw) }
                    return String(cString: raw)
                }
                if let allowedLanguages {
                    return allowedLanguages.withCString { run($0) }
                }
                return run(nil)
            }
        }
    }

    private static func libraryPath() -> String? {
        let fileManager = FileManager.default
        let executableDir = URL(fileURLWithPath: CommandLine.arguments[0]).deletingLastPathComponent()
        let candidates: [URL] = [
            Bundle.main.bundleURL.appendingPathComponent("Contents/MacOS/libdictator_api.dylib"),
            executableDir.appendingPathComponent("libdictator_api.dylib"),
            executableDir.appendingPathComponent("../native/build-macos/libdictator_api.dylib"),
            URL(fileURLWithPath: FileManager.default.currentDirectoryPath)
                .appendingPathComponent("native/build-macos/libdictator_api.dylib"),
        ]
        return candidates
            .map { $0.standardizedFileURL }
            .first { fileManager.isReadableFile(atPath: $0.path) }?
            .path
    }

    enum EngineError: LocalizedError {
        case loadFailed(String)

        var errorDescription: String? {
            switch self {
            case .loadFailed(let message):
                return message
            }
        }
    }
}
