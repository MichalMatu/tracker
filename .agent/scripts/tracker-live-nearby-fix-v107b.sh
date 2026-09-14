#!/bin/bash
set -eu

python3 - <<'PY'
from pathlib import Path
import re

p = Path('feature/radar/src/test/java/io/blueeye/feature/radar/presentation/LiveNearbyAssemblerTest.kt')
s = p.read_text()

s, n1 = re.subn(
    r'signalInfo\s*=\s*RadarUiSignalInfo\(.*?\),\s*statusInfo',
    '''signalInfo = RadarUiSignalInfo(
                rssi = rssi,
                rssiText = "$rssi dBm",
                signalColor = RadarUiColorToken.PRIMARY,
                signalProgress = 0,
                distanceText = "",
                techBadge = "BLE",
                techBadgeColor = RadarUiColorToken.PRIMARY,
                timeSinceSeen = "",
            ),
            statusInfo''',
    s,
    count=1,
    flags=re.S,
)
s, n2 = re.subn(
    r'statusInfo\s*=\s*RadarUiStatusInfo\(.*?\),\s*icons',
    '''statusInfo = RadarUiStatusInfo(
                text = "Safe",
                textColor = RadarUiColorToken.SAFE,
                backgroundTint = RadarUiColorToken.SAFE,
                isWarning = status != TrackingStatus.SAFE,
                cardBackgroundColor = null,
            ),
            icons''',
    s,
    count=1,
    flags=re.S,
)
s, n3 = re.subn(
    r'icons\s*=\s*RadarUiIcons\(.*?\),\s*isNew',
    '''icons = RadarUiIcons(
                mainIconRes = 0,
                isConnectable = false,
            ),
            isNew''',
    s,
    count=1,
    flags=re.S,
)

if (n1, n2, n3) != (1, 1, 1):
    raise SystemExit(f'constructor replacements failed: {(n1, n2, n3)}')

p.write_text(s)
PY

export JAVA_HOME=$(/usr/libexec/java_home -v 21)
export PATH="$JAVA_HOME/bin:$PATH"
./gradlew --no-daemon :feature:radar:detekt --auto-correct || true
git diff --check
