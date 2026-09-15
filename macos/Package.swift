// swift-tools-version: 5.9
import PackageDescription

let package = Package(
    name: "Dictator",
    platforms: [.macOS(.v14)],
    products: [
        .library(name: "DictatorCore", targets: ["DictatorCore"]),
        .executable(name: "Dictator", targets: ["DictatorApp"]),
    ],
    targets: [
        .target(
            name: "DictatorCore",
            path: "Sources/DictatorCore"
        ),
        .executableTarget(
            name: "DictatorApp",
            dependencies: ["DictatorCore"],
            path: "Sources/DictatorApp",
            linkerSettings: [
                .linkedFramework("AppKit"),
                .linkedFramework("ApplicationServices"),
                .linkedFramework("AVFoundation"),
                .linkedFramework("CoreGraphics"),
                .linkedFramework("IOKit"),
            ]
        ),
        .testTarget(
            name: "DictatorCoreTests",
            dependencies: ["DictatorCore"],
            path: "Tests/DictatorCoreTests"
        ),
    ]
)
