@file:Suppress("WildcardImport", "NoWildcardImports")

package io.blueeye.core.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import io.blueeye.core.decoders.BleBeaconDecoder
import io.blueeye.core.decoders.govee.*
import io.blueeye.core.decoders.misc.*
import io.blueeye.core.decoders.misc.moko.*
import io.blueeye.core.decoders.misc.tuya.*
import io.blueeye.core.decoders.qingping.*
import io.blueeye.core.decoders.shelly.*
import io.blueeye.core.decoders.switchbot.*
import io.blueeye.core.decoders.xiaomi.*

@Module
@InstallIn(SingletonComponent::class)
@Suppress("TooManyFunctions")
abstract class SmartHomeDecodersModule {
    // === XIAOMI / QINGPING ===
    @Binds @IntoSet
    abstract fun bindXiaomiLYWSD03Decoder(decoder: XiaomiLYWSD03Decoder): BleBeaconDecoder

    @Binds @IntoSet
    abstract fun bindXiaomiLYWSD02Decoder(decoder: XiaomiLYWSD02Decoder): BleBeaconDecoder

    @Binds @IntoSet
    abstract fun bindXiaomiMiJiaDecoder(decoder: XiaomiMiJiaDecoder): BleBeaconDecoder

    @Binds @IntoSet
    abstract fun bindXiaomiMiLampDecoder(decoder: XiaomiMiLampDecoder): BleBeaconDecoder

    @Binds @IntoSet
    abstract fun bindXiaomiFormaldehydeDecoder(decoder: XiaomiFormaldehydeDecoder): BleBeaconDecoder

    @Binds @IntoSet
    abstract fun bindXiaomiScaleDecoder(decoder: XiaomiScaleDecoder): BleBeaconDecoder

    @Binds @IntoSet
    abstract fun bindMiFloraDecoder(decoder: MiFloraDecoder): BleBeaconDecoder

    @Binds @IntoSet
    abstract fun bindQingpingCGD1Decoder(decoder: QingpingCGD1Decoder): BleBeaconDecoder

    @Binds @IntoSet
    abstract fun bindQingpingCGDK2Decoder(decoder: QingpingCGDK2Decoder): BleBeaconDecoder

    @Binds @IntoSet
    abstract fun bindQingpingCGDN1Decoder(decoder: QingpingCGDN1Decoder): BleBeaconDecoder

    @Binds @IntoSet
    abstract fun bindQingpingCGG1Decoder(decoder: QingpingCGG1Decoder): BleBeaconDecoder

    @Binds @IntoSet
    abstract fun bindQingpingCGH1Decoder(decoder: QingpingCGH1Decoder): BleBeaconDecoder

    @Binds @IntoSet
    abstract fun bindQingpingCGP1WDecoder(decoder: QingpingCGP1WDecoder): BleBeaconDecoder

    @Binds @IntoSet
    abstract fun bindQingpingCGP22CDecoder(decoder: QingpingCGP22CDecoder): BleBeaconDecoder

    @Binds @IntoSet
    abstract fun bindQingpingCGP23WDecoder(decoder: QingpingCGP23WDecoder): BleBeaconDecoder

    @Binds @IntoSet
    abstract fun bindQingpingCGPR1Decoder(decoder: QingpingCGPR1Decoder): BleBeaconDecoder

    // === GOVEE ===
    @Binds @IntoSet
    abstract fun bindGoveeH5072Decoder(decoder: GoveeH5072Decoder): BleBeaconDecoder

    @Binds @IntoSet
    abstract fun bindGoveeH5055Decoder(decoder: GoveeH5055Decoder): BleBeaconDecoder

    @Binds @IntoSet
    abstract fun bindGoveeBTHDecoder(decoder: GoveeBTHDecoder): BleBeaconDecoder

    @Binds @IntoSet
    abstract fun bindGoveeH5106Decoder(decoder: GoveeH5106Decoder): BleBeaconDecoder

    @Binds @IntoSet
    abstract fun bindGoveeH5179Decoder(decoder: GoveeH5179Decoder): BleBeaconDecoder

    @Binds @IntoSet
    abstract fun bindGoveeH5074Decoder(decoder: GoveeH5074Decoder): BleBeaconDecoder

    // === SWITCHBOT ===
    @Binds @IntoSet
    abstract fun bindSwitchBotBlindTiltDecoder(decoder: SwitchBotBlindTiltDecoder): BleBeaconDecoder

    @Binds @IntoSet
    abstract fun bindSwitchBotContactDecoder(decoder: SwitchBotContactDecoder): BleBeaconDecoder

    @Binds @IntoSet
    abstract fun bindSwitchBotCurtainDecoder(decoder: SwitchBotCurtainDecoder): BleBeaconDecoder

    @Binds @IntoSet
    abstract fun bindSwitchBotMeterProDecoder(decoder: SwitchBotMeterProDecoder): BleBeaconDecoder

    @Binds @IntoSet
    abstract fun bindSwitchBotMotionDecoder(decoder: SwitchBotMotionDecoder): BleBeaconDecoder

    @Binds @IntoSet
    abstract fun bindSwitchBotMeterPlusDecoder(decoder: SwitchBotMeterPlusDecoder): BleBeaconDecoder

    @Binds @IntoSet
    abstract fun bindSwitchBotOutdoorMeterDecoder(decoder: SwitchBotOutdoorMeterDecoder): BleBeaconDecoder

    @Binds @IntoSet
    abstract fun bindSwitchBotBotDecoder(decoder: SwitchBotBotDecoder): BleBeaconDecoder

    // === SHELLY ===
    @Binds @IntoSet
    abstract fun bindShellyButtonDecoder(decoder: ShellyButtonDecoder): BleBeaconDecoder

    @Binds @IntoSet
    abstract fun bindShellyHTDecoder(decoder: ShellyHTDecoder): BleBeaconDecoder

    @Binds @IntoSet
    abstract fun bindShellyDoorDecoder(decoder: ShellyDoorDecoder): BleBeaconDecoder

    @Binds @IntoSet
    abstract fun bindShellyMotionDecoder(decoder: ShellyMotionDecoder): BleBeaconDecoder

    // === TUYA / OTHER HOME ===
    @Binds @IntoSet
    abstract fun bindTuyaTHBDecoder(decoder: TuyaTHBDecoder): BleBeaconDecoder

    @Binds @IntoSet
    abstract fun bindTuyaRGBBulbDecoder(decoder: TuyaRGBBulbDecoder): BleBeaconDecoder

    @Binds @IntoSet
    abstract fun bindBeeWiDoorDecoder(decoder: BeeWiDoorDecoder): BleBeaconDecoder

    @Binds @IntoSet
    abstract fun bindNodonDecoder(decoder: NodonDecoder): BleBeaconDecoder

    @Binds @IntoSet
    abstract fun bindEq3EqivaDecoder(decoder: Eq3EqivaDecoder): BleBeaconDecoder

    @Binds @IntoSet
    abstract fun bindMokosmartDecoder(decoder: MokosmartDecoder): BleBeaconDecoder
}
