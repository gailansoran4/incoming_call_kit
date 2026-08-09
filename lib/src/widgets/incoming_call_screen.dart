import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../models/call_kit_params.dart';
import '../models/gradient_config.dart';

class IncomingCallScreen extends StatefulWidget {
  final CallKitParams params;
  final VoidCallback onAccept;
  final VoidCallback onDecline;
  final VoidCallback? onTimeout;

  /// Optional widget displayed at the top of the screen (e.g. app logo).
  final Widget? headerWidget;

  /// Optional builder to fully replace the avatar section.
  final Widget Function(String callerName, String? avatarUrl)? avatarBuilder;

  /// Optional builder to add extra widgets between caller info and buttons.
  final Widget Function(BuildContext context)? extraContentBuilder;

  const IncomingCallScreen({
    super.key,
    required this.params,
    required this.onAccept,
    required this.onDecline,
    this.onTimeout,
    this.headerWidget,
    this.avatarBuilder,
    this.extraContentBuilder,
  });

  @override
  State<IncomingCallScreen> createState() => _IncomingCallScreenState();
}

class _IncomingCallScreenState extends State<IncomingCallScreen> {
  Timer? _timeoutTimer;

  String get _callerName => widget.params.callerName;
  String? get _callerNumber => widget.params.callerNumber;
  String? get _avatar => widget.params.avatar;

  // Android params with defaults
  String get _backgroundColor =>
      widget.params.android?.backgroundColor ?? '#1B1B2F';
  GradientConfig? get _backgroundGradient =>
      widget.params.android?.backgroundGradient;
  double get _avatarSize => widget.params.android?.avatarSize ?? 120;
  String? get _avatarBorderColor => widget.params.android?.avatarBorderColor;
  double get _avatarBorderWidth =>
      widget.params.android?.avatarBorderWidth ?? 0;
  String get _initialsBackgroundColor =>
      widget.params.android?.initialsBackgroundColor ?? '#4A4A6A';
  String get _initialsTextColor =>
      widget.params.android?.initialsTextColor ?? '#FFFFFF';
  String get _callerNameColor =>
      widget.params.android?.callerNameColor ?? '#FFFFFF';
  double get _callerNameFontSize =>
      widget.params.android?.callerNameFontSize ?? 28;
  String get _callerNumberColor =>
      widget.params.android?.callerNumberColor ?? '#AAAAAA';
  double get _callerNumberFontSize =>
      widget.params.android?.callerNumberFontSize ?? 18;
  String get _statusText =>
      widget.params.android?.statusText ?? 'Incoming Call';
  String get _statusTextColor =>
      widget.params.android?.statusTextColor ?? '#888888';
  String get _acceptButtonColor =>
      widget.params.android?.acceptButtonColor ?? '#27AE60';
  String get _declineButtonColor =>
      widget.params.android?.declineButtonColor ?? '#E74C3C';
  double get _buttonSize => widget.params.android?.buttonSize ?? 80;

  @override
  void initState() {
    super.initState();

    _timeoutTimer = Timer(widget.params.duration, () {
      widget.onTimeout?.call();
    });

    SystemChrome.setEnabledSystemUIMode(SystemUiMode.immersiveSticky);
  }

  @override
  void dispose() {
    _timeoutTimer?.cancel();
    SystemChrome.setEnabledSystemUIMode(SystemUiMode.edgeToEdge);
    super.dispose();
  }

  Color _parseColor(String hex) {
    final buffer = StringBuffer();
    if (hex.length == 7) buffer.write('FF');
    if (hex.length == 9) buffer.write(hex.substring(1, 3));
    if (hex.length == 7) buffer.write(hex.substring(1));
    if (hex.length == 9) buffer.write(hex.substring(3));
    return Color(int.parse(buffer.toString(), radix: 16));
  }

  Decoration _buildBackground() {
    if (_backgroundGradient != null) {
      return _buildGradientDecoration(_backgroundGradient!);
    }
    if (widget.params.android?.backgroundColor == null) {
      return BoxDecoration(color: _parseColor('#1A1A2E'));
    }
    return BoxDecoration(color: _parseColor(_backgroundColor));
  }

  BoxDecoration _buildGradientDecoration(GradientConfig config) {
    final colors = config.colors.map(_parseColor).toList();
    final stops = config.stops;

    if (config.type == 'radial') {
      final center = config.center ?? {'x': 0.5, 'y': 0.3};
      return BoxDecoration(
        gradient: RadialGradient(
          colors: colors,
          stops: stops,
          center: Alignment((center['x']! * 2) - 1, (center['y']! * 2) - 1),
          radius: config.radius ?? 0.8,
        ),
      );
    }

    final begin = config.begin ?? {'x': 0.5, 'y': 0.0};
    final end = config.end ?? {'x': 0.5, 'y': 1.0};
    return BoxDecoration(
      gradient: LinearGradient(
        colors: colors,
        stops: stops,
        begin: Alignment((begin['x']! * 2) - 1, (begin['y']! * 2) - 1),
        end: Alignment((end['x']! * 2) - 1, (end['y']! * 2) - 1),
      ),
    );
  }

  String _getInitials(String name) {
    final parts = name.trim().split(RegExp(r'\s+'));
    if (parts.length >= 2) {
      return '${parts.first[0]}${parts.last[0]}'.toUpperCase();
    }
    return parts.first.isNotEmpty ? parts.first[0].toUpperCase() : '?';
  }

  void _handleAccept() {
    HapticFeedback.mediumImpact();
    widget.onAccept();
  }

  void _handleDecline() {
    HapticFeedback.mediumImpact();
    widget.onDecline();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: Container(
        decoration: _buildBackground(),
        child: SafeArea(
          child: Padding(
            padding: const EdgeInsets.symmetric(horizontal: 32),
            child: Column(
              children: [
                const SizedBox(height: 24),
                if (widget.headerWidget != null) ...[
                  widget.headerWidget!,
                  const SizedBox(height: 16),
                ],
                _buildStatusLabel(),
                const Spacer(flex: 2),
                _buildAvatarSection(),
                const SizedBox(height: 24),
                _buildCallerInfo(),
                if (widget.extraContentBuilder != null) ...[
                  const SizedBox(height: 16),
                  widget.extraContentBuilder!(context),
                ],
                const Spacer(flex: 3),
                _buildButtons(),
                const SizedBox(height: 64),
              ],
            ),
          ),
        ),
      ),
    );
  }

  Widget _buildStatusLabel() {
    return Text(
      _statusText,
      style: TextStyle(
        color: _parseColor(_statusTextColor),
        fontSize: 16,
      ),
      textAlign: TextAlign.center,
    );
  }

  Widget _buildAvatarSection() {
    if (widget.avatarBuilder != null) {
      return widget.avatarBuilder!(_callerName, _avatar);
    }

    Widget avatarWidget;

    if (_avatar != null && _avatar!.isNotEmpty) {
      avatarWidget = CircleAvatar(
        radius: _avatarSize / 2,
        backgroundImage: NetworkImage(_avatar!),
        backgroundColor: _parseColor(_initialsBackgroundColor),
      );
    } else {
      avatarWidget = Container(
        width: _avatarSize,
        height: _avatarSize,
        decoration: BoxDecoration(
          shape: BoxShape.circle,
          color: _parseColor(_initialsBackgroundColor),
        ),
        child: Center(
          child: Text(
            _getInitials(_callerName),
            style: TextStyle(
              color: _parseColor(_initialsTextColor),
              fontSize: _avatarSize * 0.35,
              fontWeight: FontWeight.bold,
            ),
          ),
        ),
      );
    }

    if (_avatarBorderColor != null && _avatarBorderWidth > 0) {
      avatarWidget = Container(
        decoration: BoxDecoration(
          shape: BoxShape.circle,
          border: Border.all(
            color: _parseColor(_avatarBorderColor!),
            width: _avatarBorderWidth,
          ),
        ),
        child: avatarWidget,
      );
    }

    return avatarWidget;
  }

  Widget _buildCallerInfo() {
    return Column(
      children: [
        Text(
          _callerName,
          style: TextStyle(
            color: _parseColor(_callerNameColor),
            fontSize: _callerNameFontSize,
            fontWeight: FontWeight.bold,
          ),
          maxLines: 2,
          overflow: TextOverflow.ellipsis,
          textAlign: TextAlign.center,
        ),
        if (_callerNumber != null && _callerNumber!.isNotEmpty) ...[
          const SizedBox(height: 8),
          Text(
            _callerNumber!,
            style: TextStyle(
              color: _parseColor(_callerNumberColor),
              fontSize: _callerNumberFontSize,
            ),
            textAlign: TextAlign.center,
          ),
        ],
      ],
    );
  }

  Widget _buildButtons() {
    final declineLabel = widget.params.textDecline?.trim() ?? '';
    final acceptLabel = widget.params.textAccept?.trim() ?? '';
    final showDecline = declineLabel.isNotEmpty;
    final showAccept = acceptLabel.isNotEmpty;
    if (!showDecline && !showAccept) return const SizedBox.shrink();

    return Row(
      mainAxisAlignment: MainAxisAlignment.center,
      children: [
        if (showDecline)
          _buildActionButton(
            color: _parseColor(_declineButtonColor),
            icon: Icons.close,
            label: declineLabel,
            onTap: _handleDecline,
          ),
        if (showDecline && showAccept) SizedBox(width: _buttonSize),
        if (showAccept)
          _buildActionButton(
            color: _parseColor(_acceptButtonColor),
            icon: Icons.call,
            label: acceptLabel,
            onTap: _handleAccept,
          ),
      ],
    );
  }

  Widget _buildActionButton({
    required Color color,
    required IconData icon,
    required String label,
    required VoidCallback onTap,
  }) {
    return Column(
      mainAxisSize: MainAxisSize.min,
      children: [
        GestureDetector(
          onTap: onTap,
          child: Container(
            width: _buttonSize,
            height: _buttonSize,
            decoration: BoxDecoration(shape: BoxShape.circle, color: color),
            child: Icon(icon, color: Colors.white, size: _buttonSize * 0.4),
          ),
        ),
        const SizedBox(height: 8),
        Text(label, style: const TextStyle(color: Colors.white, fontSize: 14)),
      ],
    );
  }
}
