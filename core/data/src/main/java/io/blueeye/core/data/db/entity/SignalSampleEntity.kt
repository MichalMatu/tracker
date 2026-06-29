package io.blueeye.core.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Próbka sygnału RSSI z lokalizacją GPS.
 *
 * Używane do:
 * - Rysowania wykresów RSSI w czasie
 * - Wyświetlania mapy spotkań (OSM)
 * - Algorytmu Follow-Me (korelacja czasowa)
 */
@Entity(
    tableName = "signal_samples",
    foreignKeys =
    [
        ForeignKey(
            entity = DeviceEntity::class,
            parentColumns = ["fingerprint"],
            childColumns = ["deviceFingerprint"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["deviceFingerprint"]), Index(value = ["timestamp"])],
)
data class SignalSampleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Fingerprint urządzenia (FK do devices) */
    val deviceFingerprint: String,
    /** Adres MAC widziany dokładnie w tej próbce. */
    val observedMac: String? = null,
    /** Technologia źródła próbki, np. BLE albo CLASSIC. */
    val technology: String? = null,
    /** Nazwa nadawana w pakiecie lub Classic discovery dokładnie dla tej próbki. */
    val deviceName: String? = null,
    /** Typ urządzenia rozpoznany w chwili zapisu próbki. */
    val deviceType: String? = null,
    /** Producent rozpoznany w chwili zapisu próbki. */
    val vendorName: String? = null,
    /** Siła sygnału (dBm, typowo -30 do -100) */
    val rssi: Int,
    /** Szerokość geograficzna (nullable - GPS może być niedostępny) */
    val latitude: Double? = null,
    /** Długość geograficzna */
    val longitude: Double? = null,
    /** Dokładność GPS w metrach */
    val locationAccuracy: Float? = null,
    /** Timestamp pomiaru (epoch millis) */
    val timestamp: Long,
    /** Company Identifier z Manufacturer Specific Data. */
    val manufacturerId: Int? = null,
    /** Manufacturer Specific Data dla głównego producenta jako hex. */
    val manufacturerDataHex: String? = null,
    /** Wszystkie Manufacturer Specific Data w formacie 0x004C=HEX;0x0075=HEX. */
    val manufacturerDataByIdHex: String? = null,
    /** UUID usług reklamowanych w tej próbce, rozdzielone przecinkami. */
    val serviceUuids: String? = null,
    /** Service Data w formacie uuid=HEX;uuid=HEX. */
    val serviceDataByUuidHex: String? = null,
    /** BLE Appearance z pakietu reklamowego. */
    val appearance: Int? = null,
    /** Tx Power z pakietu reklamowego. */
    val txPower: Int? = null,
    /** Czy pakiet był connectable. */
    val isConnectable: Boolean? = null,
    val primaryPhy: Int? = null,
    val secondaryPhy: Int? = null,
    val advertisingIntervalMs: Long? = null,
    val beaconType: String? = null,
    /** Pełny raw advertising payload jako hex. */
    val rawDataHex: String? = null,
    /** Dane sensora zdekodowane w chwili próbki. */
    val sensorData: String? = null,
    /** Class of Device dla Classic Bluetooth. */
    val classOfDevice: Int? = null,
    /** Status follow-me w chwili próbki. */
    val trackingStatus: String? = null,
    /** Wynik follow-me w chwili próbki. */
    val followingScore: Float? = null,
    /** Czy próbka została rozpoznana jako public-safety/professional. */
    val isTactical: Boolean? = null,
    val tacticalCategory: String? = null,
    val probeError: String? = null,
)
