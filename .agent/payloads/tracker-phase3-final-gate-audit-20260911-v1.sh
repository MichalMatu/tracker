#!/usr/bin/env bash
set -euo pipefail
ROOT="$HOME/agent-workspace/repos/tracker/checkpoints/phase3-field-reacceptance-20260911"
STAMP="$(cat "$ROOT/LATEST")"
OUT="$ROOT/$STAMP"
test -s "$OUT/blueeye-session-export.json"
test -s "$OUT/room/tracker_database"
python3 - "$OUT" <<'PY'
import collections,datetime as dt,hashlib,json,os,re,shutil,sqlite3,sys,tempfile
from zoneinfo import ZoneInfo
out=sys.argv[1]
d=json.load(open(os.path.join(out,'blueeye-session-export.json')))
samples=sorted([x for x in d.get('signalSamples',[]) if int(x.get('timestamp') or 0)>0],key=lambda x:int(x['timestamp']))
start_i=0
for i in range(1,len(samples)):
    if int(samples[i]['timestamp'])-int(samples[i-1]['timestamp'])>15*60*1000:start_i=i
walk=samples[start_i:]
ws=int(walk[0]['timestamp']);we=int(walk[-1]['timestamp'])

def iso(ms,tz):return dt.datetime.fromtimestamp(ms/1000,dt.timezone.utc).astimezone(tz).isoformat()
warsaw=ZoneInfo('Europe/Warsaw')

# Permission state from the post-walk package snapshot, no live mutation/query required.
pkg=open(os.path.join(out,'adb-package.txt'),errors='replace').read()
def perm(name):
    m=re.search(r'android\.permission\.'+re.escape(name)+r':\s*granted=(true|false)',pkg,re.I)
    return None if not m else m.group(1).lower()=='true'
permissions={'fineLocation':perm('ACCESS_FINE_LOCATION'),'coarseLocation':perm('ACCESS_COARSE_LOCATION'),'backgroundLocation':perm('ACCESS_BACKGROUND_LOCATION'),'bluetoothScan':perm('BLUETOOTH_SCAN'),'bluetoothConnect':perm('BLUETOOTH_CONNECT')}

# Analyze Room only on a host duplicate, keeping collected tuple immutable.
with tempfile.TemporaryDirectory(prefix='phase3-final-room-') as td:
    for n in ('tracker_database','tracker_database-wal','tracker_database-shm'):
        p=os.path.join(out,'room',n)
        if os.path.exists(p):shutil.copy2(p,os.path.join(td,n))
    con=sqlite3.connect(os.path.join(td,'tracker_database'))
    con.row_factory=sqlite3.Row
    integrity=con.execute('pragma integrity_check').fetchone()[0]
    follow=[]
    try:follow=list(con.execute('select timestamp,userMoved,baselineDevice,trackingStatus,score,rssi from follow_me_observations where timestamp between ? and ? order by timestamp',(ws,we)))
    except Exception:pass
    candidates=[]
    try:candidates=list(con.execute('select timestamp,reasonCode,confidence,verdict from identity_continuity_candidates where timestamp between ? and ? order by timestamp',(ws,we)))
    except Exception:pass
    con.close()
move=collections.Counter('NULL' if r['userMoved'] is None else ('TRUE' if r['userMoved'] else 'FALSE') for r in follow)
baseline=collections.Counter('NULL' if r['baselineDevice'] is None else ('TRUE' if r['baselineDevice'] else 'FALSE') for r in follow)
status=collections.Counter(str(r['trackingStatus']) for r in follow)

# Process-death evidence, deduplicated across repeated log snapshots.
all_lines=[]
for n in ('logcat-before.txt','logcat-after-export.txt','logcat-final.txt'):
    p=os.path.join(out,n)
    if os.path.exists(p):all_lines.extend(open(p,errors='replace').read().splitlines())
unique=list(dict.fromkeys(all_lines))
kill=[]
for line in unique:
    low=line.lower()
    if 'io.blueeye' not in low:continue
    if not re.search(r'(killing|has died|am_kill|force stopping|lowmemory|lmkd)',line,re.I):continue
    m=re.match(r'^(\d\d)-(\d\d) (\d\d):(\d\d):(\d\d)\.(\d+)',line)
    ms=None
    if m:
        mo,da,hh,mm,ss,frac=m.groups();us=int((frac+'000000')[:6]);stamp=dt.datetime(2026,int(mo),int(da),int(hh),int(mm),int(ss),us,tzinfo=warsaw);ms=int(stamp.timestamp()*1000)
    cats=[]
    for cat,rx in [('force_stop',r'force stopping'),('low_memory',r'lowmemory|lmkd|low memory'),('cached',r'cached'),('empty',r'\bempty\b'),('crash',r'crash'),('died',r'has died'),('activity_manager_kill',r'killing|am_kill')]:
        if re.search(rx,line,re.I):cats.append(cat)
    kill.append({'timestampMs':ms,'deltaFromWalkEndMs':None if ms is None else ms-we,'categories':cats})
# Deduplicate same timestamp/category event.
kd=[];seen=set()
for x in kill:
    k=(x['timestampMs'],tuple(x['categories']))
    if k not in seen:seen.add(k);kd.append(x)

# Scanner lifecycle markers around the first post-walk death event.
death_ms=min((x['timestampMs'] for x in kd if x['timestampMs'] is not None and x['timestampMs']>=we),default=None)
near={'startMarkers':0,'stopMarkers':0,'destroyMarkers':0,'serviceMarkers':0}
if death_ms:
    for line in unique:
        if not re.search(r'(ScannerService|BleScanner|io\.blueeye)',line,re.I):continue
        m=re.match(r'^(\d\d)-(\d\d) (\d\d):(\d\d):(\d\d)\.(\d+)',line)
        if not m:continue
        mo,da,hh,mm,ss,frac=m.groups();us=int((frac+'000000')[:6]);t=int(dt.datetime(2026,int(mo),int(da),int(hh),int(mm),int(ss),us,tzinfo=warsaw).timestamp()*1000)
        if abs(t-death_ms)>120000:continue
        near['serviceMarkers']+=1
        if re.search(r'(start|oncreate|running)',line,re.I):near['startMarkers']+=1
        if re.search(r'(stop|stopping|stopped)',line,re.I):near['stopMarkers']+=1
        if re.search(r'ondestroy',line,re.I):near['destroyMarkers']+=1

# Persisted sample burst density and address rotation structure, pseudonymized.
byfp=collections.defaultdict(list)
for s in walk:byfp[str(s.get('deviceFingerprint') or '')].append(s)
rot=[]
for fp,rows in byfp.items():
    rows=sorted(rows,key=lambda x:int(x['timestamp']))
    macs={str(x.get('observedMac') or '').upper() for x in rows if x.get('observedMac')}
    if len(macs)>1:
        rot.append({'id':hashlib.sha256(fp.encode()).hexdigest()[:10],'distinctObservedMacs':len(macs),'samples':len(rows)})
rot.sort(key=lambda x:(x['distinctObservedMacs'],x['samples']),reverse=True)

# Safe final gate interpretation. Ingest exactness is unavailable because export diagnostics were reset after process loss.
field_diag=((d.get('fieldMvpDiagnostics') or {}).get('scanner') or {}).get('ingest') or {}
session=d.get('session') or {}
report={
 'walkWindow':{'startUtc':iso(ws,dt.timezone.utc),'endUtc':iso(we,dt.timezone.utc),'startLocal':iso(ws,warsaw),'endLocal':iso(we,warsaw),'durationMs':we-ws,'samples':len(walk),'devices':len(byfp)},
 'permissions':permissions,
 'roomIntegrity':integrity,
 'followMeInWalk':{'count':len(follow),'userMovedCounts':dict(move),'baselineCounts':dict(baseline),'trackingStatusCounts':dict(status),'maxScore':max((float(r['score']) for r in follow),default=None)},
 'identityCandidatesInWalk':{'count':len(candidates),'reasonCounts':dict(collections.Counter(str(r['reasonCode']) for r in candidates)),'verdictCounts':dict(collections.Counter(str(r['verdict']) for r in candidates))},
 'rotation':{'logicalFingerprintsWithMultipleObservedMacs':len(rot),'topPseudonymous':rot[:8]},
 'processDeath':{'events':kd,'firstPostWalkDeathDeltaMs':None if death_ms is None else death_ms-we,'lifecycleMarkersWithin2m':near},
 'sessionWindowPreserved':bool(session.get('startedAt')) and int(session.get('sampleCount') or 0)>0,
 'ingestCountersAreWalkEvidence':False,
 'postRelaunchIngestSnapshot':{k:field_diag.get(k) for k in ('rawBleCallbacksTotal','enqueueAcceptedTotal','coalescedTotal','enqueueRejectedTotal','queueDroppedTotal','processingStartedTotal','processingSucceededTotal','processingFailedTotal','signalSamplesWrittenTotal','signalSamplesThrottledTotal','signalSampleWriteFailuresTotal','queueHighWaterMark','maxQueueWaitMs','maxProcessingDurationMs')},
}
json.dump(report,open(os.path.join(out,'phase3-final-gate-safe.json'),'w'),indent=2)
print('PHASE3_FINAL_GATE_AUDIT')
print('walk_window local=%s..%s duration_ms=%s samples=%s devices=%s' % (report['walkWindow']['startLocal'],report['walkWindow']['endLocal'],we-ws,len(walk),len(byfp)))
print('permissions='+json.dumps(permissions,sort_keys=True))
print('room_integrity='+str(integrity))
print('follow_me='+json.dumps(report['followMeInWalk'],sort_keys=True))
print('identity_candidates_walk='+json.dumps(report['identityCandidatesInWalk'],sort_keys=True))
print('rotation='+json.dumps(report['rotation'],sort_keys=True))
print('process_death='+json.dumps(report['processDeath'],sort_keys=True))
print('session_window_preserved='+str(report['sessionWindowPreserved']))
print('ingest_counters_are_walk_evidence=False')
print('post_relaunch_ingest='+json.dumps(report['postRelaunchIngestSnapshot'],sort_keys=True))
print('safe_report='+os.path.join(out,'phase3-final-gate-safe.json'))
PY
