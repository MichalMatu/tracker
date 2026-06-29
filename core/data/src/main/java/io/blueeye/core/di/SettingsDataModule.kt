package io.blueeye.core.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.blueeye.core.data.repository.SignalSampleRepositoryImpl
import io.blueeye.core.data.settings.ReferenceDatabaseRepositoryImpl
import io.blueeye.core.domain.repository.SignalSampleRepository
import io.blueeye.core.domain.settings.ReferenceDatabaseRepository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class SettingsDataModule {
    @Binds
    @Singleton
    abstract fun bindReferenceDatabaseRepository(
        impl: ReferenceDatabaseRepositoryImpl,
    ): ReferenceDatabaseRepository

    @Binds
    @Singleton
    abstract fun bindSignalSampleRepository(impl: SignalSampleRepositoryImpl): SignalSampleRepository
}
