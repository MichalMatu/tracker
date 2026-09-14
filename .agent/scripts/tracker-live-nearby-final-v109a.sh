#!/bin/bash
set -euo pipefail

PACKAGE="io.blueeye"
ACTIVITY="io.blueeye/.MainActivity"
UI_XML="/tmp/tracker-live-nearby-final-v109a.xml"
APK="app/build/outputs/apk/debug/app-debug.apk"
EXPECTED_START="2b43c7dcdad98e810fca233a8a9b1e098f8b5329"

export JAVA_HOME=$(/usr/libexec/java_home -v 21)
export PATH="$JAVA_HOME/bin:$PATH"

git fetch origin
git checkout main
git pull --ff-only origin main
test -z "$(git status --porcelain)"
BASE=$(git rev-parse HEAD)
test "$BASE" = "$EXPECTED_START"

python3 <<'PY'
from pathlib import Path
p=Path('feature/radar/src/main/java/io/blueeye/feature/radar/presentation/ProtocolNoiseGrouping.kt')
s=p.read_text()
old='''    fun mustStayStandalone(item: RadarUiItem): Boolean =\n        item.isInWatchlist ||\n            item.hasUserAlias ||\n            item.trackingStatus != TrackingStatus.SAFE ||\n            item.followingScore >= ATTENTION_SCORE_THRESHOLD ||\n            item.calibrationLabel in ATTENTION_LABELS ||\n            item.evidenceSignals.hasAttentionEvidence\n'''
new='''    fun mustStayStandalone(item: RadarUiItem): Boolean {\n        val hasNonTrackerAttentionEvidence =\n            item.evidenceSignals.hasAttentionFollowMeEvidence ||\n                item.evidenceSignals.hasPublicSafetyLikeEvidence ||\n                (item.evidenceSignals.hasAttentionEvidence && !item.evidenceSignals.hasTrackerLikeEvidence)\n\n        return item.isInWatchlist ||\n            item.hasUserAlias ||\n            item.trackingStatus != TrackingStatus.SAFE ||\n            item.followingScore >= ATTENTION_SCORE_THRESHOLD ||\n            item.calibrationLabel in ATTENTION_LABELS ||\n            hasNonTrackerAttentionEvidence\n    }\n'''
if old not in s:
    raise SystemExit('ProtocolNoiseGrouping target not found')
p.write_text(s.replace(old,new,1))

p=Path('feature/radar/src/test/java/io/blueeye/feature/radar/presentation/RadarProtocolGroupMapperTest.kt')
s=p.read_text()
anchor='''    @Test\n    fun `suspicious identity stays standalone instead of being hidden in group`() {\n'''
test='''    @Test\n    fun `tracker classification attention alone does not escape protocol group`() {\n        val now = 550_000L\n        val first = item("first", -55, now, "Google Find Hub")\n        val second = item("second", -65, now, "Google Find Hub")\n        val trackerAttention = RadarEvidenceSignals(false, true, false, true, false)\n        val entries =\n            RadarProtocolGroupMapper.map(\n                RadarUiSection(\n                    RadarUiSectionType.NEARBY,\n                    listOf(\n                        first.copy(evidenceSignals = trackerAttention),\n                        second.copy(evidenceSignals = trackerAttention),\n                    ),\n                ),\n                now,\n            )\n\n        assertEquals(1, entries.filterIsInstance<RadarProtocolEntry.Group>().size)\n        assertEquals(0, entries.filterIsInstance<RadarProtocolEntry.Device>().size)\n    }\n\n'''
if anchor not in s:
    raise SystemExit('test anchor not found')
p.write_text(s.replace(anchor,test+anchor,1))
PY

git diff --check
./gradlew --no-daemon :feature:radar:testDebugUnitTest :feature:radar:detekt :app:assembleDebug

git fetch origin
REMOTE=$(git rev-parse origin/main)
if [ "$REMOTE" != "$BASE" ]; then
  CHANGED=$(git diff --name-only "$BASE..$REMOTE")
  if echo "$CHANGED" | grep -Ev '^docs/' | grep -q .; then
    echo "MAIN_MOVED_WITH_CODE_CHANGES"
    echo "$CHANGED"
    exit 1
  fi
  git rebase origin/main
fi

git add feature/radar/src/main/java/io/blueeye/feature/radar/presentation/ProtocolNoiseGrouping.kt feature/radar/src/test/java/io/blueeye/feature/radar/presentation/RadarProtocolGroupMapperTest.kt
git commit -m "Keep tracker-only protocol noise grouped"
git push origin main
PUSHED=$(git rev-parse HEAD)
echo "PUSHED_HEAD=$PUSHED"
test -z "$(git status --porcelain)"

DEVICE_COUNT=$(adb devices | awk 'NR>1 && $2=="device" {c++} END {print c+0}')
test "$DEVICE_COUNT" -eq 1
MODEL=$(adb shell getprop ro.product.model | tr -d '\r')
test "$MODEL" = "SM-S906B"
adb install -r "$APK" >/tmp/tracker-v109a-install.log
grep -q 'Success' /tmp/tracker-v109a-install.log
echo "INSTALL_OK=yes"

OLD_TIMEOUT=$(adb shell settings get system screen_off_timeout | tr -d '\r')
restore_timeout() { adb shell settings put system screen_off_timeout "$OLD_TIMEOUT" >/dev/null 2>&1 || true; }
trap restore_timeout EXIT
adb shell settings put system screen_off_timeout 600000 >/dev/null

dump_ui() {
  adb shell uiautomator dump /sdcard/tracker-live-nearby-final-v109a.xml >/dev/null
  adb pull /sdcard/tracker-live-nearby-final-v109a.xml "$UI_XML" >/dev/null 2>&1
}
coords_for() {
  python3 - "$UI_XML" "$1" "$2" <<'PY'
import re,sys,xml.etree.ElementTree as ET
root=ET.parse(sys.argv[1]).getroot(); attr=sys.argv[2]; value=sys.argv[3]
for n in root.iter('node'):
    if n.attrib.get(attr)==value:
        m=re.match(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]',n.attrib.get('bounds',''))
        if m:
            x1,y1,x2,y2=map(int,m.groups()); print((x1+x2)//2,(y1+y2)//2); raise SystemExit(0)
raise SystemExit(1)
PY
}
tap_exact() { local c; c=$(coords_for "$1" "$2"); adb shell input tap $c >/dev/null; }

adb shell am start -W -n "$ACTIVITY" >/dev/null
sleep 2
dump_ui
if coords_for content-desc Menu >/dev/null 2>&1; then tap_exact content-desc Menu; else tap_exact content-desc "Open menu"; fi
sleep 1
dump_ui
tap_exact text "Live Nearby"
sleep 2
dump_ui
if coords_for text Scan >/dev/null 2>&1; then tap_exact text Scan; sleep 3; fi

echo "FINAL_SCAN_SECONDS=60"
sleep 60
adb shell input keyevent KEYCODE_WAKEUP >/dev/null 2>&1 || true
dump_ui

FOUND=0
for i in $(seq 1 18); do
  if python3 - "$UI_XML" <<'PY'
import re,sys,xml.etree.ElementTree as ET
root=ET.parse(sys.argv[1]).getroot()
texts=[n.attrib.get('text','').strip() for n in root.iter('node') if n.attrib.get('text','').strip()]
if 'Google Find Hub' not in texts: raise SystemExit(1)
summary=next((t for t in texts if re.match(r'^\d+ active now · \d+ recent · \d+ identities seen in last 3 min$',t)),None)
if not summary: raise SystemExit(1)
print('FHN_GROUP_VISIBLE=yes')
print('FHN_GROUP_SUMMARY='+summary)
raise SystemExit(0)
PY
  then FOUND=1; break; fi
  adb shell input swipe 540 1850 540 650 250 >/dev/null
  sleep 0.5
  dump_ui
done

test "$FOUND" -eq 1
echo "LIVE_NEARBY_FINAL_VALIDATION=passed"
