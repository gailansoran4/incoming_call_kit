import 'package:plugin_platform_interface/plugin_platform_interface.dart';

import 'incoming_call_kit_method_channel.dart';

abstract class IncomingCallKitPlatform extends PlatformInterface {
  IncomingCallKitPlatform() : super(token: _token);

  static final Object _token = Object();

  static IncomingCallKitPlatform _instance = IncomingCallKitMethodChannel();

  static IncomingCallKitPlatform get instance => _instance;

  static set instance(IncomingCallKitPlatform instance) {
    PlatformInterface.verifyToken(instance, _token);
    _instance = instance;
  }

  // === INCOMING CALL ===

  Future<void> show(Map<String, dynamic> params) {
    throw UnimplementedError('show() has not been implemented.');
  }

  Future<void> dismiss(String callId) {
    throw UnimplementedError('dismiss() has not been implemented.');
  }

  Future<void> dismissAll() {
    throw UnimplementedError('dismissAll() has not been implemented.');
  }

  // === OUTGOING CALL ===

  Future<void> startCall(Map<String, dynamic> params) {
    throw UnimplementedError('startCall() has not been implemented.');
  }

  Future<void> setCallConnected(String callId) {
    throw UnimplementedError('setCallConnected() has not been implemented.');
  }

  Future<void> endCall(String callId) {
    throw UnimplementedError('endCall() has not been implemented.');
  }

  Future<void> endAllCalls() {
    throw UnimplementedError('endAllCalls() has not been implemented.');
  }

  // === MISSED CALL ===

  Future<void> showMissedCallNotification(Map<String, dynamic> params) {
    throw UnimplementedError(
      'showMissedCallNotification() has not been implemented.',
    );
  }

  Future<void> clearMissedCallNotification(String callId) {
    throw UnimplementedError(
      'clearMissedCallNotification() has not been implemented.',
    );
  }

  // === EVENTS ===

  Stream<dynamic> get onEvent {
    throw UnimplementedError('onEvent has not been implemented.');
  }

  // === BACKGROUND HANDLER ===

  Future<void> registerBackgroundHandler(
    int callbackHandle, {
    required int userCallbackHandle,
  }) {
    throw UnimplementedError(
      'registerBackgroundHandler() has not been implemented.',
    );
  }

  // === ANDROID PERMISSIONS ===

  Future<bool> canUseFullScreenIntent() {
    throw UnimplementedError(
      'canUseFullScreenIntent() has not been implemented.',
    );
  }

  Future<void> requestFullIntentPermission() {
    throw UnimplementedError(
      'requestFullIntentPermission() has not been implemented.',
    );
  }

  Future<bool> hasNotificationPermission() {
    throw UnimplementedError(
      'hasNotificationPermission() has not been implemented.',
    );
  }

  Future<bool> requestNotificationPermission() {
    throw UnimplementedError(
      'requestNotificationPermission() has not been implemented.',
    );
  }

  // === OEM AUTOSTART ===

  Future<bool> isAutoStartAvailable() {
    throw UnimplementedError(
      'isAutoStartAvailable() has not been implemented.',
    );
  }

  Future<void> openAutoStartSettings() {
    throw UnimplementedError(
      'openAutoStartSettings() has not been implemented.',
    );
  }

  // === iOS VoIP ===

  Future<String> getDevicePushTokenVoIP() {
    throw UnimplementedError(
      'getDevicePushTokenVoIP() has not been implemented.',
    );
  }

  // === STATE ===

  Future<List<String>> getActiveCalls() {
    throw UnimplementedError('getActiveCalls() has not been implemented.');
  }
}
