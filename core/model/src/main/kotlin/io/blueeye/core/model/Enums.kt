package io.blueeye.core.model

/** Typ urządzenia Bluetooth rozpoznany przez aplikację. */
enum class DeviceType {
    AIRTAG,
    TILE,
    SAMSUNG_TAG,
    TAG,
    CAR_AUDIO,
    HEADPHONES,
    PHONE,
    LAPTOP,
    TV,
    PC,
    CONSOLE,
    BEACON,
    WEARABLE,
    WATCH,
    TABLET,
    SPEAKER,
    CAR,
    POLICE,
    POS,
    ACCESS_CONTROL,
    SMART_HOME,
    MEDICAL,
    DRONE,
    FITNESS,
    GAMING,
    CAMERA,
    PRINTER,
    TACHOGRAPH,
    AXON,
    BODY_CAMERA,
    TACTICAL_AUDIO,
    HOLSTER_SENSOR,
    SMART_WEAPON,
    TACTICAL_RADIO,
    TACTICAL_EUD,
    AUDIO,
    PERIPHERAL,
    SENSOR,
    VEHICLE_ROUTER,
    DOCUMENT_READER,
    FIREFIGHTER,
    TRACKER,
    AUDIO_VIDEO,
    TACTICAL,
    UNKNOWN,
}

/** Status zagrożenia przypisany do urządzenia przez algorytm analizy. */
enum class TrackingStatus {
    SAFE,
    SUSPICIOUS,
    DANGEROUS,
}

/** User-provided calibration label for real-world review and export. */
enum class DeviceCalibrationLabel {
    TRUE_POSITIVE,
    FALSE_POSITIVE,
    KNOWN_SAFE,
    UNKNOWN,
    SUSPICIOUS,
}

/** User verdict for a MAC/random-address identity carryover match. */
enum class IdentityCarryoverVerdict {
    UNREVIEWED,
    CONFIRMED_SAME_DEVICE,
    FALSE_MATCH,
    INCONCLUSIVE,
}

/** Typ adresu MAC (publiczny vs losowy). */
enum class MacAddressType {
    PUBLIC,
    RANDOM,
    UNKNOWN,
}

/** Typ alertu dla urządzeń z Watchlist. */
enum class AlertType {
    ON_APPEAR,
    ON_DISAPPEAR,
    ALWAYS,
}
