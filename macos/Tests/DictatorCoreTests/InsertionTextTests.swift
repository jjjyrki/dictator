import DictatorCore
import XCTest

final class InsertionTextTests: XCTestCase {
    func testInsertsAtCursor() {
        let result = InsertionText.apply(
            current: "Hello world",
            selectionStart: 6,
            selectionEnd: 6,
            insert: "there "
        )
        XCTAssertEqual(result.text, "Hello there world")
        XCTAssertEqual(result.cursor, 12)
    }

    func testReplacesSelection() {
        let result = InsertionText.apply(
            current: "Hello world",
            selectionStart: 6,
            selectionEnd: 11,
            insert: "Jyri"
        )
        XCTAssertEqual(result.text, "Hello Jyri")
        XCTAssertEqual(result.cursor, 10)
    }

    func testClampsInvertedSelection() {
        let result = InsertionText.apply(current: "abc", selectionStart: 2, selectionEnd: 1, insert: "X")
        XCTAssertEqual(result.text, "abXc")
        XCTAssertEqual(result.cursor, 3)
    }

    func testClampsOutOfRange() {
        let result = InsertionText.apply(current: "ab", selectionStart: 40, selectionEnd: 40, insert: "c")
        XCTAssertEqual(result.text, "abc")
        XCTAssertEqual(result.cursor, 3)
    }

    func testKeepsLeadingSpaceWhenAppending() {
        let result = InsertionText.apply(current: "Hello", selectionStart: 5, selectionEnd: 5, insert: " world")
        XCTAssertEqual(result.text, "Hello world")
        XCTAssertEqual(result.cursor, 11)
    }

    func testDropsLeadingSpaceInEmptyField() {
        let result = InsertionText.apply(current: "", selectionStart: 0, selectionEnd: 0, insert: " Hello ")
        XCTAssertEqual(result.text, "Hello ")
        XCTAssertEqual(result.cursor, 6)
    }

    func testDropsLeadingSpaceWhenFieldStartsBlank() {
        let result = InsertionText.apply(current: "  ", selectionStart: 2, selectionEnd: 2, insert: " Hi")
        XCTAssertEqual(result.text, "  Hi")
        XCTAssertEqual(result.cursor, 4)
    }

    func testKeepsLeadingSpaceWhenInsertingMidText() {
        let result = InsertionText.apply(
            current: "Hello world",
            selectionStart: 6,
            selectionEnd: 6,
            insert: " big "
        )
        XCTAssertEqual(result.text, "Hello  big world")
        XCTAssertEqual(result.cursor, 11)
    }

    func testUsesUtf16IndicesLikeAccessibility() {
        let current = "a😀b"
        XCTAssertEqual((current as NSString).length, 4)
        let result = InsertionText.apply(
            current: current,
            selectionStart: 1,
            selectionEnd: 1,
            insert: "x"
        )
        XCTAssertEqual(result.text, "ax😀b")
        XCTAssertEqual(result.cursor, 2)
    }
}
