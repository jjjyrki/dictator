import AppKit

final class AppDelegate: NSObject, NSApplicationDelegate {
    private let coordinator = DictationCoordinator()
    private var statusItem: NSStatusItem?
    private var settings: SettingsWindowController?
    private var tap: FnTapController?

    func applicationDidFinishLaunching(_ notification: Notification) {
        NSApp.setActivationPolicy(.accessory)
        let item = NSStatusBar.system.statusItem(withLength: NSStatusItem.variableLength)
        item.button?.title = "Dictate"
        let menu = NSMenu()
        menu.addItem(withTitle: "Settings…", action: #selector(openSettings), keyEquivalent: ",")
        menu.addItem(.separator())
        menu.addItem(withTitle: "Quit Dictator", action: #selector(NSApplication.terminate(_:)), keyEquivalent: "q")
        item.menu = menu
        statusItem = item

        coordinator.reloadEngineOffMain { _ in }
        let tap = FnTapController(
            onDown: { [weak self] in self?.coordinator.handleFnDown() },
            onUp: { [weak self] in self?.coordinator.handleFnUp() },
            onEscape: { [weak self] in self?.coordinator.handleEscape() }
        )
        tap.onStatus = { [weak self] in
            self?.settings?.refresh()
        }
        self.tap = tap
        tap.start()
        presentSettings(activate: false)
    }

    @objc private func openSettings() {
        presentSettings(activate: true)
    }

    private func presentSettings(activate: Bool) {
        if settings == nil {
            guard let tap else { return }
            settings = SettingsWindowController(coordinator: coordinator, tap: tap)
        }
        settings?.refresh()
        settings?.showWindow(nil)
        if activate {
            NSApp.activate(ignoringOtherApps: true)
        }
    }
}
