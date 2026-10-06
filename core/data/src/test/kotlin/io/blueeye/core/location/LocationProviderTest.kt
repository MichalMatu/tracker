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
                    whenever(location.time).thenReturn(System.currentTimeMillis() - 20_000L)
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
                    whenever(location.time).thenReturn(System.currentTimeMillis() - 1_000L)
                    whenever(location.latitude).thenReturn(51.1079)
                    whenever(location.longitude).thenReturn(17.0385)
                    whenever(location.accuracy).thenReturn(5f)
                }
            val provider =
                object : LocationProvider(context) {
                    override fun getLastLocation(): Location = recentLocation
                }

            assertEquals(
                Triple<Double?, Double?, Float?>(51.1079, 17.0385, 5f),
                provider.getFreshCoordinates(),
            )
        }
}
