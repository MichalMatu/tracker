package io.blueeye.core.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.blueeye.core.connectivity.manager.BleConnectionManager
import io.blueeye.core.connectivity.resolver.BleNamesResolver
import io.blueeye.core.connectivity.resolver.BluetoothNamesResolver
import io.blueeye.core.data.details.AndroidDeviceFocusedScanController
import io.blueeye.core.data.details.DeviceSensorDataDecoderImpl
import io.blueeye.core.data.details.DeviceServiceResolverImpl
import io.blueeye.core.domain.details.DeviceConnectionController
import io.blueeye.core.domain.details.DeviceFocusedScanController
import io.blueeye.core.domain.details.DeviceSensorDataDecoder
import io.blueeye.core.domain.details.DeviceServiceResolver
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DetailsModule {
    @Binds
    @Singleton
    abstract fun bindDeviceConnectionController(impl: BleConnectionManager): DeviceConnectionController

    @Binds
    @Singleton
    abstract fun bindDeviceFocusedScanController(
        impl: AndroidDeviceFocusedScanController,
    ): DeviceFocusedScanController

    @Binds
    @Singleton
    abstract fun bindDeviceSensorDataDecoder(impl: DeviceSensorDataDecoderImpl): DeviceSensorDataDecoder

    @Binds
    @Singleton
    abstract fun bindDeviceServiceResolver(impl: DeviceServiceResolverImpl): DeviceServiceResolver

    @Binds
    @Singleton
    abstract fun bindBluetoothNamesResolver(impl: BleNamesResolver): BluetoothNamesResolver
}
