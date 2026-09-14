package io.blueeye.feature.radar.presentation

import io.blueeye.core.model.DeviceCalibrationLabel
import io.blueeye.core.model.TrackingStatus

enum class ProtocolNoiseFamily(val title: String) {
    APPLE_FIND_MY("Apple Find My"),
    GOOGLE_FIND_HUB("Google Find Hub"),
}

object ProtocolNoiseClassifier {
    fun family(beaconType: String?, displayName: String): ProtocolNoiseFamily? {
        val beacon = beaconType.orEmpty().lowercase()
        val name = displayName.trim().lowercase()
        val isFindHub =
            beacon.contains("find hub") ||
                beacon.contains("fmdn") ||
                name == "google find hub tracker" ||
                name == "fmdn"
        val isFindMy =
            beacon.contains("findmy") ||
                beacon.contains("find my") ||
                beacon.contains("airtag") ||
                name == "find my"

        return when {
            isFindHub -> ProtocolNoiseFamily.GOOGLE_FIND_HUB
            isFindMy -> ProtocolNoiseFamily.APPLE_FIND_MY
            else -> null
        }
    }

    fun family(item: RadarUiItem): ProtocolNoiseFamily? = family(item.beaconType, item.displayName)

    fun canGroup(item: RadarUiItem): Boolean = family(item) != null && !mustStayStandalone(item)

    fun mustStayStandalone(item: RadarUiItem): Boolean =
        item.isInWatchlist ||
            item.hasUserAlias ||
            item.trackingStatus != TrackingStatus.SAFE ||
            item.followingScore >= ATTENTION_SCORE_THRESHOLD ||
            item.calibrationLabel in ATTENTION_LABELS ||
            item.evidenceSignals.hasAttentionEvidence

    private const val ATTENTION_SCORE_THRESHOLD = 51f
    private val ATTENTION_LABELS = setOf(DeviceCalibrationLabel.TRUE_POSITIVE, DeviceCalibrationLabel.SUSPICIOUS)
}

sealed interface RadarProtocolEntry {
    val key: String

    data class Device(val item: RadarUiItem) : RadarProtocolEntry {
        override val key: String = "device:${item.fingerprint}"
    }

    data class Group(
        val family: ProtocolNoiseFamily,
        val members: List<RadarUiItem>,
        val activeCount: Int,
        val strongestActiveRssi: Int?,
    ) : RadarProtocolEntry {
        override val key: String = "protocol:${family.name}"
    }
}

object RadarProtocolGroupMapper {
    @Suppress("ReturnCount")
    fun map(section: RadarUiSection, nowMs: Long = System.currentTimeMillis()): List<RadarProtocolEntry> {
        if (section.type !in GROUPABLE_SECTIONS) return section.items.map(RadarProtocolEntry::Device)

        val groups =
            section.items
                .filter(ProtocolNoiseClassifier::canGroup)
                .groupBy { ProtocolNoiseClassifier.family(it)!! }
                .filterValues { it.size >= MIN_GROUP_SIZE }
        if (groups.isEmpty()) return section.items.map(RadarProtocolEntry::Device)

        val emitted = mutableSetOf<ProtocolNoiseFamily>()
        return buildList {
            section.items.forEach { item ->
                val family = ProtocolNoiseClassifier.family(item)
                val members = family?.let(groups::get)
                if (members == null || !ProtocolNoiseClassifier.canGroup(item)) {
                    add(RadarProtocolEntry.Device(item))
                } else if (emitted.add(family)) {
                    val ordered = members.sortedWith(
                        compareByDescending<RadarUiItem> { it.isActive(nowMs) }
                            .thenByDescending { it.signalInfo.rssi }
                            .thenByDescending { it.lastSeenAt },
                    )
                    val active = ordered.filter { it.isActive(nowMs) }
                    add(
                        RadarProtocolEntry.Group(
                            family = family,
                            members = ordered,
                            activeCount = active.size,
                            strongestActiveRssi = active.maxOfOrNull { it.signalInfo.rssi },
                        ),
                    )
                }
            }
        }
    }

    private fun RadarUiItem.isActive(nowMs: Long): Boolean =
        (nowMs - lastSeenAt).coerceAtLeast(0L) <= ACTIVE_WINDOW_MS

    private const val ACTIVE_WINDOW_MS = 15_000L
    private const val MIN_GROUP_SIZE = 2
    private val GROUPABLE_SECTIONS = setOf(RadarUiSectionType.NEARBY, RadarUiSectionType.UNKNOWN_NOISE)
}
