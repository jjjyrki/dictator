import AppKit
import DictatorCore

final class SettingsWindowController: NSWindowController {
    private let coordinator: DictationCoordinator
    private let tap: FnTapController
    private let modelPopup = NSPopUpButton()
    private let languageField = NSTextField(labelWithString: "")
    private let insertPopup = NSPopUpButton()
    private let partials = NSButton(checkboxWithTitle: "Show live partial transcripts", target: nil, action: nil)
    private let status = NSTextField(wrappingLabelWithString: "")
    private let download = NSButton(title: "Download model", target: nil, action: nil)
    private let retryTap = NSButton(title: "Retry Fn watcher", target: nil, action: nil)
    private let copyDiagnostics = NSButton(title: "Copy diagnostics", target: nil, action: nil)

    init(coordinator: DictationCoordinator, tap: FnTapController) {
        self.coordinator = coordinator
        self.tap = tap
        let window = NSWindow(
            contentRect: NSRect(x: 0, y: 0, width: 520, height: 620),
            styleMask: [.titled, .closable],
            backing: .buffered,
            defer: false
        )
        window.title = "Dictator"
        super.init(window: window)
        window.contentView = build()
        refresh()
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) { nil }

    private func build() -> NSView {
        let instructions = NSTextField(wrappingLabelWithString: """
        Turn off Keyboard → Dictation, and set Globe / Fn to Do Nothing. Hold Fn to talk. Double-press to latch. Escape cancels. Whisper tags like [BLANK_AUDIO] are discarded.

        Grant Accessibility for /Applications/Dictator.app. Input Monitoring is denied for this ad-hoc binary, so Globe/Fn is invisible until Accessibility is on. The watcher is listen-only and does not swallow typing.
        """)
        let mic = button("Microphone settings", #selector(openMic))
        let access = button("Accessibility settings", #selector(openAccess))
        let input = button("Input Monitoring settings", #selector(openInput))
        let keyboard = button("Keyboard settings", #selector(openKeyboard))

        modelPopup.removeAllItems()
        for profile in SttModelProfile.allCases {
            modelPopup.addItem(withTitle: profile.displayName)
            modelPopup.lastItem?.representedObject = profile.rawValue
        }
        modelPopup.target = self
        modelPopup.action = #selector(modelChanged)

        insertPopup.removeAllItems()
        insertPopup.addItem(withTitle: "Merge at caret")
        insertPopup.lastItem?.representedObject = InsertMode.merge.rawValue
        insertPopup.addItem(withTitle: "Replace whole field")
        insertPopup.lastItem?.representedObject = InsertMode.replace.rawValue
        insertPopup.target = self
        insertPopup.action = #selector(insertChanged)

        partials.target = self
        partials.action = #selector(partialsChanged)
        download.target = self
        download.action = #selector(downloadModel)
        retryTap.target = self
        retryTap.action = #selector(retryWatcher)
        retryTap.bezelStyle = .rounded
        copyDiagnostics.target = self
        copyDiagnostics.action = #selector(copyDiagnosticsDump)
        copyDiagnostics.bezelStyle = .rounded
        let languages = button("Spoken languages…", #selector(pickLanguages))
        let load = button("Load selected model", #selector(loadModel))

        status.lineBreakMode = .byWordWrapping
        status.maximumNumberOfLines = 16

        let stack = NSStackView(views: [
            instructions,
            row(mic, access),
            row(input, keyboard),
            labeled("Model", modelPopup),
            languages,
            languageField,
            labeled("Insert", insertPopup),
            partials,
            row(download, load),
            row(retryTap, copyDiagnostics),
            status,
        ])
        stack.orientation = .vertical
        stack.alignment = .leading
        stack.spacing = 10
        stack.edgeInsets = NSEdgeInsets(top: 20, left: 20, bottom: 20, right: 20)
        stack.translatesAutoresizingMaskIntoConstraints = false
        let root = NSView()
        root.addSubview(stack)
        NSLayoutConstraint.activate([
            stack.leadingAnchor.constraint(equalTo: root.leadingAnchor),
            stack.trailingAnchor.constraint(equalTo: root.trailingAnchor),
            stack.topAnchor.constraint(equalTo: root.topAnchor),
            stack.bottomAnchor.constraint(lessThanOrEqualTo: root.bottomAnchor),
            root.widthAnchor.constraint(equalToConstant: 520),
        ])
        return root
    }

    func refresh() {
        let profile = Preferences.shared.profile
        if let index = SttModelProfile.allCases.firstIndex(of: profile) {
            modelPopup.selectItem(at: index)
        }
        insertPopup.selectItem(at: Preferences.shared.insertMode == .merge ? 0 : 1)
        partials.state = Preferences.shared.partialsEnabled ? .on : .off
        let names = Preferences.shared.spokenLanguages.map(\.displayName).sorted().joined(separator: ", ")
        languageField.stringValue = profile.isMultilingual
            ? "Languages: \(names)"
            : "English-only checkpoint"
        let installed = Preferences.shared.isInstalled(profile)
        download.isEnabled = !installed
        status.stringValue = statusLine(installed: installed)
    }

    private func statusLine(installed: Bool) -> String {
        let mic = Permissions.microphoneGranted() ? "Microphone OK" : "Microphone missing"
        let ax = Permissions.accessibilityTrusted() ? "Accessibility OK" : "Accessibility missing"
        let input = Permissions.inputMonitoringGranted() ? "Input Monitoring OK" : "Input Monitoring missing"
        let model = installed ? "Model installed" : "Model not installed"
        return "\(mic). \(ax). \(input). \(model).\n\(coordinator.lastLoadMessage)\n\(coordinator.lastOutcome)\n\(tap.statusLine)\n\(Diagnostics.nextStepLine())\n\(Permissions.identityLine())"
    }

    @objc private func retryWatcher() {
        tap.retryNow()
        refresh()
    }

    @objc private func copyDiagnosticsDump() {
        let dump = tap.diagnosticDump(lastLoad: coordinator.lastLoadMessage, lastOutcome: coordinator.lastOutcome)
        NSPasteboard.general.clearContents()
        NSPasteboard.general.setString(dump, forType: .string)
        status.stringValue = "Diagnostics copied. Paste them in chat.\n\n" + statusLine(
            installed: Preferences.shared.isInstalled(Preferences.shared.profile)
        )
    }

    private func labeled(_ title: String, _ view: NSView) -> NSStackView {
        let label = NSTextField(labelWithString: title)
        label.font = .boldSystemFont(ofSize: 12)
        return NSStackView(views: [label, view])
    }

    private func row(_ left: NSView, _ right: NSView) -> NSStackView {
        let stack = NSStackView(views: [left, right])
        stack.orientation = .horizontal
        return stack
    }

    private func button(_ title: String, _ selector: Selector) -> NSButton {
        let button = NSButton(title: title, target: self, action: selector)
        button.bezelStyle = .rounded
        return button
    }

    @objc private func openMic() { Permissions.openMicrophoneSettings() }
    @objc private func openAccess() { Permissions.openAccessibilitySettings() }
    @objc private func openInput() { Permissions.openInputMonitoringSettings() }
    @objc private func openKeyboard() { Permissions.openKeyboardSettings() }

    @objc private func modelChanged() {
        guard let raw = modelPopup.selectedItem?.representedObject as? String,
              let profile = SttModelProfile(rawValue: raw)
        else { return }
        Preferences.shared.profile = profile
        refresh()
    }

    @objc private func insertChanged() {
        guard let raw = insertPopup.selectedItem?.representedObject as? String,
              let mode = InsertMode(rawValue: raw)
        else { return }
        Preferences.shared.insertMode = mode
    }

    @objc private func partialsChanged() {
        Preferences.shared.partialsEnabled = partials.state == .on
    }

    @objc private func loadModel() {
        let profile = Preferences.shared.profile
        status.stringValue = "Loading \(profile.asset.fileName)…"
        coordinator.reloadEngineOffMain { [weak self] message in
            self?.refresh()
            self?.status.stringValue = message
        }
    }

    @objc private func downloadModel() {
        let profile = Preferences.shared.profile
        download.isEnabled = false
        status.stringValue = "Downloading \(profile.asset.fileName)…"
        DispatchQueue.global(qos: .userInitiated).async { [weak self] in
            do {
                try ModelInstaller.install(profile, to: Preferences.shared.modelsRoot) { progress in
                    DispatchQueue.main.async {
                        let mb = Double(progress.downloadedBytes) / 1_000_000
                        let total = Double(progress.totalBytes) / 1_000_000
                        self?.status.stringValue = String(format: "Downloading %.0f / %.0f MB", mb, total)
                    }
                }
                DispatchQueue.main.async {
                    self?.status.stringValue = "Downloaded \(profile.asset.fileName). Loading…"
                    self?.coordinator.reloadEngineOffMain { [weak self] message in
                        self?.refresh()
                        self?.status.stringValue = message
                    }
                }
            } catch {
                DispatchQueue.main.async {
                    self?.download.isEnabled = true
                    self?.status.stringValue = "Download failed: \(error.localizedDescription)"
                }
            }
        }
    }

    @objc private func pickLanguages() {
        let alert = NSAlert()
        alert.messageText = "Spoken languages"
        alert.informativeText = "Up to four Whisper languages. Detection uses only this list."
        alert.addButton(withTitle: "OK")
        alert.addButton(withTitle: "Cancel")
        let list = NSStackView()
        list.orientation = .vertical
        list.alignment = .leading
        var boxes: [NSButton] = []
        let selected = Preferences.shared.spokenLanguages
        for language in SpokenLanguage.catalog {
            let box = NSButton(checkboxWithTitle: language.displayName, target: nil, action: nil)
            box.state = selected.contains(language) ? .on : .off
            box.identifier = NSUserInterfaceItemIdentifier(language.whisperCode)
            boxes.append(box)
            list.addArrangedSubview(box)
        }
        let scroll = NSScrollView(frame: NSRect(x: 0, y: 0, width: 280, height: 240))
        scroll.hasVerticalScroller = true
        let document = NSView(frame: NSRect(x: 0, y: 0, width: 260, height: CGFloat(boxes.count) * 22))
        list.frame = document.bounds
        list.translatesAutoresizingMaskIntoConstraints = false
        document.addSubview(list)
        NSLayoutConstraint.activate([
            list.leadingAnchor.constraint(equalTo: document.leadingAnchor),
            list.trailingAnchor.constraint(equalTo: document.trailingAnchor),
            list.topAnchor.constraint(equalTo: document.topAnchor),
            list.bottomAnchor.constraint(equalTo: document.bottomAnchor),
        ])
        scroll.documentView = document
        alert.accessoryView = scroll
        guard alert.runModal() == .alertFirstButtonReturn else { return }
        var next: [SpokenLanguage] = []
        for box in boxes where box.state == .on {
            if let code = box.identifier?.rawValue, let language = SpokenLanguage.from(whisperCode: code) {
                next.append(language)
            }
        }
        if next.isEmpty {
            next = Array(SpokenLanguage.defaultSelection)
        }
        Preferences.shared.spokenLanguages = Set(next.prefix(SpokenLanguage.maxSelected))
        refresh()
    }
}
