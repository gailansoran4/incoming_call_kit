package com.ashiquali.incoming_call_kit

object Constants {
    const val METHOD_CHANNEL = "com.ashiquali.incoming_call_kit/methods"
    const val EVENT_CHANNEL = "com.ashiquali.incoming_call_kit/events"

    // Service actions
    const val ACTION_SHOW_INCOMING = "com.ashiquali.incoming_call_kit.ACTION_SHOW_INCOMING"
    const val ACTION_ACCEPT = "com.ashiquali.incoming_call_kit.ACTION_ACCEPT"
    const val ACTION_DECLINE = "com.ashiquali.incoming_call_kit.ACTION_DECLINE"
    const val ACTION_DISMISS = "com.ashiquali.incoming_call_kit.ACTION_DISMISS"
    const val ACTION_TIMEOUT = "com.ashiquali.incoming_call_kit.ACTION_TIMEOUT"
    const val ACTION_CALLBACK = "com.ashiquali.incoming_call_kit.ACTION_CALLBACK"
    const val ACTION_START_CALL = "com.ashiquali.incoming_call_kit.ACTION_START_CALL"
    const val ACTION_CALL_CONNECTED = "com.ashiquali.incoming_call_kit.ACTION_CALL_CONNECTED"
    const val ACTION_END_CALL = "com.ashiquali.incoming_call_kit.ACTION_END_CALL"

    // Intent extras
    const val EXTRA_CALL_ID = "extra_call_id"
    const val EXTRA_CALLER_NAME = "extra_caller_name"
    const val EXTRA_CALLER_NUMBER = "extra_caller_number"

    // Notification
    const val FOREGROUND_SERVICE_NOTIFICATION_ID = 9001
    const val INCOMING_CALL_CHANNEL_ID = "incoming_call_kit_channel"
    const val MISSED_CALL_CHANNEL_ID = "incoming_call_kit_missed_channel"
    const val ONGOING_CALL_CHANNEL_ID = "incoming_call_kit_ongoing_channel"

    // Event bus actions — must match Dart [CallKitAction] enum names (same as iOS).
    const val BROADCAST_ACCEPTED = "accept"
    const val BROADCAST_DECLINED = "decline"
    const val BROADCAST_TIMEOUT = "timeout"
    const val BROADCAST_DISMISSED = "dismissed"
    const val BROADCAST_CALLBACK = "callback"
    const val BROADCAST_CALL_START = "callStart"
    const val BROADCAST_CALL_CONNECTED = "callConnected"
    const val BROADCAST_CALL_ENDED = "callEnded"

    // SharedPreferences
    const val PREFS_NAME = "incoming_call_kit_config"
    const val PREFS_ACTIVE_CALL_IDS = "active_call_ids"
    const val PREFS_PENDING_EVENTS = "pending_events"
    const val PREFS_BACKGROUND_CALLBACK_HANDLE = "background_callback_handle"

    // Android 15 FGS
    const val PROPERTY_FGS_SUBTYPE = "incoming_voip_call"
}
