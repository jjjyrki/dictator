import AppKit
import ApplicationServices
import AVFoundation
import CoreGraphics
import Darwin
import Foundation
import IOKit.hid

enum Diagnostics {
    static func ioReturnName(_ value: kern_return_t) -> String {
        switch UInt32(bitPattern: Int32(value)) {
        case 0: return "kIOReturnSuccess"
        case 0xE00002C2: return "kIOReturnBadArgument"
        case 0xE00002BD: return "kIOReturnNotOpen"
        case 0xE00002C5: return "kIOReturnExclusiveAccess"
        case 0xE00002C7: return "kIOReturnBusy"
        case 0xE00002E2: return "kIOReturnNotPermitted"
        case 0xE00002ED: return "kIOReturnNotFound"
        case 0xE00002F0: return "kIOReturnNoResources"
        default: return "unknown"
        }
    }

    static func hidAccessName(_ value: IOHIDAccessType) -> String {
        switch value {
        case kIOHIDAccessTypeGranted: return "granted"
        case kIOHIDAccessTypeDenied: return "denied"
        case kIOHIDAccessTypeUnknown: return "unknown"
        default: return "raw(\(value.rawValue))"
        }
    }

    static func microphoneStatusName() -> String {
        switch AVCaptureDevice.authorizationStatus(for: .audio) {
        case .notDetermined: return "notDetermined"
        case .restricted: return "restricted"
        case .denied: return "denied"
        case .authorized: return "authorized"
        @unknown default: return "other"
        }
    }

    static func hex(_ value: kern_return_t) -> String {
        String(format: "0x%08x", UInt32(bitPattern: Int32(value)))
    }

    static func codesignDump(path: String) -> String {
        let process = Process()
        process.executableURL = URL(fileURLWithPath: "/usr/bin/codesign")
        process.arguments = ["-dv", "--verbose=4", path]
        let pipe = Pipe()
        process.standardOutput = pipe
        process.standardError = pipe
        do {
            try process.run()
            process.waitUntilExit()
            let data = pipe.fileHandleForReading.readDataToEndOfFile()
            return String(data: data, encoding: .utf8)?.trimmingCharacters(in: .whitespacesAndNewlines)
                ?? "(codesign output not utf8)"
        } catch {
            return "codesign failed: \(error)"
        }
    }

    static func permissionLines() -> [String] {
        let listen = IOHIDCheckAccess(kIOHIDRequestTypeListenEvent)
        let post = IOHIDCheckAccess(kIOHIDRequestTypePostEvent)
        return [
            "mic=\(microphoneStatusName()) granted=\(Permissions.microphoneGranted())",
            "axTrusted=\(AXIsProcessTrusted())",
            "hidListen=\(hidAccessName(listen)) raw=\(listen.rawValue)",
            "hidPost=\(hidAccessName(post)) raw=\(post.rawValue)",
            "cgListen=\(CGPreflightListenEventAccess()) cgPost=\(CGPreflightPostEventAccess())",
        ]
    }

    static func identityLines() -> [String] {
        let info = ProcessInfo.processInfo
        return [
            "time=\(ISO8601DateFormatter().string(from: Date()))",
            "os=\(info.operatingSystemVersionString)",
            "pid=\(info.processIdentifier) name=\(info.processName)",
            "bundleId=\(Bundle.main.bundleIdentifier ?? "nil")",
            "bundlePath=\(Bundle.main.bundlePath)",
            "executable=\(Bundle.main.executablePath ?? "nil")",
            "translated=\(info.isTranslated)",
        ]
    }

    static func nextStepLine() -> String {
        if Permissions.accessibilityTrusted() || Permissions.inputMonitoringGranted() {
            return "next=press Globe/Fn once; Last Fn should change"
        }
        if Permissions.inputMonitoringDenied() {
            return "next=grant Accessibility for /Applications/Dictator.app. HID is denied, so NSEvent needs AX."
        }
        return "next=grant Accessibility or Input Monitoring for /Applications/Dictator.app"
    }

    static func modelLines(lastLoad: String) -> [String] {
        let profile = Preferences.shared.profile
        let file = Preferences.shared.modelFile(for: profile)
        let got = (try? FileManager.default.attributesOfItem(atPath: file.path)[.size] as? NSNumber)?.int64Value ?? 0
        return [
            "modelProfile=\(profile.rawValue) file=\(profile.asset.fileName)",
            "modelPath=\(file.path)",
            "modelBytes=\(got) expected=\(profile.asset.sizeBytes) installed=\(Preferences.shared.isInstalled(profile))",
            "shortenAudioCtx=\(profile.shortenAudioContext) source=\(profile.asset.source.rawValue)",
            "lastLoad=\(lastLoad)",
        ]
    }

    static func dump(watcher: [String], lastLoad: String, lastOutcome: String) -> String {
        var lines = ["Dictator diagnostics"]
        lines.append(contentsOf: identityLines())
        lines.append(contentsOf: permissionLines())
        lines.append(nextStepLine())
        lines.append(contentsOf: modelLines(lastLoad: lastLoad))
        lines.append("lastOutcome=\(lastOutcome)")
        lines.append(contentsOf: watcher)
        lines.append("--- codesign bundle ---")
        lines.append(codesignDump(path: Bundle.main.bundlePath))
        if let exe = Bundle.main.executablePath {
            lines.append("--- codesign executable ---")
            lines.append(codesignDump(path: exe))
        }
        return lines.joined(separator: "\n")
    }
}

private extension ProcessInfo {
    var isTranslated: Bool {
        var flag: Int = 0
        var size = MemoryLayout<Int>.size
        let result = sysctlbyname("sysctl.proc_translated", &flag, &size, nil, 0)
        return result == 0 && flag == 1
    }
}
