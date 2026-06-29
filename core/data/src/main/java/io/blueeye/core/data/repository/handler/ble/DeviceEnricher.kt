package io.blueeye.core.data.repository.handler.ble

import io.blueeye.core.data.repository.handler.ble.enricher.FingerprintEnricher
import io.blueeye.core.data.repository.handler.ble.enricher.ScanEnricher
import io.blueeye.core.data.repository.handler.ble.enricher.SensorEnricher
import io.blueeye.core.data.repository.handler.ble.enricher.TacticalEnricher
import io.blueeye.core.data.repository.handler.ble.enricher.VendorEnricher
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Enriches scan data with decoded information from various sources.
 *
 * Use Composition pattern to delegate specific enrichment tasks to [ScanEnricher] implementations.
 * This class now acts as a Facade/Orchestrator.
 */
@Singleton
class DeviceEnricher @Inject constructor(
    sensorEnricher: SensorEnricher,
    fingerprintEnricher: FingerprintEnricher,
    vendorEnricher: VendorEnricher,
    tacticalEnricher: TacticalEnricher,
) {
    // Order matters: Sensor -> Fingerprint -> Vendor -> Tactical
    private val enrichers: List<ScanEnricher> = listOf(
        sensorEnricher,
        fingerprintEnricher,
        vendorEnricher,
        tacticalEnricher,
    )

    /**
     * Enriches the scan context with decoded sensor, vendor, and tactical data.
     */
    fun enrich(ctx: ScanDataContext): ScanDataContext {
        enrichers.forEach { it.enrich(ctx) }
        return ctx
    }
}
