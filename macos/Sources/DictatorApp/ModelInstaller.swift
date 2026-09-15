import CryptoKit
import DictatorCore
import Foundation

enum ModelInstaller {
    struct Progress {
        var downloadedBytes: Int64
        var totalBytes: Int64
        var fileName: String
    }

    static func install(
        _ profile: SttModelProfile,
        to root: URL,
        onProgress: @escaping (Progress) -> Void
    ) throws {
        let directory = root.appendingPathComponent(profile.asset.directoryName, isDirectory: true)
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        let destination = directory.appendingPathComponent(profile.asset.fileName)
        let partial = directory.appendingPathComponent(profile.asset.fileName + ".partial")
        let asset = profile.asset

        if fileSize(destination) == asset.sizeBytes, (try? sha256(destination)) == asset.sha256 {
            onProgress(Progress(downloadedBytes: asset.sizeBytes, totalBytes: asset.sizeBytes, fileName: asset.fileName))
            return
        }

        try? FileManager.default.removeItem(at: partial)

        let session = DownloadSession(destination: partial, expectedBytes: asset.sizeBytes, fileName: asset.fileName, onProgress: onProgress)
        try session.run(url: asset.downloadURL)

        let got = fileSize(partial)
        guard got == asset.sizeBytes else {
            try? FileManager.default.removeItem(at: partial)
            throw InstallError.badSize(got: got, expected: asset.sizeBytes)
        }
        guard try looksLikeGgml(partial) else {
            try? FileManager.default.removeItem(at: partial)
            throw InstallError.notGgml
        }
        let digest = try sha256(partial)
        guard digest == asset.sha256 else {
            try? FileManager.default.removeItem(at: partial)
            throw InstallError.checksum
        }
        if FileManager.default.fileExists(atPath: destination.path) {
            try FileManager.default.removeItem(at: destination)
        }
        try FileManager.default.moveItem(at: partial, to: destination)
    }

    private static func fileSize(_ url: URL) -> Int64 {
        (try? FileManager.default.attributesOfItem(atPath: url.path)[.size] as? NSNumber)?.int64Value ?? 0
    }

    private static func sha256(_ url: URL) throws -> String {
        let handle = try FileHandle(forReadingFrom: url)
        defer { try? handle.close() }
        var hasher = SHA256()
        while true {
            let chunk = try handle.read(upToCount: 1_048_576) ?? Data()
            if chunk.isEmpty { break }
            hasher.update(data: chunk)
        }
        return hasher.finalize().map { String(format: "%02x", $0) }.joined()
    }

    private static func looksLikeGgml(_ url: URL) throws -> Bool {
        let handle = try FileHandle(forReadingFrom: url)
        defer { try? handle.close() }
        let prefix = try handle.read(upToCount: 4) ?? Data()
        return prefix == Data("ggml".utf8) || prefix == Data("gguf".utf8)
    }

    enum InstallError: LocalizedError {
        case network(String)
        case checksum
        case notGgml
        case badSize(got: Int64, expected: Int64)

        var errorDescription: String? {
            switch self {
            case .network(let message):
                return message
            case .checksum:
                return "Downloaded file failed the SHA-256 check"
            case .notGgml:
                return "Download was not a ggml file (Hugging Face HTML or LFS pointer)"
            case .badSize(let got, let expected):
                return "Downloaded \(got) bytes, expected \(expected)"
            }
        }
    }
}

private final class DownloadSession: NSObject, URLSessionDownloadDelegate {
    private let destination: URL
    private let expectedBytes: Int64
    private let fileName: String
    private let onProgress: (ModelInstaller.Progress) -> Void
    private var session: URLSession?
    private var error: Error?
    private let done = DispatchSemaphore(value: 0)

    init(
        destination: URL,
        expectedBytes: Int64,
        fileName: String,
        onProgress: @escaping (ModelInstaller.Progress) -> Void
    ) {
        self.destination = destination
        self.expectedBytes = expectedBytes
        self.fileName = fileName
        self.onProgress = onProgress
    }

    func run(url: URL) throws {
        let config = URLSessionConfiguration.ephemeral
        config.timeoutIntervalForRequest = 120
        config.timeoutIntervalForResource = 4 * 60 * 60
        config.httpAdditionalHeaders = [
            "User-Agent": "Dictator-macOS/0.1 (ggml model download)",
            "Accept": "application/octet-stream",
        ]
        config.httpMaximumConnectionsPerHost = 1
        let session = URLSession(configuration: config, delegate: self, delegateQueue: nil)
        self.session = session
        var request = URLRequest(url: url)
        request.timeoutInterval = 4 * 60 * 60
        session.downloadTask(with: request).resume()
        done.wait()
        session.finishTasksAndInvalidate()
        if let error {
            throw error
        }
    }

    func urlSession(
        _ session: URLSession,
        downloadTask: URLSessionDownloadTask,
        didWriteData bytesWritten: Int64,
        totalBytesWritten: Int64,
        totalBytesExpectedToWrite: Int64
    ) {
        let total = totalBytesExpectedToWrite > 0 ? totalBytesExpectedToWrite : expectedBytes
        onProgress(ModelInstaller.Progress(
            downloadedBytes: totalBytesWritten,
            totalBytes: total,
            fileName: fileName
        ))
    }

    func urlSession(
        _ session: URLSession,
        downloadTask: URLSessionDownloadTask,
        didFinishDownloadingTo location: URL
    ) {
        do {
            if let http = downloadTask.response as? HTTPURLResponse, !(200...299).contains(http.statusCode) {
                throw ModelInstaller.InstallError.network("HTTP \(http.statusCode)")
            }
            if FileManager.default.fileExists(atPath: destination.path) {
                try FileManager.default.removeItem(at: destination)
            }
            do {
                try FileManager.default.moveItem(at: location, to: destination)
            } catch {
                try FileManager.default.copyItem(at: location, to: destination)
            }
        } catch {
            self.error = error
        }
    }

    func urlSession(_ session: URLSession, task: URLSessionTask, didCompleteWithError error: Error?) {
        if self.error == nil {
            self.error = error.map { ModelInstaller.InstallError.network($0.localizedDescription) }
        }
        done.signal()
    }
}
