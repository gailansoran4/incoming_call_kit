package com.ashiquali.incoming_call_kit

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log

/**
 * Plays the incoming-call ringtone + vibration.
 *
 * Always audibly rings (does not honor silent / vibrate ringer modes), using the
 * alarm audio stream so the call is still heard when the phone is muted.
 * Falls back to the system default ringtone when a custom raw resource is missing.
 */
class CallKitRingtoneManager(private val context: Context) {
    private var mediaPlayer: MediaPlayer? = null
    private var fallbackRingtone: Ringtone? = null
    private var vibrator: Vibrator? = null
    private var isRinging = false
    private var previousAlarmVolume: Int? = null

    fun startRinging(config: Map<String, Any?>) {
        if (isRinging) return
        isRinging = true

        // Always ring — mute / vibrate must not silence an incoming call UI.
        startRingtone(config)

        val enableVibration = config["enableVibration"] as? Boolean ?: true
        if (enableVibration) {
            startVibration(config)
        }
    }

    fun stopRinging() {
        if (!isRinging) return
        isRinging = false

        try {
            mediaPlayer?.stop()
        } catch (_: Exception) {
        }
        try {
            mediaPlayer?.release()
        } catch (_: Exception) {
        }
        mediaPlayer = null

        try {
            fallbackRingtone?.stop()
        } catch (_: Exception) {
        }
        fallbackRingtone = null

        try {
            vibrator?.cancel()
        } catch (_: Exception) {
        }
        vibrator = null

        restoreAlarmVolume()
    }

    private fun startRingtone(config: Map<String, Any?>) {
        val ringtoneUri = resolveRingtoneUri(config["ringtonePath"] as? String)
        if (ringtoneUri == Uri.EMPTY) {
            Log.e(TAG, "No ringtone URI available")
            return
        }

        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        ensureAudibleAlarmVolume(audioManager)

        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        try {
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(attrs)
                setDataSource(context, ringtoneUri)
                isLooping = true
                prepare()
                start()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start MediaPlayer ringtone, falling back", e)
            try {
                mediaPlayer?.release()
            } catch (_: Exception) {
            }
            mediaPlayer = null
            playRingtoneFallback(ringtoneUri, attrs)
        }
    }

    private fun playRingtoneFallback(uri: Uri, attrs: AudioAttributes) {
        try {
            val ringtone = RingtoneManager.getRingtone(context, uri) ?: return
            ringtone.audioAttributes = attrs
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                ringtone.isLooping = true
            }
            ringtone.play()
            fallbackRingtone = ringtone
        } catch (e: Exception) {
            Log.e(TAG, "Fallback Ringtone also failed", e)
        }
    }

    private fun resolveRingtoneUri(ringtonePath: String?): Uri {
        val rawName = CallKitRingtoneResolver.rawResourceName(ringtonePath)
        if (rawName != null) {
            val resId = context.resources.getIdentifier(rawName, "raw", context.packageName)
            if (resId != 0) {
                return Uri.parse("android.resource://${context.packageName}/$resId")
            }
            Log.w(TAG, "Custom ringtone raw/$rawName not found – using system default")
        }
        return RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            ?: Uri.EMPTY
    }

    private fun ensureAudibleAlarmVolume(audioManager: AudioManager) {
        try {
            val current = audioManager.getStreamVolume(AudioManager.STREAM_ALARM)
            if (current > 0) return
            val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            if (max <= 0) return
            previousAlarmVolume = current
            val target = (max * 0.7f).toInt().coerceAtLeast(1)
            audioManager.setStreamVolume(AudioManager.STREAM_ALARM, target, 0)
        } catch (e: Exception) {
            Log.w(TAG, "Unable to adjust alarm volume", e)
        }
    }

    private fun restoreAlarmVolume() {
        val previous = previousAlarmVolume ?: return
        previousAlarmVolume = null
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            audioManager.setStreamVolume(AudioManager.STREAM_ALARM, previous, 0)
        } catch (_: Exception) {
        }
    }

    @Suppress("DEPRECATION")
    private fun startVibration(config: Map<String, Any?>) {
        val patternRaw = config["vibrationPattern"] as? List<*>
        val pattern = patternRaw?.map { (it as? Number)?.toLong() ?: 0L }?.toLongArray()
            ?: longArrayOf(0L, 1000L, 1000L)

        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            manager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(pattern, 0)
        }
    }

    companion object {
        private const val TAG = "CallKitRingtone"
    }
}
