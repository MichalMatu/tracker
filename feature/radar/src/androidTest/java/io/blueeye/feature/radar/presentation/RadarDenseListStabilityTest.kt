package io.blueeye.feature.radar.presentation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToIndex
import io.blueeye.core.model.DeviceCalibrationLabel
import io.blueeye.core.model.RadarEvidenceSignals
import io.blueeye.core.model.TrackingStatus
import io.blueeye.core.ui.theme.BlueEyeTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class RadarDenseListStabilityTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun denseListKeepsCardHeightStableAcrossLiveUpdatesAndScrollsToEnd() {
        val initialItems = List(DENSE_ITEM_COUNT) { index -> item(index) }
        val state = mutableStateOf(initialItems)

        composeRule.setContent {
            BlueEyeTheme {
                LazyColumn(modifier = Modifier.testTag(LIST_TAG)) {
                    items(
                        items = state.value,
                        key = { it.fingerprint },
                    ) { item ->
                        Box(modifier = Modifier.testTag(cardTag(item.fingerprint))) {
                            RadarDeviceItem(
                                item = item,
                                onClick = {},
                                onWatchlistClick = {},
                            )
                        }
                    }
                }
            }
        }

        val firstCard = composeRule.onNodeWithTag(cardTag("dense-0"))
        firstCard.assertIsDisplayed()
        val beforeHeight = firstCard.fetchSemanticsNode().boundsInRoot.height

        composeRule.runOnIdle {
            state.value =
                state.value.mapIndexed { index, item ->
                    item.copy(
                        signalInfo =
                            item.signalInfo.copy(
                                rssi = -45 - (index % 35),
                                rssiText = "${-45 - (index % 35)} dBm",
                                timeSinceSeen = if (index % 3 == 0) "now" else "${index % 20}s",
                            ),
                        statusInfo =
                            if (index == 0) {
                                item.statusInfo.copy(
                                    text = "SUSPICIOUS",
                                    textColor = RadarUiColorToken.SUSPICIOUS,
                                    isWarning = true,
                                )
                            } else {
                                item.statusInfo
                            },
                        isInWatchlist = index == 0 || item.isInWatchlist,
                    )
                }
        }
        composeRule.waitForIdle()

        val afterHeight = firstCard.fetchSemanticsNode().boundsInRoot.height
        assertEquals(beforeHeight, afterHeight, HEIGHT_TOLERANCE_PX)

        composeRule.onNodeWithTag(LIST_TAG).performScrollToIndex(DENSE_ITEM_COUNT - 1)
        composeRule.onNodeWithTag(cardTag("dense-${DENSE_ITEM_COUNT - 1}")).assertIsDisplayed()
    }

    private fun item(index: Int): RadarUiItem {
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

    private fun cardTag(fingerprint: String): String = "card-$fingerprint"

    private companion object {
        const val DENSE_ITEM_COUNT = 120
        const val LIST_TAG = "dense-radar-list"
        const val NOW_MS = 1_789_000_000_000L
        const val HEIGHT_TOLERANCE_PX = 0.5f
    }
}
