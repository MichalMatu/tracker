@file:Suppress("WildcardImport", "NoWildcardImports")

package io.blueeye.core.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import io.blueeye.core.decoders.BleBeaconDecoder
import io.blueeye.core.decoders.environmental.*
import io.blueeye.core.decoders.inkbird.*
import io.blueeye.core.decoders.misc.energy.*
import io.blueeye.core.decoders.misc.sensor.*

@Module
@InstallIn(SingletonComponent::class)
@Suppress("TooManyFunctions")
abstract class IndustrialDecodersModule {
    // === SENSORS & LOGGERS ===
    @Binds @IntoSet
    abstract fun bindRuuviTagDecoder(decoder: RuuviTagDecoder): BleBeaconDecoder

    @Binds @IntoSet
    abstract fun bindBlueMaestro1Decoder(decoder: BlueMaestro1Decoder): BleBeaconDecoder

    @Binds @IntoSet
    abstract fun bindBlueMaestro3Decoder(decoder: BlueMaestro3Decoder): BleBeaconDecoder

    @Binds @IntoSet
    abstract fun bindBlueMaestro4Decoder(decoder: BlueMaestro4Decoder): BleBeaconDecoder

    @Binds @IntoSet
    abstract fun bindInkbirdTHDecoder(decoder: InkbirdTHDecoder): BleBeaconDecoder

    @Binds @IntoSet
    abstract fun bindInkbirdIBBQDecoder(decoder: InkbirdIBBQDecoder): BleBeaconDecoder

    @Binds @IntoSet
    abstract fun bindInkbirdPoolDecoder(decoder: InkbirdPoolDecoder): BleBeaconDecoder

    @Binds @IntoSet
    abstract fun bindThermoProDecoder(decoder: ThermoProDecoder): BleBeaconDecoder

    @Binds @IntoSet
    abstract fun bindThermoBeaconDecoder(decoder: ThermoBeaconDecoder): BleBeaconDecoder

    @Binds @IntoSet
    abstract fun bindHoboDecoder(decoder: HoboDecoder): BleBeaconDecoder

    @Binds @IntoSet
    abstract fun bindSensorPushDecoder(decoder: SensorPushDecoder): BleBeaconDecoder

    @Binds @IntoSet
    abstract fun bindSensorEasyDecoder(decoder: SensorEasyDecoder): BleBeaconDecoder

    @Binds @IntoSet
    abstract fun bindAranet4Decoder(decoder: Aranet4Decoder): BleBeaconDecoder

    @Binds @IntoSet
    abstract fun bindSensirionSCD4XDecoder(decoder: SensirionSCD4XDecoder): BleBeaconDecoder

    @Binds @IntoSet
    abstract fun bindSensirionSHT4XDecoder(decoder: SensirionSHT4XDecoder): BleBeaconDecoder

    // === ENERGY/VEHICLE ===
    @Binds @IntoSet
    abstract fun bindVictronDecoder(decoder: VictronDecoder): BleBeaconDecoder

    @Binds @IntoSet
    abstract fun bindEcoFlowDecoder(decoder: EcoFlowDecoder): BleBeaconDecoder

    @Binds @IntoSet
    abstract fun bindBM2Decoder(decoder: BM2BatteryMonitorDecoder): BleBeaconDecoder

    @Binds @IntoSet
    abstract fun bindBM6Decoder(decoder: BM6BatteryMonitorDecoder): BleBeaconDecoder

    @Binds @IntoSet
    abstract fun bindMopekaDecoder(decoder: MopekaDecoder): BleBeaconDecoder

    @Binds @IntoSet
    abstract fun bindTPMSDecoder(decoder: TPMSDecoder): BleBeaconDecoder

    @Binds @IntoSet
    abstract fun bindTPMSBRDecoder(decoder: TPMSBRDecoder): BleBeaconDecoder

    @Binds @IntoSet
    abstract fun bindBoschEbDecoder(decoder: BoschEbDecoder): BleBeaconDecoder

    @Binds @IntoSet
    abstract fun bindOtodataDecoder(decoder: OtodataDecoder): BleBeaconDecoder
}
