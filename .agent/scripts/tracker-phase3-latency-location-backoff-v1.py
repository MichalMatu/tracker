from pathlib import Path


def replace_once(path: Path, old: str, new: str) -> None:
    text = path.read_text()
    if text.count(old) != 1:
        raise SystemExit(f"expected exactly one match in {path}: {text.count(old)}")
    path.write_text(text.replace(old, new, 1))


path = Path("core/data/src/main/java/io/blueeye/core/location/LocationProvider.kt")
replace_once(
    path,
    '''    private val readGate = LocationReadGate(PROVIDER_READ_THROTTLE_MS)
''',
    '''    private val readGate = LocationReadGate(PROVIDER_READ_THROTTLE_MS)
    private val activeFixGate = LocationReadGate(ACTIVE_FIX_RETRY_BACKOFF_MS)
''',
)
replace_once(
    path,
    '''        val location =
            activeFixMutex.withLock {
                cachedLocation?.takeIf(::isActiveFixFresh)
                    ?: requestActiveLocation()
                    ?: getLastLocation()
            }
''',
    '''        val location =
            activeFixMutex.withLock {
                cachedLocation?.takeIf(::isActiveFixFresh)
                    ?: requestActiveLocationIfDue()
                    ?: getLastLocation()
            }
''',
)
replace_once(
    path,
    '''    @SuppressLint("MissingPermission")
    private suspend fun requestActiveLocation(): Location? {
''',
    '''    private suspend fun requestActiveLocationIfDue(): Location? =
        if (activeFixGate.shouldReadProviders()) requestActiveLocation() else null

    @SuppressLint("MissingPermission")
    private suspend fun requestActiveLocation(): Location? {
''',
)
replace_once(
    path,
    '''        private const val ACTIVE_FIX_REUSE_MS = 10_000L
        private const val ACTIVE_FIX_TIMEOUT_MS = 2_000L
''',
    '''        private const val ACTIVE_FIX_REUSE_MS = 10_000L
        private const val ACTIVE_FIX_RETRY_BACKOFF_MS = 10_000L
        private const val ACTIVE_FIX_TIMEOUT_MS = 2_000L
''',
)
