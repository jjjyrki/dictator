import CoreFoundation
import DictatorCore
import Foundation
import IOKit.hid

/// Opens the keyboard HID stream. That is what actually puts a signed .app in
/// Input Monitoring. A listen-only CG tap can succeed and still never see Fn.
final class FnHIDListener {
    private var manager: IOHIDManager?
    private let onFn: (Bool) -> Void
    private(set) var description = "HID closed"
    private(set) var lastOpen: kern_return_t = 0
    private(set) var callbackCount = 0
    private(set) var matchedFnCount = 0
    private(set) var lastValueLine = "none"
    private(set) var attachedDeviceCount = 0

    init(onFn: @escaping (Bool) -> Void) {
        self.onFn = onFn
    }

    func start() {
        stop()
        let manager = IOHIDManagerCreate(kCFAllocatorDefault, IOOptionBits(kIOHIDOptionsTypeNone))
        let matches: [[String: Any]] = [
            [
                kIOHIDDeviceUsagePageKey as String: kHIDPage_GenericDesktop,
                kIOHIDDeviceUsageKey as String: kHIDUsage_GD_Keyboard,
            ],
            [
                kIOHIDDeviceUsagePageKey as String: kHIDPage_GenericDesktop,
                kIOHIDDeviceUsageKey as String: kHIDUsage_GD_Keypad,
            ],
        ]
        IOHIDManagerSetDeviceMatchingMultiple(manager, matches as CFArray)
        let elementMatches: [[String: Any]] = [
            [
                kIOHIDElementUsagePageKey as String: 0xFF,
                kIOHIDElementUsageKey as String: FnHardware.appleVendorFnUsage,
            ],
            [
                kIOHIDElementUsagePageKey as String: 0xFF00,
                kIOHIDElementUsageKey as String: FnHardware.appleVendorFnUsage,
            ],
            [
                kIOHIDElementUsagePageKey as String: 0xFF01,
                kIOHIDElementUsageKey as String: FnHardware.appleVendorFnUsage,
            ],
        ]
        IOHIDManagerSetInputValueMatchingMultiple(manager, elementMatches as CFArray)
        let context = Unmanaged.passUnretained(self).toOpaque()
        IOHIDManagerRegisterInputValueCallback(manager, { context, _, _, value in
            guard let context else { return }
            Unmanaged<FnHIDListener>.fromOpaque(context).takeUnretainedValue().handle(value)
        }, context)
        IOHIDManagerScheduleWithRunLoop(manager, CFRunLoopGetMain(), CFRunLoopMode.commonModes.rawValue)
        let opened = IOHIDManagerOpen(manager, IOOptionBits(kIOHIDOptionsTypeNone))
        self.manager = manager
        lastOpen = opened
        if let set = IOHIDManagerCopyDevices(manager) {
            attachedDeviceCount = CFSetGetCount(set)
        } else {
            attachedDeviceCount = 0
        }
        let name = Diagnostics.ioReturnName(opened)
        if opened == kIOReturnSuccess {
            description = "HID keyboard open devices=\(attachedDeviceCount)"
        } else {
            description = "HID keyboard denied \(name) (\(opened) \(Diagnostics.hex(opened)))"
        }
    }

    func stop() {
        guard let manager else { return }
        IOHIDManagerUnscheduleFromRunLoop(manager, CFRunLoopGetMain(), CFRunLoopMode.commonModes.rawValue)
        IOHIDManagerClose(manager, IOOptionBits(kIOHIDOptionsTypeNone))
        self.manager = nil
        description = "HID closed"
    }

    private func handle(_ value: IOHIDValue) {
        let element = IOHIDValueGetElement(value)
        let page = IOHIDElementGetUsagePage(element)
        let usage = IOHIDElementGetUsage(element)
        let integer = IOHIDValueGetIntegerValue(value)
        callbackCount += 1
        lastValueLine = String(format: "page=0x%X usage=%u value=%ld #\(callbackCount)", page, usage, integer)
        guard FnHardware.isFnHID(usagePage: page, usage: usage) else { return }
        matchedFnCount += 1
        onFn(integer != 0)
    }

    func diagnosticLines() -> [String] {
        [
            "hidOpen=\(Diagnostics.ioReturnName(lastOpen)) raw=\(lastOpen) \(Diagnostics.hex(lastOpen))",
            "hidDevices=\(attachedDeviceCount) hidCallbacks=\(callbackCount) hidFnMatches=\(matchedFnCount)",
            "lastHid=\(lastValueLine)",
        ]
    }
}
