package io.blueeye.core.alert

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.blueeye.core.data.classifier.vendor.tactical.ConfidenceLevel
import io.blueeye.core.data.classifier.vendor.tactical.TacticalCategory
import io.blueeye.core.data.classifier.vendor.tactical.TacticalOuiInfo
import io.blueeye.core.data.preferences.WatchlistPreferences
import io.blueeye.core.domain.alert.AlertDispatcher
import io.blueeye.core.model.DeviceType
import io.blueeye.core.model.EvidenceProvenance
import io.blueeye.core.model.EvidenceSource
import kotlinx.coroutines.runBlocking
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
    private lateinit var alertDispatcher: AlertDispatcher

    private lateinit var tacticalAlertService: TacticalAlertService

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        watchlistPreferences = WatchlistPreferences(context)

        runBlocking {
            watchlistPreferences.setTacticalDetectionEnabled(true)
            watchlistPreferences.setTacticalVibrationEnabled(false)
            watchlistPreferences.setFavoriteVibrationEnabled(false)
        }

        tacticalAlertService = TacticalAlertService(watchlistPreferences, alertDispatcher)
    }

    @Test
    fun testTacticalDetectionIncrementsCounter() {
        val request = request(mac = "00:11:22:33:44:55")

        tacticalAlertService.onDeviceDetected(request)
        Thread.sleep(200)

        assertEquals(1, tacticalAlertService.activeCount.value)
        assertEquals(request.macAddress, tacticalAlertService.activeDetections.value.first().macAddress)
        assertEquals(EvidenceSource.OUI, tacticalAlertService.activeDetections.value.first().evidence.source)
    }

    @Test
    fun testIgnoringDetectionWhenDisabled() {
        runBlocking {
            watchlistPreferences.setTacticalDetectionEnabled(false)
        }

        tacticalAlertService.onDeviceDetected(request(mac = "00:11:22:AA:BB:CC"))
        Thread.sleep(200)

        assertEquals("Should not count devices when Disabled", 0, tacticalAlertService.activeCount.value)
    }

    private fun request(mac: String): TacticalAlertRequest =
        TacticalAlertRequest(
            macAddress = mac,
            rssi = -50,
            match =
                TacticalOuiInfo(
                    ouiPrefix = "001122",
                    vendorName = "Test Vendor",
                    category = TacticalCategory.POLICE_EQUIPMENT,
                    deviceType = DeviceType.POLICE,
                    confidence = ConfidenceLevel.HIGH,
                    description = "Test Device",
                ),
            evidenceSource = EvidenceSource.OUI,
            rawEvidenceValue = "001122",
            evidenceProvenance = EvidenceProvenance.DEVICE_REGISTRY,
        )
}
