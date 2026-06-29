package io.blueeye.core.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import io.blueeye.core.model.DeviceCalibrationLabel
import io.blueeye.core.model.DeviceType
import io.blueeye.core.model.IdentityCarryoverVerdict
import io.blueeye.core.model.MacAddressType
import io.blueeye.core.model.TrackingStatus

/**
 * Reprezentacja urządzenia Bluetooth w bazie danych.
 *
 * Przechowuje zarówno surowe dane (MAC, nazwa) jak i wyniki analizy (typ urządzenia, status
 * zagrożenia, score śledzenia).
 */
@Entity(tableName = "devices")
data class DeviceEntity(
    /** Unikalny identyfikator - hash z MAC/UUID */
    @PrimaryKey val fingerprint: String,
    // Podstawowe dane identyfikacyjne
    /** Ostatni widziany adres MAC */
    val lastMacAddress: String?,
    /** Typ adresu MAC (PUBLIC/RANDOM) */
    val macAddressType: MacAddressType = MacAddressType.UNKNOWN,
    /** Ostatni kod powodu korelacji rotującego adresu MAC */
    val carryoverReasonCode: String? = null,
    /** Pewność ostatniej korelacji rotującego adresu MAC (0.0 - 1.0) */
    val carryoverConfidence: Float = 0f,
    /** Skrócone cechy użyte przez matcher rotującego adresu MAC */
    val carryoverFeatures: String? = null,
    /** Technologia (BLE, CLASSIC, BLE 5.0) */
    val technology: String = "UNKNOWN",
    /** Ostatnia widziana nazwa urządzenia */
    val lastDeviceName: String?,
    // Klasyfikacja urządzenia
    /** Rozpoznany typ urządzenia */
    val deviceType: DeviceType = DeviceType.UNKNOWN,
    /** Nazwa producenta (z OUI lub Manufacturer Data) */
    val vendorName: String? = null,
    /** ID producenta z Manufacturer Specific Data */
    val manufacturerId: Int? = null,
    /** Przewidywany model (np. "JBL Flip 6" z Fast Pair) */
    val predictedModel: String? = null,
    // Dane BT Classic
    /** Class of Device (dla BT Classic) */
    val classOfDevice: Int? = null,
    /** Czy to system audio samochodowy (CoD 0x2004) */
    val isCarAudio: Boolean = false,
    // Analiza zagrożeń
    /** Status zagrożenia przypisany przez algorytm */
    val trackingStatus: TrackingStatus = TrackingStatus.SAFE,
    /** Wynik algorytmu Follow-Me (0.0 - 1.0) */
    val followingScore: Float = 0f,
    /** Wyjaśnienie ostatniego wyniku Follow-Me */
    val followMeExplanation: String? = null,
    /** Komponent czasu obserwacji w wyniku Follow-Me */
    val followMeDurationScore: Int = 0,
    /** Komponent stabilności RSSI w wyniku Follow-Me */
    val followMeRssiStabilityScore: Int = 0,
    /** Komponent typu urządzenia w wyniku Follow-Me */
    val followMeDeviceTypeScore: Int = 0,
    /** Komponent rotacji MAC w wyniku Follow-Me */
    val followMeMacBehaviorScore: Int = 0,
    /** Komponent liczby spotkań w wyniku Follow-Me */
    val followMeEncounterScore: Int = 0,
    /** Czy użytkownik ruszył się w bieżącej sesji scoringowej */
    val followMeUserMoved: Boolean? = null,
    /** Czy urządzenie było bazowe/zastane przed ruchem użytkownika */
    val followMeBaselineDevice: Boolean? = null,
    /** Łączny czas obserwacji w sekundach */
    val totalSeenDurationSeconds: Long = 0,
    // Watchlist
    /** Czy urządzenie jest na liście obserwowanych */
    val isInWatchlist: Boolean = false,
    /** Czy to "Safe Beacon" (trigger Smart Home) */
    val isSafeBeacon: Boolean = false,
    /** Alias nadany przez użytkownika */
    val userAlias: String? = null,
    /** Notatki użytkownika */
    val userNotes: String? = null,
    // Konfiguracja Alertów
    /** Czy odtwarzać dźwięk przy wykryciu */
    val alertSound: Boolean = false,
    /** Czy wibrować przy wykryciu */
    val alertVibration: Boolean = false,
    /** Czy śledzenie (alerty) dla tego urządzenia jest aktywne */
    val isTrackingEnabled: Boolean = true,
    /** Czy urządzenie jest ignorowane w algorytmie Follow-Me (ręczny whitelist) */
    val isIgnoredForTracking: Boolean = false,
    /** Etykieta użytkownika do kalibracji realnych false/true positive. */
    val calibrationLabel: DeviceCalibrationLabel = DeviceCalibrationLabel.UNKNOWN,
    /** Osobna decyzja użytkownika o tym, czy identity carryover / merge MAC był trafny. */
    val identityCarryoverVerdict: IdentityCarryoverVerdict = IdentityCarryoverVerdict.UNREVIEWED,
    /** Czas ostatniego alertu powrotu urządzenia z watchlisty */
    val lastWatchlistReturnAlertAt: Long = 0,
    /** Jak długo urządzenie było offline przed ostatnim alertem powrotu */
    val lastWatchlistReturnOfflineDurationMs: Long = 0,
    // Metadane czasowe i sygnału
    /** Ostatnie zmierzone RSSI (siła sygnału) */
    val lastRssi: Int = 0,
    /** Timestamp pierwszego wykrycia */
    val firstSeenAt: Long,
    /** Timestamp ostatniego wykrycia */
    val lastSeenAt: Long,
    /** Liczba spotkań (sesji skanowania) */
    val encounterCount: Int = 1,
    // Dane z Sensorów (Broadcast)
    /** Zdekodowane dane z sensora (np. Temp: 20C, Bat: 100%) */
    val sensorData: String? = null,
    /** Moc nadawcza (z pakietu reklamowego) - kluczowa do obliczenia dystansu */
    val txPower: Int? = null,
    /** Czy urządzenie jest "Connectable" (czy można kliknąć połącz) */
    val isConnectable: Boolean? = null,
    // Advanced Radio Info
    /** Primary PHY (1M, Coded) */
    val primaryPhy: Int? = null,
    /** Secondary PHY (2M, Coded, etc.) */
    val secondaryPhy: Int? = null,
    /** Szacowany interwał reklamowania w ms */
    val advertisingIntervalMs: Long? = null,
    /** Typ beacona (iBeacon, Eddystone, AltBeacon) */
    val beaconType: String? = null,
    // Active Reconnaissance (GATT Probing)
    /** Status procedury łączenia (NONE, PROBED, FAILED) */
    val connectionStatus: String = "NONE",
    /** Liczba prób połączenia */
    val connectionAttempts: Int = 0,
    /** Czas ostatniej próby */
    val lastProbeTimestamp: Long = 0,
    // Dane wyciągnięte z Device Info Service (0x180A)
    val modelNumber: String? = null,
    val serialNumber: String? = null,
    val firmwareRevision: String? = null,
    val hardwareRevision: String? = null,
    val softwareRevision: String? = null,
    val manufacturerName: String? = null,
    // Dane z Battery Service (0x180F)
    val batteryLevel: Int? = null,
    // Lista znalezionych usług (UUID po przecinku)
    val gattServices: String? = null,
    /** Dane wszystkich charakterystyk: UUID=HexValue */
    val characteristicData: String? = null,
    /** Ostatni surowy pakiet reklamowy (Hex String) */
    val lastRawData: String? = null,
    /** Błąd ostatniej próby połączenia */
    val probeError: String? = null,
    /** Czy urządzenie jest sparowane w systemie Android */
    val isPaired: Boolean = false,
)
