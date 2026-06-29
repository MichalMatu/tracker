package io.blueeye.feature.watchlist

import io.blueeye.core.domain.repository.WatchlistDeviceItem
import io.blueeye.core.model.AlertType
import io.blueeye.core.model.DetectionConfidence
import io.blueeye.core.model.DetectionEvidence
import io.blueeye.core.model.Device
import io.blueeye.core.model.EvidenceProvenance
import io.blueeye.core.model.EvidenceSource

object WatchlistUiFormatter {
    fun map(
        item: WatchlistDeviceItem,
        now: Long,
    ): WatchlistDeviceUiInfo {
        val device = item.device
        return WatchlistDeviceUiInfo(
            displayName = device.getDisplayName(),
            identityText = device.identityText(),
            status = if (item.isInRange) WatchlistRangeStatus.IN_RANGE else WatchlistRangeStatus.OFFLINE,
            lastSeenText = "Last seen ${formatElapsed(device.lastSeenAt, now)}",
            rssiText = "${device.rssi} dBm",
            alertsText = if (device.isTrackingEnabled) "Alerts active" else "Alerts paused",
            alertTypeText = formatAlertType(item.alertType),
            priorityText = "Priority ${item.priorityLevel}",
            returnEvidence = device.evidence.latestWatchlistReturnEvidence()?.toReturnEvidenceUiInfo(),
        )
    }

    private fun formatElapsed(
        lastSeenAt: Long,
        now: Long,
    ): String {
        val diff = now - lastSeenAt
        return when {
            diff < ONE_SECOND_MS -> "now"
            diff < ONE_MINUTE_MS -> "${diff / ONE_SECOND_MS}s ago"
            diff < ONE_HOUR_MS -> "${diff / ONE_MINUTE_MS}m ago"
            else -> ">1h ago"
        }
    }

    private fun formatAlertType(alertType: AlertType): String =
        when (alertType) {
            AlertType.ON_APPEAR -> "Return alerts"
            AlertType.ON_DISAPPEAR -> "Lost alerts"
            AlertType.ALWAYS -> "Continuous alerts"
        }

    private fun Device.identityText(): String {
        val alias = userAlias.normalized() ?: return "MAC: $macAddress"
        val observedIdentity = observedIdentity(alias)
        return if (observedIdentity == null) {
            "MAC: $macAddress"
        } else {
            "Observed: $observedIdentity | MAC: $macAddress"
        }
    }

    private fun Device.observedIdentity(alias: String): String? =
        (
            name.normalized()
                ?: predictedModel.normalized()
                ?: vendorName.normalized()?.let { "$it device" }
        )?.takeUnless { identity -> identity.equals(alias, ignoreCase = true) }

    private fun String?.normalized(): String? = this?.trim()?.takeIf { it.isNotBlank() }

    private fun List<DetectionEvidence>.latestWatchlistReturnEvidence(): DetectionEvidence? =
        filter {
            it.source == EvidenceSource.WATCHLIST &&
                it.reasonText.contains("returned", ignoreCase = true)
        }.maxByOrNull { it.timestamp }

    private fun DetectionEvidence.toReturnEvidenceUiInfo(): WatchlistReturnEvidenceUiInfo =
        WatchlistReturnEvidenceUiInfo(
            confidenceText = confidence.label,
            sourceText = "Source: ${source.label} - ${modeLabel()}",
            reasonText = reasonText,
            valueText = valueText(),
        )

    private fun DetectionEvidence.valueText(): String? {
        val raw = rawValue?.trim().orEmpty()
        val parsed = parsedValue?.trim().orEmpty()
        return when {
            raw.isNotBlank() && parsed.isNotBlank() && raw != parsed -> "Value: $raw -> $parsed"
            raw.isNotBlank() -> "Value: $raw"
            parsed.isNotBlank() -> "Value: $parsed"
            else -> null
        }
    }

    private fun DetectionEvidence.modeLabel(): String =
        when {
            source == EvidenceSource.USER_CONFIRMATION -> "verdict"
            source == EvidenceSource.GATT_PROBE || source == EvidenceSource.RFCOMM_PROBE -> "active"
            provenance != EvidenceProvenance.UNKNOWN -> provenance.label
            isPassive -> "passive"
            else -> "active"
        }

    private val EvidenceProvenance.label: String
        get() =
            when (this) {
                EvidenceProvenance.BLE_ADVERTISEMENT -> "BLE ad"
                EvidenceProvenance.CLASSIC_DISCOVERY -> "Classic"
                EvidenceProvenance.CLASSIC_SDP -> "Classic SDP"
                EvidenceProvenance.ACTIVE_GATT -> "active GATT"
                EvidenceProvenance.ACTIVE_RFCOMM -> "active RFCOMM"
                EvidenceProvenance.USER_ACTION -> "user action"
                EvidenceProvenance.FOLLOW_ME_ANALYSIS -> "analysis"
                EvidenceProvenance.DEVICE_REGISTRY -> "registry"
                EvidenceProvenance.UNKNOWN -> "passive"
            }

    private val DetectionConfidence.label: String
        get() =
            when (this) {
                DetectionConfidence.LOW -> "Low confidence"
                DetectionConfidence.MEDIUM -> "Medium confidence"
                DetectionConfidence.HIGH -> "High confidence"
                DetectionConfidence.CRITICAL -> "High confidence"
            }

    private val EvidenceSource.label: String
        get() =
            when (this) {
                EvidenceSource.NAME -> "Name"
                EvidenceSource.MODEL -> "Model"
                EvidenceSource.APPEARANCE -> "Appearance"
                EvidenceSource.CLASS_OF_DEVICE -> "Class"
                EvidenceSource.OUI -> "OUI"
                EvidenceSource.MANUFACTURER_ID -> "Manufacturer"
                EvidenceSource.SERVICE_UUID -> "Service"
                EvidenceSource.RAW_PAYLOAD -> "Payload"
                EvidenceSource.FOLLOW_ME_SCORE -> "Follow-Me"
                EvidenceSource.RSSI_PATTERN -> "RSSI"
                EvidenceSource.IDENTITY_CARRYOVER -> "Identity"
                EvidenceSource.WATCHLIST -> "Watchlist"
                EvidenceSource.USER_CONFIRMATION -> "User"
                EvidenceSource.GATT_PROBE -> "GATT"
                EvidenceSource.RFCOMM_PROBE -> "RFCOMM"
            }

    private const val ONE_SECOND_MS = 1_000L
    private const val ONE_MINUTE_MS = 60_000L
    private const val ONE_HOUR_MS = 3_600_000L
}

data class WatchlistDeviceUiInfo(
    val displayName: String,
    val identityText: String,
    val status: WatchlistRangeStatus,
    val lastSeenText: String,
    val rssiText: String,
    val alertsText: String,
    val alertTypeText: String,
    val priorityText: String,
    val returnEvidence: WatchlistReturnEvidenceUiInfo?,
)

data class WatchlistReturnEvidenceUiInfo(
    val confidenceText: String,
    val sourceText: String,
    val reasonText: String,
    val valueText: String?,
)

enum class WatchlistRangeStatus(
    val label: String,
) {
    IN_RANGE("In range"),
    OFFLINE("Offline"),
}
