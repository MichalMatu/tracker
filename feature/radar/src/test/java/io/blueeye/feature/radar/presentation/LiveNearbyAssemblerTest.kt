package io.blueeye.feature.radar.presentation

import io.blueeye.core.model.DeviceCalibrationLabel
import io.blueeye.core.model.RadarEvidenceSignals
import io.blueeye.core.model.TrackingStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveNearbyAssemblerTest {
    @Test
    fun `active devices sort by signal and recent stays below`() {
        val devices =
            listOf(
                device("weak", -80, LiveNearbyFreshness.ACTIVE, 10_000L),
                device("recent", -30, LiveNearbyFreshness.RECENT, 9_000L),
                device("strong", -45, LiveNearbyFreshness.ACTIVE, 8_000L),
            )

        val snapshot = LiveNearbyAssembler.assemble(devices, pinnedFingerprint = null)

        assertEquals(listOf("strong", "weak"), snapshot.active.deviceFingerprints())
        assertEquals(listOf("recent"), snapshot.recent.deviceFingerprints())
    }

    @Test
    fun `find hub identities group while suspicious identity escapes`() {
        val ordinaryA = device("a", -55, LiveNearbyFreshness.ACTIVE, 10_000L, "Google Find Hub")
        val ordinaryB = device("b", -65, LiveNearbyFreshness.ACTIVE, 9_000L, "Google Find Hub")
        val suspicious =
            device(
                fingerprint = "suspicious",
                rssi = -40,
                freshness = LiveNearbyFreshness.ACTIVE,
                lastSeenAt = 11_000L,
                beaconType = "Google Find Hub",
                status = TrackingStatus.SUSPICIOUS,
            )

        val snapshot = LiveNearbyAssembler.assemble(
            listOf(ordinaryA, ordinaryB, suspicious),
            pinnedFingerprint = null,
        )

        val group = snapshot.active.filterIsInstance<LiveNearbyEntry.ProtocolGroup>().single()
        assertEquals(2, group.activeCount)
        assertEquals(2, group.totalIdentities)
        assertTrue(
            snapshot.active.any {
                it is LiveNearbyEntry.Device && it.device.item.fingerprint == "suspicious"
            },
        )
    }

    @Test
    fun `pinned stale device remains visible and outside protocol group`() {
        val pinned = device("pinned", -70, LiveNearbyFreshness.STALE, 1_000L, "Apple Find My")
        val otherA = device("a", -60, LiveNearbyFreshness.ACTIVE, 10_000L, "Apple Find My")
        val otherB = device("b", -65, LiveNearbyFreshness.ACTIVE, 9_000L, "Apple Find My")

        val snapshot = LiveNearbyAssembler.assemble(
            listOf(pinned, otherA, otherB),
            pinnedFingerprint = "pinned",
        )

        assertEquals("pinned", snapshot.pinned?.item?.fingerprint)
        val group = snapshot.active.filterIsInstance<LiveNearbyEntry.ProtocolGroup>().single()
        assertEquals(2, group.totalIdentities)
    }

    @Test
    fun `order controller refreshes every five seconds and freezes while scrolling`() {
        val controller = LiveNearbyOrderController()
        val first = snapshot(device("a", -40), device("b", -70))
        val inverted = snapshot(device("b", -30), device("a", -80))

        assertEquals(listOf("a", "b"), controller.apply(first, 0L, frozen = false).active.deviceFingerprints())
        assertEquals(
            listOf("a", "b"),
            controller.apply(inverted, 4_000L, frozen = false).active.deviceFingerprints(),
        )
        assertEquals(
            listOf("b", "a"),
            controller.apply(inverted, 5_000L, frozen = false).active.deviceFingerprints(),
        )
        assertEquals(
            listOf("b", "a"),
            controller.apply(first, 11_000L, frozen = true).active.deviceFingerprints(),
        )
        assertEquals(
            listOf("a", "b"),
            controller.apply(first, 11_001L, frozen = false).active.deviceFingerprints(),
        )
    }

    private fun snapshot(vararg devices: LiveNearbyDevice): LiveNearbySnapshot =
        LiveNearbySnapshot(
            pinned = null,
            active = devices.map { LiveNearbyEntry.Device(it) },
            recent = emptyList(),
            activeIdentityCount = devices.size,
            recentIdentityCount = 0,
        )

    private fun List<LiveNearbyEntry>.deviceFingerprints(): List<String> =
        mapNotNull { (it as? LiveNearbyEntry.Device)?.device?.item?.fingerprint }

    @Suppress("LongParameterList")
    private fun device(
        fingerprint: String,
        rssi: Int,
        freshness: LiveNearbyFreshness = LiveNearbyFreshness.ACTIVE,
        lastSeenAt: Long = 10_000L,
        beaconType: String? = null,
        status: TrackingStatus = TrackingStatus.SAFE,
    ): LiveNearbyDevice =
        LiveNearbyDevice(
            item =
                RadarUiItem(
                    fingerprint = fingerprint,
                    displayName = fingerprint,
                    vendorAndType = "BLE device",
                    signalInfo = RadarUiSignalInfo(
                rssi = rssi,
                rssiText = "$rssi dBm",
                signalColor = RadarUiColorToken.PRIMARY,
                signalProgress = 0,
                distanceText = "",
                techBadge = "BLE",
                techBadgeColor = RadarUiColorToken.PRIMARY,
                timeSinceSeen = "",
            ),
            statusInfo = RadarUiStatusInfo(
                text = "Safe",
                textColor = RadarUiColorToken.SAFE,
                backgroundTint = RadarUiColorToken.SAFE,
                isWarning = status != TrackingStatus.SAFE,
                cardBackgroundColor = null,
            ),
            icons = RadarUiIcons(
                mainIconRes = 0,
                isConnectable = false,
            ),
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
                ),
            smoothedRssi = rssi,
            trend = LiveSignalTrend.STEADY,
            freshness = freshness,
            ageMs = 0L,
        )
}
