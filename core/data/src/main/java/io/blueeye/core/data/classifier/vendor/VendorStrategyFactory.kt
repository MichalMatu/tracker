package io.blueeye.core.data.classifier.vendor

import io.blueeye.core.data.classifier.vendor.strategy.AmazonStrategy
import io.blueeye.core.data.classifier.vendor.strategy.AppleStrategy
import io.blueeye.core.data.classifier.vendor.strategy.AutomotiveStrategy
import io.blueeye.core.data.classifier.vendor.strategy.BoseStrategy
import io.blueeye.core.data.classifier.vendor.strategy.CommercialStrategy
import io.blueeye.core.data.classifier.vendor.strategy.DjiStrategy
import io.blueeye.core.data.classifier.vendor.strategy.GarminStrategy
import io.blueeye.core.data.classifier.vendor.strategy.GoProStrategy
import io.blueeye.core.data.classifier.vendor.strategy.GoogleStrategy
import io.blueeye.core.data.classifier.vendor.strategy.JblStrategy
import io.blueeye.core.data.classifier.vendor.strategy.MicrosoftStrategy
import io.blueeye.core.data.classifier.vendor.strategy.MotorolaStrategy
import io.blueeye.core.data.classifier.vendor.strategy.SamsungStrategy
import io.blueeye.core.data.classifier.vendor.strategy.SmartHomeStrategy
import io.blueeye.core.data.classifier.vendor.strategy.SonyStrategy
import io.blueeye.core.data.classifier.vendor.strategy.SpecializedStrategy
import io.blueeye.core.data.classifier.vendor.strategy.TacticalStrategy
import io.blueeye.core.data.classifier.vendor.strategy.TileStrategy
import io.blueeye.core.data.classifier.vendor.strategy.XiaomiStrategy
import io.blueeye.core.model.DeviceType
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Groups related tech strategies to avoid LongParameterList.
 */
@Singleton
class BigTechStrategies @Inject constructor(
    val apple: AppleStrategy,
    val microsoft: MicrosoftStrategy,
    val samsung: SamsungStrategy,
    val google: GoogleStrategy,
    val amazon: AmazonStrategy,
    val tile: TileStrategy
)

/**
 * Groups audio and fitness strategies.
 */
@Singleton
@Suppress("LongParameterList")
class ConsumerStrategies @Inject constructor(
    val sony: SonyStrategy,
    val bose: BoseStrategy,
    val jbl: JblStrategy,
    val garmin: GarminStrategy,
    val xiaomi: XiaomiStrategy,
    val dji: DjiStrategy,
    val goPro: GoProStrategy
)

/**
 * Groups professional and tactical strategies.
 */
@Singleton
class ProfessionalStrategies @Inject constructor(
    val motorola: MotorolaStrategy,
    val commercial: CommercialStrategy,
    val smartHome: SmartHomeStrategy,
    val automotive: AutomotiveStrategy,
    val tactical: TacticalStrategy,
    val specialized: SpecializedStrategy
)

/**
 * Modular Vendor Strategy Factory.
 */
@Singleton
class VendorStrategyFactory @Inject constructor(
    private val bigTech: BigTechStrategies,
    private val consumers: ConsumerStrategies,
    private val pro: ProfessionalStrategies
) {
    private val allStrategies: List<VendorStrategy> = listOf(
        bigTech.apple, bigTech.tile, bigTech.microsoft, bigTech.samsung, bigTech.google,
        bigTech.amazon, consumers.sony, consumers.bose, consumers.jbl, consumers.garmin,
        consumers.xiaomi, consumers.dji, consumers.goPro, pro.motorola, pro.smartHome,
        pro.commercial, pro.tactical, pro.specialized
    )

    private val nameAnalyzers: List<NameAnalyzer> = allStrategies.filterIsInstance<NameAnalyzer>().plus(
        listOf(pro.tactical, pro.automotive, pro.commercial, pro.smartHome, pro.motorola, pro.specialized)
    ).distinct()

    fun decode(
        manufacturerRecords: Map<Int, ByteArray>,
        serviceUuids: List<String>,
        name: String? = null
    ): VendorScanResult? {
        val input = VendorScanInput(manufacturerRecords, serviceUuids)
        val result = allStrategies.filter { it.canHandle(input) }.firstNotNullOfOrNull {
            val res = it.decode(input)
            if (res.deviceType != DeviceType.UNKNOWN) res else null
        }
        if (result != null) return result

        return if (!name.isNullOrBlank()) {
            nameAnalyzers.firstNotNullOfOrNull { it.analyzeName(name) }
        } else {
            null
        }
    }

    fun getStrategyCount(): Int = allStrategies.size
    fun getNameAnalyzerCount(): Int = nameAnalyzers.size
}
