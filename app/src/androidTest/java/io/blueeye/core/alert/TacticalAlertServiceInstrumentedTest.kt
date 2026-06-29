package io.blueeye.core.alert

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.blueeye.core.data.classifier.vendor.tactical.ConfidenceLevel
import io.blueeye.core.data.classifier.vendor.tactical.TacticalCategory
import io.blueeye.core.data.classifier.vendor.tactical.TacticalOuiInfo
import io.blueeye.core.data.preferences.WatchlistPreferences
import io.blueeye.core.model.DeviceType
import io.blueeye.core.model.EvidenceSource
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.MockitoAnnotations

@RunWith(AndroidJUnit4::class)
class TacticalAlertServiceInstrumentedTest {
    private lateinit var watchlistPreferences: WatchlistPreferences

    @Mock
    private lateinit var vibrationHandler: TacticalVibrationHandler

    private lateinit var tacticalAlertService: TacticalAlertService

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        // Use REAL preferences (isolated in test APK context)
        watchlistPreferences = WatchlistPreferences(context)

        // Set initial state
        kotlinx.coroutines.runBlocking {
            watchlistPreferences.setTacticalDetectionEnabled(true)
            watchlistPreferences.setTacticalVibrationEnabled(false)
            watchlistPreferences.setFavoriteVibrationEnabled(false)
        }

        tacticalAlertService = TacticalAlertService(context, watchlistPreferences, vibrationHandler)
    }

    @Test
    fun testTacticalDetectionIncrementsCounter() {
        // Given
        val mac = "00:11:22:33:44:55"
        val rssi = -50
        val match =
            TacticalOuiInfo(
                ouiPrefix = "001122",
                vendorName = "Test Vendor",
                category = TacticalCategory.POLICE_EQUIPMENT,
                deviceType = DeviceType.POLICE,
                confidence = ConfidenceLevel.HIGH,
                description = "Test Device",
            )

        // When
        tacticalAlertService.onDeviceDetected(mac, rssi, match)

        // Wait for coroutine (service uses Default Dispatcher)
        Thread.sleep(200)

        // Then
        assertEquals(1, tacticalAlertService.activeCount.value)
        assertEquals(mac, tacticalAlertService.activeDetections.value.first().macAddress)
        assertEquals(EvidenceSource.OUI, tacticalAlertService.activeDetections.value.first().evidence.source)
    }

    @Test
    fun testIgnoringDetectionWhenDisabled() {
        // Given
        val mac = "00:11:22:AA:BB:CC"
        val match =
            TacticalOuiInfo(
                ouiPrefix = "001122",
                vendorName = "Test Vendor",
                category = TacticalCategory.POLICE_EQUIPMENT,
                deviceType = DeviceType.POLICE,
                confidence = ConfidenceLevel.HIGH,
                description = "Test Device",
            )

        // Disable detection preference
        kotlinx.coroutines.runBlocking {
            watchlistPreferences.setTacticalDetectionEnabled(false)
        }

        // When
        tacticalAlertService.onDeviceDetected(mac, -50, match)

        Thread.sleep(200)

        // Then
        assertEquals("Should not count devices when Disabled", 0, tacticalAlertService.activeCount.value)
    }
}
