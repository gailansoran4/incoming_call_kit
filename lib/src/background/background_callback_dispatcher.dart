import 'dart:ui';

import 'package:flutter/services.dart';
import 'package:flutter/widgets.dart';

import '../models/call_kit_event.dart';

const String backgroundMethodChannelName =
    'com.ashiquali.incoming_call_kit/background';

/// Headless isolate entrypoint used by Android/iOS when the app is killed.
///
/// Native code starts this dispatcher, waits for
/// `backgroundHandlerInitialized`, then delivers events via
/// `onBackgroundEvent` with the user callback handle.
@pragma('vm:entry-point')
void callbackDispatcher() {
  WidgetsFlutterBinding.ensureInitialized();

  const channel = MethodChannel(backgroundMethodChannelName);

  channel.setMethodCallHandler((call) async {
    if (call.method != 'onBackgroundEvent') {
      return;
    }

    final rawArgs = call.arguments;
    if (rawArgs is! Map) {
      return;
    }

    final args = Map<String, dynamic>.from(rawArgs);
    final userHandleRaw = args.remove('userCallbackHandle');
    final userHandle = switch (userHandleRaw) {
      int value => value,
      num value => value.toInt(),
      _ => null,
    };
    if (userHandle == null) {
      return;
    }

    final callback = PluginUtilities.getCallbackFromHandle(
      CallbackHandle.fromRawHandle(userHandle),
    );
    if (callback == null) {
      return;
    }

    final event = CallKitEvent.fromMap(args);
    final result = callback(event);
    if (result is Future) {
      await result;
    }
  });

  channel.invokeMethod<void>('backgroundHandlerInitialized');
}
