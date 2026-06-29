package io.blueeye.feature.radar.presentation

/**
 * Semantic color tokens for Radar UI.
 * Decouples ViewModel from specific UI colors (Theme Layer 3 compliance).
 */
enum class RadarUiColorToken {
    PRIMARY,
    SECONDARY,
    TERTIARY,

    // Domain Semantics
    DANGEROUS,
    DANGEROUS_CONTAINER,
    SAFE,
    SAFE_CONTAINER,
    SUSPICIOUS,
    SUSPICIOUS_CONTAINER,
    WARNING,

    // Neutrals
    SURFACE,
    ON_SURFACE,
    ON_SURFACE_VARIANT,
    OUTLINE,
    WHITE,
    GRAY,
    TRANSPARENT
}
