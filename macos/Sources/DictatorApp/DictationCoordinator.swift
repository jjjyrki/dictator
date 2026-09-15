import AppKit
import DictatorCore
import Foundation

final class DictationCoordinator {
    private var machine = FnGestureMachine()
    private var holdTimer: Timer?
    private var tapTimer: Timer?
    private let hud = RecordingHUD()
    private let capture = MicrophoneCapture()
    private var engine: WhisperEngine?
    private var target: FocusedEditor?
    private var generation = 0
    private var busy = false
    private var partialInFlight = false
    private var partialTimer: Timer?
    private(set) var lastLoadMessage = "Model not loaded yet"
    private(set) var lastOutcome = "none"

    func reloadEngineOffMain(_ done: @escaping (String) -> Void) {
        let profile = Preferences.shared.profile
        guard Preferences.shared.isInstalled(profile) else {
            let message = "Download \(profile.asset.fileName) first."
            lastLoadMessage = message
            engine = nil
            done(message)
            return
        }
        DispatchQueue.global(qos: .userInitiated).async { [weak self] in
            do {
                let next = try WhisperEngine(
                    modelPath: Preferences.shared.modelFile(for: profile).path,
                    shortenAudioContext: profile.shortenAudioContext
                )
                DispatchQueue.main.async {
                    guard let self else { return }
                    self.engine = next
                    self.lastLoadMessage = "Loaded \(profile.asset.fileName) (\(profile.asset.sizeLabel))."
                    done(self.lastLoadMessage)
                }
            } catch {
                let message = "Load failed: \(error.localizedDescription)"
                DispatchQueue.main.async {
                    guard let self else { return }
                    self.engine = nil
                    self.lastLoadMessage = message
                    self.hud.show(status: "Model failed to load", detail: message)
                    done(message)
                }
            }
        }
    }

    func handleFnDown() {
        apply(machine.handleDown())
    }

    func handleFnUp() {
        apply(machine.handleUp())
    }

    func handleEscape() {
        if !EscapeGate.shouldCancel(phaseIsIdle: machine.phase == .idle, busy: busy) {
            return
        }
        lastOutcome = "cancelled by Escape"
        apply(machine.handleEscape())
        if busy {
            cancelRecording()
            busy = false
        }
        hud.show(status: "Cancelled")
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) { [weak self] in
            self?.hud.hide()
        }
    }

    private func apply(_ step: FnStep) {
        for work in step.cancelWork {
            cancel(work)
        }
        for command in step.commands {
            switch command {
            case .startRecording:
                if !startRecording() {
                    machine.reset()
                    cancel(.holdThreshold)
                    cancel(.tapWindow)
                    return
                }
            case .commit:
                commit()
            case .cancel:
                cancelRecording()
            }
        }
        for item in step.schedule {
            schedule(item.work, delay: item.delay)
        }
    }

    @discardableResult
    private func startRecording() -> Bool {
        if busy {
            return false
        }
        guard Permissions.microphoneGranted() else {
            hud.show(status: "Microphone permission needed")
            Permissions.requestMicrophone { [weak self] granted in
                DispatchQueue.main.async {
                    if !granted {
                        self?.hud.show(status: "Microphone permission needed")
                    } else {
                        self?.hud.hide()
                    }
                }
            }
            return false
        }
        if !Permissions.accessibilityTrusted() {
            Permissions.promptAccessibility()
        }
        let focused = FocusedEditor.current()
        guard engine != nil else {
            hud.show(status: "Load a Whisper model first")
            DispatchQueue.main.asyncAfter(deadline: .now() + 1.4) { [weak self] in
                self?.hud.hide()
            }
            return false
        }
        target = focused ?? FocusedEditor.frontmostForeign()
        generation += 1
        capture.setLevelListener { [weak self] level in
            self?.hud.show(status: "Listening", level: level)
        }
        do {
            try capture.start()
        } catch {
            hud.show(status: "Microphone failed")
            return false
        }
        hud.show(status: "Listening", detail: "", level: 0)
        if Preferences.shared.partialsEnabled {
            let cycle = generation
            partialTimer = Timer.scheduledTimer(withTimeInterval: 2, repeats: true) { [weak self] _ in
                self?.runPartial(cycle: cycle)
            }
        }
        return true
    }

    private func schedule(_ work: FnScheduledWork, delay: TimeInterval) {
        let timer = Timer.scheduledTimer(withTimeInterval: delay, repeats: false) { [weak self] _ in
            guard let self else { return }
            self.apply(self.machine.handle(work))
        }
        switch work {
        case .holdThreshold:
            holdTimer = timer
        case .tapWindow:
            tapTimer = timer
        }
    }

    private func cancel(_ work: FnScheduledWork) {
        switch work {
        case .holdThreshold:
            holdTimer?.invalidate()
            holdTimer = nil
        case .tapWindow:
            tapTimer?.invalidate()
            tapTimer = nil
        }
    }

    private func runPartial(cycle: Int) {
        guard cycle == generation, let engine, !busy, !partialInFlight else { return }
        let clip = capture.snapshot()
        guard clip.count >= Int(MicrophoneCapture.sampleRate) else { return }
        partialInFlight = true
        let request = transcriptionRequest()
        DispatchQueue.global(qos: .utility).async { [weak self] in
            let text = engine.transcribe(
                pcm16kMono: clip,
                language: request.language,
                allowedLanguages: request.allowed
            )
            DispatchQueue.main.async {
                self?.partialInFlight = false
                guard cycle == self?.generation else { return }
                if let speech = TranscriptNoise.usableSpeech(text) {
                    self?.hud.show(status: "Listening", detail: speech)
                }
            }
        }
    }

    private func commit() {
        partialTimer?.invalidate()
        partialTimer = nil
        capture.setLevelListener(nil)
        let clip = capture.stop()
        let focused = target
        target = nil
        busy = true
        partialInFlight = false
        generation += 1
        let cycle = generation
        hud.show(status: "Processing")
        let request = transcriptionRequest()
        guard let engine else {
            finishError("Model is not loaded")
            return
        }
        DispatchQueue.global(qos: .userInitiated).async { [weak self] in
            let text = engine.transcribe(
                pcm16kMono: clip,
                language: request.language,
                allowedLanguages: request.allowed
            )
            DispatchQueue.main.async {
                self?.finish(transcript: text, focused: focused, cycle: cycle)
            }
        }
    }

    private func cancelRecording() {
        partialTimer?.invalidate()
        partialTimer = nil
        capture.setLevelListener(nil)
        _ = capture.stop()
        target = nil
        partialInFlight = false
        generation += 1
        hud.hide()
    }

    private func finish(transcript: String, focused: FocusedEditor?, cycle: Int) {
        defer { busy = false }
        guard cycle == generation else { return }
        guard let trimmedReady = TranscriptNoise.usableSpeech(transcript) else {
            lastOutcome = "discarded noise"
            hud.show(status: "Discarded")
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.6) { [weak self] in
                self?.hud.hide()
            }
            return
        }
        let live = FocusedEditor.current()
        let front = FocusedEditor.frontmostForeign()
        let pid = InsertDestination.choose(
            selfPid: FocusedEditor.selfPid,
            livePid: live?.pid,
            storedPid: focused?.pid,
            frontmostPid: front?.pid
        )
        guard let pid else {
            lastOutcome = "copied, no foreign app"
            copyTranscript(trimmedReady)
            return
        }
        let target = [live, focused, front].compactMap { $0 }.first { $0.pid == pid }
            ?? FocusedEditor.pasteTarget(pid: pid)
        switch AccessibilityInserter.insert(
            into: target,
            transcript: trimmedReady,
            mode: Preferences.shared.insertMode
        ) {
        case .direct:
            lastOutcome = "inserted AX pid=\(target.pid) role=\(target.role)"
            hud.show(status: "Inserted")
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.7) { [weak self] in
                self?.hud.hide()
            }
        case .pastePosted(let previous, let before, let expected, let chunk):
            hud.show(status: "Inserting")
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.4) { [weak self] in
                guard let self else { return }
                if AccessibilityInserter.valueLooksInserted(
                    element: target.element,
                    before: before,
                    expected: expected,
                    chunk: chunk
                ) {
                    AccessibilityInserter.restoreClipboard(previous)
                    self.lastOutcome = "inserted paste pid=\(target.pid)"
                    self.hud.show(status: "Inserted")
                    DispatchQueue.main.asyncAfter(deadline: .now() + 0.7) {
                        self.hud.hide()
                    }
                } else {
                    self.lastOutcome = "copied after paste pid=\(target.pid)"
                    self.hud.show(status: "Copied", detail: "Cmd+V to paste")
                    DispatchQueue.main.asyncAfter(deadline: .now() + 2.0) {
                        self.hud.hide()
                    }
                }
            }
        case .failed:
            lastOutcome = "copied, AX set failed pid=\(target.pid)"
            copyTranscript(trimmedReady)
        }
    }

    private func copyTranscript(_ text: String) {
        if lastOutcome == "none" {
            lastOutcome = "copied to clipboard"
        }
        NSPasteboard.general.clearContents()
        NSPasteboard.general.setString(text, forType: .string)
        hud.show(status: "Copied", detail: "Cmd+V to paste")
        DispatchQueue.main.asyncAfter(deadline: .now() + 2.0) { [weak self] in
            self?.hud.hide()
        }
    }

    private func finishError(_ message: String) {
        busy = false
        hud.show(status: message)
        DispatchQueue.main.asyncAfter(deadline: .now() + 1.4) { [weak self] in
            self?.hud.hide()
        }
    }

    private func transcriptionRequest() -> (language: String, allowed: String?) {
        let profile = Preferences.shared.profile
        if !profile.isMultilingual {
            return ("en", nil)
        }
        let languages = Preferences.shared.spokenLanguages
        if languages.count == 1, let only = languages.first {
            return (only.whisperCode, nil)
        }
        let allowed = languages.map(\.whisperCode).joined(separator: ",")
        return ("auto", allowed)
    }
}
