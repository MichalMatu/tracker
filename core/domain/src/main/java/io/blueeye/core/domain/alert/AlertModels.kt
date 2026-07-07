package io.blueeye.core.domain.alert

import kotlinx.coroutines.flow.StateFlow

enum class AlertCategory {
    WATCHLIST_RETURN,
    FOLLOW_ME,
    PUBLIC_SAFETY_SIGNAL,
    TEST,
}

enum class AlertVibrationPattern {
    NONE,
    MEDIUM,
    HIGH,
    CRITICAL,
    WATCHLIST_RETURN,
}

enum class AlertDeliveryStatus {
    POSTED,
    BLOCKED_BY_POLICY,
    BLOCKED_BY_PERMISSION,
    BLOCKED_BY_CHANNEL,
    COOLED_DOWN,
    FAILED,
}

data class AlertRequest(
    val category: AlertCategory,
    val key: String,
    val title: String,
    val body: String,
    val vibrationPattern: AlertVibrationPattern = AlertVibrationPattern.MEDIUM,
    val cooldownMs: Long = DEFAULT_COOLDOWN_MS,
) {
    companion object {
        const val DEFAULT_COOLDOWN_MS = 300_000L
    }
}

data class AlertDeliveryResult(
    val category: AlertCategory = AlertCategory.TEST,
    val key: String = "initial",
    val status: AlertDeliveryStatus = AlertDeliveryStatus.BLOCKED_BY_POLICY,
    val postedNotification: Boolean = false,
    val soundPlayed: Boolean = false,
    val vibrationTriggered: Boolean = false,
    val message: String = "No alert dispatched yet",
    val timestamp: Long = 0L,
)

data class AlertChannelDiagnostics(
    val channelId: String,
    val importance: Int? = null,
    val blocked: Boolean = false,
    val soundEnabled: Boolean? = null,
    val vibrationEnabled: Boolean? = null,
)

data class AlertDeliveryDiagnostics(
    val postNotificationsGranted: Boolean = false,
    val notificationsEnabled: Boolean = false,
    val headsUpChannel: AlertChannelDiagnostics = AlertChannelDiagnostics(channelId = "field_mvp_alerts_heads_up_v1"),
    val trayChannel: AlertChannelDiagnostics = AlertChannelDiagnostics(channelId = "field_mvp_alerts_tray_v1"),
    val lastResult: AlertDeliveryResult = AlertDeliveryResult(),
)

interface AlertDispatcher {
    val diagnostics: StateFlow<AlertDeliveryDiagnostics>

    suspend fun dispatch(request: AlertRequest): Result<AlertDeliveryResult>

    fun refreshDiagnostics(): Result<Unit>
}
