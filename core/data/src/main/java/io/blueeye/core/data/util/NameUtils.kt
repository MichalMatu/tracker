package io.blueeye.core.data.util

/**
 * Utility functions for device name processing.
 * Centralizes "generic name" detection to avoid drift between components.
 */
object NameUtils {
    
    private val GENERIC_NAMES = setOf(
        "find my",
        "apple inc",
        "apple, inc",
        "apple, inc.",
        "apple device",
        "apple, inc. device",
        "apple, inc device",
        "unknown",
        "ble device",
        "tracker",
        "le device",
        "smart beacon",
        "beacon",
        "n/a",
        ""
    )

    /**
     * Checks if a device name is considered "generic" (manufacturer placeholder, not user-meaningful).
     * Generic names should be overwritten by more specific names when available.
     */
    fun isGenericName(name: String?): Boolean {
        if (name.isNullOrBlank()) return true
        return name.lowercase().trim() in GENERIC_NAMES
    }

    /**
     * Resolves the best name between current and candidate.
     * Prefers specific names over generic ones.
     */
    fun resolveBestName(current: String?, candidate: String?): String? {
        if (candidate.isNullOrBlank()) return current
        if (current.isNullOrBlank()) return candidate
        
        // If candidate is generic, DON'T overwrite a specific current name
        if (isGenericName(candidate) && !isGenericName(current)) {
            return current
        }
        
        return candidate
    }
}
