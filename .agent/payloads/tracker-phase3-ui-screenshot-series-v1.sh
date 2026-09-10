#!/usr/bin/env bash
set -euo pipefail

EXPECTED='53cccf4fa39525febf54e0a57f65796efbd51812'
EXPECTED_INSTALLED_SHA='3b88a37485cf3a500af8d2e33b38819370b0fb58a5fca8bd9e940e7aea10d8ca'
PKG='io.blueeye'
OUT='/tmp/tracker-phase3-ui-screenshot-series-v1'
rm -rf "$OUT"
mkdir -p "$OUT/screens" "$OUT/dumps"

cleanup() {
  if [ -n "${SERIAL:-}" ]; then
    adb -s "$SERIAL" shell svc power stayon false >/dev/null 2>&1 || true
  fi
}
trap cleanup EXIT

git fetch --no-tags origin main >/dev/null
HEAD="$(git rev-parse origin/main)"
echo "source_head=$HEAD"
test "$HEAD" = "$EXPECTED"

SERIAL="$(adb devices | awk 'NR>1 && $2=="device" {print $1}' | head -n1)"
test -n "$SERIAL"
echo "serial=$SERIAL"
test "$(adb -s "$SERIAL" shell am get-current-user | tr -d '\r')" = '0'
adb -s "$SERIAL" shell input keyevent KEYCODE_WAKEUP >/dev/null 2>&1 || true
adb -s "$SERIAL" shell wm dismiss-keyguard >/dev/null 2>&1 || true
adb -s "$SERIAL" shell svc power stayon usb >/dev/null 2>&1 || true

CURRENT_PATH="$(adb -s "$SERIAL" shell pm path --user 0 "$PKG" | tr -d '\r' | sed -n 's/^package://p' | head -n1)"
test -n "$CURRENT_PATH"
adb -s "$SERIAL" pull "$CURRENT_PATH" "$OUT/installed.apk" >/dev/null
INSTALLED_SHA="$(shasum -a 256 "$OUT/installed.apk" | awk '{print $1}')"
echo "installed_apk_sha256=$INSTALLED_SHA"
test "$INSTALLED_SHA" = "$EXPECTED_INSTALLED_SHA"

XML="$OUT/window.xml"
dump_ui() {
  local dest="$1"
  adb -s "$SERIAL" shell uiautomator dump /sdcard/blueeye-screenshot-series.xml >/dev/null
  adb -s "$SERIAL" exec-out cat /sdcard/blueeye-screenshot-series.xml > "$dest"
}
coords() {
  dump_ui "$XML"
  python3 tools/ui-smoke/ui_node.py "$XML" "$1" "$2"
}
tap_node() {
  local c
  c="$(coords "$1" "$2")"
  test -n "$c"
  set -- $c
  adb -s "$SERIAL" shell input tap "$1" "$2"
  sleep 1
}
ensure_root() {
  adb -s "$SERIAL" shell am start -W --user 0 -n "$PKG/.MainActivity" >/dev/null
  sleep 1
  for _ in 1 2 3 4 5; do
    if coords content-desc Menu >/dev/null 2>&1; then return 0; fi
    adb -s "$SERIAL" shell input keyevent BACK >/dev/null 2>&1 || true
    sleep 0.5
  done
  coords content-desc Menu >/dev/null
}
open_drawer_item() {
  local label="$1"
  ensure_root
  tap_node content-desc Menu
  tap_node text "$label"
  sleep 1
}

capture_series() {
  local label="$1"
  local count="$2"
  local delay="$3"
  echo "capture_start=$label count=$count delay=$delay"
  for i in $(seq 1 "$count"); do
    printf -v n '%03d' "$i"
    adb -s "$SERIAL" exec-out screencap -p > "$OUT/screens/$label-$n.png"
    dump_ui "$OUT/dumps/$label-$n.xml"
    sleep "$delay"
  done
  echo "capture_done=$label"
}

# Radar with live scanner.
open_drawer_item Radar
if ! adb -s "$SERIAL" shell dumpsys activity services "$PKG" | grep -q 'ScannerService'; then
  tap_node content-desc Scan
  sleep 6
fi
adb -s "$SERIAL" shell dumpsys activity services "$PKG" | grep -q 'ScannerService'
echo 'scanner_service=present'
capture_series radar 16 0.35

# Open one live device card and capture top + scrolled sections.
if coords content-desc Details >/dev/null 2>&1; then
  tap_node content-desc Details
  sleep 2
  capture_series details-top 12 0.35
  adb -s "$SERIAL" shell input swipe 540 1700 540 650 350
  sleep 1
  capture_series details-mid 12 0.35
  adb -s "$SERIAL" shell input swipe 540 1700 540 650 350
  sleep 1
  capture_series details-low 12 0.35
  echo 'details_series=complete'
else
  echo 'details_series=skipped_no_visible_device'
fi

# Watchlist.
open_drawer_item Watchlist
capture_series watchlist 14 0.35

# Settings landing plus the live diagnostics card when reachable.
open_drawer_item Settings
capture_series settings-home 8 0.35
if coords text 'Alerts & Collection' >/dev/null 2>&1; then
  tap_node text 'Alerts & Collection'
  sleep 1
  for _ in 1 2 3 4 5 6 7; do
    if coords text 'Runtime profile' >/dev/null 2>&1; then break; fi
    adb -s "$SERIAL" shell input swipe 540 1700 540 550 300
    sleep 0.5
  done
  capture_series settings-live 14 0.35
fi

python3 - "$OUT" <<'PY'
import glob, os, re, statistics, sys, xml.etree.ElementTree as ET
from collections import defaultdict

root=sys.argv[1]
series=defaultdict(list)
for path in glob.glob(os.path.join(root,'dumps','*.xml')):
    base=os.path.basename(path)
    label=re.sub(r'-\d{3}\.xml$','',base)
    series[label].append(path)

bounds_re=re.compile(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]')

def geometry_report(label, paths):
    paths=sorted(paths)
    frames=[]
    for path in paths:
        tree=ET.parse(path)
        frame=defaultdict(list)
        for n in tree.iter('node'):
            b=bounds_re.match(n.attrib.get('bounds',''))
            if not b:
                continue
            rect=tuple(map(int,b.groups()))
            for attr in ('text','content-desc'):
                value=n.attrib.get(attr,'').strip()
                if value:
                    frame[(attr,value)].append(rect)
        for k in frame:
            frame[k].sort()
        frames.append(frame)

    tracked=[]
    keys=set().union(*(f.keys() for f in frames)) if frames else set()
    min_frames=max(3, int(len(frames)*0.75))
    for key in keys:
        present=[f[key] for f in frames if key in f]
        if len(present) < min_frames:
            continue
        counts=[len(v) for v in present]
        if len(set(counts)) != 1:
            continue
        for idx in range(counts[0]):
            rects=[v[idx] for v in present]
            xs=[(r[0]+r[2])//2 for r in rects]
            ys=[(r[1]+r[3])//2 for r in rects]
            ws=[r[2]-r[0] for r in rects]
            hs=[r[3]-r[1] for r in rects]
            spread=max(max(xs)-min(xs), max(ys)-min(ys), max(ws)-min(ws), max(hs)-min(hs))
            tracked.append((spread,key,idx,len(rects)))
    tracked.sort(reverse=True, key=lambda x:x[0])
    moving=[x for x in tracked if x[0] > 3]
    print(f'GEOMETRY {label}: frames={len(frames)} persistent_nodes={len(tracked)} moving_gt3px={len(moving)} max_spread_px={(tracked[0][0] if tracked else -1)}')
    for spread,key,idx,n in moving[:8]:
        print(f'  MOVE spread={spread}px attr={key[0]} value={key[1]!r} occurrence={idx} frames={n}')

for label,paths in sorted(series.items()):
    geometry_report(label,paths)

# Screenshot pixel-diff summary. This intentionally measures visual churn, not just hierarchy movement.
try:
    from PIL import Image, ImageChops
except Exception as exc:
    print(f'PIXEL_ANALYSIS_SKIPPED={type(exc).__name__}:{exc}')
else:
    for label in sorted(series):
        paths=sorted(glob.glob(os.path.join(root,'screens',label+'-*.png')))
        ratios=[]
        boxes=[]
        for a,b in zip(paths,paths[1:]):
            ia=Image.open(a).convert('RGB')
            ib=Image.open(b).convert('RGB')
            if ia.size != ib.size:
                continue
            diff=ImageChops.difference(ia,ib).convert('L')
            # Ignore tiny antialiasing/noise. Count pixels with visible luminance delta >= 16.
            mask=diff.point(lambda p: 255 if p >= 16 else 0)
            hist=mask.histogram()
            changed=hist[255]
            total=ia.size[0]*ia.size[1]
            ratios.append(changed/total)
            boxes.append(mask.getbbox())
        if ratios:
            print(f'PIXEL {label}: pairs={len(ratios)} avg_changed_pct={statistics.mean(ratios)*100:.4f} max_changed_pct={max(ratios)*100:.4f}')
            print(f'  max_diff_bbox={boxes[ratios.index(max(ratios))]}')

print(f'screenshot_dir={root}/screens')
print(f'xml_dir={root}/dumps')
print('UI_SCREENSHOT_SERIES_CAPTURE_PASS')
PY

find "$OUT/screens" -type f -name '*.png' | wc -l | awk '{print "screenshot_count="$1}'
find "$OUT/dumps" -type f -name '*.xml' | wc -l | awk '{print "xml_count="$1}'
echo 'PHASE3_UI_SCREENSHOT_SERIES_PASS'
