package io.blueeye.navigation

import kotlinx.serialization.Serializable

/**
 * Type-safe definition of application screens.
 */
sealed interface Screen {
    @Serializable
    data object Radar : Screen

    @Serializable
    data class Details(val deviceId: String) : Screen

    @Serializable
    data object Settings : Screen

    @Serializable
    data object Watchlist : Screen
}
