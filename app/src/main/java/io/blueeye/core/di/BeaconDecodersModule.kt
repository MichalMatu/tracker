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
    abstract fun bindAirTagDecoder(decoder: AirTagBroadcastDecoder): BleBeaconDecoder
}
