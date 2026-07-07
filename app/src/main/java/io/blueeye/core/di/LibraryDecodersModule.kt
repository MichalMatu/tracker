@file:Suppress("WildcardImport", "NoWildcardImports")

package io.blueeye.core.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import io.blueeye.core.decoders.BleBeaconDecoder
import io.blueeye.core.decoders.beacon.*
import io.blueeye.core.decoders.bose.*
import io.blueeye.core.decoders.microsoft.*
import io.blueeye.core.decoders.misc.GAENDecoder
import io.blueeye.core.decoders.misc.ServiceDataDecoder
import io.blueeye.core.decoders.misc.beacon.*
import io.blueeye.core.decoders.misc.moko.MokobeaconDecoder
import io.blueeye.core.decoders.misc.tracker.*
import io.blueeye.core.decoders.samsung.*

@Module
@InstallIn(SingletonComponent::class)
@Suppress("TooManyFunctions")
abstract class LibraryDecodersModule {
    // === FIELD MVP TRACKERS, BEACONS, AUDIO, AND SIGNAL EVIDENCE ===
    @Binds @IntoSet
    abstract fun bindABN03Decoder(decoder: ABN03Decoder): BleBeaconDecoder

    @Binds @IntoSet
    abstract fun bindABN07Decoder(decoder: ABN07Decoder): BleBeaconDecoder

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

    @Binds @IntoSet
    abstract fun bindMicrosoftCDPDecoder(decoder: MicrosoftCDPDecoder): BleBeaconDecoder

    @Binds @IntoSet
    abstract fun bindGAENDecoder(decoder: GAENDecoder): BleBeaconDecoder

    @Binds @IntoSet
    abstract fun bindGenericTrackerDecoder(decoder: GenericTrackerDecoder): BleBeaconDecoder

    @Binds @IntoSet
    abstract fun bindServiceDataDecoder(decoder: ServiceDataDecoder): BleBeaconDecoder

    @Binds @IntoSet
    abstract fun bindMikrotikDecoder(decoder: MikrotikDecoder): BleBeaconDecoder

    @Binds @IntoSet
    abstract fun bindBoseDecoder(decoder: BoseDecoder): BleBeaconDecoder
}
