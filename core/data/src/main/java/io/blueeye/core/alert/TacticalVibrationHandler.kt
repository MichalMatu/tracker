package io.blueeye.core.alert

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import io.blueeye.core.data.classifier.vendor.tactical.ConfidenceLevel
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TacticalVibrationHandler @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "TacticalVibrationHandler"

        // Vibration Amplitudes
        private const val AMP_MAX = 255
        private const val AMP_MEDIUM = 200
        private const val AMP_OFF = 0

        // Vibration Durations (ms)
        private const val DUR_CRITICAL = 5000L
        private const val DUR_HIGH = 3000L
        private const val DUR_MEDIUM = 1000L
        private const val DUR_FAVORITE_PULSE = 1000L
        private const val DUR_FAVORITE_WAIT = 500L

        // Repeat index (-1 for no repeat)
        private const val NO_REPEAT = -1

        // Favorite pulsing pattern
        private val FAVORITE_TIMING = longArrayOf(
            0,
            DUR_FAVORITE_PULSE,
            DUR_FAVORITE_WAIT,
            DUR_FAVORITE_PULSE,
            DUR_FAVORITE_WAIT,
            DUR_FAVORITE_PULSE,
            DUR_FAVORITE_WAIT,
            DUR_FAVORITE_PULSE
        )
        private val FAVORITE_AMPLITUDES = intArrayOf(
            AMP_OFF,
            AMP_MAX,
            AMP_OFF,
            AMP_MAX,
            AMP_OFF,
            AMP_MAX,
            AMP_OFF,
            AMP_MAX
        )
    }

    fun vibrate(confidence: ConfidenceLevel) {
        try {
            val vibrator = getVibrator() ?: return

            // Different patterns based on confidence
            val (duration, amplitude) = when (confidence) {
                ConfidenceLevel.CRITICAL -> Pair(DUR_CRITICAL, AMP_MAX)
                ConfidenceLevel.HIGH -> Pair(DUR_HIGH, AMP_MAX)
                ConfidenceLevel.MEDIUM -> Pair(DUR_MEDIUM, AMP_MEDIUM)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(duration, amplitude))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(duration)
            }

            Log.d(TAG, "Vibration triggered: ${confidence.name} ($duration ms)")
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            Log.e(TAG, "Failed to vibrate: ${e.message}")
        }
    }

    fun vibrateForFavorite() {
        try {
            val vibrator = getVibrator() ?: return

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(FAVORITE_TIMING, FAVORITE_AMPLITUDES, NO_REPEAT))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(FAVORITE_TIMING, NO_REPEAT)
            }

            Log.d(TAG, "Vibration triggered for favorite")
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            Log.e(TAG, "Failed to vibrate for favorite: ${e.message}")
        }
    }

    private fun getVibrator(): Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
        manager?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }
}
