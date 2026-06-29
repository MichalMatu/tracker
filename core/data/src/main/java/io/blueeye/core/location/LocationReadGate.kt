package io.blueeye.core.location

internal class LocationReadGate(
    private val minIntervalMs: Long,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private var lastReadAtMs: Long? = null

    @Synchronized
    fun shouldReadProviders(): Boolean {
        val now = clock()
        val previousReadAt = lastReadAtMs
        val shouldRead =
            previousReadAt == null ||
                now < previousReadAt ||
                now - previousReadAt >= minIntervalMs

        if (shouldRead) {
            lastReadAtMs = now
        }

        return shouldRead
    }
}
