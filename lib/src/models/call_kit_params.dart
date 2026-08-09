import 'android_call_kit_params.dart';
import 'ios_call_kit_params.dart';
import 'notification_params.dart';

class CallKitParams {
  final String id;
  final String callerName;
  final String? callerNumber;
  final String? avatar;
  final int type;

  /// Accept button label. Null or blank hides the accept button.
  final String? textAccept;

  /// Decline button label. Null or blank hides the decline button.
  final String? textDecline;
  final Duration duration;
  final Map<String, dynamic>? extra;
  final NotificationParams? missedCallNotification;
  final AndroidCallKitParams? android;
  final IOSCallKitParams? ios;

  const CallKitParams({
    required this.id,
    required this.callerName,
    this.callerNumber,
    this.avatar,
    this.type = 0,
    this.textAccept = 'Accept',
    this.textDecline = 'Decline',
    this.duration = const Duration(seconds: 30),
    this.extra,
    this.missedCallNotification,
    this.android,
    this.ios,
  });

  Map<String, dynamic> toMap() {
    return {
      'id': id,
      'callerName': callerName,
      'callerNumber': callerNumber,
      'avatar': avatar,
      'type': type,
      'textAccept': textAccept,
      'textDecline': textDecline,
      'duration': duration.inMilliseconds,
      'extra': extra,
      if (missedCallNotification != null)
        'missedCallNotification': missedCallNotification!.toMap(),
      if (android != null) 'android': android!.toMap(),
      if (ios != null) 'ios': ios!.toMap(),
    };
  }

  factory CallKitParams.fromMap(Map<String, dynamic> map) {
    return CallKitParams(
      id: map['id'] as String,
      callerName: map['callerName'] as String,
      callerNumber: map['callerNumber'] as String?,
      avatar: map['avatar'] as String?,
      type: map['type'] as int? ?? 0,
      textAccept: map['textAccept'] as String?,
      textDecline: map['textDecline'] as String?,
      duration: Duration(milliseconds: map['duration'] as int? ?? 30000),
      extra: map['extra'] != null
          ? Map<String, dynamic>.from(map['extra'] as Map)
          : null,
      missedCallNotification: map['missedCallNotification'] != null
          ? NotificationParams.fromMap(
              Map<String, dynamic>.from(map['missedCallNotification'] as Map),
            )
          : null,
      android: map['android'] != null
          ? AndroidCallKitParams.fromMap(
              Map<String, dynamic>.from(map['android'] as Map),
            )
          : null,
      ios: map['ios'] != null
          ? IOSCallKitParams.fromMap(
              Map<String, dynamic>.from(map['ios'] as Map),
            )
          : null,
    );
  }

  CallKitParams copyWith({
    String? id,
    String? callerName,
    String? callerNumber,
    String? avatar,
    int? type,
    String? textAccept,
    String? textDecline,
    Duration? duration,
    Map<String, dynamic>? extra,
    NotificationParams? missedCallNotification,
    AndroidCallKitParams? android,
    IOSCallKitParams? ios,
  }) {
    return CallKitParams(
      id: id ?? this.id,
      callerName: callerName ?? this.callerName,
      callerNumber: callerNumber ?? this.callerNumber,
      avatar: avatar ?? this.avatar,
      type: type ?? this.type,
      textAccept: textAccept ?? this.textAccept,
      textDecline: textDecline ?? this.textDecline,
      duration: duration ?? this.duration,
      extra: extra ?? this.extra,
      missedCallNotification:
          missedCallNotification ?? this.missedCallNotification,
      android: android ?? this.android,
      ios: ios ?? this.ios,
    );
  }
}
