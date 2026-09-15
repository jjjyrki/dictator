import DictatorCore
import XCTest

final class SttModelsTests: XCTestCase {
    func testDefaultLanguagesAreEnglishAndFinnish() {
        XCTAssertEqual(SpokenLanguage.defaultSelection, [.english, .finnish])
    }

    func testMultilingualSmallMatchesAndroidPins() {
        let asset = SttModelProfile.multilingualSmall.asset
        XCTAssertEqual(asset.fileName, "small_acft_q8_0.bin")
        XCTAssertEqual(asset.sizeBytes, 264_464_624)
        XCTAssertEqual(asset.sha256, "15ef255465a6dc582ecf1ec651a4618c7ee2c18c05570bbe46493d248d465ac4")
    }

    func testCatalogIncludesFinnishAndCantonese() {
        XCTAssertNotNil(SpokenLanguage.from(whisperCode: "fi"))
        XCTAssertNotNil(SpokenLanguage.from(whisperCode: "yue"))
        XCTAssertEqual(SpokenLanguage.catalog.count, 100)
    }

    func testOfficialMediumSkipsAcftAudioContext() {
        let profile = SttModelProfile.multilingualMediumOfficial
        XCTAssertEqual(profile.asset.fileName, "ggml-medium-q8_0.bin")
        XCTAssertEqual(profile.asset.sizeBytes, 823_369_779)
        XCTAssertEqual(
            profile.asset.sha256,
            "42a1ffcbe4167d224232443396968db4d02d4e8e87e213d3ee2e03095dea6502"
        )
        XCTAssertFalse(profile.shortenAudioContext)
        XCTAssertTrue(profile.asset.downloadURL.absoluteString.contains("huggingface.co"))
        XCTAssertTrue(profile.asset.downloadURL.absoluteString.contains("download=true"))
    }

    func testOfficialLargeTurboSkipsAcftAudioContext() {
        let profile = SttModelProfile.multilingualLargeTurboOfficial
        XCTAssertEqual(profile.asset.fileName, "ggml-large-v3-turbo-q8_0.bin")
        XCTAssertEqual(profile.asset.sizeBytes, 874_188_075)
        XCTAssertEqual(
            profile.asset.sha256,
            "317eb69c11673c9de1e1f0d459b253999804ec71ac4c23c17ecf5fbe24e259a1"
        )
        XCTAssertFalse(profile.shortenAudioContext)
    }

    func testFutoSmallStillShortensAudioContext() {
        XCTAssertTrue(SttModelProfile.multilingualSmall.shortenAudioContext)
        XCTAssertTrue(SttModelProfile.englishTiny.asset.downloadURL.absoluteString.contains("voiceinput.futo.org"))
    }
}
