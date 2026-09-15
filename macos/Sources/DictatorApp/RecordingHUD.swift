import AppKit

final class RecordingHUD {
    private let panel: NSPanel
    private let status = NSTextField(labelWithString: "")
    private let detail = NSTextField(labelWithString: "")
    private let level = NSLevelIndicator()

    init() {
        panel = NSPanel(
            contentRect: NSRect(x: 0, y: 0, width: 280, height: 88),
            styleMask: [.nonactivatingPanel, .borderless],
            backing: .buffered,
            defer: true
        )
        panel.level = .statusBar
        panel.isOpaque = false
        panel.backgroundColor = .clear
        panel.hasShadow = true
        panel.ignoresMouseEvents = true
        panel.collectionBehavior = [.canJoinAllSpaces, .fullScreenAuxiliary]

        let box = NSVisualEffectView(frame: NSRect(x: 0, y: 0, width: 280, height: 88))
        box.material = .hudWindow
        box.state = .active
        box.wantsLayer = true
        box.layer?.cornerRadius = 12

        status.font = .systemFont(ofSize: 13, weight: .semibold)
        status.alignment = .center
        detail.font = .systemFont(ofSize: 11)
        detail.alignment = .center
        detail.textColor = .secondaryLabelColor
        detail.maximumNumberOfLines = 2
        detail.lineBreakMode = .byTruncatingTail
        level.minValue = 0
        level.maxValue = 1
        level.warningValue = 0.7
        level.criticalValue = 0.9
        level.levelIndicatorStyle = .continuousCapacity

        let stack = NSStackView(views: [status, detail, level])
        stack.orientation = .vertical
        stack.spacing = 6
        stack.edgeInsets = NSEdgeInsets(top: 14, left: 16, bottom: 14, right: 16)
        stack.translatesAutoresizingMaskIntoConstraints = false
        box.addSubview(stack)
        NSLayoutConstraint.activate([
            stack.leadingAnchor.constraint(equalTo: box.leadingAnchor),
            stack.trailingAnchor.constraint(equalTo: box.trailingAnchor),
            stack.topAnchor.constraint(equalTo: box.topAnchor),
            stack.bottomAnchor.constraint(equalTo: box.bottomAnchor),
        ])
        panel.contentView = box
    }

    func show(status: String, detail: String? = nil, level: Float? = nil) {
        self.status.stringValue = status
        if let detail {
            self.detail.stringValue = detail
        }
        if let level {
            self.level.doubleValue = Double(level)
            self.level.isHidden = false
        } else {
            self.level.isHidden = true
        }
        if !panel.isVisible, let screen = NSScreen.main {
            let frame = screen.visibleFrame
            let size = panel.frame.size
            panel.setFrameOrigin(NSPoint(
                x: frame.midX - size.width / 2,
                y: frame.minY + 80
            ))
        }
        panel.orderFrontRegardless()
    }

    func hide() {
        detail.stringValue = ""
        panel.orderOut(nil)
    }
}
