package io.blueeye.feature.radar.presentation

import io.blueeye.core.model.Device
import io.blueeye.core.model.DeviceType
import io.blueeye.core.model.MacAddressType
import io.blueeye.core.model.TrackingStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class RadarSectionViewMapperTest {
    @Test
    fun `options expose all decision views in attention first order with zero counts`() {
        val sections =
            listOf(
                section(RadarUiSectionType.SUSPICIOUS, count = 2),
                section(RadarUiSectionType.NEARBY, count = 4),
                section(RadarUiSectionType.UNKNOWN_NOISE, count = 3),
            )

        val options = RadarSectionViewMapper.options(sections)

        assertEquals(
            listOf(
                RadarSectionViewType.ALL,
                RadarSectionViewType.WATCHLIST,
                RadarSectionViewType.SUSPICIOUS,
                RadarSectionViewType.PUBLIC_SAFETY,
                RadarSectionViewType.NEARBY,
                RadarSectionViewType.UNKNOWN_NOISE,
            ),
            options.map { it.type },
        )
        assertEquals(
            listOf(
                "All 9",
                "Watchlist 0",
                "Suspicious 2",
                "Public Safety Signals 0",
                "Nearby 4",
                "Unknown / Noise 3",
            ),
            options.map { it.label },
        )
    }

    @Test
    fun `visible sections returns selected decision view only`() {
        val watchlist = section(RadarUiSectionType.WATCHLIST, count = 1)
        val nearby = section(RadarUiSectionType.NEARBY, count = 2)
        val sections = listOf(watchlist, nearby)

        val visibleSections =
            RadarSectionViewMapper.visibleSections(
                sections = sections,
                selectedView = RadarSectionViewType.NEARBY,
            )

        assertEquals(listOf(RadarUiSectionType.NEARBY), visibleSections.map { it.type })
    }

    @Test
    fun `all view keeps every section`() {
        val sections =
            listOf(
                section(RadarUiSectionType.WATCHLIST, count = 1),
                section(RadarUiSectionType.PUBLIC_SAFETY, count = 1),
            )

        assertEquals(
            sections,
            RadarSectionViewMapper.visibleSections(
                sections = sections,
                selectedView = RadarSectionViewType.ALL,
            ),
        )
    }

    @Test
    fun `all view remains selected when attention sections are present`() {
        val options =
            RadarSectionViewMapper.options(
                listOf(
                    section(RadarUiSectionType.SUSPICIOUS, count = 2),
                    section(RadarUiSectionType.NEARBY, count = 4),
                    section(RadarUiSectionType.UNKNOWN_NOISE, count = 9),
                ),
            )

        val selected =
            RadarSectionViewMapper.resolveSelectedView(
                requested = RadarSectionViewType.ALL,
                options = options,
            )

        assertEquals(RadarSectionViewType.ALL, selected)
    }

    @Test
    fun `zero count decision view remains selectable for explanatory empty state`() {
        val options = RadarSectionViewMapper.options(listOf(section(RadarUiSectionType.NEARBY, count = 1)))

        val selected =
            RadarSectionViewMapper.resolveSelectedView(
                requested = RadarSectionViewType.SUSPICIOUS,
                options = options,
            )

        assertEquals(RadarSectionViewType.SUSPICIOUS, selected)
        assertEquals(
            emptyList<RadarUiSection>(),
            RadarSectionViewMapper.visibleSections(
                sections = listOf(section(RadarUiSectionType.NEARBY, count = 1)),
                selectedView = selected,
            ),
        )
    }

    @Test
    fun `empty text explains no public safety signals as absence not certainty`() {
        assertEquals(
            "No public-safety-like signals are visible right now.",
            RadarSectionViewMapper.emptyText(RadarSectionViewType.PUBLIC_SAFETY),
        )
    }

    private fun section(
        type: RadarUiSectionType,
        count: Int,
    ): RadarUiSection =
        RadarUiSection(
            type = type,
            items = List(count) { index -> item("item-$index") },
        )

    private fun item(fingerprint: String): RadarUiItem =
        RadarUiItem(
            device = device(fingerprint),
            fingerprint = fingerprint,
            displayName = fingerprint,
            vendorAndType = "",
            sensorData = null,
            signalInfo =
                RadarUiSignalInfo(
                    rssi = -60,
                    rssiText = "-60 dBm",
                    signalColor = RadarUiColorToken.PRIMARY,
                    signalProgress = 50,
                    distanceText = "Unknown",
                    techBadge = "BLE",
                    techBadgeColor = RadarUiColorToken.PRIMARY,
                    timeSinceSeen = "now",
                ),
            statusInfo =
                RadarUiStatusInfo(
                    text = "SAFE",
                    textColor = RadarUiColorToken.SAFE,
                    backgroundTint = RadarUiColorToken.SAFE_CONTAINER,
                    isWarning = false,
                    cardBackgroundColor = null,
                ),
            connectionInfo =
                RadarUiConnectionInfo(
                    isVisible = false,
                    text = "",
                    textColor = RadarUiColorToken.TRANSPARENT,
                ),
            icons =
                RadarUiIcons(
                    mainIconRes = 0,
                    isConnectable = false,
                ),
            badges =
                RadarBadgeInfo(
                    techBadge = "BLE",
                    techColor = RadarUiColorToken.PRIMARY,
                    privacyBadge = "PUBLIC",
                    watchlistBadge = null,
                    watchlistColor = RadarUiColorToken.GRAY,
                    statusBadge = null,
                    statusColor = RadarUiColorToken.SAFE,
                    calibrationBadge = null,
                    calibrationColor = RadarUiColorToken.GRAY,
                    batteryText = null,
                    temperatureText = null,
                    humidityText = null,
                    voltageText = null,
                    extraText = null,
                ),
            isNew = false,
            isInWatchlist = false,
            isIgnored = false,
            nameColor = RadarUiColorToken.PRIMARY,
            evidenceInfo = null,
        )

    private fun device(fingerprint: String): Device =
        Device(
            fingerprint = fingerprint,
            macAddress = "AA:BB:CC:11:22:33",
            macAddressType = MacAddressType.PUBLIC,
            technology = "BLE",
            name = fingerprint,
            deviceType = DeviceType.UNKNOWN,
            vendorName = null,
            predictedModel = null,
            trackingStatus = TrackingStatus.SAFE,
            followingScore = 0f,
            isSafeBeacon = false,
            isInWatchlist = false,
            userAlias = null,
            userNotes = null,
            alertSound = false,
            alertVibration = false,
            firstSeenAt = NOW,
            lastSeenAt = NOW,
            rssi = -60,
            encounterCount = 1,
        )

    private companion object {
        private const val NOW = 1_789_000_000_000L
    }
}
