import DictatorCore
import XCTest

final class DictationPoliciesTests: XCTestCase {
    func testEscapeIgnoredWhenIdleAndNotBusy() {
        XCTAssertFalse(EscapeGate.shouldCancel(phaseIsIdle: true, busy: false))
    }

    func testEscapeCancelsOpenGesture() {
        XCTAssertTrue(EscapeGate.shouldCancel(phaseIsIdle: false, busy: false))
    }

    func testEscapeCancelsInFlightTranscript() {
        XCTAssertTrue(EscapeGate.shouldCancel(phaseIsIdle: true, busy: true))
    }

    func testInsertPrefersLiveFocusOverStoredPid() {
        XCTAssertEqual(
            InsertDestination.choose(selfPid: 1, livePid: 42, storedPid: 7, frontmostPid: 9),
            42
        )
    }

    func testInsertSkipsDictatorEvenIfItIsLive() {
        XCTAssertEqual(
            InsertDestination.choose(selfPid: 1, livePid: 1, storedPid: 7, frontmostPid: 9),
            7
        )
    }

    func testInsertFallsBackToFrontmostForeignApp() {
        XCTAssertEqual(
            InsertDestination.choose(selfPid: 1, livePid: 1, storedPid: 1, frontmostPid: 99),
            99
        )
    }

    func testInsertCopiesWhenEveryCandidateIsSelf() {
        XCTAssertNil(
            InsertDestination.choose(selfPid: 1, livePid: 1, storedPid: 1, frontmostPid: 1)
        )
        XCTAssertNil(
            InsertDestination.choose(selfPid: 1, livePid: nil, storedPid: nil, frontmostPid: nil)
        )
    }

    func testEditableAXRoles() {
        XCTAssertTrue(AXTextRole.isEditable("AXTextField"))
        XCTAssertTrue(AXTextRole.isEditable("AXTextArea"))
        XCTAssertTrue(AXTextRole.isEditable("AXWebArea"))
        XCTAssertTrue(AXTextRole.isEditable("AXSearchField"))
        XCTAssertTrue(AXTextRole.isEditable("AXComboBox"))
        XCTAssertTrue(AXTextRole.isEditable("AXTextEntry"))
        XCTAssertFalse(AXTextRole.isEditable("AXStaticText"))
        XCTAssertFalse(AXTextRole.isEditable("AXApplication"))
        XCTAssertFalse(AXTextRole.isEditable("AXGroup"))
    }

    func testInsertVerifyAcceptsExactAndSubstring() {
        XCTAssertTrue(
            InsertVerify.looksInserted(
                before: "Hi",
                after: "Hi there",
                expected: "Hi there",
                chunk: " there"
            )
        )
        XCTAssertTrue(
            InsertVerify.looksInserted(
                before: "",
                after: "hello world",
                expected: "hello world extra",
                chunk: "hello"
            )
        )
        XCTAssertFalse(
            InsertVerify.looksInserted(
                before: "Hi",
                after: "Hi",
                expected: "Hi there",
                chunk: "there"
            )
        )
        XCTAssertTrue(
            InsertVerify.looksInserted(
                before: "",
                after: "x",
                expected: "y",
                chunk: "   "
            )
        )
        XCTAssertFalse(
            InsertVerify.looksInserted(
                before: "same",
                after: "same",
                expected: "same extra",
                chunk: "   "
            )
        )
    }

    func testWatcherMustStayListenOnly() {
        XCTAssertFalse(FnWatchContract.stealTapAllowed)
        XCTAssertTrue(FnWatchContract.listensToFlagsChanged)
        XCTAssertTrue(FnWatchContract.listensToKeyDownForEscape)
        XCTAssertFalse(FnWatchContract.swallowsEscape)
        XCTAssertEqual(FnWatchContract.escapeDebounceSeconds, 0.2)
    }
}
