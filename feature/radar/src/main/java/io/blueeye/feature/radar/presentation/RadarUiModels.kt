package io.blueeye.feature.radar.presentation

import io.blueeye.core.model.Device

data class RadarUiItem(
    val device: Device,
    val fingerprint: String,
    val displayName: String,
    val vendorAndType: String,
    val sensorData: String?,
    val signalInfo: RadarUiSignalInfo,
    val statusInfo: RadarUiStatusInfo,
    val connectionInfo: RadarUiConnectionInfo,
    val icons: RadarUiIcons,
    val badges: RadarBadgeInfo,
    val isNew: Boolean,
    val isInWatchlist: Boolean,
    val isIgnored: Boolean,
    val nameColor: RadarUiColorToken,
    val isProbing: Boolean = false,
    val evidenceInfo: RadarEvidenceInfo?
)

data class RadarUiSignalInfo(
    val rssi: Int,
    val rssiText: String,
    val signalColor: RadarUiColorToken,
    val signalProgress: Int,
    val distanceText: String,
    val techBadge: String,
    val techBadgeColor: RadarUiColorToken,
    val timeSinceSeen: String
)

data class RadarUiStatusInfo(
    val text: String,
    val textColor: RadarUiColorToken,
    val backgroundTint: RadarUiColorToken,
    val isWarning: Boolean,
    val cardBackgroundColor: RadarUiColorToken?
)

data class RadarUiConnectionInfo(
    val isVisible: Boolean,
    val text: String,
    val textColor: RadarUiColorToken
)

data class RadarUiIcons(
    val mainIconRes: Int,
    val isConnectable: Boolean
)

data class RadarBadgeInfo(
    val techBadge: String,
    val techColor: RadarUiColorToken,
    val privacyBadge: String,
    val watchlistBadge: String?,
    val watchlistColor: RadarUiColorToken,
    val statusBadge: String?,
    val statusColor: RadarUiColorToken,
    val calibrationBadge: String?,
    val calibrationColor: RadarUiColorToken,
    val batteryText: String?,
    val temperatureText: String?,
    val humidityText: String?,
    val voltageText: String?,
    val extraText: String?
)

enum class RadarUiSectionType(
    val title: String,
    val description: String,
    val statusText: String,
    val tone: RadarUiColorToken,
) {
    WATCHLIST(
        title = "Watchlist",
        description = "Devices you marked or devices that returned after being offline.",
        statusText = "Watched",
        tone = RadarUiColorToken.WARNING,
    ),
    SUSPICIOUS(
        title = "Suspicious",
        description = "Tracker-like, follow-me, or RSSI-pattern signals that need review.",
        statusText = "Review",
        tone = RadarUiColorToken.SUSPICIOUS,
    ),
    PUBLIC_SAFETY(
        title = "Public Safety Signals",
        description = "Signals consistent with public-safety or professional equipment, not a confirmed presence.",
        statusText = "Evidence review",
        tone = RadarUiColorToken.WARNING,
    ),
    NEARBY(
        title = "Nearby",
        description = "Identified devices without attention evidence.",
        statusText = "No attention",
        tone = RadarUiColorToken.SAFE,
    ),
    UNKNOWN_NOISE(
        title = "Unknown / Noise",
        description = "Ignored, weak, or unidentified broadcasts with no attention evidence.",
        statusText = "Low priority",
        tone = RadarUiColorToken.OUTLINE,
    ),
}

data class RadarUiSection(
    val type: RadarUiSectionType,
    val items: List<RadarUiItem>,
)

data class RadarDecisionSummary(
    val headline: String,
    val detail: String,
    val tone: RadarUiColorToken,
    val attentionCount: Int,
)

data class RadarTopBarState(
    val deviceCount: Int,
    val totalCount: Int,
    val isFilterActive: Boolean,
    val isScanning: Boolean,
    val isBaselineActive: Boolean,
    val autoActiveProbeEnabled: Boolean,
    val decisionSummary: RadarDecisionSummary?,
    val filterCount: Int,
)

data class RadarTopBarActions(
    val onMenuClick: () -> Unit,
    val onScanToggle: () -> Unit,
    val onBaselineToggle: () -> Unit,
    val onAutoActiveProbeToggle: () -> Unit,
    val onFilterClick: () -> Unit,
    val onClearClick: () -> Unit,
)

data class RadarEvidenceInfo(
    val confidenceText: String,
    val confidenceColor: RadarUiColorToken,
    val primarySourceText: String,
    val primaryReasonText: String,
    val primaryValueText: String?,
    val chips: List<RadarEvidenceChipInfo>
)

data class RadarEvidenceChipInfo(
    val text: String,
    val color: RadarUiColorToken
)
