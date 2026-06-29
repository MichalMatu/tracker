package io.blueeye.core.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.blueeye.core.data.publicsafety.PublicSafetySignalMonitorImpl
import io.blueeye.core.data.repository.ActiveCollectionRepositoryImpl
import io.blueeye.core.data.repository.DeviceRepositoryImpl
import io.blueeye.core.data.repository.SettingsPreferencesRepositoryImpl
import io.blueeye.core.data.repository.WatchlistRepositoryImpl
import io.blueeye.core.data.scanner.AndroidScannerRuntimeController
import io.blueeye.core.domain.publicsafety.PublicSafetySignalMonitor
import io.blueeye.core.domain.repository.ActiveCollectionRepository
import io.blueeye.core.domain.repository.DeviceRepository
import io.blueeye.core.domain.repository.SettingsPreferencesRepository
import io.blueeye.core.domain.repository.WatchlistRepository
import io.blueeye.core.domain.scanner.ScannerRuntimeController
import javax.inject.Singleton

/** Moduł Hilt wiążący interfejs DeviceRepository z implementacją DeviceRepositoryImpl. */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    @Singleton
    abstract fun bindDeviceRepository(impl: DeviceRepositoryImpl): DeviceRepository

    @Binds
    @Singleton
    abstract fun bindWatchlistRepository(impl: WatchlistRepositoryImpl): WatchlistRepository

    @Binds
    @Singleton
    abstract fun bindActiveCollectionRepository(impl: ActiveCollectionRepositoryImpl): ActiveCollectionRepository

    @Binds
    @Singleton
    abstract fun bindSettingsPreferencesRepository(
        impl: SettingsPreferencesRepositoryImpl,
    ): SettingsPreferencesRepository

    @Binds
    @Singleton
    abstract fun bindScannerRuntimeController(impl: AndroidScannerRuntimeController): ScannerRuntimeController

    @Binds
    @Singleton
    abstract fun bindPublicSafetySignalMonitor(impl: PublicSafetySignalMonitorImpl): PublicSafetySignalMonitor
}
