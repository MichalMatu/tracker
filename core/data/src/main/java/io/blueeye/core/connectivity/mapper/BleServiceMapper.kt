package io.blueeye.core.connectivity.mapper

import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import io.blueeye.core.connectivity.resolver.BleNamesResolver
import io.blueeye.core.model.GattCharacteristic
import io.blueeye.core.model.GattService
import javax.inject.Inject

class BleServiceMapper
@Inject
constructor(private val bleNamesResolver: BleNamesResolver) {
    fun map(services: List<BluetoothGattService>): List<GattService> {
        return services.map { service ->
            GattService(
                uuid = service.uuid.toString(),
                name = bleNamesResolver.resolveServiceName(service.uuid.toString()),
                characteristics =
                service.characteristics.map { char ->
                    GattCharacteristic(
                        uuid = char.uuid.toString(),
                        name =
                        bleNamesResolver.resolveCharName(
                            char.uuid.toString(),
                        ),
                        properties = getProperties(char.properties),
                    )
                },
            )
        }
    }

    private fun getProperties(properties: Int): List<String> {
        val props = mutableListOf<String>()
        if (properties and BluetoothGattCharacteristic.PROPERTY_READ > 0) props.add("READ")
        if (properties and BluetoothGattCharacteristic.PROPERTY_WRITE > 0) props.add("WRITE")
        if (properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY > 0) props.add("NOTIFY")
        if (properties and BluetoothGattCharacteristic.PROPERTY_INDICATE > 0) props.add("INDICATE")
        if (properties and BluetoothGattCharacteristic.PROPERTY_BROADCAST > 0) {
            props.add("BROADCAST")
        }
        return props
    }
}
