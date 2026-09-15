import DictatorCore
import XCTest

final class FnHardwareTests: XCTestCase {
    func testRecognizesFunctionAndGlobeKeycodes() {
        XCTAssertTrue(FnHardware.isFnKeyCode(63))
        XCTAssertTrue(FnHardware.isFnKeyCode(179))
        XCTAssertFalse(FnHardware.isFnKeyCode(0))
        XCTAssertFalse(FnHardware.isFnKeyCode(55))
        XCTAssertEqual(FnHardware.escapeKeyCode, 53)
    }

    func testRecognizesAppleVendorFnHID() {
        XCTAssertTrue(FnHardware.isFnHID(usagePage: 0xFF01, usage: 0x03))
        XCTAssertFalse(FnHardware.isFnHID(usagePage: 0x07, usage: 63))
        XCTAssertFalse(FnHardware.isFnHID(usagePage: 0x07, usage: 0x04))
        XCTAssertFalse(FnHardware.isFnHID(usagePage: 0xFF01, usage: 0x04))
    }

    func testRecognizesFunctionFlagEdgeWithoutFnKeycode() {
        XCTAssertTrue(FnHardware.isFnFlagsEvent(keyCode: 0, functionFlag: true, previousFunctionFlag: false))
        XCTAssertTrue(FnHardware.isFnFlagsEvent(keyCode: 63, functionFlag: false, previousFunctionFlag: false))
        XCTAssertTrue(FnHardware.isFnFlagsEvent(keyCode: 179, functionFlag: true, previousFunctionFlag: true))
        XCTAssertFalse(FnHardware.isFnFlagsEvent(keyCode: 56, functionFlag: true, previousFunctionFlag: false))
        XCTAssertFalse(FnHardware.isFnFlagsEvent(keyCode: 123, functionFlag: true, previousFunctionFlag: false))
        XCTAssertFalse(FnHardware.isFnFlagsEvent(keyCode: 122, functionFlag: true, previousFunctionFlag: false))
        XCTAssertFalse(FnHardware.isFnFlagsEvent(keyCode: 0, functionFlag: true, previousFunctionFlag: true))
    }

    func testVendorHIDPages() {
        XCTAssertTrue(FnHardware.isFnHID(usagePage: 0xFF, usage: 0x03))
        XCTAssertTrue(FnHardware.isFnHID(usagePage: 0xFF00, usage: 0x03))
        XCTAssertFalse(FnHardware.isFnHID(usagePage: 0x01, usage: 0x03))
    }
}
