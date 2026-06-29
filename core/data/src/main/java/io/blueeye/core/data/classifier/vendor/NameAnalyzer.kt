package io.blueeye.core.data.classifier.vendor

/**
 * Interface for strategies that can analyze device names. This promotes modularity - strategies can
 * optionally implement this.
 */
interface NameAnalyzer {
    /**
     * Analyze a device name and return a classification result if matched.
     * @param name The BLE device name to analyze
     * @return VendorScanResult if the name matches known patterns, null otherwise
     */
    fun analyzeName(name: String): VendorScanResult?
}
