package io.blueeye.core.data.classifier.vendor.tactical

/**
 * Tactical device category.
 */
enum class TacticalCategory {
    /** Body-Worn Cameras (Axon, Reveal, Wolfcom, Motorola V300/V700) */
    BODY_CAMERA,

    /** Holster sensors (Signal Sidearm, Yardarm, Holster Aware) */
    HOLSTER_SENSOR,

    /** Professional audio/comms equipment (Invisio, Silynx, Peltor, Sordin) */
    TACTICAL_AUDIO,

    /** Professional/public-safety radios (APX, TETRA, Sepura SC20, Hytera) */
    TACTICAL_RADIO,

    /** Connected ballistic or firearm-adjacent accessories (Sig BDX, Radetec Smart Slide) */
    SMART_WEAPON,

    /** Professional displays/EUDs (ATAK-compatible devices, Toughbook, MTN) */
    TACTICAL_EUD,

    /** Fire brigade and Emergency Medical Services (PSP, PRM, Dräger) */
    FIRE_EMS,

    /** General public-safety equipment */
    POLICE_EQUIPMENT,

    /** Vehicle routers and mobile infrastructure (Sierra, Cradlepoint) */
    VEHICLE_ROUTER,

    /** Document readers and border control (Regula, ARH) */
    DOCUMENT_READER,

    /** SCBA and firefighter telemetry (Dräger PSS Merlin, MSA) */
    FIREFIGHTER
}
