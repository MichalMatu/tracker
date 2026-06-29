@file:Suppress("WildcardImport", "NoWildcardImports")

package io.blueeye.core.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import io.blueeye.core.decoders.BleBeaconDecoder
import io.blueeye.core.decoders.apple.*
import io.blueeye.core.decoders.beacon.*
import io.blueeye.core.decoders.bose.*
import io.blueeye.core.decoders.environmental.*
import io.blueeye.core.decoders.govee.*
import io.blueeye.core.decoders.inkbird.*
import io.blueeye.core.decoders.microsoft.*
import io.blueeye.core.decoders.misc.*
import io.blueeye.core.decoders.misc.beacon.*
import io.blueeye.core.decoders.misc.health.*
import io.blueeye.core.decoders.misc.moko.*
import io.blueeye.core.decoders.misc.sensor.*
import io.blueeye.core.decoders.misc.tracker.*
import io.blueeye.core.decoders.qingping.*
import io.blueeye.core.decoders.samsung.*
import io.blueeye.core.decoders.shelly.*
import io.blueeye.core.decoders.switchbot.*
import io.blueeye.core.decoders.xiaomi.*

@Module
@InstallIn(SingletonComponent::class)
@Suppress("TooManyFunctions")
abstract class LibraryDecodersModule {
    // === TRACKERS & BEACONS ===
    @Binds @IntoSet
    abstract fun bindABN03Decoder(decoder: ABN03Decoder): BleBeaconDecoder

    @Binds @IntoSet
    abstract fun bindABN07Decoder(decoder: ABN07Decoder): BleBeaconDecoder

    @Binds @IntoSet
    abstract fun bindAltBeaconDecoder(decoder: AltBeaconDecoder): BleBeaconDecoder

    @Binds @IntoSet
    abstract fun bindEddystoneDecoder(decoder: EddystoneDecoder): BleBeaconDecoder

    @Binds @IntoSet
    abstract fun bindSamsungFindDecoder(decoder: SamsungFindDecoder): BleBeaconDecoder

    @Binds @IntoSet
    abstract fun bindSamsungGenericDecoder(decoder: SamsungGenericDecoder): BleBeaconDecoder

    @Binds @IntoSet
    abstract fun bindChipoloDecoder(decoder: ChipoloDecoder): BleBeaconDecoder

    @Binds @IntoSet
    abstract fun bindMokobeaconDecoder(decoder: MokobeaconDecoder): BleBeaconDecoder

    @Binds @IntoSet
    abstract fun bindJaaleeDecoder(decoder: JaaleeDecoder): BleBeaconDecoder

    @Binds @IntoSet
    abstract fun bindKKMDecoder(decoder: KKMDecoder): BleBeaconDecoder

    @Binds @IntoSet
    abstract fun bindFeasycomDecoder(decoder: FeasycomDecoder): BleBeaconDecoder

    @Binds @IntoSet
    abstract fun bindBlueCharmDecoder(decoder: BlueCharmDecoder): BleBeaconDecoder

    @Binds @IntoSet
    abstract fun bindHolyIotDecoder(decoder: HolyIotDecoder): BleBeaconDecoder

    // === HEALTH/MEDICAL ===
    @Binds @IntoSet
    abstract fun bindABTempDecoder(decoder: ABTempDecoder): BleBeaconDecoder

    @Binds @IntoSet
    abstract fun bindOralBDecoder(decoder: OralBDecoder): BleBeaconDecoder

    @Binds @IntoSet
    abstract fun bindMiBandDecoder(decoder: MiBandDecoder): BleBeaconDecoder

    @Binds @IntoSet
    abstract fun bindLaicaPS7200LDecoder(decoder: LaicaPS7200LDecoder): BleBeaconDecoder

    @Binds @IntoSet
    abstract fun bindHeartRateDecoder(decoder: HeartRateDecoder): BleBeaconDecoder

    // === MISC / IT / EXPERIMENTAL ===
    @Binds @IntoSet
    abstract fun bindMicrosoftCDPDecoder(decoder: MicrosoftCDPDecoder): BleBeaconDecoder

    @Binds @IntoSet
    abstract fun bindGAENDecoder(decoder: GAENDecoder): BleBeaconDecoder // Exposure Notification (COVID)

    @Binds @IntoSet
    abstract fun bindGenericTrackerDecoder(decoder: GenericTrackerDecoder): BleBeaconDecoder

    @Binds @IntoSet
    abstract fun bindServiceDataDecoder(decoder: ServiceDataDecoder): BleBeaconDecoder

    @Binds @IntoSet
    abstract fun bindMikrotikDecoder(decoder: MikrotikDecoder): BleBeaconDecoder

    // === OTHER SPECIFIC ===
    @Binds @IntoSet
    abstract fun bindAmphiroDecoder(decoder: AmphiroDecoder): BleBeaconDecoder

    @Binds @IntoSet
    abstract fun bindBParasiteDecoder(decoder: BParasiteDecoder): BleBeaconDecoder

    @Binds @IntoSet
    abstract fun bindBParasiteV2Decoder(decoder: BParasiteV2Decoder): BleBeaconDecoder

    @Binds @IntoSet
    abstract fun bindTiltDecoder(decoder: TiltDecoder): BleBeaconDecoder // Homebrew hydrometer

    @Binds @IntoSet
    abstract fun bindSkaleDecoder(decoder: SkaleDecoder): BleBeaconDecoder

    @Binds @IntoSet
    abstract fun bindSmartDryDecoder(decoder: SmartDryDecoder): BleBeaconDecoder

    @Binds @IntoSet
    abstract fun bindBikeSharingDecoder(decoder: BikeSharingDecoder): BleBeaconDecoder

    @Binds @IntoSet
    abstract fun bindAnemometerDecoder(decoder: AnemometerDecoder): BleBeaconDecoder

    @Binds @IntoSet
    abstract fun bindRadiolandDecoder(decoder: RadiolandDecoder): BleBeaconDecoder

    @Binds @IntoSet
    abstract fun bindINodeDecoder(decoder: INodeDecoder): BleBeaconDecoder

    @Binds @IntoSet
    abstract fun bindBoseDecoder(decoder: BoseDecoder): BleBeaconDecoder

    @Binds @IntoSet
    abstract fun bindVchonDecoder(decoder: VchonDecoder): BleBeaconDecoder

    @Binds @IntoSet
    abstract fun bindOrasDecoder(decoder: OrasDecoder): BleBeaconDecoder

    @Binds @IntoSet
    abstract fun bindHHCCRoPotDecoder(decoder: HHCCRoPotDecoder): BleBeaconDecoder
}
