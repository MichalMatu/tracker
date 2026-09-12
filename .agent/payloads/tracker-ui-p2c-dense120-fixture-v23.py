from pathlib import Path

p = Path('feature/radar/src/androidTest/java/io/blueeye/feature/radar/presentation/RadarDenseListStabilityTest.kt')
s = p.read_text()
s = s.replace(
    'import io.blueeye.core.model.Device\nimport io.blueeye.core.model.DeviceType\nimport io.blueeye.core.model.MacAddressType\n',
    'import io.blueeye.core.model.DeviceCalibrationLabel\nimport io.blueeye.core.model.RadarEvidenceSignals\n',
)
start = s.index('    private fun item(index: Int): RadarUiItem {')
end = s.index('    private fun cardTag(', start)
replacement = '''    private fun item(index: Int): RadarUiItem {
        val fingerprint = "dense-$index"
        val watchlist = index % 17 == 0
        val suspicious = index % 11 == 0

        return RadarUiItem(
            fingerprint = fingerprint,
            displayName = "Synthetic device $index",
            vendorAndType = "Vendor ${index % 7} · Unknown",
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
                    text = if (suspicious) "SUSPICIOUS" else "SAFE",
                    textColor = if (suspicious) RadarUiColorToken.SUSPICIOUS else RadarUiColorToken.SAFE,
                    backgroundTint =
                        if (suspicious) {
                            RadarUiColorToken.SUSPICIOUS_CONTAINER
                        } else {
                            RadarUiColorToken.SAFE_CONTAINER
                        },
                    isWarning = suspicious,
                    cardBackgroundColor = null,
                ),
            icons =
                RadarUiIcons(
                    mainIconRes = android.R.drawable.ic_menu_search,
                    isConnectable = false,
                ),
            isNew = false,
            isInWatchlist = watchlist,
            isIgnored = false,
            nameColor = RadarUiColorToken.PRIMARY,
            firstSeenAt = NOW_MS - index * 1_000L,
            trackingStatus = if (suspicious) TrackingStatus.SUSPICIOUS else TrackingStatus.SAFE,
            followingScore = if (suspicious) 60f else 0f,
            isSafeBeacon = false,
            calibrationLabel = DeviceCalibrationLabel.UNKNOWN,
            hasIdentitySignal = true,
            evidenceSignals =
                RadarEvidenceSignals(
                    hasWatchlistEvidence = watchlist,
                    hasTrackerLikeEvidence = suspicious,
                    hasPublicSafetyLikeEvidence = false,
                    hasAttentionEvidence = suspicious || watchlist,
                    hasAttentionFollowMeEvidence = suspicious,
                ),
        )
    }

'''
s = s[:start] + replacement + s[end:]
p.write_text(s)
