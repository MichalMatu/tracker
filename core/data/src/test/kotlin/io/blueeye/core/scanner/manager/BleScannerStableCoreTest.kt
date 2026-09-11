package io.blueeye.core.scanner.manager

import android.content.Context
import io.blueeye.core.domain.repository.DeviceRepository
import io.blueeye.core.scanner.extractor.ScanResultExtractor
import io.blueeye.core.scanner.source.BleScanSource
import io.blueeye.core.scanner.source.ClassicScanSource
import io.blueeye.core.scanner.source.PassiveBleScanMode
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class BleScannerStableCoreTest {
    @Test
    fun `passive start keeps BLE enabled and never starts Classic in stable core`() = runTest {
        val bleScanSource: BleScanSource = mock()
        val classicScanSource: ClassicScanSource = mock()
        whenever(bleScanSource.isScanning()).thenReturn(false)
        whenever(bleScanSource.start(anyOrNull(), any(), any(), any())).thenReturn(true)

        val scanner = createScanner(bleScanSource, classicScanSource)

        scanner.performPassiveBleScan()

        verify(bleScanSource).start(
            anyOrNull(),
            any(),
            any(),
            eq(PassiveBleScanMode.BROAD),
        )
        verify(classicScanSource, never()).start(any())
        assertSame(ScannerState.Scanning, scanner.state.value)
    }

    @Test
    fun `passive maintenance refreshes BLE source without restarting Classic`() = runTest {
        val bleScanSource: BleScanSource = mock()
        val classicScanSource: ClassicScanSource = mock()
        whenever(bleScanSource.isScanning()).thenReturn(false)
        whenever(bleScanSource.start(anyOrNull(), any(), any(), any())).thenReturn(true)

        val scanner = createScanner(bleScanSource, classicScanSource)

        scanner.performPassiveBleScan()
        val maintenanceJob = launch { scanner.maintainPassiveScan(refreshIntervalMs = 1L) }

        advanceTimeBy(ScannerConstants.SCAN_TRANSITION_DELAY_MS + 1L)
        runCurrent()
        maintenanceJob.cancel()

        verify(bleScanSource).stop()
        verify(bleScanSource, times(2)).start(anyOrNull(), any(), any(), any())
        verify(classicScanSource, never()).start(any())
        assertSame(ScannerState.Scanning, scanner.state.value)
        assertTrue(ScannerConstants.PASSIVE_SCAN_REFRESH_INTERVAL_MS < 300_000L)
    }

    @Test
    fun `screen state transitions switch passive BLE mode without restarting Classic`() = runTest {
        val bleScanSource: BleScanSource = mock()
        val classicScanSource: ClassicScanSource = mock()
        whenever(bleScanSource.isScanning()).thenReturn(false)
        whenever(bleScanSource.start(anyOrNull(), any(), any(), any())).thenReturn(true)

        val scanner = createScanner(bleScanSource, classicScanSource)

        scanner.performPassiveBleScan()
        scanner.transitionPassiveScanMode(PassiveBleScanMode.BACKGROUND_FILTERED)
        scanner.transitionPassiveScanMode(PassiveBleScanMode.BROAD)

        verify(bleScanSource, times(2)).stop()
        verify(bleScanSource).start(
            anyOrNull(),
            any(),
            any(),
            eq(PassiveBleScanMode.BACKGROUND_FILTERED),
        )
        verify(bleScanSource, times(2)).start(
            anyOrNull(),
            any(),
            any(),
            eq(PassiveBleScanMode.BROAD),
        )
        verify(classicScanSource, never()).start(any())
        assertSame(ScannerState.Scanning, scanner.state.value)
    }

    @Test
    fun `screen policy keeps broad foreground and filtered background modes`() {
        assertSame(PassiveBleScanMode.BROAD, passiveBleScanModeForInteractive(isInteractive = true))
        assertSame(
            PassiveBleScanMode.BACKGROUND_FILTERED,
            passiveBleScanModeForInteractive(isInteractive = false),
        )
    }

    @Test
    fun `focused scan may resume passive while duplicate passive starts remain idempotent`() {
        assertTrue(ScannerState.Focused("AA:BB:CC:DD:EE:FF").allowsPassiveStart())
        assertTrue(ScannerState.Idle.allowsPassiveStart())
        assertTrue(ScannerState.Error("failed").allowsPassiveStart())
        assertFalse(ScannerState.Starting.allowsPassiveStart())
        assertFalse(ScannerState.Scanning.allowsPassiveStart())
    }

    private fun createScanner(
        bleScanSource: BleScanSource,
        classicScanSource: ClassicScanSource,
    ): BleScanner =
        BleScanner(
            context = mock<Context>(),
            repository = mock<DeviceRepository>(),
            adapter = null,
            bleScanSource = bleScanSource,
            classicScanSource = classicScanSource,
            scanResultExtractor = mock<ScanResultExtractor>(),
            screenStateMonitor = mock<PassiveBleScreenStateMonitor>(),
        )
}
