import 'dart:ui';

import 'background/background_callback_dispatcher.dart';
import 'models/call_kit_event.dart';
import 'models/call_kit_params.dart';
import 'platform/incoming_call_kit_platform_interface.dart';

class IncomingCallKit {
  static final IncomingCallKit instance = IncomingCallKit._();
  IncomingCallKit._();

  // === INCOMING CALL ===

  Future<void> show(CallKitParams params) {
    return IncomingCallKitPlatform.instance.show(params.toMap());
  }

  Future<void> dismiss(String callId) {
    return IncomingCallKitPlatform.instance.dismiss(callId);
  }

  Future<void> dismissAll() {
    return IncomingCallKitPlatform.instance.dismissAll();
  }

  // === OUTGOING CALL ===

  Future<void> startCall(CallKitParams params) {
    return IncomingCallKitPlatform.instance.startCall(params.toMap());
  }

  Future<void> setCallConnected(String callId) {
    return IncomingCallKitPlatform.instance.setCallConnected(callId);
  }

  Future<void> endCall(String callId) {
    return IncomingCallKitPlatform.instance.endCall(callId);
  }

  Future<void> endAllCalls() {
    return IncomingCallKitPlatform.instance.endAllCalls();
  }

  // === MISSED CALL ===

  Future<void> showMissedCallNotification(CallKitParams params) {
    return IncomingCallKitPlatform.instance.showMissedCallNotification(
      params.toMap(),
    );
  }

  Future<void> clearMissedCallNotification(String callId) {
    return IncomingCallKitPlatform.instance.clearMissedCallNotification(callId);
  }

  // === EVENTS ===

  Stream<CallKitEvent> get onEvent {
    return IncomingCallKitPlatform.instance.onEvent.map((event) {
      return CallKitEvent.fromMap(Map<String, dynamic>.from(event as Map));
    });
  }

  // === BACKGROUND HANDLER ===

  static void registerBackgroundHandler(
    Future<void> Function(CallKitEvent) handler,
  ) {
    final dispatcherHandle = PluginUtilities.getCallbackHandle(
      callbackDispatcher,
    );
    final userHandle = PluginUtilities.getCallbackHandle(handler);
    if (dispatcherHandle == null || userHandle == null) {
      throw ArgumentError(
        'The handler must be a top-level or static function. '
        'Make sure it is annotated with @pragma(\'vm:entry-point\').',
      );
    }
    IncomingCallKitPlatform.instance.registerBackgroundHandler(
      dispatcherHandle.toRawHandle(),
      userCallbackHandle: userHandle.toRawHandle(),
    );
  }

  // === ANDROID PERMISSIONS ===

  Future<bool> canUseFullScreenIntent() {
    return IncomingCallKitPlatform.instance.canUseFullScreenIntent();
  }

  Future<void> requestFullIntentPermission() {
    return IncomingCallKitPlatform.instance.requestFullIntentPermission();
  }

  Future<bool> hasNotificationPermission() {
    return IncomingCallKitPlatform.instance.hasNotificationPermission();
  }

  Future<bool> requestNotificationPermission() {
    return IncomingCallKitPlatform.instance.requestNotificationPermission();
  }

  // === OEM AUTOSTART (Android only) ===

  Future<bool> isAutoStartAvailable() {
    return IncomingCallKitPlatform.instance.isAutoStartAvailable();
  }

  Future<void> openAutoStartSettings() {
    return IncomingCallKitPlatform.instance.openAutoStartSettings();
  }

  // === iOS VoIP ===

  Future<String> getDevicePushTokenVoIP() {
    return IncomingCallKitPlatform.instance.getDevicePushTokenVoIP();
  }

  // === STATE ===

  Future<List<String>> getActiveCalls() {
    return IncomingCallKitPlatform.instance.getActiveCalls();
  }
}
