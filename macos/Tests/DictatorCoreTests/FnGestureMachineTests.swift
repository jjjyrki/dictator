import DictatorCore
import XCTest

final class FnGestureMachineTests: XCTestCase {
    func testHoldThenReleaseCommits() {
        var machine = FnGestureMachine()
        let down = machine.handleDown()
        XCTAssertEqual(down.commands, [.startRecording])
        XCTAssertEqual(machine.phase, .firstDown)

        XCTAssertEqual(machine.handle(.holdThreshold).commands, [])
        XCTAssertEqual(machine.phase, .pushToTalk)

        let up = machine.handleUp()
        XCTAssertEqual(up.commands, [.commit])
        XCTAssertEqual(machine.phase, .idle)
    }

    func testLoneShortTapCancels() {
        var machine = FnGestureMachine()
        _ = machine.handleDown()
        let up = machine.handleUp()
        XCTAssertEqual(up.commands, [])
        XCTAssertEqual(up.cancelWork, [.holdThreshold])
        XCTAssertEqual(machine.phase, .shortReleased)

        let timedOut = machine.handle(.tapWindow)
        XCTAssertEqual(timedOut.commands, [.cancel])
        XCTAssertEqual(machine.phase, .idle)
    }

    func testDoublePressLatchesThenCommitsOnNextDown() {
        var machine = FnGestureMachine()
        XCTAssertEqual(machine.handleDown().commands, [.startRecording])
        _ = machine.handleUp()
        let secondDown = machine.handleDown()
        XCTAssertEqual(secondDown.commands, [])
        XCTAssertEqual(secondDown.cancelWork, [.tapWindow])
        XCTAssertEqual(machine.phase, .latched)

        let stop = machine.handleDown()
        XCTAssertEqual(stop.commands, [.commit])
        XCTAssertEqual(machine.phase, .idle)
    }

    func testEscapeCancelsLatchedRecording() {
        var machine = FnGestureMachine()
        XCTAssertEqual(machine.handleDown().commands, [.startRecording])
        _ = machine.handleUp()
        _ = machine.handleDown()
        XCTAssertEqual(machine.phase, .latched)
        let escape = machine.handleEscape()
        XCTAssertEqual(escape.commands, [.cancel])
        XCTAssertEqual(escape.cancelWork, [.holdThreshold, .tapWindow])
        XCTAssertEqual(machine.phase, .idle)
    }

    func testEscapeIgnoredWhenIdle() {
        var machine = FnGestureMachine()
        XCTAssertEqual(machine.handleEscape(), .none)
    }

    func testEscapeCancelsHoldToTalk() {
        var machine = FnGestureMachine()
        _ = machine.handleDown()
        XCTAssertEqual(machine.handle(.holdThreshold).commands, [])
        XCTAssertEqual(machine.phase, .pushToTalk)
        XCTAssertEqual(machine.handleEscape().commands, [.cancel])
        XCTAssertEqual(machine.phase, .idle)
    }

    func testHoldTimerIgnoredAfterEarlyRelease() {
        var machine = FnGestureMachine()
        _ = machine.handleDown()
        _ = machine.handleUp()
        XCTAssertEqual(machine.handle(.holdThreshold).commands, [])
        XCTAssertEqual(machine.phase, .shortReleased)
    }
}
