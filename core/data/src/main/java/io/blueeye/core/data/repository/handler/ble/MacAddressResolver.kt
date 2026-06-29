package io.blueeye.core.data.repository.handler.ble

import android.util.Log
import io.blueeye.core.data.repository.VendorRepository
import io.blueeye.core.data.tracker.AddressCarryoverTracker
import io.blueeye.core.domain.bluetooth.MacAddressAnalyzer
import io.blueeye.core.model.MacAddressType
import io.blueeye.core.scanner.model.BleScanResultData
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resolves MAC address information including:
 * - Vendor name from OUI or Company ID
 * - MAC address type (Public, Random, RPA)
 * - Address carryover tracking for rotating MACs
 */
@Singleton
class MacAddressResolver @Inject constructor(
    private val vendorRepository: VendorRepository,
    private val addressCarryoverTracker: AddressCarryoverTracker,
) {
    /**
     * Enriches the scan context with MAC address information.
     * This includes vendor resolution, MAC type analysis, and carryover tracking.
     */
    suspend fun resolve(ctx: ScanDataContext): ScanDataContext {
        // Resolve vendor name using BLE Company ID or MAC OUI
        ctx.vendorName = vendorRepository.getVendorName(ctx.mac, ctx.manufacturerId)

        // Analyze MAC address type
        ctx.macAddressType = analyzeMacType(ctx.mac)

        // Extract Advertising Interval from raw data (0x1A)
        ctx.advertisingInterval = AddressCarryoverTracker.extractAdvertisingInterval(ctx.rawData)

        // Handle address carryover for random MACs
        if (ctx.macAddressType == MacAddressType.RANDOM) {
            processCarryover(ctx)
        }

        return ctx
    }

    private fun analyzeMacType(mac: String): MacAddressType {
        // If we know the OUI (e.g. from manuf.txt), it is definitely PUBLIC,
        // regardless of what the RPA bits say
        val isKnownOui = vendorRepository.hasOui(mac)

        return if (isKnownOui) {
            MacAddressType.PUBLIC
        } else {
            val macAnalysis = MacAddressAnalyzer.analyze(mac)
            when (macAnalysis.privacyLevel) {
                MacAddressAnalyzer.PrivacyLevel.STATIC -> MacAddressType.PUBLIC
                MacAddressAnalyzer.PrivacyLevel.SEMI_STATIC -> MacAddressType.RANDOM
                MacAddressAnalyzer.PrivacyLevel.DYNAMIC -> MacAddressType.RANDOM
                MacAddressAnalyzer.PrivacyLevel.UNKNOWN -> MacAddressType.UNKNOWN
            }
        }
    }

    private suspend fun processCarryover(ctx: ScanDataContext) {
        // Use scanData if available for cleaner parameter passing
        val correlationInfo = if (ctx.scanData != null) {
            addressCarryoverTracker.processScan(ctx.scanData, ctx.sanitizedName)
        } else {
            val fallbackData = BleScanResultData(
                mac = ctx.mac,
                rssi = ctx.rssi,
                timestamp = ctx.timestamp,
                technology = ctx.technology,
                name = ctx.name,
                manufacturerId = ctx.manufacturerId,
                manufacturerData = ctx.manufacturerData,
                manufacturerDataById = ctx.manufacturerDataById,
                serviceUuids = ctx.serviceUuids,
                serviceDataByUuid = ctx.serviceDataByUuid,
                appearance = ctx.appearance,
                txPower = ctx.txPower,
                isConnectable = ctx.isConnectable,
                primaryPhy = ctx.primaryPhy,
                secondaryPhy = ctx.secondaryPhy,
                rawData = ctx.rawData,
            )
            addressCarryoverTracker.processScan(fallbackData, ctx.sanitizedName)
        }

        if (correlationInfo.isPending) {
            ctx.isProvisional = true
            // We can optionally log here
            // android.util.Log.v(TAG, "Pending/Provisional signal for ${ctx.mac} - waiting for more data")
            return
        }

        ctx.macChangeCount = correlationInfo.macChangeCount

        val stableMac = correlationInfo.correlatedMac
        if (stableMac != null && stableMac != ctx.mac) {
            // Use the PRIMARY/ORIGINAL MAC as fingerprint to update existing record.
            // This applies both to a newly detected carryover and to later scans of a known alias.
            ctx.fingerprint = stableMac
            ctx.isCarryover = correlationInfo.isCarryover
            correlationInfo.matchEvidence?.let { matchEvidence ->
                ctx.carryoverReasonCode = matchEvidence.reasonCode.name
                ctx.carryoverConfidence = matchEvidence.confidence
                ctx.carryoverFeatures = matchEvidence.featureSummary
            }
            Log.i(
                TAG,
                "MAC identity carryover ${ctx.mac} -> ${ctx.fingerprint}; " +
                    "reason=${ctx.carryoverReasonCode}; confidence=${ctx.carryoverConfidence}",
            )
        }
    }

    companion object {
        private const val TAG = "MacAddressResolver"
    }
}
