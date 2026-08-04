enum CallKitAction {
  // Incoming
  accept,
  decline,
  timeout,
  dismissed,
  callback,
  // Outgoing
  callStart,
  callConnected,
  callEnded,
  // iOS specific
  audioSessionActivated,
  toggleHold,
  toggleMute,
  toggleDmtf,
  toggleGroup,
  // VoIP
  voipTokenUpdated,
}

class CallKitEvent {
  final CallKitAction action;
  final String callId;
  final Map<String, dynamic>? extra;

  const CallKitEvent({required this.action, required this.callId, this.extra});

  factory CallKitEvent.fromMap(Map<String, dynamic> map) {
    return CallKitEvent(
      action: _parseAction(map['action'] as String? ?? ''),
      callId: map['callId'] as String? ?? '',
      extra: map['extra'] != null
          ? Map<String, dynamic>.from(map['extra'] as Map)
          : null,
    );
  }

  Map<String, dynamic> toMap() {
    return {
      'action': action.name,
      'callId': callId,
      if (extra != null) 'extra': extra,
    };
  }

  static CallKitAction _parseAction(String action) {
    final normalized = _normalizeAction(action);
    return CallKitAction.values.firstWhere(
      (e) => e.name == normalized,
      orElse: () => CallKitAction.dismissed,
    );
  }

  /// Maps Android broadcast strings (and case variants) to [CallKitAction] names.
  /// iOS already emits enum names (`accept`, `decline`, …).
  static String _normalizeAction(String action) {
    final trimmed = action.trim();
    if (trimmed.isEmpty) return trimmed;

    const androidBroadcastToName = <String, String>{
      'incoming_call_kit.ACCEPTED': 'accept',
      'incoming_call_kit.DECLINED': 'decline',
      'incoming_call_kit.TIMEOUT': 'timeout',
      'incoming_call_kit.DISMISSED': 'dismissed',
      'incoming_call_kit.CALLBACK': 'callback',
      'incoming_call_kit.CALL_START': 'callStart',
      'incoming_call_kit.CALL_CONNECTED': 'callConnected',
      'incoming_call_kit.CALL_ENDED': 'callEnded',
    };
    final mapped = androidBroadcastToName[trimmed];
    if (mapped != null) return mapped;

    // Tolerate uppercase enum-style payloads: ACCEPTED → accept, CALL_START → callStart
    if (trimmed.contains('.')) {
      final suffix = trimmed.split('.').last;
      return _snakeOrUpperToCamel(suffix);
    }
    if (trimmed == trimmed.toUpperCase() && trimmed.contains('_')) {
      return _snakeOrUpperToCamel(trimmed);
    }
    if (trimmed == trimmed.toUpperCase()) {
      return trimmed.toLowerCase();
    }
    return trimmed;
  }

  static String _snakeOrUpperToCamel(String value) {
    final parts = value.toLowerCase().split('_').where((p) => p.isNotEmpty);
    if (parts.isEmpty) return value;
    final iterator = parts.iterator;
    iterator.moveNext();
    final buffer = StringBuffer(iterator.current);
    while (iterator.moveNext()) {
      final part = iterator.current;
      buffer.write(part[0].toUpperCase());
      if (part.length > 1) buffer.write(part.substring(1));
    }
    return buffer.toString();
  }

  @override
  String toString() =>
      'CallKitEvent(action: $action, callId: $callId, extra: $extra)';
}
