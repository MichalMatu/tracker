package io.blueeye.core.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// Layer 2: Material 3 (Standard Wrapper)
private val DarkColorScheme =
    darkColorScheme(
        primary = BlueEyePrimaryContainer,
        onPrimary = BlueEyeOnPrimaryContainer,
        primaryContainer = BlueEyePrimary, // Inverted for dark? keeping standard mapping
        onPrimaryContainer = BlueEyeOnPrimary,
        secondary = BlueEyeSecondaryContainer,
        onSecondary = BlueEyeOnSecondaryContainer,
        tertiary = BlueEyeTertiaryContainer,
        error = BlueEyeError
    )

private val LightColorScheme =
    lightColorScheme(
        primary = BlueEyePrimary,
        onPrimary = BlueEyeOnPrimary,
        primaryContainer = BlueEyePrimaryContainer,
        onPrimaryContainer = BlueEyeOnPrimaryContainer,
        secondary = BlueEyeSecondary,
        onSecondary = BlueEyeOnSecondary,
        tertiary = BlueEyeTertiary,
        background = BlueEyeBackground,
        surface = BlueEyeSurface,
        onBackground = BlueEyeOnBackground,
        onSurface = BlueEyeOnSurface,
        error = BlueEyeError
    )

// Alternative Material Schemes
private val TacticalColorScheme =
    darkColorScheme(
        primary = TacticalPrimary,
        onPrimary = TacticalOnPrimary,
        primaryContainer = TacticalPrimaryContainer,
        onPrimaryContainer = TacticalOnPrimaryContainer,
        secondary = TacticalSecondary,
        background = TacticalBackground,
        surface = TacticalSurface,
        error = BlueEyeError
    )

private val MidnightColorScheme =
    darkColorScheme(
        primary = MidnightPrimary,
        onPrimary = MidnightOnPrimary,
        primaryContainer = MidnightPrimaryContainer,
        onPrimaryContainer = MidnightOnPrimaryContainer,
        secondary = MidnightSecondary,
        background = MidnightBackground,
        surface = MidnightSurface,
        error = BlueEyeError
    )

private val ForestColorScheme =
    darkColorScheme(
        primary = ForestPrimary,
        onPrimary = ForestOnPrimary,
        primaryContainer = ForestPrimaryContainer,
        onPrimaryContainer = ForestOnPrimaryContainer,
        secondary = ForestSecondary,
        background = ForestBackground,
        surface = ForestSurface,
        error = BlueEyeError
    )

// Layer 3: Extended (Domain Specific & Chat Semantics)
@Immutable
data class ExtendedColors(
    // Domain State Colors
    val dangerous: Color,
    val safe: Color,
    val suspicious: Color,
    val warning: Color,
    // Chat Agent Semantics
    val userBubbleContainer: Color,
    val onUserBubble: Color,
    val agentBubbleContainer: Color,
    val onAgentBubble: Color
)

val LocalExtendedColors =
    staticCompositionLocalOf<ExtendedColors> {
        error("LocalExtendedColors not present. Did you forget to wrap your composable in BlueEyeTheme?")
    }

val MaterialTheme.extendedColors: ExtendedColors
    @Composable
    get() = LocalExtendedColors.current

// Define instances for each theme (Allocated ONCE for performance)
private val LightExtendedColors =
    ExtendedColors(
        dangerous = StatusDangerous,
        safe = StatusSafe,
        suspicious = StatusSuspicious,
        warning = StatusWarning,
        userBubbleContainer = BlueEyePrimary,
        onUserBubble = BlueEyeOnPrimary,
        agentBubbleContainer = AgentBubbleLight,
        onAgentBubble = AgentBubbleOnLight
    )

private val DarkExtendedColors =
    ExtendedColors(
        dangerous = StatusDangerous,
        safe = StatusSafe,
        suspicious = StatusSuspicious,
        warning = StatusWarning,
        userBubbleContainer = BlueEyePrimaryContainer,
        onUserBubble = BlueEyeOnPrimaryContainer,
        agentBubbleContainer = AgentBubbleDark,
        onAgentBubble = AgentBubbleOnDark
    )

private val ForestExtendedColors =
    ExtendedColors(
        dangerous = StatusDangerous,
        safe = StatusSafe,
        suspicious = StatusSuspicious,
        warning = StatusWarning,
        userBubbleContainer = ForestPrimaryContainer,
        onUserBubble = ForestOnPrimaryContainer,
        agentBubbleContainer = AgentBubbleForest,
        onAgentBubble = AgentBubbleOnForest
    )

@Composable
fun BlueEyeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    colorSchemeName: String = "Classic",
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current

    // Efficient Selection of Scheme + Extended Colors
    val (colorScheme, extendedColors) =
        when {
            dynamicColor && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S -> {
                val scheme =
                    if (darkTheme) {
                        androidx.compose.material3.dynamicDarkColorScheme(context)
                    } else {
                        androidx.compose.material3.dynamicLightColorScheme(context)
                    }
                scheme to if (darkTheme) DarkExtendedColors else LightExtendedColors
            }
            else -> {
                when (colorSchemeName) {
                    "Tactical" -> TacticalColorScheme to DarkExtendedColors // Tactical shares Dark Semantics
                    "Midnight" -> MidnightColorScheme to DarkExtendedColors
                    "Forest" -> ForestColorScheme to ForestExtendedColors // Forest has custom chat colors
                    else -> {
                        if (darkTheme) {
                            DarkColorScheme to DarkExtendedColors
                        } else {
                            LightColorScheme to LightExtendedColors
                        }
                    }
                }
            }
        }

    // Strict Nesting
    CompositionLocalProvider(
        LocalExtendedColors provides extendedColors
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}

/**
 * Helper to get the primary color of a theme for UI previews.
 */
fun getThemePrimaryColor(
    schemeName: String,
    isDark: Boolean
): Color {
    return when (schemeName) {
        "Tactical" -> TacticalPrimary
        "Midnight" -> MidnightPrimary
        "Forest" -> ForestPrimary
        "Classic" -> if (isDark) BlueEyePrimaryContainer else BlueEyePrimary
        else -> if (isDark) BlueEyePrimaryContainer else BlueEyePrimary
    }
}
