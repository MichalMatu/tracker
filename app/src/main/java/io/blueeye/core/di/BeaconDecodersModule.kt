package io.blueeye.core.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import io.blueeye.core.decoders.AirTagBroadcastDecoder
import io.blueeye.core.decoders.AltBeaconDecoder
import io.blueeye.core.decoders.AmazonAmaDecoder
import io.blueeye.core.decoders.BleBeaconDecoder
import io.blueeye.core.decoders.GoogleFastPairBeaconDecoder
import io.blueeye.core.decoders.TP357SDecoder
import io.blueeye.core.decoders.TheengsInteroperableDecoder

@Module
@InstallIn(SingletonComponent::class)
abstract class BeaconDecodersModule {
    @Binds
    @IntoSet
    abstract fun bindAltBeaconDecoder(decoder: AltBeaconDecoder): BleBeaconDecoder

    @Binds
    @IntoSet
    abstract fun bindGoogleFastPairBeaconDecoder(decoder: GoogleFastPairBeaconDecoder): BleBeaconDecoder

    @Binds
    @IntoSet
    abstract fun bindAmazonAmaDecoder(decoder: AmazonAmaDecoder): BleBeaconDecoder

    @Binds
    @IntoSet
    abstract fun bindTp357sDecoder(decoder: TP357SDecoder): BleBeaconDecoder

    @Binds
    @IntoSet
    abstract fun bindAirTagDecoder(decoder: AirTagBroadcastDecoder): BleBeaconDecoder

    @Binds
    @IntoSet
    abstract fun bindTheengsDecoder(decoder: TheengsInteroperableDecoder): BleBeaconDecoder
}
