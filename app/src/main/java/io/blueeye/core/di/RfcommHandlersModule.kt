package io.blueeye.core.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import io.blueeye.core.scanner.rfcomm.RfcommDeviceHandler
import io.blueeye.core.scanner.rfcomm.handlers.BoseRfcommHandler
import io.blueeye.core.scanner.rfcomm.handlers.GenericRfcommHandler

/**
 * Hilt module for RFCOMM device handlers.
 *
 * Uses multibindings so handlers are automatically discovered by RfcommConnectionManager. Add new
 * handlers here as they are implemented.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RfcommHandlersModule {
    @Binds
    @IntoSet
    abstract fun bindBoseHandler(handler: BoseRfcommHandler): RfcommDeviceHandler

    @Binds
    @IntoSet
    abstract fun bindGenericHandler(handler: GenericRfcommHandler): RfcommDeviceHandler
}
