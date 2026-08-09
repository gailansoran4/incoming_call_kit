import 'package:flutter_test/flutter_test.dart';
import 'package:incoming_call_kit/incoming_call_kit.dart';

void main() {
  test('CallKitParams serialization', () {
    final params = CallKitParams(
      id: 'test-123',
      callerName: 'Test User',
      callerNumber: '+1234567890',
      type: 0,
    );

    final map = params.toMap();
    expect(map['id'], 'test-123');
    expect(map['callerName'], 'Test User');
    expect(map['callerNumber'], '+1234567890');
    expect(map['type'], 0);
    expect(map['duration'], 30000);
  });

  test('CallKitEvent fromMap', () {
    final event = CallKitEvent.fromMap({
      'action': 'accept',
      'callId': 'call-1',
      'extra': {'key': 'value'},
    });

    expect(event.action, CallKitAction.accept);
    expect(event.callId, 'call-1');
    expect(event.extra?['key'], 'value');
  });

  test('CallKitEvent fromMap maps Android broadcast actions', () {
    expect(
      CallKitEvent.fromMap({
        'action': 'incoming_call_kit.ACCEPTED',
        'callId': 'a',
      }).action,
      CallKitAction.accept,
    );
    expect(
      CallKitEvent.fromMap({
        'action': 'incoming_call_kit.DECLINED',
        'callId': 'b',
      }).action,
      CallKitAction.decline,
    );
    expect(
      CallKitEvent.fromMap({
        'action': 'incoming_call_kit.TIMEOUT',
        'callId': 'c',
      }).action,
      CallKitAction.timeout,
    );
    expect(
      CallKitEvent.fromMap({
        'action': 'incoming_call_kit.DISMISSED',
        'callId': 'd',
      }).action,
      CallKitAction.dismissed,
    );
    expect(
      CallKitEvent.fromMap({
        'action': 'incoming_call_kit.CALL_START',
        'callId': 'e',
      }).action,
      CallKitAction.callStart,
    );
  });

  test('GradientConfig serialization', () {
    final config = GradientConfig(
      colors: ['#1A1A2E', '#16213E', '#0F3460'],
      type: 'linear',
    );

    final map = config.toMap();
    expect(map['colors'], ['#1A1A2E', '#16213E', '#0F3460']);
    expect(map['type'], 'linear');

    final restored = GradientConfig.fromMap(map);
    expect(restored.colors, config.colors);
    expect(restored.type, config.type);
  });

  test('AndroidCallKitParams assert on both bg + gradient', () {
    expect(
      () => AndroidCallKitParams(
        backgroundColor: '#FF0000',
        backgroundGradient: GradientConfig(colors: ['#000000', '#FFFFFF']),
      ),
      throwsA(isA<AssertionError>()),
    );
  });

  test('IOSCallKitParams defaults', () {
    const params = IOSCallKitParams();
    expect(params.handleType, 'generic');
    expect(params.supportsVideo, false);
    expect(params.maximumCallGroups, 2);
    expect(params.ringtonePath, isNull);
    expect(params.toMap()['ringtonePath'], isNull);
  });

  test('Android ringtonePath serializes system default alias', () {
    const params = AndroidCallKitParams(
      ringtonePath: 'system_ringtone_default',
    );
    expect(params.toMap()['ringtonePath'], 'system_ringtone_default');
  });

  test('IOS ringtonePath serializes custom sound name', () {
    const params = IOSCallKitParams(
      ringtonePath: 'MyRingtone.caf',
    );
    expect(params.toMap()['ringtonePath'], 'MyRingtone.caf');
  });

  test('nullable accept/decline labels serialize as null', () {
    final params = CallKitParams(
      id: 'hide-btns',
      callerName: 'Title',
      callerNumber: 'Body',
      textAccept: null,
      textDecline: null,
    );

    final map = params.toMap();
    expect(map['callerName'], 'Title');
    expect(map['callerNumber'], 'Body');
    expect(map['textAccept'], isNull);
    expect(map['textDecline'], isNull);

    final restored = CallKitParams.fromMap(map);
    expect(restored.textAccept, isNull);
    expect(restored.textDecline, isNull);
  });
}
