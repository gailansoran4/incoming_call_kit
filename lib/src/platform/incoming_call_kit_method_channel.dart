import 'package:flutter/services.dart';

import 'incoming_call_kit_platform_interface.dart';

class IncomingCallKitMethodChannel extends IncomingCallKitPlatform {
  static const _methodChannel = MethodChannel(
    'com.ashiquali.incoming_call_kit/methods',
  );
  static const _eventChannel = EventChannel(
    'com.ashiquali.incoming_call_kit/events',
  );

  Stream<dynamic>? _eventStream;

  // === INCOMING CALL ===

  @override
  Future<void> show(Map<String, dynamic> params) async {
    await _methodChannel.invokeMethod('show', params);
  }

  @override
  Future<void> dismiss(String callId) async {
    await _methodChannel.invokeMethod('dismiss', {'id': callId});
  }

  @override
  Future<void> dismissAll() async {
    await _methodChannel.invokeMethod('dismissAll');
  }

  // === OUTGOING CALL ===

  @override
  Future<void> startCall(Map<String, dynamic> params) async {
    await _methodChannel.invokeMethod('startCall', params);
  }

  @override
  Future<void> setCallConnected(String callId) async {
    await _methodChannel.invokeMethod('setCallConnected', {'id': callId});
  }

  @override
  Future<void> endCall(String callId) async {
    await _methodChannel.invokeMethod('endCall', {'id': callId});
  }

  @override
  Future<void> endAllCalls() async {
    await _methodChannel.invokeMethod('endAllCalls');
  }

  // === MISSED CALL ===

  @override
  Future<void> showMissedCallNotification(Map<String, dynamic> params) async {
    await _methodChannel.invokeMethod('showMissedCallNotification', params);
  }

  @override
  Future<void> clearMissedCallNotification(String callId) async {
    await _methodChannel.invokeMethod('clearMissedCallNotification', {
      'id': callId,
    });
  }

  // === EVENTS ===

  @override
  Stream<dynamic> get onEvent {
    _eventStream ??= _eventChannel.receiveBroadcastStream();
    return _eventStream!;
  }

  // === BACKGROUND HANDLER ===

  @override
  Future<void> registerBackgroundHandler(
    int callbackHandle, {
    required int userCallbackHandle,
  }) async {
    await _methodChannel.invokeMethod('registerBackgroundHandler', {
      'callbackHandle': callbackHandle,
      'userCallbackHandle': userCallbackHandle,
    });
  }

  // === ANDROID PERMISSIONS ===

  @override
  Future<bool> canUseFullScreenIntent() async {
    return await _methodChannel.invokeMethod<bool>('canUseFullScreenIntent') ??
        true;
  }

  @override
  Future<void> requestFullIntentPermission() async {
    await _methodChannel.invokeMethod('requestFullIntentPermission');
  }

  @override
  Future<bool> hasNotificationPermission() async {
    return await _methodChannel.invokeMethod<bool>(
          'hasNotificationPermission',
        ) ??
        true;
  }

  @override
  Future<bool> requestNotificationPermission() async {
    return await _methodChannel.invokeMethod<bool>(
          'requestNotificationPermission',
        ) ??
        false;
  }

  // === OEM AUTOSTART ===

  @override
  Future<bool> isAutoStartAvailable() async {
    return await _methodChannel.invokeMethod<bool>('isAutoStartAvailable') ??
        false;
  }

  @override
  Future<void> openAutoStartSettings() async {
    await _methodChannel.invokeMethod('openAutoStartSettings');
  }

  // === iOS VoIP ===

  @override
  Future<String> getDevicePushTokenVoIP() async {
    return await _methodChannel.invokeMethod<String>(
          'getDevicePushTokenVoIP',
        ) ??
        '';
  }

  // === STATE ===

  @override
  Future<List<String>> getActiveCalls() async {
    final result = await _methodChannel.invokeMethod<List>('getActiveCalls');
    return result?.cast<String>() ?? [];
  }
}
