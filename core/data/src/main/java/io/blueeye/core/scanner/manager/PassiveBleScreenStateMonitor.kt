package io.blueeye.core.scanner.manager

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.PowerManager
import dagger.hilt.android.qualifiers.ApplicationContext
import io.blueeye.core.scanner.source.PassiveBleScanMode
import javax.inject.Inject
import javax.inject.Singleton

/** Keeps screen-state policy outside the BLE lifecycle owner. */
@Singleton
class PassiveBleScreenStateMonitor
@Inject
constructor(
    @ApplicationContext private val context: Context,
) {
    private var receiver: BroadcastReceiver? = null

    fun currentMode(): PassiveBleScanMode {
        val powerManager = context.getSystemService(PowerManager::class.java)
        return passiveBleScanModeForInteractive(powerManager?.isInteractive != false)
    }

    @Synchronized
    fun start(onModeChanged: (PassiveBleScanMode) -> Unit) {
        if (receiver != null) return

        val screenReceiver =
            object : BroadcastReceiver() {
                override fun onReceive(
                    context: Context?,
                    intent: Intent?,
                ) {
                    val mode =
                        when (intent?.action) {
                            Intent.ACTION_SCREEN_OFF -> PassiveBleScanMode.BACKGROUND_FILTERED
                            Intent.ACTION_SCREEN_ON -> PassiveBleScanMode.BROAD
                            else -> return
                        }
                    onModeChanged(mode)
                }
            }

        val filter =
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
            }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(screenReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                @Suppress("DEPRECATION")
                context.registerReceiver(screenReceiver, filter)
            }
            receiver = screenReceiver
        } catch (error: RuntimeException) {
            receiver = null
            throw error
        }
    }

    @Synchronized
    fun stop() {
        val screenReceiver = receiver ?: return
        receiver = null
        try {
            context.unregisterReceiver(screenReceiver)
        } catch (@Suppress("SwallowedException") error: IllegalArgumentException) {
            // Receiver was already unregistered with its owning runtime.
        }
    }
}

internal fun passiveBleScanModeForInteractive(isInteractive: Boolean): PassiveBleScanMode =
    if (isInteractive) {
        PassiveBleScanMode.BROAD
    } else {
        PassiveBleScanMode.BACKGROUND_FILTERED
    }
