import Flutter
import UIKit
import CallKit
import AVFoundation
import PushKit
import UserNotifications

public class IncomingCallKitPlugin: NSObject, FlutterPlugin, CXProviderDelegate,
    FlutterStreamHandler, PKPushRegistryDelegate, UNUserNotificationCenterDelegate {

    // MARK: - Properties

    private static var methodChannel: FlutterMethodChannel?
    private static var eventChannel: FlutterEventChannel?
    private static var sharedInstance: IncomingCallKitPlugin?

    private var provider: CXProvider?
    private let callController = CXCallController()
    private var eventSink: FlutterEventSink?

    /// Active calls: UUID → params dictionary
    private var activeCallParams: [UUID: [String: Any]] = [:]
    /// Timeout timers per call
    private var timeoutTimers: [UUID: DispatchWorkItem] = [:]
    /// Track UUIDs dismissed by our code (not user) to distinguish dismiss vs decline
    private var pendingDismissUUIDs: Set<UUID> = []
    /// Track outgoing call UUIDs
    private var outgoingCallUUIDs: Set<UUID> = []

    // PushKit
    private var voipRegistry: PKPushRegistry?
    private var voipToken: String = ""

    // Background handler
    private var backgroundCallbackHandle: Int64 = 0
    private var backgroundEngine: FlutterEngine?

    // Pending events for when no event sink
    private var pendingEvents: [[String: Any?]] = []

    // MARK: - Plugin Registration

    public static func register(with registrar: FlutterPluginRegistrar) {
        let methodChannel = FlutterMethodChannel(
            name: "com.ashiquali.incoming_call_kit/methods",
            binaryMessenger: registrar.messenger()
        )
        let eventChannel = FlutterEventChannel(
            name: "com.ashiquali.incoming_call_kit/events",
            binaryMessenger: registrar.messenger()
        )

        let instance = IncomingCallKitPlugin()
        registrar.addMethodCallDelegate(instance, channel: methodChannel)
        eventChannel.setStreamHandler(instance)

        self.methodChannel = methodChannel
        self.eventChannel = eventChannel
        self.sharedInstance = instance

        instance.setupProvider()
        instance.setupVoIP()
        instance.setupNotificationCategories()
    }

    // MARK: - CXProvider Setup

    private func setupProvider() {
        let config = CXProviderConfiguration()
        config.supportsVideo = false
        config.maximumCallGroups = 2
        config.maximumCallsPerCallGroup = 1
        config.supportedHandleTypes = [.phoneNumber, .generic]

        provider = CXProvider(configuration: config)
        provider?.setDelegate(self, queue: nil)
    }

    private func updateProviderConfig(from params: [String: Any]) {
        guard let provider = provider else { return }

        let ios = params["ios"] as? [String: Any] ?? [:]
        let config = CXProviderConfiguration()
        config.supportsVideo = ios["supportsVideo"] as? Bool ?? false
        config.maximumCallGroups = ios["maximumCallGroups"] as? Int ?? 2
        config.maximumCallsPerCallGroup = ios["maximumCallsPerCallGroup"] as? Int ?? 1
        config.supportedHandleTypes = [.phoneNumber, .generic]

        if let iconName = ios["iconName"] as? String, !iconName.isEmpty {
            config.iconTemplateImageData = UIImage(named: iconName)?.pngData()
        }

        // Leave ringtoneSound nil for Apple's system default. Only set a custom
        // sound when the file exists in the app bundle — a missing file can
        // produce CallKit UI with no audible ring.
        if let customRingtone = resolveCustomRingtoneSound(ios["ringtonePath"] as? String) {
            config.ringtoneSound = customRingtone
        }

        if ios["supportsDTMF"] as? Bool ?? true {
            // DTMF is supported by default
        }

        provider.configuration = config
    }

    /// Returns a bundle ringtone filename when a real custom sound is requested
    /// and present. Nil means use the system default CallKit ringtone.
    private func resolveCustomRingtoneSound(_ ringtonePath: String?) -> String? {
        guard let ringtonePath = ringtonePath?.trimmingCharacters(in: .whitespacesAndNewlines),
              !ringtonePath.isEmpty else {
            return nil
        }

        let lower = ringtonePath.lowercased()
        if lower == "system_ringtone_default" || lower == "default" || lower == "system" {
            return nil
        }

        let nsPath = ringtonePath as NSString
        let name = nsPath.deletingPathExtension
        let ext = nsPath.pathExtension

        if !ext.isEmpty {
            if Bundle.main.url(forResource: name, withExtension: ext) != nil {
                return ringtonePath
            }
            if Bundle.main.url(forResource: ringtonePath, withExtension: nil) != nil {
                return ringtonePath
            }
            return nil
        }

        for candidateExt in ["caf", "aiff", "wav", "mp3"] {
            if Bundle.main.url(forResource: name, withExtension: candidateExt) != nil {
                return "\(name).\(candidateExt)"
            }
        }

        if Bundle.main.url(forResource: ringtonePath, withExtension: nil) != nil {
            return ringtonePath
        }

        return nil
    }

    // MARK: - PushKit Setup

    private func setupVoIP() {
        voipRegistry = PKPushRegistry(queue: DispatchQueue.main)
        voipRegistry?.delegate = self
        voipRegistry?.desiredPushTypes = [.voIP]
    }

    // MARK: - Notification Categories (Missed Call)

    private func setupNotificationCategories() {
        let callbackAction = UNNotificationAction(
            identifier: "CALLBACK_ACTION",
            title: "Call Back",
            options: [.foreground]
        )
        let category = UNNotificationCategory(
            identifier: "INCOMING_CALL_KIT_MISSED_CALL",
            actions: [callbackAction],
            intentIdentifiers: [],
            options: []
        )
        UNUserNotificationCenter.current().setNotificationCategories([category])
        UNUserNotificationCenter.current().delegate = self
    }

    // MARK: - FlutterPlugin Method Handler

    public func handle(_ call: FlutterMethodCall, result: @escaping FlutterResult) {
        let args = call.arguments as? [String: Any]

        switch call.method {
        case "show":
            handleShow(params: args, result: result)
        case "dismiss":
            handleDismiss(callId: args?["callId"] as? String, result: result)
        case "dismissAll":
            handleDismissAll(result: result)
        case "startCall":
            handleStartCall(params: args, result: result)
        case "setCallConnected":
            handleSetCallConnected(callId: args?["callId"] as? String, result: result)
        case "endCall":
            handleEndCall(callId: args?["callId"] as? String, result: result)
        case "endAllCalls":
            handleEndAllCalls(result: result)
        case "showMissedCallNotification":
            handleShowMissedCallNotification(params: args, result: result)
        case "clearMissedCallNotification":
            handleClearMissedCallNotification(callId: args?["callId"] as? String, result: result)
        case "registerBackgroundHandler":
            handleRegisterBackgroundHandler(rawHandle: args?["callbackHandle"] as? Int64, result: result)
        case "canUseFullScreenIntent":
            result(true) // Always true on iOS
        case "requestFullIntentPermission":
            result(nil) // No-op on iOS
        case "hasNotificationPermission":
            handleHasNotificationPermission(result: result)
        case "requestNotificationPermission":
            handleRequestNotificationPermission(result: result)
        case "isAutoStartAvailable":
            result(false) // Not applicable on iOS
        case "openAutoStartSettings":
            result(nil) // No-op on iOS
        case "getDevicePushTokenVoIP":
            result(voipToken)
        case "getActiveCalls":
            let ids = activeCallParams.keys.map { $0.uuidString.lowercased() }
            result(ids)
        default:
            result(FlutterMethodNotImplemented)
        }
    }

    // MARK: - Incoming Call

    private func handleShow(params: [String: Any]?, result: @escaping FlutterResult) {
        guard let params = params else {
            result(FlutterError(code: "INVALID_ARGS", message: "Missing params", details: nil))
            return
        }

        let callId = params["id"] as? String ?? UUID().uuidString
        guard let uuid = UUID(uuidString: callId) ?? createUUID(from: callId) else {
            result(FlutterError(code: "INVALID_ID", message: "Invalid call ID", details: nil))
            return
        }

        updateProviderConfig(from: params)

        let callerName = params["callerName"] as? String ?? "Unknown"
        let callerNumber = params["callerNumber"] as? String ?? ""
        let hasVideo = params["type"] as? Int == 1
        let duration = params["duration"] as? Double ?? 30.0

        let ios = params["ios"] as? [String: Any] ?? [:]
        let handleType: CXHandle.HandleType = {
            switch ios["handleType"] as? String {
            case "generic": return .generic
            case "email": return .emailAddress
            default: return .phoneNumber
            }
        }()

        let handle = CXHandle(type: handleType, value: callerNumber.isEmpty ? callerName : callerNumber)

        let update = CXCallUpdate()
        update.remoteHandle = handle
        update.localizedCallerName = callerName
        update.hasVideo = hasVideo
        update.supportsGrouping = false
        update.supportsUngrouping = false
        update.supportsDTMF = ios["supportsDTMF"] as? Bool ?? true
        update.supportsHolding = ios["supportsHolding"] as? Bool ?? false

        activeCallParams[uuid] = params

        provider?.reportNewIncomingCall(with: uuid, update: update) { [weak self] error in
            if let error = error {
                self?.activeCallParams.removeValue(forKey: uuid)
                result(FlutterError(code: "CALLKIT_ERROR", message: error.localizedDescription, details: nil))
                return
            }

            // Start timeout timer
            if duration > 0 {
                self?.startTimeoutTimer(uuid: uuid, duration: duration)
            }

            result(nil)
        }
    }

    private func handleDismiss(callId: String?, result: @escaping FlutterResult) {
        guard let callId = callId,
              let uuid = findUUID(for: callId) else {
            result(nil)
            return
        }

        pendingDismissUUIDs.insert(uuid)

        let endAction = CXEndCallAction(call: uuid)
        let transaction = CXTransaction(action: endAction)
        callController.request(transaction) { error in
            if let error = error {
                result(FlutterError(code: "END_CALL_ERROR", message: error.localizedDescription, details: nil))
            } else {
                result(nil)
            }
        }
    }

    private func handleDismissAll(result: @escaping FlutterResult) {
        let uuids = Array(activeCallParams.keys)
        for uuid in uuids {
            pendingDismissUUIDs.insert(uuid)
        }

        let actions = uuids.map { CXEndCallAction(call: $0) }
        guard !actions.isEmpty else {
            result(nil)
            return
        }

        let transaction = CXTransaction()
        actions.forEach { transaction.addAction($0) }
        callController.request(transaction) { error in
            if let error = error {
                result(FlutterError(code: "END_ALL_ERROR", message: error.localizedDescription, details: nil))
            } else {
                result(nil)
            }
        }
    }

    // MARK: - Outgoing Call

    private func handleStartCall(params: [String: Any]?, result: @escaping FlutterResult) {
        guard let params = params else {
            result(FlutterError(code: "INVALID_ARGS", message: "Missing params", details: nil))
            return
        }

        let callId = params["id"] as? String ?? UUID().uuidString
        guard let uuid = UUID(uuidString: callId) ?? createUUID(from: callId) else {
            result(FlutterError(code: "INVALID_ID", message: "Invalid call ID", details: nil))
            return
        }

        updateProviderConfig(from: params)

        let callerName = params["callerName"] as? String ?? "Unknown"
        let callerNumber = params["callerNumber"] as? String ?? ""
        let hasVideo = params["type"] as? Int == 1

        let ios = params["ios"] as? [String: Any] ?? [:]
        let handleType: CXHandle.HandleType = {
            switch ios["handleType"] as? String {
            case "generic": return .generic
            case "email": return .emailAddress
            default: return .phoneNumber
            }
        }()

        let handle = CXHandle(type: handleType, value: callerNumber.isEmpty ? callerName : callerNumber)

        activeCallParams[uuid] = params
        outgoingCallUUIDs.insert(uuid)

        let startAction = CXStartCallAction(call: uuid, handle: handle)
        startAction.isVideo = hasVideo
        startAction.contactIdentifier = callerName

        let transaction = CXTransaction(action: startAction)
        callController.request(transaction) { [weak self] error in
            if let error = error {
                self?.activeCallParams.removeValue(forKey: uuid)
                self?.outgoingCallUUIDs.remove(uuid)
                result(FlutterError(code: "START_CALL_ERROR", message: error.localizedDescription, details: nil))
            } else {
                result(nil)
            }
        }
    }

    private func handleSetCallConnected(callId: String?, result: @escaping FlutterResult) {
        guard let callId = callId,
              let uuid = findUUID(for: callId) else {
            result(nil)
            return
        }

        provider?.reportOutgoingCall(with: uuid, connectedAt: Date())
        sendEvent(action: "callConnected", callId: callId)
        result(nil)
    }

    private func handleEndCall(callId: String?, result: @escaping FlutterResult) {
        guard let callId = callId,
              let uuid = findUUID(for: callId) else {
            result(nil)
            return
        }

        let endAction = CXEndCallAction(call: uuid)
        let transaction = CXTransaction(action: endAction)
        callController.request(transaction) { error in
            if let error = error {
                result(FlutterError(code: "END_CALL_ERROR", message: error.localizedDescription, details: nil))
            } else {
                result(nil)
            }
        }
    }

    private func handleEndAllCalls(result: @escaping FlutterResult) {
        let uuids = Array(activeCallParams.keys)
        let actions = uuids.map { CXEndCallAction(call: $0) }
        guard !actions.isEmpty else {
            result(nil)
            return
        }

        let transaction = CXTransaction()
        actions.forEach { transaction.addAction($0) }
        callController.request(transaction) { error in
            if let error = error {
                result(FlutterError(code: "END_ALL_ERROR", message: error.localizedDescription, details: nil))
            } else {
                result(nil)
            }
        }
    }

    // MARK: - Missed Call Notification

    private func handleShowMissedCallNotification(params: [String: Any]?, result: @escaping FlutterResult) {
        guard let params = params else {
            result(nil)
            return
        }

        let callerName = params["callerName"] as? String ?? "Unknown"
        let callId = params["id"] as? String ?? ""
        let extra = params["extra"] as? [String: Any] ?? [:]
        let missedNotif = params["missedCallNotification"] as? [String: Any] ?? [:]
        let subtitle = missedNotif["subtitle"] as? String ?? "Missed Call"
        let callbackText = missedNotif["callbackText"] as? String ?? "Call Back"

        let content = UNMutableNotificationContent()
        content.title = callerName
        content.body = subtitle
        content.sound = .default
        content.categoryIdentifier = "INCOMING_CALL_KIT_MISSED_CALL"
        content.userInfo = ["callId": callId, "extra": extra, "callbackText": callbackText]

        let request = UNNotificationRequest(
            identifier: "missed_call_\(callId)",
            content: content,
            trigger: nil
        )
        UNUserNotificationCenter.current().add(request) { error in
            if let error = error {
                result(FlutterError(code: "NOTIFICATION_ERROR", message: error.localizedDescription, details: nil))
            } else {
                result(nil)
            }
        }
    }

    private func handleClearMissedCallNotification(callId: String?, result: @escaping FlutterResult) {
        guard let callId = callId else {
            result(nil)
            return
        }
        UNUserNotificationCenter.current().removeDeliveredNotifications(
            withIdentifiers: ["missed_call_\(callId)"]
        )
        result(nil)
    }

    // MARK: - Background Handler

    private func handleRegisterBackgroundHandler(rawHandle: Int64?, result: @escaping FlutterResult) {
        guard let handle = rawHandle else {
            result(FlutterError(code: "INVALID_ARGS", message: "Missing callback handle", details: nil))
            return
        }
        backgroundCallbackHandle = handle
        UserDefaults.standard.set(handle, forKey: "incoming_call_kit_bg_callback_handle")
        result(nil)
    }

    private func dispatchBackgroundEvent(_ event: [String: Any?]) {
        let handle = backgroundCallbackHandle != 0
            ? backgroundCallbackHandle
            : UserDefaults.standard.object(forKey: "incoming_call_kit_bg_callback_handle") as? Int64 ?? 0

        guard handle != 0 else { return }
        guard let info = FlutterCallbackCache.lookupCallbackInformation(handle) else { return }

        if backgroundEngine == nil {
            backgroundEngine = FlutterEngine(name: "incoming_call_kit_bg", project: nil, allowHeadlessExecution: true)
        }

        guard let engine = backgroundEngine else { return }

        engine.run(withEntrypoint: info.callbackName, libraryURI: info.callbackLibraryPath)

        let bgChannel = FlutterMethodChannel(
            name: "com.ashiquali.incoming_call_kit/background",
            binaryMessenger: engine.binaryMessenger
        )

        // Send event after a brief delay to allow engine initialization
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) {
            bgChannel.invokeMethod("onBackgroundEvent", arguments: event)
        }
    }

    // MARK: - Notification Permissions

    private func handleHasNotificationPermission(result: @escaping FlutterResult) {
        UNUserNotificationCenter.current().getNotificationSettings { settings in
            DispatchQueue.main.async {
                result(settings.authorizationStatus == .authorized)
            }
        }
    }

    private func handleRequestNotificationPermission(result: @escaping FlutterResult) {
        UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound, .badge]) { granted, _ in
            DispatchQueue.main.async {
                result(granted)
            }
        }
    }

    // MARK: - CXProviderDelegate

    public func providerDidReset(_ provider: CXProvider) {
        activeCallParams.removeAll()
        outgoingCallUUIDs.removeAll()
        pendingDismissUUIDs.removeAll()
        cancelAllTimeoutTimers()
    }

    public func provider(_ provider: CXProvider, perform action: CXAnswerCallAction) {
        let uuid = action.callUUID
        let callId = uuid.uuidString.lowercased()

        // Configure audio session for WebRTC/VoIP
        configureAudioSession()

        sendEvent(action: "accept", callId: callId)
        sendEvent(action: "audioSessionActivated", callId: callId)

        cancelTimeoutTimer(for: uuid)
        action.fulfill()
    }

    public func provider(_ provider: CXProvider, perform action: CXEndCallAction) {
        let uuid = action.callUUID
        let callId = uuid.uuidString.lowercased()

        if pendingDismissUUIDs.contains(uuid) {
            // Dismissed programmatically (remote cancel)
            sendEvent(action: "dismissed", callId: callId)
            pendingDismissUUIDs.remove(uuid)
        } else if outgoingCallUUIDs.contains(uuid) {
            sendEvent(action: "callEnded", callId: callId)
            outgoingCallUUIDs.remove(uuid)
        } else {
            // User declined
            sendEvent(action: "decline", callId: callId)
        }

        // Show missed call notification if configured and this was an incoming call
        if !outgoingCallUUIDs.contains(uuid) {
            showMissedCallIfNeeded(uuid: uuid)
        }

        cancelTimeoutTimer(for: uuid)
        activeCallParams.removeValue(forKey: uuid)
        action.fulfill()
    }

    public func provider(_ provider: CXProvider, perform action: CXStartCallAction) {
        let uuid = action.callUUID
        let callId = uuid.uuidString.lowercased()

        provider.reportOutgoingCall(with: uuid, startedConnectingAt: Date())
        sendEvent(action: "callStart", callId: callId)
        action.fulfill()
    }

    public func provider(_ provider: CXProvider, perform action: CXSetHeldCallAction) {
        let callId = action.callUUID.uuidString.lowercased()
        sendEvent(action: "toggleHold", callId: callId, extra: ["isOnHold": action.isOnHold])
        action.fulfill()
    }

    public func provider(_ provider: CXProvider, perform action: CXSetMutedCallAction) {
        let callId = action.callUUID.uuidString.lowercased()
        sendEvent(action: "toggleMute", callId: callId, extra: ["isMuted": action.isMuted])
        action.fulfill()
    }

    public func provider(_ provider: CXProvider, perform action: CXSetGroupCallAction) {
        let callId = action.callUUID.uuidString.lowercased()
        sendEvent(action: "toggleGroup", callId: callId)
        action.fulfill()
    }

    public func provider(_ provider: CXProvider, perform action: CXPlayDTMFCallAction) {
        let callId = action.callUUID.uuidString.lowercased()
        sendEvent(action: "toggleDmtf", callId: callId, extra: ["digits": action.digits])
        action.fulfill()
    }

    public func provider(_ provider: CXProvider, timedOutPerforming action: CXAction) {
        // Timeout — system timed out
        action.fulfill()
    }

    public func provider(_ provider: CXProvider, didActivate audioSession: AVAudioSession) {
        // Audio session activated by system
    }

    public func provider(_ provider: CXProvider, didDeactivate audioSession: AVAudioSession) {
        // Audio session deactivated
    }

    // MARK: - FlutterStreamHandler

    public func onListen(withArguments arguments: Any?, eventSink events: @escaping FlutterEventSink) -> FlutterError? {
        eventSink = events
        // Replay pending events
        for event in pendingEvents {
            events(event)
        }
        pendingEvents.removeAll()
        return nil
    }

    public func onCancel(withArguments arguments: Any?) -> FlutterError? {
        eventSink = nil
        return nil
    }

    // MARK: - PKPushRegistryDelegate

    public func pushRegistry(_ registry: PKPushRegistry, didUpdate pushCredentials: PKPushCredentials, for type: PKPushType) {
        voipToken = pushCredentials.token.map { String(format: "%02x", $0) }.joined()
        sendEvent(action: "voipTokenUpdated", callId: "", extra: ["token": voipToken])
    }

    public func pushRegistry(_ registry: PKPushRegistry, didReceiveIncomingPushWith payload: PKPushPayload, for type: PKPushType, completion: @escaping () -> Void) {
        // Parse VoIP push payload and show CallKit UI
        let data = payload.dictionaryPayload

        let callId = data["id"] as? String ?? data["uuid"] as? String ?? UUID().uuidString
        let callerName = data["callerName"] as? String ?? data["caller_name"] as? String ?? "Unknown"
        let callerNumber = data["callerNumber"] as? String ?? data["caller_number"] as? String ?? ""
        let hasVideo = data["hasVideo"] as? Bool ?? false

        guard let uuid = UUID(uuidString: callId) ?? createUUID(from: callId) else {
            completion()
            return
        }

        let update = CXCallUpdate()
        update.remoteHandle = CXHandle(type: .phoneNumber, value: callerNumber.isEmpty ? callerName : callerNumber)
        update.localizedCallerName = callerName
        update.hasVideo = hasVideo

        activeCallParams[uuid] = data as? [String: Any] ?? ["id": callId, "callerName": callerName]

        provider?.reportNewIncomingCall(with: uuid, update: update) { [weak self] error in
            if let error = error {
                self?.activeCallParams.removeValue(forKey: uuid)
                NSLog("[IncomingCallKit] Failed to report incoming call from push: \(error.localizedDescription)")
            }
            // CRITICAL: must call completion or iOS kills the app
            completion()
        }
    }

    public func pushRegistry(_ registry: PKPushRegistry, didInvalidatePushTokenFor type: PKPushType) {
        voipToken = ""
        sendEvent(action: "voipTokenUpdated", callId: "", extra: ["token": ""])
    }

    // MARK: - UNUserNotificationCenterDelegate

    public func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        didReceive response: UNNotificationResponse,
        withCompletionHandler completionHandler: @escaping () -> Void
    ) {
        if response.actionIdentifier == "CALLBACK_ACTION" {
            let userInfo = response.notification.request.content.userInfo
            let callId = userInfo["callId"] as? String ?? ""
            let extra = userInfo["extra"] as? [String: Any]
            sendEvent(action: "callback", callId: callId, extra: extra)
        }
        completionHandler()
    }

    public func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        willPresent notification: UNNotification,
        withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void
    ) {
        completionHandler([.banner, .sound])
    }

    // MARK: - Audio Session

    private func configureAudioSession() {
        let audioSession = AVAudioSession.sharedInstance()
        do {
            try audioSession.setCategory(.playAndRecord, mode: .voiceChat, options: [.allowBluetooth, .defaultToSpeaker])
            try audioSession.setActive(true)
        } catch {
            NSLog("[IncomingCallKit] Failed to configure audio session: \(error)")
        }
    }

    // MARK: - Timeout

    private func startTimeoutTimer(uuid: UUID, duration: Double) {
        let callId = uuid.uuidString.lowercased()
        let workItem = DispatchWorkItem { [weak self] in
            guard let self = self, self.activeCallParams[uuid] != nil else { return }

            self.sendEvent(action: "timeout", callId: callId)

            // Report call ended as unanswered
            self.provider?.reportCall(with: uuid, endedAt: Date(), reason: .unanswered)

            // Show missed call notification
            self.showMissedCallIfNeeded(uuid: uuid)

            self.activeCallParams.removeValue(forKey: uuid)
            self.timeoutTimers.removeValue(forKey: uuid)
        }

        timeoutTimers[uuid]?.cancel()
        timeoutTimers[uuid] = workItem
        DispatchQueue.main.asyncAfter(deadline: .now() + duration, execute: workItem)
    }

    private func cancelTimeoutTimer(for uuid: UUID) {
        timeoutTimers[uuid]?.cancel()
        timeoutTimers.removeValue(forKey: uuid)
    }

    private func cancelAllTimeoutTimers() {
        timeoutTimers.values.forEach { $0.cancel() }
        timeoutTimers.removeAll()
    }

    // MARK: - Missed Call

    private func showMissedCallIfNeeded(uuid: UUID) {
        guard let params = activeCallParams[uuid] else { return }
        let missedNotif = params["missedCallNotification"] as? [String: Any] ?? [:]
        let showNotif = missedNotif["showNotification"] as? Bool ?? false

        guard showNotif else { return }

        handleShowMissedCallNotification(params: params) { _ in }
    }

    // MARK: - Event Emission

    private func sendEvent(action: String, callId: String, extra: [String: Any]? = nil) {
        var params: [String: Any?] = [:]
        if let uuid = findUUID(for: callId) {
            if let stored = activeCallParams[uuid]?["extra"] as? [String: Any] {
                var merged = stored
                extra?.forEach { merged[$0.key] = $0.value }
                params = ["action": action, "callId": callId, "extra": merged]
            } else {
                params = ["action": action, "callId": callId, "extra": extra]
            }
        } else {
            params = ["action": action, "callId": callId, "extra": extra]
        }

        if let sink = eventSink {
            sink(params)
        } else {
            pendingEvents.append(params)
            dispatchBackgroundEvent(params)
        }
    }

    // MARK: - Helpers

    private func findUUID(for callId: String) -> UUID? {
        // Try direct UUID parse
        if let uuid = UUID(uuidString: callId), activeCallParams[uuid] != nil {
            return uuid
        }
        // Try deterministic UUID from string
        if let uuid = createUUID(from: callId), activeCallParams[uuid] != nil {
            return uuid
        }
        // Search by stored ID
        for (uuid, params) in activeCallParams {
            if params["id"] as? String == callId {
                return uuid
            }
        }
        return nil
    }

    private func createUUID(from string: String) -> UUID? {
        // Create a deterministic UUID from any string using UUID v5-like hashing
        let data = Data(string.utf8)
        var hash = [UInt8](repeating: 0, count: 16)
        data.withUnsafeBytes { bytes in
            guard let ptr = bytes.baseAddress else { return }
            for i in 0..<data.count {
                hash[i % 16] ^= ptr.advanced(by: i).load(as: UInt8.self)
            }
        }
        // Set version and variant bits for UUID
        hash[6] = (hash[6] & 0x0F) | 0x50 // Version 5
        hash[8] = (hash[8] & 0x3F) | 0x80 // Variant 1
        let uuidString = String(format: "%02x%02x%02x%02x-%02x%02x-%02x%02x-%02x%02x-%02x%02x%02x%02x%02x%02x",
                                hash[0], hash[1], hash[2], hash[3],
                                hash[4], hash[5], hash[6], hash[7],
                                hash[8], hash[9], hash[10], hash[11],
                                hash[12], hash[13], hash[14], hash[15])
        return UUID(uuidString: uuidString)
    }
}
