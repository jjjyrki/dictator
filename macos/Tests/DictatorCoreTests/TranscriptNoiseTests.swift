import DictatorCore
import XCTest

final class TranscriptNoiseTests: XCTestCase {
    func testDiscardsBlankAudioTag() {
        XCTAssertNil(TranscriptNoise.usableSpeech("[BLANK_AUDIO]"))
        XCTAssertNil(TranscriptNoise.usableSpeech("  [BLANK_AUDIO]  "))
        XCTAssertNil(TranscriptNoise.usableSpeech("(blank audio)"))
    }

    func testDiscardsMusicAndStackedTags() {
        XCTAssertNil(TranscriptNoise.usableSpeech("[MUSIC]"))
        XCTAssertNil(TranscriptNoise.usableSpeech("[BLANK_AUDIO] [MUSIC]"))
        XCTAssertNil(TranscriptNoise.usableSpeech("♪"))
    }

    func testDiscardsTypingTag() {
        XCTAssertNil(TranscriptNoise.usableSpeech("(typing)"))
        XCTAssertNil(TranscriptNoise.usableSpeech("[TYPING]"))
    }

    func testKeepsRealSpeech() {
        XCTAssertEqual(TranscriptNoise.usableSpeech(" Hello there. "), "Hello there.")
    }

    func testStripsNoiseTagFromSpeech() {
        XCTAssertEqual(TranscriptNoise.usableSpeech("Hello [BLANK_AUDIO]"), "Hello")
        XCTAssertEqual(TranscriptNoise.usableSpeech("[MUSIC] Hello there"), "Hello there")
    }

    func testDiscardsSilenceAndEmpty() {
        XCTAssertNil(TranscriptNoise.usableSpeech(""))
        XCTAssertNil(TranscriptNoise.usableSpeech("   "))
        XCTAssertNil(TranscriptNoise.usableSpeech("[SILENCE]"))
        XCTAssertNil(TranscriptNoise.usableSpeech("[INAUDIBLE]"))
        XCTAssertNil(TranscriptNoise.usableSpeech("[]"))
    }
}
