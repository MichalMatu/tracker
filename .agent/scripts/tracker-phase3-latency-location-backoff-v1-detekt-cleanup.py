from pathlib import Path

path = Path("core/data/src/main/java/io/blueeye/core/location/LocationProvider.kt")
text = path.read_text()
old = '''                cachedLocation?.takeIf(::isActiveFixFresh)
                    ?: requestActiveLocationIfDue()
                    ?: getLastLocation()
'''
new = '''                cachedLocation?.takeIf(::isActiveFixFresh)
                    ?: if (activeFixGate.shouldReadProviders()) requestActiveLocation() else null
                    ?: getLastLocation()
'''
if text.count(old) != 1:
    raise SystemExit(f"expected exactly one active-fix call site: {text.count(old)}")
text = text.replace(old, new, 1)
old_helper = '''    private suspend fun requestActiveLocationIfDue(): Location? =
        if (activeFixGate.shouldReadProviders()) requestActiveLocation() else null

'''
if text.count(old_helper) != 1:
    raise SystemExit(f"expected exactly one active-fix helper: {text.count(old_helper)}")
path.write_text(text.replace(old_helper, "", 1))
