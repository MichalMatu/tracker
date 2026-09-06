package io.blueeye.core.scanner.manager

import android.content.Context
import io.blueeye.core.domain.repository.DeviceRepository
import io.blueeye.core.scanner.extractor.ScanResultExtractor
import io.blueeye.core.scanner.source.BleScanSource
import io.blueeye.core.scanner.source.ClassicScanSource
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertSame
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class BleScannerStableCoreTest {
    @Test
    fun `passive start keeps BLE enabled and never starts Classic in stable core`() = runTest {
        val bleScanSource: BleScanSource = mock()
        val classicScanSource: ClassicScanSource = mock()
        whenever(bleScanSource.isScanning()).thenReturn(false)
        whenever(bleScanSource.start(anyOrNull(), any(), any())).thenReturn(true)

        val scanner =
            BleScanner(
                context = mock<Context>(),
                repository = mock<DeviceRepository>(),
                adapter = null,
                bleScanSource = bleScanSource,
                classicScanSource = classicScanSource,
                scanResultExtractor = mock<ScanResultExtractor>(),
            )

        scanner.performPassiveBleScan()

        verify(bleScanSource).start(anyOrNull(), any(), any())
        verify(classicScanSource, never()).start(any())
        assertSame(ScannerState.Scanning, scanner.state.value)
    }
}
