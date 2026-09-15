import Foundation

/// Globe / Fn hardware identity. 63 is `kVK_Function`. 179 is Globe on some Apple keyboards.
public enum FnHardware {
    public static let keyCodes: Set<UInt16> = [63, 179]
    public static let escapeKeyCode: UInt16 = 53
    public static let appleVendorFnUsage: UInt32 = 0x03

    public static func isFnKeyCode(_ keyCode: UInt16) -> Bool {
        keyCodes.contains(keyCode)
    }

    /// Globe/Fn `flagsChanged` uses keycode 63 or 179. Some Globe events use keycode 0
    /// with only a `.function` flag edge. Arrow keys and F-keys also set `.function`;
    /// those must not count as Fn or a steal tap will eat typing.
    public static func isFnFlagsEvent(keyCode: UInt16, functionFlag: Bool, previousFunctionFlag: Bool) -> Bool {
        if isFnKeyCode(keyCode) {
            return true
        }
        if keyCode == 0 {
            return functionFlag != previousFunctionFlag
        }
        return false
    }

    public static func isFnHID(usagePage: UInt32, usage: UInt32) -> Bool {
        // USB HID keyboard usages are not Carbon keycodes. 63 on page 0x07 is F6.
        if usagePage == 0xFF || usagePage == 0xFF00 || usagePage == 0xFF01 {
            return usage == appleVendorFnUsage
        }
        return false
    }
}
