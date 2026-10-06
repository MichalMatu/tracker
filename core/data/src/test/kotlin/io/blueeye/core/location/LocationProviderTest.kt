package io.blueeye.core.location

import android.content.Context
import android.location.Location
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class LocationProviderTest {
    private val context: Context = mock()

    @Test
    fun `fresh coordinates reject a stale last-known fallback`() =
        runTest {
            whenever(context.getSystemService(Context.LOCATION_SERVICE)).thenReturn(null)
            val staleLocation =
                mock<Location>().also { location ->
                    whenever(location.time).thenReturn(System.currentTimeMillis() - STALE_AGE_MS)
                }
            val provider =
                object : LocationProvider(context) {
                    override fun getLastLocation(): Location = staleLocation
                }

            assertNull(provider.getFreshCoordinates())
        }

    @Test
    fun `fresh coordinates accept a recent last-known fallback`() =
        runTest {
            whenever(context.getSystemService(Context.LOCATION_SERVICE)).thenReturn(null)
            val recentLocation =
                mock<Location>().also { location ->
                    whenever(location.time).thenReturn(System.currentTimeMillis() - RECENT_AGE_MS)
                    whenever(location.latitude).thenReturn(LATITUDE)
                    whenever(location.longitude).thenReturn(LONGITUDE)
                    whenever(location.accuracy).thenReturn(ACCURACY_METERS)
                }
            val provider =
                object : LocationProvider(context) {
                    override fun getLastLocation(): Location = recentLocation
                }

            assertEquals(
                Triple<Double?, Double?, Float?>(LATITUDE, LONGITUDE, ACCURACY_METERS),
                provider.getFreshCoordinates(),
            )
        }

    private companion object {
        const val STALE_AGE_MS = 20_000L
        const val RECENT_AGE_MS = 1_000L
        const val LATITUDE = 51.1079
        const val LONGITUDE = 17.0385
        const val ACCURACY_METERS = 5f
    }
}
