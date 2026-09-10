package io.blueeye.core.alert

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import io.blueeye.core.domain.alert.AlertCategory
import io.blueeye.core.domain.alert.AlertDispatcher
import javax.inject.Inject

@AndroidEntryPoint
class AlertAcknowledgeReceiver : BroadcastReceiver() {
    @Inject lateinit var alertDispatcher: AlertDispatcher

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action != ACTION_ACKNOWLEDGE_ALERT) return
        val target =
            alertAcknowledgeTarget(
                categoryName = intent.getStringExtra(EXTRA_ALERT_CATEGORY),
                key = intent.getStringExtra(EXTRA_ALERT_KEY),
            ) ?: return

        alertDispatcher.acknowledge(target.category, target.key)
    }

    companion object {
        const val ACTION_ACKNOWLEDGE_ALERT = "io.blueeye.action.ACKNOWLEDGE_ALERT"
        private const val EXTRA_ALERT_CATEGORY = "io.blueeye.extra.ALERT_CATEGORY"
        private const val EXTRA_ALERT_KEY = "io.blueeye.extra.ALERT_KEY"

        fun intent(
            context: Context,
            category: AlertCategory,
            key: String,
        ): Intent =
            Intent(context, AlertAcknowledgeReceiver::class.java)
                .setAction(ACTION_ACKNOWLEDGE_ALERT)
                .putExtra(EXTRA_ALERT_CATEGORY, category.name)
                .putExtra(EXTRA_ALERT_KEY, key)
    }
}

internal data class AlertAcknowledgeTarget(
    val category: AlertCategory,
    val key: String,
)

internal fun alertAcknowledgeTarget(
    categoryName: String?,
    key: String?,
): AlertAcknowledgeTarget? =
    categoryName
        ?.let { value -> runCatching { AlertCategory.valueOf(value) }.getOrNull() }
        ?.let { category ->
            key
                ?.takeIf(String::isNotBlank)
                ?.let { normalizedKey -> AlertAcknowledgeTarget(category, normalizedKey) }
        }
