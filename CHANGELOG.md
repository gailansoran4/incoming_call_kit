## 0.0.7

* **Breaking-ish**: Null or blank `textAccept` / `textDecline` now hide that button (full-screen + notification actions) instead of falling back to "Accept"/"Decline".
* Constructor defaults remain `Accept` / `Decline` when omitted; pass `null` or `''` explicitly to hide.

## 0.0.6

* **Fix**: Background accept/reject when the app is killed now works. Added a headless Dart `callbackDispatcher` that listens on the background MethodChannel, signals `backgroundHandlerInitialized`, and invokes the registered user handler with `userCallbackHandle`.
* **Android**: Store dispatcher + user callback handles; ensure FlutterLoader is initialized before starting the headless engine; wait for Dart handler completion before tearing down the engine (no more fixed 2s destroy race).
* **iOS**: Same dispatcher/user-handle registration and MethodChannel handshake instead of a blind delayed `onBackgroundEvent` send.

## 0.0.5

* **Android**: Incoming call ringtone always plays, including when the device is on mute/vibrate (alarm stream + MediaPlayer). Missing custom `raw/` tones fall back to the system default ringtone. Vibration is no longer suppressed in silent mode.
* **iOS**: CallKit always uses Apple's system ringtone unless a custom bundle sound exists. `system_ringtone_default` / missing files no longer leave CallKit silent.

## 0.0.4

* **Android**: Emit Dart `CallKitAction` names (`accept`, `decline`, …) on the event bus instead of `incoming_call_kit.ACCEPTED`-style strings. Unknown actions previously fell back to `dismissed`, so heads-up Accept incorrectly triggered decline/reject handlers.
* **Dart**: `CallKitEvent.fromMap` now normalizes legacy Android broadcast action strings for backward compatibility.

## 0.0.3

* **Android**: Fix full-screen Accept/Decline tap doing nothing. Swipe `OnTouchListener` consumed touches so `OnClickListener` never fired; taps are now handled in the touch listener.

## 0.0.2

* **Android**: Fix duplicate incoming-call UI caused by posting the same FullScreenIntent notification twice (foreground service + second notify under another id).
* **Android**: `ensureForeground` now uses a silent minimal notification only; the real call notification is posted once in `handleShowIncoming`.
* **Android**: Heads-up / notification actions now use `textAccept` and `textDecline` (same labels as the full-screen Activity). Custom labels skip `CallStyle` because it forces system "Answer"/"Decline".
* **Android**: Stronger full-screen Activity dedupe for concurrent FullScreenIntent launches.
* **iOS**: No duplicate-UI change needed (single `reportNewIncomingCall`). Accept/Decline button titles remain system-controlled by CallKit (Apple does not allow custom action labels).

## 0.0.1

* Initial release of `incoming_call_kit`.
* **Incoming calls**: Show native full-screen call UI on Android (custom Activity with gradient/solid background, avatar, swipe-to-answer) and CallKit on iOS.
* **Outgoing calls**: `startCall`, `setCallConnected`, `endCall`, `endAllCalls` with ongoing call notifications (Android) and CXStartCallAction (iOS).
* **Missed call notifications**: Custom missed call notifications with "Call Back" action on both platforms.
* **Event stream**: Unified `onEvent` stream for all call lifecycle events (accept, decline, timeout, dismissed, callback, callStart, callConnected, callEnded, audioSessionActivated, toggleHold, toggleMute, toggleDmtf, toggleGroup, voipTokenUpdated).
* **Background handler**: `registerBackgroundHandler` for processing call events when the app is killed/terminated via headless FlutterEngine.
* **Pending event replay**: Events fired while the Flutter engine is dead are persisted and replayed on next attach.
* **Android full-screen call Activity**: Lock screen support, display cutouts, gradient backgrounds, avatar loading, pulse animation, swipe gestures, haptic feedback.
* **Android notifications**: `NotificationCompat.CallStyle` on API 31+, per-call notification IDs, non-dismissible ongoing notifications, foreground service with `phoneCall` type.
* **Android foreground service fallback**: Graceful fallback to notification-only when `ForegroundServiceStartNotAllowedException` is thrown.
* **Android permission helpers**: `canUseFullScreenIntent`, `requestFullIntentPermission`, `hasNotificationPermission`, `requestNotificationPermission`.
* **OEM autostart detection**: `isAutoStartAvailable` and `openAutoStartSettings` for Xiaomi, OPPO, Vivo, Huawei, Samsung, OnePlus, and Realme devices.
* **iOS CallKit integration**: Full CXProvider/CXCallController implementation with proper audio session configuration for WebRTC/VoIP.
* **iOS PushKit/VoIP**: `PKPushRegistry` delegate with `getDevicePushTokenVoIP` and automatic `voipTokenUpdated` events.
* **iOS missed call**: `UNUserNotificationCenter` with custom category and "Call Back" action.
* **iOS audio session**: Proper `AVAudioSession` configuration with `.playAndRecord` category and `.voiceChat` mode before fulfilling answer action.
* **Multi-call support**: Track multiple simultaneous calls independently on both platforms.
* **Flutter widget**: `IncomingCallScreen` widget for foreground use with customizable gradient/solid background, avatar with pulse animation, and swipe-to-answer/decline.
* **Swift Package Manager**: `Package.swift` included for SPM support alongside CocoaPods.
* **Android 15 compliance**: `PROPERTY_SPECIAL_USE_FGS_SUBTYPE` declared for foreground service.
