package io.blueeye.core.data.repository.handler.ble.enricher

import io.blueeye.core.data.repository.handler.ble.ScanDataContext

/**
 * Common interface for components that enrich scan data with specific information.
 * Part of the DeviceEnricher modularity refactor.
 */
interface ScanEnricher {
    /**
     * Enriches the given [ctx] with specific data.
     * Implementations should modify the mutable properties of [ctx].
     */
    fun enrich(ctx: ScanDataContext)
}
