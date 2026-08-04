package com.ashiquali.incoming_call_kit

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationManagerCompat

class IncomingCallService : Service() {

    companion object {
        private const val TAG = "IncomingCallService"
        private const val STALE_CALL_TIMEOUT_MS = 120_000L // 2 minutes
    }

    private lateinit var ringtoneManager: CallKitRingtoneManager
    private var wakeLock: PowerManager.WakeLock? = null
    private val timeoutHandler = Handler(Looper.getMainLooper())
    private val timeoutRunnables = mutableMapOf<String, Runnable>()
    private val activeCallIds = mutableSetOf<String>()
    private val callTimestamps = mutableMapOf<String, Long>()

    override fun onCreate() {
        super.onCreate()
        ringtoneManager = CallKitRingtoneManager(this)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // CRITICAL: call ensureForeground() IMMEDIATELY — Android kills after 5s otherwise
        ensureForeground(intent)

        when (intent?.action) {
            Constants.ACTION_SHOW_INCOMING -> handleShowIncoming(intent)
            Constants.ACTION_ACCEPT -> handleAccept(intent)
            Constants.ACTION_DECLINE -> handleDecline(intent)
            Constants.ACTION_DISMISS -> handleDismiss(intent)
            Constants.ACTION_CALLBACK -> handleCallback(intent)
            Constants.ACTION_START_CALL -> handleStartCall(intent)
            Constants.ACTION_CALL_CONNECTED -> handleCallConnected(intent)
            Constants.ACTION_END_CALL -> handleEndCall(intent)
        }

        return START_NOT_STICKY
    }

    @Suppress("DEPRECATION")
    private fun ensureForeground(intent: Intent? = null) {
        try {
            // Always use a silent minimal notification here.
            // Posting the real FSI/CallStyle notification in ensureForeground AND again in
            // handleShowIncoming (plus a second notify() under another id) caused duplicate UIs.
            val notification = NotificationBuilder.buildMinimalForegroundNotification(this)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    Constants.FOREGROUND_SERVICE_NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL
                )
            } else {
                startForeground(Constants.FOREGROUND_SERVICE_NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start foreground service", e)
        }
    }

    // === INCOMING CALL ===

    private fun handleShowIncoming(intent: Intent) {
        val callId = intent.getStringExtra(Constants.EXTRA_CALL_ID) ?: return

        // Dedup guard — skip if already showing this call
        if (activeCallIds.contains(callId)) {
            Log.d(TAG, "Duplicate incoming call for callId=$callId — skipping")
            return
        }

        // Clean up stale calls
        expireStaleInvites()

        val config = CallKitConfigStore.load(this, callId) ?: return

        val callerName = config["callerName"] as? String ?: "Unknown"
        val callerNumber = config["callerNumber"] as? String
        val androidConfig = config["android"] as? Map<String, Any?>
        val textAccept = config["textAccept"] as? String ?: "Accept"
        val textDecline = config["textDecline"] as? String ?: "Decline"
        val duration = (config["duration"] as? Number)?.toLong() ?: 30000L

        // Build and show notification — initials bitmap immediately (no network delay)
        val initialsBitmap = NotificationBuilder.buildInitialsBitmap(callerName)
        val notification = NotificationBuilder.buildIncomingCallNotification(
            this,
            callId,
            callerName,
            callerNumber,
            androidConfig,
            initialsBitmap,
            textAccept,
            textDecline,
        )
        val notifId = NotificationBuilder.getNotificationId(callId)

        // Clear any leftover per-call notification from older plugin versions / fallbacks
        NotificationManagerCompat.from(this).cancel(notifId)

        // Single post via startForeground only — never dual-notify under a second id
        // (second post re-fired FullScreenIntent and showed two call UIs).
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    Constants.FOREGROUND_SERVICE_NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL
                )
            } else {
                startForeground(Constants.FOREGROUND_SERVICE_NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update foreground notification", e)
            try {
                NotificationManagerCompat.from(this).notify(notifId, notification)
            } catch (_: Exception) {}
        }

        // Start ringtone + vibration
        ringtoneManager.startRinging(androidConfig ?: emptyMap())

        // Acquire wake lock
        acquireWakeLock()

        // Schedule timeout (cancel old runnable first if exists)
        timeoutRunnables.remove(callId)?.let { timeoutHandler.removeCallbacks(it) }
        val timeoutRunnable = Runnable { handleTimeout(callId) }
        timeoutRunnables[callId] = timeoutRunnable
        timeoutHandler.postDelayed(timeoutRunnable, duration)

        activeCallIds.add(callId)
        callTimestamps[callId] = System.currentTimeMillis()

        // Download real avatar in background and update the same FGS notification
        val avatarUrl = config["avatar"] as? String
        if (!avatarUrl.isNullOrEmpty()) {
            Thread {
                val avatarBitmap = NotificationBuilder.downloadCircularBitmap(avatarUrl)
                if (avatarBitmap != null && activeCallIds.contains(callId)) {
                    val updated = NotificationBuilder.buildIncomingCallNotification(
                        this,
                        callId,
                        callerName,
                        callerNumber,
                        androidConfig,
                        avatarBitmap,
                        textAccept,
                        textDecline,
                    )
                    try {
                        NotificationManagerCompat.from(this).notify(
                            Constants.FOREGROUND_SERVICE_NOTIFICATION_ID,
                            updated,
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to update notification with avatar", e)
                    }
                }
            }.start()
        }
    }

    private fun handleAccept(intent: Intent) {
        val callId = intent.getStringExtra(Constants.EXTRA_CALL_ID) ?: return
        cleanup(callId)
        emitEvent(Constants.BROADCAST_ACCEPTED, callId)
        CallKitConfigStore.remove(this, callId)
        finishActivityIfAlive()
        stopSelfIfEmpty()
    }

    private fun handleDecline(intent: Intent) {
        val callId = intent.getStringExtra(Constants.EXTRA_CALL_ID) ?: return
        cleanup(callId)
        emitEvent(Constants.BROADCAST_DECLINED, callId)
        CallKitConfigStore.remove(this, callId)
        finishActivityIfAlive()
        stopSelfIfEmpty()
    }

    private fun handleDismiss(intent: Intent) {
        val callId = intent.getStringExtra(Constants.EXTRA_CALL_ID) ?: return
        cleanup(callId)
        emitEvent(Constants.BROADCAST_DISMISSED, callId)
        CallKitConfigStore.remove(this, callId)
        finishActivityIfAlive()
        stopSelfIfEmpty()
    }

    private fun handleCallback(intent: Intent) {
        val callId = intent.getStringExtra(Constants.EXTRA_CALL_ID) ?: return
        emitEvent(Constants.BROADCAST_CALLBACK, callId)
        // Cancel missed call notification
        val missedNotifId = NotificationBuilder.getMissedNotificationId(callId)
        NotificationManagerCompat.from(this).cancel(missedNotifId)
    }

    private fun handleTimeout(callId: String) {
        cleanup(callId)

        // Show missed call notification if configured
        val config = CallKitConfigStore.load(this, callId)
        val missedConfig = config?.get("missedCallNotification") as? Map<String, Any?>
        val showMissedNotification = missedConfig?.get("showNotification") as? Boolean ?: false
        if (showMissedNotification) {
            val callerName = config?.get("callerName") as? String ?: "Unknown"
            val androidConfig = config?.get("android") as? Map<String, Any?>
            val notification = NotificationBuilder.buildMissedCallNotification(
                this, callId, callerName, missedConfig, androidConfig
            )
            val missedNotifId = NotificationBuilder.getMissedNotificationId(callId)
            try {
                NotificationManagerCompat.from(this).notify(missedNotifId, notification)
            } catch (_: Exception) {}
        }

        emitEvent(Constants.BROADCAST_TIMEOUT, callId)
        CallKitConfigStore.remove(this, callId)
        finishActivityIfAlive()
        stopSelfIfEmpty()
    }

    // === OUTGOING CALL ===

    private fun handleStartCall(intent: Intent) {
        val callId = intent.getStringExtra(Constants.EXTRA_CALL_ID) ?: return
        val config = CallKitConfigStore.load(this, callId) ?: return
        val callerName = config["callerName"] as? String ?: "Unknown"

        val notification = NotificationBuilder.buildOngoingCallNotification(
            this, callId, callerName, connected = false
        )
        val notifId = NotificationBuilder.getOngoingNotificationId(callId)
        NotificationManagerCompat.from(this).cancel(notifId)

        try {
            NotificationManagerCompat.from(this).notify(notifId, notification)
        } catch (_: Exception) {}

        activeCallIds.add(callId)
        emitEvent(Constants.BROADCAST_CALL_START, callId)
    }

    private fun handleCallConnected(intent: Intent) {
        val callId = intent.getStringExtra(Constants.EXTRA_CALL_ID) ?: return
        val config = CallKitConfigStore.load(this, callId) ?: return
        val callerName = config["callerName"] as? String ?: "Unknown"

        val notification = NotificationBuilder.buildOngoingCallNotification(
            this, callId, callerName, connected = true
        )
        val notifId = NotificationBuilder.getOngoingNotificationId(callId)

        try {
            NotificationManagerCompat.from(this).notify(notifId, notification)
        } catch (_: Exception) {}

        emitEvent(Constants.BROADCAST_CALL_CONNECTED, callId)
    }

    private fun handleEndCall(intent: Intent) {
        val callId = intent.getStringExtra(Constants.EXTRA_CALL_ID) ?: return

        val notifId = NotificationBuilder.getOngoingNotificationId(callId)
        NotificationManagerCompat.from(this).cancel(notifId)

        activeCallIds.remove(callId)
        emitEvent(Constants.BROADCAST_CALL_ENDED, callId)
        CallKitConfigStore.remove(this, callId)
        stopSelfIfEmpty()
    }

    // === Helpers ===

    private fun cleanup(callId: String) {
        ringtoneManager.stopRinging()
        releaseWakeLock()

        // Cancel timeout
        timeoutRunnables.remove(callId)?.let { timeoutHandler.removeCallbacks(it) }

        // Cancel notifications
        val notifId = NotificationBuilder.getNotificationId(callId)
        NotificationManagerCompat.from(this).cancel(notifId)

        activeCallIds.remove(callId)
        callTimestamps.remove(callId)
    }

    private fun emitEvent(action: String, callId: String) {
        val config = CallKitConfigStore.load(this, callId)
        val extra = config?.get("extra") as? Map<String, Any?>

        if (CallKitEventBus.hasListeners()) {
            CallKitEventBus.emit(action, callId, extra)
        } else if (BackgroundCallHandler.hasHandler(this)) {
            val eventMap = mutableMapOf<String, Any?>(
                "action" to action,
                "callId" to callId,
            )
            if (extra != null) eventMap["extra"] = extra
            BackgroundCallHandler.dispatchEvent(this, eventMap)
        } else {
            val eventMap = mutableMapOf<String, Any?>(
                "action" to action,
                "callId" to callId,
            )
            if (extra != null) eventMap["extra"] = extra
            CallKitConfigStore.storePendingEvent(this, eventMap)
        }
    }

    private fun expireStaleInvites() {
        val now = System.currentTimeMillis()
        val staleIds = callTimestamps.filter { (_, ts) ->
            now - ts > STALE_CALL_TIMEOUT_MS
        }.keys.toList()
        for (staleId in staleIds) {
            Log.w(TAG, "Expiring stale call: $staleId")
            cleanup(staleId)
            CallKitConfigStore.remove(this, staleId)
        }
        if (staleIds.isNotEmpty()) {
            finishActivityIfAlive()
        }
    }

    @Suppress("DEPRECATION")
    private fun acquireWakeLock() {
        if (wakeLock != null) return
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(
            PowerManager.FULL_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP or PowerManager.ON_AFTER_RELEASE,
            "IncomingCallKit:WakeLock"
        ).apply {
            acquire(60000L)
        }
    }

    private fun releaseWakeLock() {
        try {
            wakeLock?.let { if (it.isHeld) it.release() }
        } catch (_: Exception) {}
        wakeLock = null
    }

    private fun finishActivityIfAlive() {
        if (IncomingCallActivity.isActivityAlive) {
            IncomingCallActivity.finishActivity?.invoke()
        }
    }

    private fun stopSelfIfEmpty() {
        if (activeCallIds.isEmpty()) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        ringtoneManager.stopRinging()
        releaseWakeLock()
        for (runnable in timeoutRunnables.values) {
            timeoutHandler.removeCallbacks(runnable)
        }
        timeoutRunnables.clear()
    }
}
