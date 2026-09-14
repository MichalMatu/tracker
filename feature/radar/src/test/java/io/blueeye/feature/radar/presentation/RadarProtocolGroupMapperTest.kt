package io.blueeye.feature.radar.presentation

import io.blueeye.core.model.DeviceCalibrationLabel
import io.blueeye.core.model.RadarEvidenceSignals
import io.blueeye.core.model.TrackingStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RadarProtocolGroupMapperTest {
    @Test
    fun `protocol group counts active identities and sorts active members first`() {
        val now = 500_000L
        val activeWeak = item("active-weak", -80, now - 1_000, "Apple FindMy / AirTag")
        val recentStrong = item("recent-strong", -40, now - 30_000, "Apple FindMy / AirTag")
        val activeStrong = item("active-strong", -50, now - 2_000, "Apple FindMy / AirTag")
        val section = RadarUiSection(RadarUiSectionType.NEARBY, listOf(activeWeak, recentStrong, activeStrong))

        val group = RadarProtocolGroupMapper.map(section, now).single() as RadarProtocolEntry.Group

        assertEquals(2, group.activeCount)
        assertEquals(listOf("active-strong", "active-weak", "recent-strong"), group.members.map { it.fingerprint })
    }

    @Test
    fun `tracker classification attention alone does not escape protocol group`() {
        val now = 550_000L
        val first = item("first", -55, now, "Google Find Hub")
        val second = item("second", -65, now, "Google Find Hub")
        val trackerAttention = RadarEvidenceSignals(false, true, false, true, false)
        val entries =
            RadarProtocolGroupMapper.map(
                RadarUiSection(
                    RadarUiSectionType.NEARBY,
                    listOf(
                        first.copy(evidenceSignals = trackerAttention),
                        second.copy(evidenceSignals = trackerAttention),
                    ),
                ),
                now,
            )

        assertEquals(1, entries.filterIsInstance<RadarProtocolEntry.Group>().size)
        assertEquals(0, entries.filterIsInstance<RadarProtocolEntry.Device>().size)
    }

    @Test
    fun `suspicious identity stays standalone instead of being hidden in group`() {
        val now = 600_000L
        val suspicious = item("suspicious", -50, now, "Google Find Hub", TrackingStatus.SUSPICIOUS)
        val ordinaryA = item("a", -60, now, "Google Find Hub")
        val ordinaryB = item("b", -70, now, "Google Find Hub")
        val entries = RadarProtocolGroupMapper.map(
            RadarUiSection(RadarUiSectionType.NEARBY, listOf(suspicious, ordinaryA, ordinaryB)),
            now
        )

        assertTrue(entries.any { it is RadarProtocolEntry.Device && it.item.fingerprint == "suspicious" })
        assertEquals(2, entries.filterIsInstance<RadarProtocolEntry.Group>().single().members.size)
    }

    private fun item(
        fingerprint: String,
        rssi: Int,
        lastSeenAt: Long,
        beaconType: String,
        status: TrackingStatus = TrackingStatus.SAFE,
    ): RadarUiItem =
        RadarUiItem(
            fingerprint = fingerprint,
            displayName = fingerprint,
            vendorAndType = "Tracker",
            signalInfo = RadarUiSignalInfo(
                rssi,
                "$rssi dBm",
                RadarUiColorToken.PRIMARY,
                0,
                "",
                "BLE",
                RadarUiColorToken.PRIMARY,
                ""
            ),
            statusInfo = RadarUiStatusInfo(
                "",
                RadarUiColorToken.PRIMARY,
                RadarUiColorToken.PRIMARY,
                status != TrackingStatus.SAFE,
                null
            ),
            icons = RadarUiIcons(0, false),
            isNew = false,
            isInWatchlist = false,
            isIgnored = false,
            nameColor = RadarUiColorToken.PRIMARY,
            firstSeenAt = lastSeenAt,
            lastSeenAt = lastSeenAt,
            trackingStatus = status,
            followingScore = 0f,
            isSafeBeacon = false,
            calibrationLabel = DeviceCalibrationLabel.UNKNOWN,
            hasIdentitySignal = true,
            evidenceSignals = RadarEvidenceSignals(false, true, false, false, false),
            beaconType = beaconType,
        )
}
