import Foundation

public struct FnGestureConfig: Equatable, Sendable {
    public var holdThreshold: TimeInterval
    public var tapWindow: TimeInterval

    public init(holdThreshold: TimeInterval = 0.25, tapWindow: TimeInterval = 0.30) {
        self.holdThreshold = holdThreshold
        self.tapWindow = tapWindow
    }
}

public enum FnScheduledWork: Equatable, Sendable {
    case holdThreshold
    case tapWindow
}

public enum FnSessionCommand: Equatable, Sendable {
    case startRecording
    case commit
    case cancel
}

public struct FnStep: Equatable, Sendable {
    public var commands: [FnSessionCommand]
    public var cancelWork: [FnScheduledWork]
    public var schedule: [(work: FnScheduledWork, delay: TimeInterval)]

    public static let none = FnStep(commands: [], cancelWork: [], schedule: [])

    public static func == (lhs: FnStep, rhs: FnStep) -> Bool {
        lhs.commands == rhs.commands
            && lhs.cancelWork == rhs.cancelWork
            && lhs.schedule.map(\.work) == rhs.schedule.map(\.work)
            && lhs.schedule.map(\.delay) == rhs.schedule.map(\.delay)
    }
}

/// Hold vs double-press on one key. Recording starts on the first down.
public struct FnGestureMachine {
    public enum Phase: Equatable, Sendable {
        case idle
        case firstDown
        case pushToTalk
        case shortReleased
        case latched
    }

    public private(set) var phase: Phase = .idle
    public var config: FnGestureConfig

    public init(config: FnGestureConfig = FnGestureConfig()) {
        self.config = config
    }

    public mutating func handleDown() -> FnStep {
        switch phase {
        case .idle:
            phase = .firstDown
            return FnStep(
                commands: [.startRecording],
                cancelWork: [],
                schedule: [(.holdThreshold, config.holdThreshold)]
            )
        case .shortReleased:
            phase = .latched
            return FnStep(commands: [], cancelWork: [.tapWindow], schedule: [])
        case .latched:
            phase = .idle
            return FnStep(commands: [.commit], cancelWork: [], schedule: [])
        case .firstDown, .pushToTalk:
            return .none
        }
    }

    public mutating func handleUp() -> FnStep {
        switch phase {
        case .firstDown:
            phase = .shortReleased
            return FnStep(
                commands: [],
                cancelWork: [.holdThreshold],
                schedule: [(.tapWindow, config.tapWindow)]
            )
        case .pushToTalk:
            phase = .idle
            return FnStep(commands: [.commit], cancelWork: [], schedule: [])
        case .idle, .shortReleased, .latched:
            return .none
        }
    }

    public mutating func handle(_ work: FnScheduledWork) -> FnStep {
        switch (work, phase) {
        case (.holdThreshold, .firstDown):
            phase = .pushToTalk
            return .none
        case (.tapWindow, .shortReleased):
            phase = .idle
            return FnStep(commands: [.cancel], cancelWork: [], schedule: [])
        default:
            return .none
        }
    }

    public mutating func handleEscape() -> FnStep {
        if phase == .idle {
            return .none
        }
        phase = .idle
        return FnStep(
            commands: [.cancel],
            cancelWork: [.holdThreshold, .tapWindow],
            schedule: []
        )
    }

    public mutating func reset() {
        phase = .idle
    }
}
