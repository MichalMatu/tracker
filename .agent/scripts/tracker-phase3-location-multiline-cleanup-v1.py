from pathlib import Path

path = Path('core/data/src/main/java/io/blueeye/core/location/LocationProvider.kt')
text = path.read_text()
old = '''                cachedLocation?.takeIf(::isActiveFixFresh)
                    ?: if (activeFixGate.shouldReadProviders()) requestActiveLocation() else null
                    ?: getLastLocation()
'''
new = '''                cachedLocation?.takeIf(::isActiveFixFresh)
                    ?: if (activeFixGate.shouldReadProviders()) {
                        requestActiveLocation()
                    } else {
                        null
                    }
                    ?: getLastLocation()
'''
if old not in text:
    raise SystemExit('expected LocationProvider expression not found')
path.write_text(text.replace(old, new, 1))
