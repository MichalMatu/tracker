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

@Module
@InstallIn(SingletonComponent::class)
abstract class AppleDecodersModule {
    @Binds @IntoSet
    abstract fun bindAppleAirPodsDecoder(decoder: AppleAirPodsDecoder): BleBeaconDecoder

    @Binds @IntoSet
    abstract fun bindAppleWatchDecoder(decoder: AppleWatchDecoder): BleBeaconDecoder

    @Binds @IntoSet
    abstract fun bindAppleGenericDecoder(decoder: AppleGenericDecoder): BleBeaconDecoder

    @Binds @IntoSet
    abstract fun bindIBeaconDecoder(decoder: IBeaconDecoder): BleBeaconDecoder // Apple standard
}
