package io.blueeye.feature.details

object DetailsFocusedScanPolicy {
    fun canStartFocusedScan(technology: String): Boolean = !technology.equals(CLASSIC_TECHNOLOGY, ignoreCase = true)

    private const val CLASSIC_TECHNOLOGY = "CLASSIC"
}
