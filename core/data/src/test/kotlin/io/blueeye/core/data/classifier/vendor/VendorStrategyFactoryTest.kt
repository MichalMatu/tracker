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
import io.blueeye.core.decoders.parser.apple.AppleContinuityParser
import io.blueeye.core.decoders.parser.microsoft.CdpParser
import io.blueeye.core.decoders.parser.microsoft.SwiftPairParser
import io.blueeye.core.decoders.parser.samsung.SamsungManufacturerParser
import io.blueeye.core.model.DeviceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.mockito.kotlin.mock

class VendorStrategyFactoryTest {
    private val factory =
        VendorStrategyFactory(
            bigTech =
                BigTechStrategies(
                    apple = AppleStrategy(mock<AppleContinuityParser>()),
                    microsoft = MicrosoftStrategy(mock<SwiftPairParser>(), mock<CdpParser>()),
                    samsung = SamsungStrategy(mock<SamsungManufacturerParser>()),
                    google = GoogleStrategy(),
                    amazon = AmazonStrategy(),
                    tile = TileStrategy(),
                ),
            consumers =
                ConsumerStrategies(
                    sony = SonyStrategy(),
                    bose = BoseStrategy(),
                    jbl = JblStrategy(),
                    garmin = GarminStrategy(),
                    xiaomi = XiaomiStrategy(),
                    dji = DjiStrategy(),
                    goPro = GoProStrategy(),
                ),
            pro =
                ProfessionalStrategies(
                    motorola = MotorolaStrategy(),
                    commercial = CommercialStrategy(),
                    smartHome = SmartHomeStrategy(),
                    automotive = AutomotiveStrategy(),
                    tactical = TacticalStrategy(),
                    specialized = SpecializedStrategy(),
                ),
        )

    @Test
    fun `decode uses service uuid strategy without manufacturer records`() {
        val result =
            factory.decode(
                manufacturerRecords = emptyMap(),
                serviceUuids = listOf(SMARTTHINGS_FIND_UUID),
                name = null,
            )

        assertNotNull(result)
        assertEquals(DeviceType.SAMSUNG_TAG, result?.deviceType)
        assertEquals("SmartThings Find.", result?.extraInfo)
    }

    private companion object {
        private const val SMARTTHINGS_FIND_UUID = "0000fd5a-0000-1000-8000-00805f9b34fb"
    }
}
