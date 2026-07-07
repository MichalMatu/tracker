package io.blueeye.core.di

/**
 * Field MVP intentionally detaches smart-home decoders from the production
 * BleBeaconDecoder multibinding. The parser source stays available for future
 * tests, but runtime evidence is limited to tracker, beacon, audio, and
 * public-safety-like signals.
 */
abstract class SmartHomeDecodersModule
