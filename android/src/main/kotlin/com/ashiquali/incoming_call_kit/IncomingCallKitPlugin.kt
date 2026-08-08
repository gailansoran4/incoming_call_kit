package com.ashiquali.incoming_call_kit

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import io.flutter.plugin.common.PluginRegistry

class IncomingCallKitPlugin :
    FlutterPlugin,
    MethodCallHandler,
    EventChannel.StreamHandler,
    ActivityAware,
    PluginRegistry.RequestPermissionsResultListener {

    private lateinit var methodChannel: MethodChannel
    private lateinit var eventChannel: EventChannel
    private var eventSink: EventChannel.EventSink? = null
    private var applicationContext: Context? = null
    private var activity: Activity? = null
    private var pendingPermissionResult: Result? = null
    private val pendingEvents = mutableListOf<Map<String, Any?>>()

    private val eventBusListener: (String, String, Map<String, Any?>?) -> Unit =
        { action, callId, extra ->
            val eventMap = mutableMapOf<String, Any?>(
                "action" to action,
                "callId" to callId,
            )
            if (extra != null) eventMap["extra"] = extra
            val sink = eventSink
            if (sink != null) {
                sink.success(eventMap)
            } else {
                synchronized(pendingEvents) {
                    pendingEvents.add(eventMap)
                }
            }
        }

    // === FlutterPlugin ===

    override fun onAttachedToEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        applicationContext = binding.applicationContext

        methodChannel = MethodChannel(binding.binaryMessenger, Constants.METHOD_CHANNEL)
        methodChannel.setMethodCallHandler(this)

        eventChannel = EventChannel(binding.binaryMessenger, Constants.EVENT_CHANNEL)
        eventChannel.setStreamHandler(this)

        CallKitEventBus.register(eventBusListener)
    }

    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        methodChannel.setMethodCallHandler(null)
        eventChannel.setStreamHandler(null)
        CallKitEventBus.unregister(eventBusListener)
        applicationContext = null
    }

    // === EventChannel.StreamHandler ===

    override fun onListen(arguments: Any?, events: EventChannel.EventSink?) {
        eventSink = events
        if (events != null) {
            // Replay persisted events from killed state
            applicationContext?.let { ctx ->
                val persisted = CallKitConfigStore.getPendingEvents(ctx)
                if (persisted.isNotEmpty()) {
                    for (event in persisted) {
                        events.success(event)
                    }
                    CallKitConfigStore.clearPendingEvents(ctx)
                }
            }
            // Flush in-memory pending events
            val toFlush: List<Map<String, Any?>>
            synchronized(pendingEvents) {
                toFlush = pendingEvents.toList()
                pendingEvents.clear()
            }
            for (event in toFlush) {
                events.success(event)
            }
        }
    }

    override fun onCancel(arguments: Any?) {
        eventSink = null
    }

    // === ActivityAware ===

    override fun onAttachedToActivity(binding: ActivityPluginBinding) {
        activity = binding.activity
        binding.addRequestPermissionsResultListener(this)
    }

    override fun onDetachedFromActivityForConfigChanges() {
        activity = null
    }

    override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
        activity = binding.activity
        binding.addRequestPermissionsResultListener(this)
    }

    override fun onDetachedFromActivity() {
        activity = null
    }

    // === MethodCallHandler ===

    override fun onMethodCall(call: MethodCall, result: Result) {
        val ctx = applicationContext ?: run {
            result.error("NO_CONTEXT", "Plugin not attached to engine", null)
            return
        }

        when (call.method) {
            "show" -> handleShow(ctx, call, result)
            "dismiss" -> handleDismiss(ctx, call, result)
            "dismissAll" -> handleDismissAll(ctx, result)
            "startCall" -> handleStartCall(ctx, call, result)
            "setCallConnected" -> handleSetCallConnected(ctx, call, result)
            "endCall" -> handleEndCall(ctx, call, result)
            "endAllCalls" -> handleEndAllCalls(ctx, result)
            "showMissedCallNotification" -> handleShowMissedCallNotification(ctx, call, result)
            "clearMissedCallNotification" -> handleClearMissedCallNotification(ctx, call, result)
            "registerBackgroundHandler" -> handleRegisterBackgroundHandler(ctx, call, result)
            "canUseFullScreenIntent" -> handleCanUseFullScreenIntent(ctx, result)
            "requestFullIntentPermission" -> handleRequestFullIntentPermission(result)
            "hasNotificationPermission" -> handleHasNotificationPermission(ctx, result)
            "requestNotificationPermission" -> handleRequestNotificationPermission(result)
            "isAutoStartAvailable" -> handleIsAutoStartAvailable(ctx, result)
            "openAutoStartSettings" -> handleOpenAutoStartSettings(ctx, result)
            "getDevicePushTokenVoIP" -> result.success("")
            "getActiveCalls" -> handleGetActiveCalls(ctx, result)
            else -> result.notImplemented()
        }
    }

    // === Method handlers ===

    private fun handleShow(context: Context, call: MethodCall, result: Result) {
        val params = call.arguments as? Map<String, Any?> ?: run {
            result.error("INVALID_ARGS", "Expected map arguments", null)
            return
        }
        val callId = params["id"] as? String ?: run {
            result.error("INVALID_ARGS", "Missing 'id' parameter", null)
            return
        }

        CallKitConfigStore.store(context, callId, params)

        val intent = Intent(context, IncomingCallService::class.java).apply {
            action = Constants.ACTION_SHOW_INCOMING
            putExtra(Constants.EXTRA_CALL_ID, callId)
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        } catch (e: Exception) {
            // Fallback: post notification directly
            val config = CallKitConfigStore.load(context, callId)
            if (config != null) {
                val callerName = config["callerName"] as? String ?: "Unknown"
                val callerNumber = config["callerNumber"] as? String
                val androidConfig = config["android"] as? Map<String, Any?>
                val textAccept = config["textAccept"] as? String ?: "Accept"
                val textDecline = config["textDecline"] as? String ?: "Decline"
                val notification = NotificationBuilder.buildIncomingCallNotification(
                    context,
                    callId,
                    callerName,
                    callerNumber,
                    androidConfig,
                    textAccept = textAccept,
                    textDecline = textDecline,
                )
                val notifId = NotificationBuilder.getNotificationId(callId)
                NotificationManagerCompat.from(context).notify(notifId, notification)
            }
        }

        result.success(null)
    }

    private fun handleDismiss(context: Context, call: MethodCall, result: Result) {
        val callId = (call.arguments as? Map<String, Any?>)?.get("id") as? String ?: run {
            result.error("INVALID_ARGS", "Missing 'id' parameter", null)
            return
        }

        val intent = Intent(context, IncomingCallService::class.java).apply {
            action = Constants.ACTION_DISMISS
            putExtra(Constants.EXTRA_CALL_ID, callId)
        }
        try {
            context.startService(intent)
        } catch (_: Exception) {}

        result.success(null)
    }

    private fun handleDismissAll(context: Context, result: Result) {
        val callIds = CallKitConfigStore.getActiveCallIds(context)
        for (callId in callIds) {
            val intent = Intent(context, IncomingCallService::class.java).apply {
                action = Constants.ACTION_DISMISS
                putExtra(Constants.EXTRA_CALL_ID, callId)
            }
            try {
                context.startService(intent)
            } catch (_: Exception) {}
        }
        result.success(null)
    }

    private fun handleStartCall(context: Context, call: MethodCall, result: Result) {
        val params = call.arguments as? Map<String, Any?> ?: run {
            result.error("INVALID_ARGS", "Expected map arguments", null)
            return
        }
        val callId = params["id"] as? String ?: run {
            result.error("INVALID_ARGS", "Missing 'id' parameter", null)
            return
        }

        CallKitConfigStore.store(context, callId, params)

        val intent = Intent(context, IncomingCallService::class.java).apply {
            action = Constants.ACTION_START_CALL
            putExtra(Constants.EXTRA_CALL_ID, callId)
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        } catch (_: Exception) {}

        result.success(null)
    }

    private fun handleSetCallConnected(context: Context, call: MethodCall, result: Result) {
        val callId = (call.arguments as? Map<String, Any?>)?.get("id") as? String ?: run {
            result.error("INVALID_ARGS", "Missing 'id' parameter", null)
            return
        }

        val intent = Intent(context, IncomingCallService::class.java).apply {
            action = Constants.ACTION_CALL_CONNECTED
            putExtra(Constants.EXTRA_CALL_ID, callId)
        }
        try {
            context.startService(intent)
        } catch (_: Exception) {}

        result.success(null)
    }

    private fun handleEndCall(context: Context, call: MethodCall, result: Result) {
        val callId = (call.arguments as? Map<String, Any?>)?.get("id") as? String ?: run {
            result.error("INVALID_ARGS", "Missing 'id' parameter", null)
            return
        }

        val intent = Intent(context, IncomingCallService::class.java).apply {
            action = Constants.ACTION_END_CALL
            putExtra(Constants.EXTRA_CALL_ID, callId)
        }
        try {
            context.startService(intent)
        } catch (_: Exception) {}

        result.success(null)
    }

    private fun handleEndAllCalls(context: Context, result: Result) {
        val callIds = CallKitConfigStore.getActiveCallIds(context)
        for (callId in callIds) {
            val intent = Intent(context, IncomingCallService::class.java).apply {
                action = Constants.ACTION_END_CALL
                putExtra(Constants.EXTRA_CALL_ID, callId)
            }
            try {
                context.startService(intent)
            } catch (_: Exception) {}
        }
        result.success(null)
    }

    private fun handleShowMissedCallNotification(context: Context, call: MethodCall, result: Result) {
        val params = call.arguments as? Map<String, Any?> ?: run {
            result.error("INVALID_ARGS", "Expected map arguments", null)
            return
        }
        val callId = params["id"] as? String ?: ""
        val callerName = params["callerName"] as? String ?: "Unknown"
        val missedConfig = params["missedCallNotification"] as? Map<String, Any?>
        val androidConfig = params["android"] as? Map<String, Any?>

        val notification = NotificationBuilder.buildMissedCallNotification(
            context, callId, callerName, missedConfig, androidConfig
        )
        val notifId = NotificationBuilder.getMissedNotificationId(callId)
        NotificationManagerCompat.from(context).notify(notifId, notification)

        result.success(null)
    }

    private fun handleClearMissedCallNotification(context: Context, call: MethodCall, result: Result) {
        val callId = (call.arguments as? Map<String, Any?>)?.get("id") as? String ?: run {
            result.error("INVALID_ARGS", "Missing 'id' parameter", null)
            return
        }
        val notifId = NotificationBuilder.getMissedNotificationId(callId)
        NotificationManagerCompat.from(context).cancel(notifId)
        result.success(null)
    }

    private fun handleRegisterBackgroundHandler(context: Context, call: MethodCall, result: Result) {
        val args = call.arguments as? Map<String, Any?>
        val dispatcherHandle = (args?.get("callbackHandle") as? Number)?.toLong()
        val userCallbackHandle = (args?.get("userCallbackHandle") as? Number)?.toLong()
        if (dispatcherHandle == null || userCallbackHandle == null) {
            result.error(
                "INVALID_ARGS",
                "Missing 'callbackHandle' or 'userCallbackHandle' parameter",
                null,
            )
            return
        }

        BackgroundCallHandler.setCallbackHandles(
            context,
            dispatcherHandle,
            userCallbackHandle,
        )
        result.success(null)
    }

    private fun handleCanUseFullScreenIntent(context: Context, result: Result) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            result.success(nm.canUseFullScreenIntent())
        } else {
            result.success(true)
        }
    }

    private fun handleRequestFullIntentPermission(result: Result) {
        val act = activity
        if (act == null) {
            result.error("NO_ACTIVITY", "No activity available", null)
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val intent = Intent(
                Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                Uri.parse("package:${act.packageName}")
            )
            act.startActivity(intent)
        }
        result.success(null)
    }

    private fun handleHasNotificationPermission(context: Context, result: Result) {
        result.success(NotificationManagerCompat.from(context).areNotificationsEnabled())
    }

    private fun handleRequestNotificationPermission(result: Result) {
        val act = activity
        if (act == null) {
            result.error("NO_ACTIVITY", "No activity available", null)
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pendingPermissionResult = result
            act.requestPermissions(
                arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                PERMISSION_REQUEST_CODE
            )
        } else {
            result.success(true)
        }
    }

    private fun handleIsAutoStartAvailable(context: Context, result: Result) {
        result.success(OemAutostartHelper.isAutoStartAvailable(context))
    }

    private fun handleOpenAutoStartSettings(context: Context, result: Result) {
        OemAutostartHelper.openAutoStartSettings(context)
        result.success(null)
    }

    private fun handleGetActiveCalls(context: Context, result: Result) {
        result.success(CallKitConfigStore.getActiveCallIds(context).toList())
    }

    // === Permission result ===

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ): Boolean {
        if (requestCode == PERMISSION_REQUEST_CODE) {
            val granted = grantResults.isNotEmpty() &&
                grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED
            pendingPermissionResult?.success(granted)
            pendingPermissionResult = null
            return true
        }
        return false
    }

    companion object {
        private const val PERMISSION_REQUEST_CODE = 29001
    }
}
