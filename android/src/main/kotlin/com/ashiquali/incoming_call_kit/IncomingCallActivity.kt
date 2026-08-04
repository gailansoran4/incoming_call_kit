package com.ashiquali.incoming_call_kit

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.content.res.ColorStateList
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.animation.DecelerateInterpolator
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.net.HttpURLConnection
import java.net.URL

class IncomingCallActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "IncomingCallActivity"
        private val activityLock = Any()
        @Volatile
        var isActivityAlive = false
        @Volatile
        var activeCallId: String? = null
        var lastAliveTimestamp = 0L
        var finishActivity: (() -> Unit)? = null
    }

    private var callId: String? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var pulseAnimator: ObjectAnimator? = null
    @Volatile
    private var callHandled = false

    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Show over lock screen
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        window.addFlags(
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_FULLSCREEN
        )

        // Display cutout
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }

        // Wake screen
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        @Suppress("DEPRECATION")
        wakeLock = pm.newWakeLock(
            PowerManager.FULL_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP or PowerManager.ON_AFTER_RELEASE,
            "IncomingCallKit:ActivityWake"
        ).apply { acquire(60000L) }

        // Read callId
        callId = intent?.getStringExtra(Constants.EXTRA_CALL_ID) ?: run { finish(); return }
        val id = callId ?: run { finish(); return }

        // Dedup concurrent FullScreenIntent launches (same or rapid re-post).
        synchronized(activityLock) {
            if (isActivityAlive) {
                finish()
                return
            }
            isActivityAlive = true
            activeCallId = id
            lastAliveTimestamp = System.currentTimeMillis()
            finishActivity = { finish() }
        }

        // MIUI overlay fallback for lock screen
        if (isMiui() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val km = getSystemService(KEYGUARD_SERVICE) as? android.app.KeyguardManager
            if (km?.isKeyguardLocked == true && Settings.canDrawOverlays(this)) {
                window.setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
            }
        }

        // Load config
        val config = CallKitConfigStore.load(this, id) ?: run { finish(); return }

        setContentView(R.layout.activity_incoming_call)

        applyBackground(config)
        applyAvatar(config)
        applyCallerInfo(config)
        applyButtons(config)
        setupSwipeGesture(config)
        setupDismissListener()
    }

    private fun applyBackground(config: Map<String, Any?>) {
        val gradientOverlay = findViewById<View>(R.id.gradient_overlay)
        val androidConfig = config["android"] as? Map<String, Any?>

        val gradientConfig = androidConfig?.get("backgroundGradient") as? Map<String, Any?>
        if (gradientConfig != null) {
            val colors = (gradientConfig["colors"] as? List<*>)?.map { parseColor(it?.toString() ?: "#000000") }?.toIntArray()
            if (colors != null) {
                val type = gradientConfig["type"] as? String ?: "linear"
                val drawable = GradientDrawable()
                drawable.colors = colors
                if (type == "radial") {
                    drawable.gradientType = GradientDrawable.RADIAL_GRADIENT
                    drawable.gradientRadius = ((gradientConfig["radius"] as? Number)?.toFloat() ?: 0.8f) *
                        resources.displayMetrics.heightPixels
                } else {
                    drawable.orientation = GradientDrawable.Orientation.TOP_BOTTOM
                }
                gradientOverlay.background = drawable
                return
            }
        }

        val bgColor = androidConfig?.get("backgroundColor") as? String
        if (bgColor != null) {
            gradientOverlay.setBackgroundColor(parseColor(bgColor))
        } else {
            // Default gradient
            val drawable = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(
                    parseColor("#1A1A2E"),
                    parseColor("#16213E"),
                    parseColor("#0F3460")
                )
            )
            gradientOverlay.background = drawable
        }
    }

    private fun applyAvatar(config: Map<String, Any?>) {
        val avatarView = findViewById<ImageView>(R.id.caller_avatar)
        val pulseRing = findViewById<View>(R.id.pulse_ring)
        val callerName = config["callerName"] as? String ?: "?"
        val avatarUrl = config["avatar"] as? String
        val androidConfig = config["android"] as? Map<String, Any?>
        val avatarSize = (androidConfig?.get("avatarSize") as? Number)?.toInt()
            ?: 96
        val avatarSizePx = (avatarSize * resources.displayMetrics.density).toInt()
        val pulseSizePx = ((avatarSize + 24) * resources.displayMetrics.density).toInt()

        avatarView.layoutParams = FrameLayout.LayoutParams(avatarSizePx, avatarSizePx).apply {
            gravity = android.view.Gravity.CENTER
        }
        pulseRing.layoutParams = FrameLayout.LayoutParams(pulseSizePx, pulseSizePx).apply {
            gravity = android.view.Gravity.CENTER
        }

        // Show initials immediately as fallback
        showInitials(avatarView, callerName, avatarSizePx, androidConfig)

        // Load avatar in background
        if (!avatarUrl.isNullOrEmpty()) {
            Thread {
                try {
                    val url = URL(avatarUrl)
                    val connection = url.openConnection() as HttpURLConnection
                    connection.connectTimeout = 5000
                    connection.readTimeout = 5000
                    connection.doInput = true
                    connection.connect()
                    val bitmap = BitmapFactory.decodeStream(connection.inputStream)
                    if (bitmap != null) {
                        val circular = getCircularBitmap(bitmap, avatarSizePx)
                        runOnUiThread { avatarView.setImageBitmap(circular) }
                    }
                } catch (_: Exception) {
                    // Keep initials
                }
            }.start()
        }

        // Outward sonar pulse: ring scales out + fades away (RESTART mode)
        val enablePulse = androidConfig?.get("avatarPulseAnimation") as? Boolean ?: true
        if (enablePulse) {
            val ringColor = parseColor(
                androidConfig?.get("avatarBorderColor") as? String ?: "#AAFFFFFF"
            )
            pulseRing.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(ringColor)
            }
            pulseRing.alpha = 0f
            pulseAnimator = ObjectAnimator.ofPropertyValuesHolder(
                pulseRing,
                PropertyValuesHolder.ofFloat("scaleX", 1f, 1.6f),
                PropertyValuesHolder.ofFloat("scaleY", 1f, 1.6f),
                PropertyValuesHolder.ofFloat("alpha", 0.8f, 0f)
            ).apply {
                duration = 1600
                repeatCount = ObjectAnimator.INFINITE
                repeatMode = ObjectAnimator.RESTART
                interpolator = DecelerateInterpolator()
                start()
            }
        } else {
            pulseRing.visibility = View.GONE
        }
    }

    private fun showInitials(
        imageView: ImageView,
        name: String,
        sizePx: Int,
        androidConfig: Map<String, Any?>?
    ) {
        val initials = getInitials(name)
        val bgColor = parseColor(
            androidConfig?.get("initialsBackgroundColor") as? String ?: "#3A3A5C"
        )
        val textColor = parseColor(
            androidConfig?.get("initialsTextColor") as? String ?: "#FFFFFF"
        )

        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = bgColor }
        canvas.drawCircle(sizePx / 2f, sizePx / 2f, sizePx / 2f, bgPaint)

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = textColor
            textSize = sizePx * 0.35f
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
        }
        val textY = sizePx / 2f - (textPaint.ascent() + textPaint.descent()) / 2f
        canvas.drawText(initials, sizePx / 2f, textY, textPaint)

        imageView.setImageBitmap(bitmap)
    }

    private fun getInitials(name: String): String {
        val parts = name.trim().split(Regex("\\s+"))
        return if (parts.size >= 2) {
            "${parts.first().first()}${parts.last().first()}".uppercase()
        } else {
            parts.first().firstOrNull()?.uppercase() ?: "?"
        }
    }

    private fun getCircularBitmap(bitmap: Bitmap, sizePx: Int): Bitmap {
        val scaled = Bitmap.createScaledBitmap(bitmap, sizePx, sizePx, true)
        val output = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = BitmapShader(scaled, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        }
        canvas.drawCircle(sizePx / 2f, sizePx / 2f, sizePx / 2f, paint)
        return output
    }

    private fun applyCallerInfo(config: Map<String, Any?>) {
        val androidConfig = config["android"] as? Map<String, Any?>

        val callerNameView = findViewById<TextView>(R.id.caller_name)
        callerNameView.text = config["callerName"] as? String ?: "Unknown"
        callerNameView.setTextColor(
            parseColor(androidConfig?.get("callerNameColor") as? String ?: "#FFFFFF")
        )
        callerNameView.textSize = (androidConfig?.get("callerNameFontSize") as? Number)?.toFloat() ?: 28f

        val callerNumberView = findViewById<TextView>(R.id.caller_number)
        val number = config["callerNumber"] as? String
        if (!number.isNullOrEmpty()) {
            callerNumberView.text = number
            callerNumberView.setTextColor(
                parseColor(androidConfig?.get("callerNumberColor") as? String ?: "#B3FFFFFF")
            )
            callerNumberView.textSize = (androidConfig?.get("callerNumberFontSize") as? Number)?.toFloat() ?: 16f
        } else {
            callerNumberView.visibility = View.GONE
        }

        val statusView = findViewById<TextView>(R.id.call_status)
        statusView.text = androidConfig?.get("statusText") as? String ?: "Incoming Call"
        statusView.setTextColor(
            parseColor(androidConfig?.get("statusTextColor") as? String ?: "#80FFFFFF")
        )
    }

    private fun applyButtons(config: Map<String, Any?>) {
        val androidConfig = config["android"] as? Map<String, Any?>
        val buttonSize = (androidConfig?.get("buttonSize") as? Number)?.toFloat() ?: 64f
        val buttonSizePx = (buttonSize * resources.displayMetrics.density).toInt()

        val acceptColor = parseColor(androidConfig?.get("acceptButtonColor") as? String ?: "#4CAF50")
        val declineColor = parseColor(androidConfig?.get("declineButtonColor") as? String ?: "#F44336")

        val acceptIcon = findViewById<ImageView>(R.id.accept_button_icon)
        val declineIcon = findViewById<ImageView>(R.id.decline_button_icon)

        // Wrap circle background in RippleDrawable for vonage_voice-style touch feedback
        val rippleColor = ColorStateList.valueOf(Color.argb(60, 255, 255, 255))
        val acceptOval = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(acceptColor) }
        val declineOval = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(declineColor) }
        acceptIcon.background = RippleDrawable(rippleColor, acceptOval, acceptOval)
        declineIcon.background = RippleDrawable(rippleColor, declineOval, declineOval)

        val acceptContainer = findViewById<FrameLayout>(R.id.accept_button_container)
        val declineContainer = findViewById<FrameLayout>(R.id.decline_button_container)
        acceptContainer.layoutParams = (acceptContainer.layoutParams as LinearLayout.LayoutParams).apply {
            width = buttonSizePx
            height = buttonSizePx
        }
        declineContainer.layoutParams = (declineContainer.layoutParams as LinearLayout.LayoutParams).apply {
            width = buttonSizePx
            height = buttonSizePx
        }

        val textAccept = config["textAccept"] as? String ?: "Accept"
        val textDecline = config["textDecline"] as? String ?: "Decline"
        findViewById<TextView>(R.id.accept_label).text = textAccept
        findViewById<TextView>(R.id.decline_label).text = textDecline

        acceptContainer.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            acceptCall()
        }
        declineContainer.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            declineCall()
        }
    }

    @Suppress("ClickableViewAccessibility")
    private fun setupSwipeGesture(config: Map<String, Any?>) {
        val androidConfig = config["android"] as? Map<String, Any?>
        val enableSwipe = androidConfig?.get("enableSwipeGesture") as? Boolean ?: true
        if (!enableSwipe) return

        val threshold = ((androidConfig?.get("swipeThreshold") as? Number)?.toFloat() ?: 120f) *
            resources.displayMetrics.density

        setupSwipeForButton(
            containerId = R.id.accept_button_container,
            threshold = threshold,
            positive = true
        ) { acceptCall() }

        setupSwipeForButton(
            containerId = R.id.decline_button_container,
            threshold = threshold,
            positive = false
        ) { declineCall() }
    }

    @Suppress("ClickableViewAccessibility")
    private fun setupSwipeForButton(
        containerId: Int,
        threshold: Float,
        positive: Boolean,
        onTrigger: () -> Unit
    ) {
        val container = findViewById<FrameLayout>(containerId)
        val tapSlop = ViewConfiguration.get(this).scaledTouchSlop.toFloat()
        var startX = 0f
        var triggered = false

        // OnTouchListener consumes events (returns true), so OnClickListener never runs.
        // Handle both tap and swipe here.
        container.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = event.rawX
                    triggered = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val delta = event.rawX - startX
                    val dragDistance = if (positive) delta.coerceAtLeast(0f) else (-delta).coerceAtLeast(0f)
                    val progress = (dragDistance / threshold).coerceIn(0f, 1f)
                    val scale = 1.0f + progress * 0.3f
                    v.scaleX = scale
                    v.scaleY = scale
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (!triggered) {
                        val delta = event.rawX - startX
                        val absDelta = kotlin.math.abs(delta)
                        val swipeDistance = if (positive) delta else -delta
                        val isTap = event.action == MotionEvent.ACTION_UP && absDelta <= tapSlop
                        val isSwipe = swipeDistance >= threshold
                        if (isTap || isSwipe) {
                            triggered = true
                            v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                            onTrigger()
                        }
                    }
                    v.scaleX = 1f
                    v.scaleY = 1f
                    true
                }
                else -> false
            }
        }
    }

    private fun setupDismissListener() {
        CallKitEventBus.register(dismissListener)
    }

    private val dismissListener: (String, String, Map<String, Any?>?) -> Unit =
        { action, eventCallId, _ ->
            if (action == Constants.BROADCAST_DISMISSED && eventCallId == callId) {
                runOnUiThread { finish() }
            }
        }

    private fun acceptCall() {
        if (callHandled) { finish(); return }
        callHandled = true

        val intent = Intent(this, IncomingCallService::class.java).apply {
            action = Constants.ACTION_ACCEPT
            putExtra(Constants.EXTRA_CALL_ID, callId)
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send ACTION_ACCEPT: ${e.message}")
        }
        finish()
    }

    private fun declineCall() {
        if (callHandled) { finish(); return }
        callHandled = true

        val intent = Intent(this, IncomingCallService::class.java).apply {
            action = Constants.ACTION_DECLINE
            putExtra(Constants.EXTRA_CALL_ID, callId)
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send ACTION_DECLINE: ${e.message}")
        }
        finish()
    }

    private fun parseColor(hex: String): Int {
        return try {
            Color.parseColor(hex)
        } catch (_: Exception) {
            Color.BLACK
        }
    }

    private fun isMiui(): Boolean {
        return try {
            val clazz = Class.forName("android.os.SystemProperties")
            val getMethod = clazz.getMethod("get", String::class.java)
            val value = getMethod.invoke(null, "ro.miui.ui.version.name") as? String
            value != null && value.isNotEmpty()
        } catch (e: Exception) {
            false
        }
    }

    @Deprecated("Deprecated in Java")
    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        // Blocked — user must accept or decline
    }

    override fun onDestroy() {
        super.onDestroy()
        synchronized(activityLock) {
            if (activeCallId == callId) {
                isActivityAlive = false
                activeCallId = null
                finishActivity = null
            }
        }
        pulseAnimator?.cancel()
        try {
            wakeLock?.let { if (it.isHeld) it.release() }
        } catch (_: Exception) {}
        wakeLock = null
        CallKitEventBus.unregister(dismissListener)
    }
}
