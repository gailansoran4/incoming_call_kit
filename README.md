# incoming_call_kit

<p align="center">
  <a href="https://pub.dev/packages/incoming_call_kit">
    <img src="https://img.shields.io/pub/v/incoming_call_kit?color=blueviolet"/>
  </a>
  <a href="https://pub.dev/packages/incoming_call_kit/score">
    <img src="https://img.shields.io/pub/points/incoming_call_kit?logo=dart"/>
  </a>
  <a href="https://pub.dev/packages/incoming_call_kit/score">
    <img src="https://img.shields.io/pub/likes/incoming_call_kit?logo=dart"/>
  </a>
  <img src="https://img.shields.io/badge/Platform-Android%20%7C%20iOS-blue?logo=flutter"/>
  <a href="https://github.com/ashiqu-ali/incoming_call_kit">
    <img src="https://img.shields.io/github/stars/ashiqu-ali/incoming_call_kit?style=social"/>
  </a>
  <a href="https://github.com/ashiqu-ali/incoming_call_kit/blob/main/LICENSE">
    <img src="https://img.shields.io/github/license/ashiqu-ali/incoming_call_kit"/>
  </a>
</p>

<p align="center">
  A highly customizable Flutter plugin for <strong>incoming</strong>, <strong>outgoing</strong>, and <strong>missed call</strong> UI.<br/>
  Native full-screen Activity on Android. Apple CallKit on iOS.<br/>
  Works with <strong>Twilio</strong>, <strong>Agora</strong>, <strong>WebRTC</strong>, <strong>Firebase</strong>, <strong>Vonage</strong>, <strong>Stream</strong>, or any VoIP backend.
</p>

<p align="center">
  <a href="https://www.buymeacoffee.com/ashiqu.ali">
    <img src="https://img.buymeacoffee.com/button-api/?text=Buy%20me%20a%20coffee&emoji=%E2%98%95&slug=ashiqu.ali&button_colour=FFDD00&font_colour=000000&font_family=Lato&outline_colour=000000&coffee_colour=ffffff"/>
  </a>
</p>

---

## Screenshots

<p align="center">
  <img src="https://raw.githubusercontent.com/ashiqu-ali/incoming_call_kit/refs/heads/main/assets/incoming_screen.png" width="280" alt="Full-screen incoming call UI"/>
  &nbsp;&nbsp;&nbsp;&nbsp;
  <img src="https://raw.githubusercontent.com/ashiqu-ali/incoming_call_kit/refs/heads/main/assets/incoming_notification.png" width="280" alt="Incoming call notification"/>
</p>
<p align="center">
  <em>Full-screen call UI &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; Incoming call notification</em>
</p>

---

## Table of Contents

- [Features](#-features)
- [How It Works](#-how-it-works)
- [Installation](#-installation)
- [Android Setup](#-android-setup)
- [iOS Setup](#-ios-setup)
- [Quick Start](#-quick-start)
  - [Show an Incoming Call](#1-show-an-incoming-call)
  - [Listen to Events](#2-listen-to-events)
  - [Dismiss a Call](#3-dismiss-a-call)
  - [Outgoing Calls](#4-outgoing-calls)
  - [Missed Call Notification](#5-missed-call-notification)
  - [Background Handler](#6-background-handler)
  - [Permissions](#7-permissions)
  - [OEM Autostart](#8-oem-autostart-android)
  - [iOS VoIP Token](#9-ios-voip-token)
  - [Active Calls](#10-active-calls)
- [Customization](#-customization)
  - [Android UI Customization](#android-ui-customization)
  - [Gradient Backgrounds](#gradient-backgrounds)
  - [iOS CallKit Customization](#ios-callkit-customization)
  - [Missed Call Notification Config](#missed-call-notification-config)
  - [Flutter Widget (Foreground)](#flutter-widget-foreground)
- [VoIP Integration Guides](#-voip-integration-guides)
  - [Twilio Voice](#twilio-voice)
  - [Agora Voice/Video](#agora-voicevideo)
  - [WebRTC (SIP / Location-based)](#webrtc-sip--location-based)
  - [Firebase Cloud Messaging](#firebase-cloud-messaging-fcm)
- [Event Reference](#-event-reference)
- [API Reference](#-api-reference)
- [Architecture](#-architecture)
- [Troubleshooting](#-troubleshooting)
- [Support](#-support)
- [Connect](#-connect)

---

## ✨ Features

| Feature | Android | iOS |
|---|:---:|:---:|
| Incoming call full-screen UI | Custom native Activity | CallKit |
| Outgoing call management | Notification + timer | CallKit |
| Missed call notification | System notification | UNNotification |
| Lock screen / background | `showWhenLocked` + FGS | CallKit (native) |
| Gradient backgrounds | Linear & Radial | N/A (CallKit) |
| Avatar with pulse animation | Native Activity | N/A (CallKit) |
| Swipe-to-answer gesture | Native Activity | N/A (CallKit) |
| Custom ringtone & vibration | Per-call, ringer-aware | Per-provider |
| Background event handler | Headless FlutterEngine | Headless FlutterEngine |
| PushKit / VoIP token | N/A | PKPushRegistry |
| OEM autostart detection | 7 manufacturers | N/A |
| `CallStyle` notification | API 31+ native treatment | N/A |
| Multi-call support | Per-call notification IDs | Per-UUID tracking |
| Pending event replay | SharedPreferences | In-memory queue |
| Foreground service fallback | Graceful on Android 12+ | N/A |
| Android 15 compliance | FGS subtype declared | N/A |

---

## 📱 How It Works

```
┌─────────────────────────────────────────────────────────────┐
│                      Your Flutter App                       │
│                                                             │
│    VoIP Push (Twilio / Agora / FCM / PushKit)               │
│           │                                                 │
│           ▼                                                 │
│    IncomingCallKit.instance.show(params)                     │
│           │                                                 │
│     ┌─────┴──────┐                                          │
│     ▼            ▼                                          │
│  Android        iOS                                         │
│     │            │                                          │
│  Foreground     CXProvider                                  │
│  Service        .reportNewIncomingCall()                     │
│     │            │                                          │
│     ▼            ▼                                          │
│  Notification   Native CallKit UI                           │
│  + Full-screen  (Apple-designed)                            │
│  Activity       │                                           │
│  (lock/bg)      │                                           │
│     │            │                                          │
│     ▼            ▼                                          │
│  User taps      User taps                                   │
│  Accept/Decline Accept/Decline                              │
│     │            │                                          │
│     ▼            ▼                                          │
│  EventBus ──► Dart onEvent stream ◄── CXProviderDelegate    │
│     │                                                       │
│     ▼                                                       │
│  Connect your Twilio / Agora / WebRTC call                  │
└─────────────────────────────────────────────────────────────┘
```

**Key idea:** This plugin handles the **call UI only** — the ringing screen, notifications, and user actions. Your VoIP SDK (Twilio, Agora, WebRTC, etc.) handles the **actual audio/video connection**. They work together like this:

1. VoIP push arrives → you call `callKit.show()` to display the call screen
2. User taps Accept → you receive `CallKitAction.accept` → you connect your VoIP SDK
3. User taps Decline → you receive `CallKitAction.decline` → you reject on your server
4. Remote side hangs up → you call `callKit.dismiss()` → call screen disappears

---

## 📦 Installation

Add to your `pubspec.yaml`:

```yaml
dependencies:
  incoming_call_kit: ^0.0.1
```

Then run:

```bash
flutter pub get
```

**Requirements:**

| | Minimum |
|---|---|
| Flutter | 3.27.0 |
| Dart SDK | 3.6.0 |
| Android `minSdk` | 24 (Android 7.0) |
| Android `compileSdk` | 36 |
| iOS | 13.0 |

---

## 🤖 Android Setup

### 1. Set minimum SDK

In your app's `android/app/build.gradle`:

```groovy
android {
    defaultConfig {
        minSdk = 24  // Required: Android 7.0+
    }
}
```

### 2. Permissions

All permissions are declared by the plugin automatically:

| Permission | Purpose |
|---|---|
| `FOREGROUND_SERVICE` | Keep call alive in background |
| `FOREGROUND_SERVICE_PHONE_CALL` | Phone call FGS type |
| `POST_NOTIFICATIONS` | Show call notification (Android 13+) |
| `USE_FULL_SCREEN_INTENT` | Lock screen display (Android 14+) |
| `WAKE_LOCK` | Wake device on incoming call |
| `VIBRATE` | Vibration during ring |
| `INTERNET` | Load avatar images |

> **No `SYSTEM_ALERT_WINDOW` needed.** The plugin uses `USE_FULL_SCREEN_INTENT` + foreground service instead.

### 3. Runtime permissions

Request at runtime using the built-in helpers — see [Permissions](#7-permissions).

### 4. OEM autostart (Xiaomi, OPPO, Vivo, etc.)

Some OEMs kill background services. Guide users to whitelist your app — see [OEM Autostart](#8-oem-autostart-android).

---

## 🍎 iOS Setup

### 1. Enable background modes

In Xcode: **Runner** → **Signing & Capabilities** → **+ Capability** → **Background Modes**:

- [x] **Voice over IP**
- [x] **Remote notifications**

Or in `ios/Runner/Info.plist`:

```xml
<key>UIBackgroundModes</key>
<array>
    <string>voip</string>
    <string>remote-notification</string>
</array>
```

### 2. Enable Push Notifications

In Xcode: **Signing & Capabilities** → **+ Capability** → **Push Notifications**.

### 3. CallKit icon (optional)

Add a 40×40pt single-color PNG named `CallKitLogo` to your asset catalog. This appears in the native CallKit UI.

### 4. Custom ringtone (optional)

Add a `.caf` or `.aiff` file to your Xcode project:

```dart
ios: IOSCallKitParams(
  ringtonePath: 'MyRingtone.caf',
),
```

---

## 🚀 Quick Start

### 1. Show an Incoming Call

```dart
import 'package:incoming_call_kit/incoming_call_kit.dart';

final callKit = IncomingCallKit.instance;

await callKit.show(
  CallKitParams(
    id: 'call-123',                         // Unique call ID from your server
    callerName: 'John Doe',
    callerNumber: '+1 234 567 890',
    avatar: 'https://i.pravatar.cc/200',
    duration: const Duration(seconds: 30),   // Auto-timeout → missed call
    extra: {'meetingId': 'abc'},             // Your custom data (passed back in events)
    android: AndroidCallKitParams(
      backgroundGradient: GradientConfig(
        colors: ['#1A1A2E', '#16213E', '#0F3460'],
      ),
    ),
    ios: IOSCallKitParams(
      handleType: 'phoneNumber',
    ),
  ),
);
```

**What happens:**
- **Android** → Foreground service starts → high-priority notification → full-screen `IncomingCallActivity` (even on lock screen)
- **iOS** → Native CallKit UI with caller name and accept/decline

### 2. Listen to Events

```dart
callKit.onEvent.listen((event) {
  switch (event.action) {
    case CallKitAction.accept:
      // User accepted → connect your VoIP call here
      print('Accepted call ${event.callId}');
      break;
    case CallKitAction.decline:
      // User declined → reject on your server
      print('Declined call ${event.callId}');
      break;
    case CallKitAction.timeout:
      // No answer within duration
      print('Call timed out');
      break;
    case CallKitAction.dismissed:
      // You called dismiss() (remote cancel)
      print('Call dismissed');
      break;
    case CallKitAction.callback:
      // User tapped "Call Back" on missed call notification
      print('Callback requested for ${event.callId}');
      break;
    default:
      break;
  }
});
```

### 3. Dismiss a Call

When the remote side cancels or hangs up:

```dart
// Dismiss a specific call
await callKit.dismiss('call-123');

// Dismiss all active calls
await callKit.dismissAll();
```

### 4. Outgoing Calls

```dart
// Start an outgoing call
await callKit.startCall(
  CallKitParams(
    id: 'out-456',
    callerName: 'Jane Smith',
    callerNumber: '+1 987 654 321',
    android: AndroidCallKitParams(),
    ios: IOSCallKitParams(),
  ),
);

// When media connects (WebRTC peer connection / Twilio connected / Agora joined)
await callKit.setCallConnected('out-456');

// End the call
await callKit.endCall('out-456');

// Or end all calls at once
await callKit.endAllCalls();
```

**What happens:**
- **Android** → Ongoing call notification with "End Call" button. On `setCallConnected`, notification shows a duration timer.
- **iOS** → CallKit outgoing call UI via `CXStartCallAction`.

### 5. Missed Call Notification

```dart
await callKit.showMissedCallNotification(
  CallKitParams(
    id: 'missed-789',
    callerName: 'Missed Caller',
    callerNumber: '+1 111 222 333',
    missedCallNotification: NotificationParams(
      showNotification: true,
      subtitle: 'Missed Call',
      showCallback: true,
      callbackText: 'Call Back',
    ),
    android: AndroidCallKitParams(),
    ios: IOSCallKitParams(),
  ),
);

// Clear it later
await callKit.clearMissedCallNotification('missed-789');
```

> Tapping "Call Back" fires `CallKitAction.callback`.

### 6. Background Handler

Process call events even when your app is **killed / terminated**:

```dart
// Must be top-level or static — NOT an instance method or closure
@pragma('vm:entry-point')
Future<void> backgroundCallHandler(CallKitEvent event) async {
  print('Background event: ${event.action} for ${event.callId}');
  // e.g. log to analytics, notify your server
}

void main() {
  WidgetsFlutterBinding.ensureInitialized();

  // Register BEFORE runApp()
  IncomingCallKit.registerBackgroundHandler(backgroundCallHandler);

  runApp(MyApp());
}
```

> The plugin starts a headless `FlutterEngine` with an internal dispatcher that
> delivers events over a MethodChannel. Your handler runs there — it won't have
> access to your app's widget tree or Riverpod/GetX UI state. Persist what you
> need (token, order id in `event.extra`) and call your API directly.

### 7. Permissions

```dart
final callKit = IncomingCallKit.instance;

// ── Notification permission (Android 13+) ──
final hasNotif = await callKit.hasNotificationPermission();
if (!hasNotif) {
  final granted = await callKit.requestNotificationPermission();
  print('Notification permission: $granted');
}

// ── Full-screen intent (Android 14+) ──
final canFullScreen = await callKit.canUseFullScreenIntent();
if (!canFullScreen) {
  await callKit.requestFullIntentPermission();
  // Opens system settings — user must grant manually
}
```

> On iOS, both return sensible defaults. Notification permission uses the standard iOS authorization flow.

### 8. OEM Autostart (Android)

Chinese OEMs (Xiaomi, OPPO, Vivo, Huawei, Realme, OnePlus, Samsung) aggressively kill background processes:

```dart
final available = await callKit.isAutoStartAvailable();
if (available) {
  // Show a dialog explaining why autostart is needed, then:
  await callKit.openAutoStartSettings();
}
```

**Supported manufacturers:**

| Manufacturer | Settings screen |
|---|---|
| Xiaomi / Redmi | MIUI Autostart Manager |
| OPPO / Realme | ColorOS Startup Manager |
| Vivo / iQOO | Background App Manager |
| Huawei / Honor | Startup Manager / Protected Apps |
| Samsung | Battery Optimization |
| OnePlus | Chain Launch Manager |

> Returns `false` on iOS and non-OEM Android devices.

### 9. iOS VoIP Token

```dart
final token = await callKit.getDevicePushTokenVoIP();
print('VoIP token: $token');
// Send this to your server for PushKit delivery

// Listen for token updates:
callKit.onEvent.listen((event) {
  if (event.action == CallKitAction.voipTokenUpdated) {
    final newToken = event.extra?['token'] as String?;
    print('New VoIP token: $newToken');
  }
});
```

> Returns an empty string on Android.

### 10. Active Calls

```dart
final activeCalls = await callKit.getActiveCalls();
print('Active call IDs: $activeCalls'); // ['call-123', 'out-456']
```

---

## 🎨 Customization

**Important:** All customization is done from **Dart**. You never need to touch Kotlin, Swift, or XML files.

### Android UI Customization

The Android call screen is a fully native `Activity`, but every visual element is controlled from Dart via `AndroidCallKitParams`:

```dart
AndroidCallKitParams(
  // ── Background ──
  backgroundColor: '#1B1B2F',          // Solid color (hex string)
  // backgroundGradient: ...,          // OR gradient (see below) — mutually exclusive
  // backgroundImageUrl: '...',        // Background image URL

  // ── Avatar ──
  avatarSize: 96,                      // Diameter in dp
  avatarBorderColor: '#FFFFFF',
  avatarBorderWidth: 3.0,
  avatarPulseAnimation: true,          // Breathing ring animation

  // ── Initials fallback (when no avatar URL) ──
  initialsBackgroundColor: '#3A3A5C',
  initialsTextColor: '#FFFFFF',

  // ── Text ──
  callerNameColor: '#FFFFFF',
  callerNameFontSize: 28,
  callerNumberColor: '#B3FFFFFF',
  callerNumberFontSize: 16,
  statusText: 'Incoming Call',
  statusTextColor: '#80FFFFFF',

  // ── Buttons ──
  acceptButtonColor: '#4CAF50',        // Green
  declineButtonColor: '#F44336',       // Red
  buttonSize: 64,                      // Diameter in dp

  // ── Gestures ──
  enableSwipeGesture: true,            // Swipe up = accept, down = decline
  swipeThreshold: 120,                 // Pixels needed to trigger

  // ── Sound ──
  ringtonePath: 'system_ringtone_default',  // or custom resource name
  enableVibration: true,
  vibrationPattern: [0, 1000, 1000],        // [delay, vibrate, sleep, ...]

  // ── Behavior ──
  showOnLockScreen: true,
  channelName: 'Incoming Calls',
  showCallerIdInNotification: true,
)
```

### Gradient Backgrounds

Use `GradientConfig` instead of a solid color:

```dart
// ── Linear gradient (top to bottom) ──
AndroidCallKitParams(
  backgroundGradient: GradientConfig(
    type: 'linear',
    colors: ['#1A1A2E', '#16213E', '#0F3460'],
    stops: [0.0, 0.5, 1.0],              // Optional
    begin: {'x': 0.5, 'y': 0.0},         // Top center
    end: {'x': 0.5, 'y': 1.0},           // Bottom center
  ),
)

// ── Radial gradient ──
AndroidCallKitParams(
  backgroundGradient: GradientConfig(
    type: 'radial',
    colors: ['#2D1B69', '#11001C'],
    center: {'x': 0.5, 'y': 0.3},
    radius: 0.8,
  ),
)
```

> `backgroundColor` and `backgroundGradient` are **mutually exclusive**. Setting both throws `AssertionError` in debug mode. If neither is set, the default gradient `['#1A1A2E', '#16213E', '#0F3460']` is used.

### iOS CallKit Customization

iOS uses Apple's native CallKit — customization is limited to what Apple allows:

```dart
IOSCallKitParams(
  iconName: 'CallKitLogo',          // 40×40pt asset catalog image
  handleType: 'phoneNumber',        // 'phoneNumber', 'email', or 'generic'
  supportsVideo: false,
  maximumCallGroups: 2,
  maximumCallsPerCallGroup: 1,
  ringtonePath: 'MyRingtone.caf',
  supportsDTMF: true,
  supportsHolding: false,
)
```

### Missed Call Notification Config

```dart
NotificationParams(
  showNotification: true,      // Enable the notification
  subtitle: 'Missed Call',     // Notification body text
  showCallback: true,          // Show "Call Back" action button
  callbackText: 'Call Back',   // Button label
)
```

### Flutter Widget (Foreground)

For **foreground** use, push the included Flutter widget as a route:

```dart
Navigator.of(context).push(
  MaterialPageRoute(
    builder: (_) => IncomingCallScreen(
      params: params,
      onAccept: () {
        Navigator.pop(context);
        // Connect your VoIP call
      },
      onDecline: () {
        Navigator.pop(context);
        // Reject the call
      },
    ),
  ),
);
```

> Renders the same gradient, avatar, pulse animation, and swipe buttons — but as a Flutter widget inside your app's navigation.

---

## 🔌 VoIP Integration Guides

This plugin provides the **call UI layer**. Your VoIP SDK provides the **audio/video layer**. Here's how to connect them.

### Twilio Voice

```dart
// 1. Receive push notification from Twilio
// (via firebase_messaging or your push handler)

// 2. Show the call UI
await callKit.show(CallKitParams(
  id: twilioCallSid,
  callerName: callerIdentity,
  callerNumber: fromNumber,
  android: AndroidCallKitParams(),
  ios: IOSCallKitParams(),
));

// 3. Handle events
callKit.onEvent.listen((event) {
  switch (event.action) {
    case CallKitAction.accept:
      // Accept the Twilio call
      await twilioVoice.call.answer();
      break;
    case CallKitAction.decline:
      // Reject the Twilio call
      await twilioVoice.call.reject();
      break;
    case CallKitAction.callEnded:
      // End the Twilio call
      await twilioVoice.call.disconnect();
      break;
    default:
      break;
  }
});

// 4. When Twilio remote party disconnects:
twilioVoice.call.onDisconnected(() {
  callKit.dismiss(twilioCallSid);
});
```

### Agora Voice/Video

```dart
// 1. Receive signaling message (via FCM, WebSocket, etc.)

// 2. Show the call UI
await callKit.show(CallKitParams(
  id: agoraChannelName,
  callerName: callerName,
  avatar: callerAvatar,
  android: AndroidCallKitParams(),
  ios: IOSCallKitParams(),
));

// 3. Handle events
callKit.onEvent.listen((event) async {
  switch (event.action) {
    case CallKitAction.accept:
      // Join the Agora channel
      await agoraEngine.joinChannel(
        token: agoraToken,
        channelId: agoraChannelName,
        uid: localUid,
      );
      await callKit.setCallConnected(agoraChannelName);
      break;
    case CallKitAction.decline:
      // Notify server you declined
      await signaling.rejectCall(agoraChannelName);
      break;
    case CallKitAction.callEnded:
      await agoraEngine.leaveChannel();
      break;
    default:
      break;
  }
});

// 4. When remote user leaves:
agoraEngine.onUserOffline = (uid, reason) {
  callKit.endCall(agoraChannelName);
};
```

### WebRTC (SIP / Location-based)

```dart
// 1. Receive SIP INVITE or signaling push

// 2. Show the call UI
await callKit.show(CallKitParams(
  id: sessionId,
  callerName: sipCaller.displayName,
  callerNumber: sipCaller.uri,
  android: AndroidCallKitParams(),
  ios: IOSCallKitParams(),
));

// 3. Handle events
callKit.onEvent.listen((event) async {
  switch (event.action) {
    case CallKitAction.accept:
      // Create WebRTC peer connection, add tracks, send SDP answer
      await peerConnection.setRemoteDescription(offer);
      final answer = await peerConnection.createAnswer();
      await peerConnection.setLocalDescription(answer);
      await signaling.sendAnswer(answer);
      await callKit.setCallConnected(sessionId);
      break;
    case CallKitAction.decline:
      await signaling.sendReject(sessionId);
      break;
    case CallKitAction.audioSessionActivated:
      // iOS only — configure audio track NOW
      await peerConnection.setAudioEnabled(true);
      break;
    default:
      break;
  }
});
```

### Firebase Cloud Messaging (FCM)

Use FCM as the push transport to trigger the call UI:

```dart
// In your FCM background handler:
@pragma('vm:entry-point')
Future<void> firebaseMessagingBackgroundHandler(RemoteMessage message) async {
  if (message.data['type'] == 'incoming_call') {
    await IncomingCallKit.instance.show(
      CallKitParams(
        id: message.data['callId'],
        callerName: message.data['callerName'],
        callerNumber: message.data['callerNumber'],
        avatar: message.data['avatar'],
        android: AndroidCallKitParams(),
        ios: IOSCallKitParams(),
      ),
    );
  }
}

// For iOS, use PushKit VoIP pushes instead of FCM for reliable wake-up:
final voipToken = await callKit.getDevicePushTokenVoIP();
// Send voipToken to your server → server sends PushKit push → plugin shows CallKit
```

> **iOS important:** On iOS 13+, Apple requires you to report a CallKit call for every PushKit push. This plugin handles that automatically in `pushRegistry:didReceiveIncomingPushWith`.

---

## 📋 Event Reference

All events arrive through `callKit.onEvent` as `CallKitEvent` objects:

| Action | Platform | Fires when |
|---|---|---|
| `accept` | Both | User tapped accept or swiped up |
| `decline` | Both | User tapped decline or swiped down |
| `timeout` | Both | No answer within `duration` |
| `dismissed` | Both | Call cancelled via `dismiss()` (remote cancel) |
| `callback` | Both | User tapped "Call Back" on missed call notification |
| `callStart` | Both | Outgoing call started via `startCall()` |
| `callConnected` | Both | `setCallConnected()` acknowledged |
| `callEnded` | Both | Call ended (outgoing) via `endCall()` |
| `audioSessionActivated` | iOS | Audio session ready — configure WebRTC audio here |
| `toggleHold` | iOS | User toggled hold in CallKit UI |
| `toggleMute` | iOS | User toggled mute in CallKit UI |
| `toggleDmtf` | iOS | User sent DTMF tone |
| `toggleGroup` | iOS | User toggled call group |
| `voipTokenUpdated` | iOS | PushKit VoIP token changed |

```dart
class CallKitEvent {
  final CallKitAction action;              // The event type (enum)
  final String callId;                     // Which call this belongs to
  final Map<String, dynamic>? extra;       // Your custom data + event-specific data
}
```

---

## 📚 API Reference

### `IncomingCallKit.instance`

| Method | Returns | Description |
|---|---|---|
| `show(CallKitParams)` | `Future<void>` | Show incoming call UI |
| `dismiss(String callId)` | `Future<void>` | Dismiss a specific call (remote cancel) |
| `dismissAll()` | `Future<void>` | Dismiss all active calls |
| `startCall(CallKitParams)` | `Future<void>` | Start an outgoing call |
| `setCallConnected(String)` | `Future<void>` | Mark outgoing call as connected |
| `endCall(String callId)` | `Future<void>` | End a specific call |
| `endAllCalls()` | `Future<void>` | End all active calls |
| `showMissedCallNotification(CallKitParams)` | `Future<void>` | Show missed call notification |
| `clearMissedCallNotification(String)` | `Future<void>` | Remove a missed call notification |
| `onEvent` | `Stream<CallKitEvent>` | Stream of all call lifecycle events |
| `canUseFullScreenIntent()` | `Future<bool>` | Check full-screen permission (Android) |
| `requestFullIntentPermission()` | `Future<void>` | Open settings for full-screen (Android) |
| `hasNotificationPermission()` | `Future<bool>` | Check notification permission |
| `requestNotificationPermission()` | `Future<bool>` | Request notification permission |
| `isAutoStartAvailable()` | `Future<bool>` | Check OEM autostart settings (Android) |
| `openAutoStartSettings()` | `Future<void>` | Open OEM autostart settings (Android) |
| `getDevicePushTokenVoIP()` | `Future<String>` | Get PushKit VoIP token (iOS) |
| `getActiveCalls()` | `Future<List<String>>` | Get all active call IDs |

### `IncomingCallKit.registerBackgroundHandler(handler)`

| Parameter | Type | Description |
|---|---|---|
| `handler` | `Future<void> Function(CallKitEvent)` | Top-level or static function with `@pragma('vm:entry-point')` |

---

## 🏗 Architecture

```
┌────────────────────────────────────────────────────────────────────┐
│                          Dart Layer                                │
│                                                                    │
│  IncomingCallKit (singleton)                                       │
│       │                                                            │
│       ▼                                                            │
│  IncomingCallKitPlatform (abstract)                                │
│       │                                                            │
│       ▼                                                            │
│  IncomingCallKitMethodChannel                                      │
│  ├─ MethodChannel: "com.ashiquali.incoming_call_kit/methods"       │
│  └─ EventChannel:  "com.ashiquali.incoming_call_kit/events"        │
└─────────────────────┬──────────────────────┬───────────────────────┘
                      │                      │
    ┌─────────────────▼────────┐  ┌──────────▼──────────────┐
    │     Android (Kotlin)     │  │      iOS (Swift)        │
    │                          │  │                          │
    │  IncomingCallKitPlugin   │  │  IncomingCallKitPlugin   │
    │  ├─ IncomingCallService  │  │  ├─ CXProviderDelegate   │
    │  ├─ IncomingCallActivity │  │  ├─ PKPushRegistryDel.   │
    │  ├─ AnswerTrampoline     │  │  ├─ UNNotificationDel.   │
    │  ├─ NotificationBuilder  │  │  └─ FlutterStreamHandler │
    │  ├─ CallKitEventBus      │  │                          │
    │  ├─ CallKitConfigStore   │  │  Frameworks:             │
    │  ├─ CallKitRingtoneManager│ │  - CallKit               │
    │  ├─ BackgroundCallHandler│  │  - PushKit               │
    │  └─ OemAutostartHelper   │  │  - AVFoundation          │
    │                          │  │  - UserNotifications     │
    └──────────────────────────┘  └──────────────────────────┘
```

**Key design decisions:**

- **EventBus over LocalBroadcastManager** — `LocalBroadcastManager` is deprecated. The in-process `CallKitEventBus` is thread-safe and lifecycle-aware.
- **Pending event replay** — Events fired while the Flutter engine is dead are persisted to `SharedPreferences` and replayed when the engine reattaches.
- **Per-call notification IDs** — Derived from `callId.hashCode()`, supporting multiple simultaneous calls.
- **Foreground service fallback** — On Android 12+, if `startForegroundService()` throws `ForegroundServiceStartNotAllowedException`, the plugin falls back to notification-only.
- **`CallStyle` on API 31+** — Native Android call notification treatment with big accept/decline buttons.
- **No `SYSTEM_ALERT_WINDOW`** — Full-screen lock-screen display via `USE_FULL_SCREEN_INTENT` + foreground service.

---

## 🔧 Troubleshooting

### Call screen doesn't show on lock screen (Android 14+)

Full-screen intent permission is required. Check and request:

```dart
if (!await callKit.canUseFullScreenIntent()) {
  await callKit.requestFullIntentPermission(); // Opens system settings
}
```

### Calls not received when app is killed (Xiaomi, OPPO, Vivo)

1. Guide users to enable autostart:
   ```dart
   if (await callKit.isAutoStartAvailable()) {
     await callKit.openAutoStartSettings();
   }
   ```
2. Register a background handler:
   ```dart
   IncomingCallKit.registerBackgroundHandler(myHandler);
   ```

### No audio after accepting call on iOS

Listen for `audioSessionActivated` before configuring WebRTC audio:

```dart
callKit.onEvent.listen((event) {
  if (event.action == CallKitAction.audioSessionActivated) {
    // Configure your WebRTC / Twilio / Agora audio NOW
  }
});
```

### Duplicate notifications on Android

Each call must have a **unique `id`**. The plugin derives notification IDs from `callId.hashCode()`.

### Events not received after app restart

Events from killed state are stored and replayed automatically when you listen to `onEvent`. Subscribe early — in `initState` of your root widget.

### Android 15 Play Store warning

The plugin declares `PROPERTY_SPECIAL_USE_FGS_SUBTYPE` with value `incoming_voip_call` in the manifest. No action needed.

### iOS PushKit requirement

On iOS 13+, Apple **requires** a CallKit call for every PushKit VoIP push. The plugin handles this in `pushRegistry:didReceiveIncomingPushWith:` — just make sure your push payload includes `id`, `callerName`, and `callerNumber` keys.

---

## 🌐 Connect

<p align="center">
  <a href="https://www.linkedin.com/in/ashiqu-ali">
    <img src="https://cdn-icons-png.flaticon.com/512/174/174857.png" width="30"/>
  </a>
  &nbsp;&nbsp;
  <a href="https://ashiqu-ali.medium.com/">
    <img src="https://cdn-icons-png.flaticon.com/512/5968/5968906.png" width="30"/>
  </a>
  &nbsp;&nbsp;
  <a href="https://www.instagram.com/ashiqu_ali_">
    <img src="https://cdn-icons-png.flaticon.com/512/174/174855.png" width="30"/>
  </a>
  &nbsp;&nbsp;
  <a href="https://x.com/ashiquali007">
    <img src="https://cdn-icons-png.flaticon.com/512/733/733579.png" width="30"/>
  </a>
</p>

---

## License

See [LICENSE](LICENSE) for details.
