import AVFoundation
import Foundation

final class MicrophoneCapture {
    static let sampleRate: Double = 16_000
    static let maxSamples = Int(sampleRate * 60)

    private let engine = AVAudioEngine()
    private var converter: AVAudioConverter?
    private let lock = NSLock()
    private var samples: [Float] = []
    private var levelListener: ((Float) -> Void)?
    private var lastLevelEmit: TimeInterval = 0
    private var started = false

    func setLevelListener(_ listener: ((Float) -> Void)?) {
        lock.lock()
        levelListener = listener
        lock.unlock()
    }

    func start() throws {
        lock.lock()
        samples.removeAll(keepingCapacity: true)
        lock.unlock()

        let input = engine.inputNode
        let inputFormat = input.outputFormat(forBus: 0)
        guard let targetFormat = AVAudioFormat(
            commonFormat: .pcmFormatFloat32,
            sampleRate: Self.sampleRate,
            channels: 1,
            interleaved: false
        ) else {
            throw CaptureError.format
        }
        converter = AVAudioConverter(from: inputFormat, to: targetFormat)
        input.removeTap(onBus: 0)
        input.installTap(onBus: 0, bufferSize: 1024, format: inputFormat) { [weak self] buffer, _ in
            self?.append(buffer, targetFormat: targetFormat)
        }
        do {
            engine.prepare()
            try engine.start()
        } catch {
            input.removeTap(onBus: 0)
            converter = nil
            throw error
        }
        started = true
    }

    func stop() -> [Float] {
        stopAndReset()
        lock.lock()
        let clip = samples
        samples.removeAll(keepingCapacity: true)
        lock.unlock()
        return clip
    }

    private func stopAndReset() {
        if started {
            engine.inputNode.removeTap(onBus: 0)
            engine.stop()
            started = false
        }
        converter = nil
    }

    func snapshot() -> [Float] {
        lock.lock()
        defer { lock.unlock() }
        return samples
    }

    private func append(_ buffer: AVAudioPCMBuffer, targetFormat: AVAudioFormat) {
        guard let converter else { return }
        let ratio = targetFormat.sampleRate / buffer.format.sampleRate
        let capacity = AVAudioFrameCount((Double(buffer.frameLength) * ratio).rounded(.up) + 32)
        guard let out = AVAudioPCMBuffer(pcmFormat: targetFormat, frameCapacity: capacity) else { return }
        var error: NSError?
        var consumed = false
        let inputBlock: AVAudioConverterInputBlock = { _, status in
            if consumed {
                status.pointee = .noDataNow
                return nil
            }
            consumed = true
            status.pointee = .haveData
            return buffer
        }
        converter.convert(to: out, error: &error, withInputFrom: inputBlock)
        converter.reset()
        guard error == nil, let channel = out.floatChannelData?[0] else { return }
        let count = Int(out.frameLength)
        var rms: Float = 0
        lock.lock()
        let room = max(0, Self.maxSamples - samples.count)
        let keep = min(count, room)
        samples.reserveCapacity(samples.count + keep)
        for i in 0..<count {
            let sample = channel[i]
            if i < keep {
                samples.append(sample)
            }
            rms += sample * sample
        }
        let level = count == 0 ? 0 : sqrt(rms / Float(count))
        let now = ProcessInfo.processInfo.systemUptime
        let emit = now - lastLevelEmit >= 0.1
        if emit { lastLevelEmit = now }
        let listener = emit ? levelListener : nil
        lock.unlock()
        guard let listener else { return }
        let shown = min(1, level * 8)
        DispatchQueue.main.async {
            listener(shown)
        }
    }

    enum CaptureError: Error {
        case format
    }
}
