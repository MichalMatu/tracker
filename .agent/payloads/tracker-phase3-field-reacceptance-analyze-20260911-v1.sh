#!/usr/bin/env bash
set -euo pipefail
ROOT="$HOME/agent-workspace/repos/tracker/checkpoints/phase3-field-reacceptance-20260911"
STAMP="$(cat "$ROOT/LATEST")"
OUT="$ROOT/$STAMP"
EXPORT="$OUT/blueeye-session-export.json"
DB="$OUT/room/tracker_database"
BUG="$OUT/bugreport.zip"
test -s "$EXPORT"
test -s "$DB"
test -s "$BUG"

# Inspect bugreport structure without exposing raw contents.
python3 - "$BUG" "$OUT" <<'PY'
import json,os,re,sys,zipfile
bug,out=sys.argv[1:]
with zipfile.ZipFile(bug) as z:
    names=[i.filename for i in z.infolist() if not i.is_dir()]
    hits=[n for n in names if re.search(r'(bluetooth|btsnoop|hci|snoop|bt/|bt_)',n,re.I)]
    exact=[n for n in hits if re.search(r'btsnoop|snoop.*hci|hci.*snoop',n,re.I)]
    summary={'entry_count':len(names),'bt_related_count':len(hits),'snoop_like_count':len(exact),'bt_related_entries':hits[:200]}
    json.dump(summary,open(os.path.join(out,'bugreport-bt-entries-private.json'),'w'),indent=2)
    print('bugreport_entry_count='+str(len(names)))
    print('bugreport_bt_related_count='+str(len(hits)))
    print('bugreport_snoop_like_count='+str(len(exact)))
    for n in hits[:40]: print('bugreport_bt_entry='+n)
PY

# Full export/Room correlation. Output contains aggregates only; no MAC/GPS/raw payloads.
python3 - "$EXPORT" "$DB" "$OUT" <<'PY'
import json,sqlite3,sys,statistics,math,os,collections,hashlib
exp_path,db_path,out=sys.argv[1:]
d=json.load(open(exp_path))
sess=d.get('session') or {}
diag=((d.get('fieldMvpDiagnostics') or {}).get('scanner') or {})
ing=diag.get('ingest') or {}
started=sess.get('startedAt') or 0
exported=sess.get('exportedAt') or d.get('exportDate') or 0
samples=d.get('signalSamples') or []
ss=[s for s in samples if (s.get('timestamp') or 0) >= started] if started else []
# Stored session arrays/summaries
summ=sess.get('deviceSummaries') or []
idc=sess.get('identityContinuityCandidates') or []
review=sess.get('reviewDeviceQueue') or []
# GPS and RSSI
coords=[s for s in ss if s.get('latitude') is not None and s.get('longitude') is not None]
acc=[float(s['locationAccuracy']) for s in coords if s.get('locationAccuracy') is not None]
rssi=[int(s['rssi']) for s in ss if s.get('rssi') is not None]
ts=sorted(int(s['timestamp']) for s in ss if s.get('timestamp') is not None)
gaps=[b-a for a,b in zip(ts,ts[1:])]
# per-fingerprint continuity, anonymized counts only
byfp=collections.defaultdict(list)
for s in ss:
    fp=s.get('deviceFingerprint')
    t=s.get('timestamp')
    if fp and t is not None: byfp[fp].append(int(t))
max_device_gaps=[]
for arr in byfp.values():
    arr.sort()
    if len(arr)>1: max_device_gaps.append(max(b-a for a,b in zip(arr,arr[1:])))
# DB ground truth; query only aggregate counts/time ranges.
con=sqlite3.connect('file:'+db_path+'?mode=ro',uri=True)
cur=con.cursor()
integrity=cur.execute('PRAGMA integrity_check').fetchone()[0]
tables=[r[0] for r in cur.execute("select name from sqlite_master where type='table'")]
def count_table(name):
    return cur.execute(f'select count(*) from "{name}"').fetchone()[0] if name in tables else None
def range_table(name,col='timestamp'):
    if name not in tables: return (None,None)
    cols=[r[1] for r in cur.execute(f'pragma table_info("{name}")')]
    if col not in cols: return (None,None)
    return cur.execute(f'select min("{col}"), max("{col}") from "{name}"').fetchone()
counts={t:count_table(t) for t in ['devices','signal_samples','follow_me_observations','alert_evidence_events','identity_continuity_candidates']}
ranges={t:range_table(t) for t in ['signal_samples','follow_me_observations','alert_evidence_events','identity_continuity_candidates']}
# session DB sample count using startedAt
session_db_samples=None
session_db_range=(None,None)
if started and 'signal_samples' in tables:
    session_db_samples=cur.execute('select count(*) from signal_samples where timestamp>=?',(started,)).fetchone()[0]
    session_db_range=cur.execute('select min(timestamp),max(timestamp) from signal_samples where timestamp>=?',(started,)).fetchone()
con.close()
# equations
raw=int(ing.get('rawBleCallbacksTotal') or 0); accq=int(ing.get('enqueueAcceptedTotal') or 0); coal=int(ing.get('coalescedTotal') or 0); rej=int(ing.get('enqueueRejectedTotal') or 0)
ps=int(ing.get('processingStartedTotal') or 0); pok=int(ing.get('processingSucceededTotal') or 0); pf=int(ing.get('processingFailedTotal') or 0)
sw=int(ing.get('signalSamplesWrittenTotal') or 0); st=int(ing.get('signalSamplesThrottledTotal') or 0); sf=int(ing.get('signalSampleWriteFailuresTotal') or 0)
pd=int(ing.get('provisionalDiscardedTotal') or 0)
pu=int(ing.get('persistedDeviceUpdatesTotal') or 0); dut=int(ing.get('deviceUpdateThrottledTotal') or 0)
qd=int(ing.get('queueDroppedTotal') or 0); qdepth=int(ing.get('queueDepth') or 0)
summary={
 'schemaVersion':d.get('schemaVersion'),'top_deviceCount':d.get('deviceCount'),'top_sampleCount':d.get('sampleCount'),
 'session_startedAt':started,'session_exportedAt':exported,'session_durationMs':sess.get('durationMs'),'session_deviceCount':sess.get('deviceCount'),'session_sampleCount':sess.get('sampleCount'),
 'session_samples_recomputed':len(ss),'session_device_fingerprints':len(byfp),
 'gps_samples':len(coords),'gps_coverage_pct':(100.0*len(coords)/len(ss) if ss else None),
 'gps_accuracy_median':statistics.median(acc) if acc else None,'gps_accuracy_p90':(sorted(acc)[min(len(acc)-1,math.ceil(.9*len(acc))-1)] if acc else None),'gps_accuracy_max':max(acc) if acc else None,
 'rssi_min':min(rssi) if rssi else None,'rssi_max':max(rssi) if rssi else None,'rssi_median':statistics.median(rssi) if rssi else None,
 'sample_firstAt':min(ts) if ts else None,'sample_lastAt':max(ts) if ts else None,'sample_spanMs':(max(ts)-min(ts) if ts else None),'sample_max_global_gapMs':max(gaps) if gaps else None,
 'device_max_gap_over_30s_count':sum(1 for x in max_device_gaps if x>30000),'device_max_gap_over_5m_count':sum(1 for x in max_device_gaps if x>300000),'device_max_gap_maxMs':max(max_device_gaps) if max_device_gaps else None,
 'identity_candidate_count':len(idc),'review_queue_count':len(review),'review_category_counts':sess.get('reviewCategoryCounts'),'identity_verdict_counts':sess.get('identityCarryoverVerdictCounts'),
 'scanner_state':diag.get('state'),'scanner_startedAt':diag.get('startedAt'),'scanner_lastBleSeenAt':diag.get('lastBleSeenAt'),'scanner_lastError':diag.get('lastError'),'scanner_lifecycleTransitionCount':diag.get('lifecycleTransitionCount'),
 'ingest':ing,
 'eq_raw':{'lhs':raw,'rhs':accq+coal+rej,'pass':raw==accq+coal+rej},
 'eq_processing':{'started':ps,'finished':pok+pf,'inflight':ps-(pok+pf),'pass':ps>=pok+pf and ps-(pok+pf)>=0},
 'eq_samples':{'processed_success':pok,'provisional_discarded':pd,'written':sw,'throttled':st,'failed':sf,'outcome_sum':sw+st+sf,'pass_for_persisted_path':(sw+st+sf)==(pok-pd)},
 'eq_devices':{'processed_success':pok,'provisional_discarded':pd,'updated':pu,'throttled':dut,'outcome_sum':pu+dut,'pass_for_persisted_path':(pu+dut)==(pok-pd)},
 'queueDroppedTotal':qd,'queueDepth':qdepth,'queueHighWaterMark':ing.get('queueHighWaterMark'),'maxQueueWaitMs':ing.get('maxQueueWaitMs'),'maxProcessingDurationMs':ing.get('maxProcessingDurationMs'),
 'room_integrity':integrity,'room_counts':counts,'room_ranges':ranges,'room_session_samples':session_db_samples,'room_session_sample_range':session_db_range,
 'export_room_session_sample_count_match':(session_db_samples==len(ss) if session_db_samples is not None else None),
}
json.dump(summary,open(os.path.join(out,'phase3-analysis-safe.json'),'w'),indent=2)
print('--- EXPORT / ROOM ---')
for k in ['schemaVersion','top_deviceCount','top_sampleCount','session_startedAt','session_exportedAt','session_durationMs','session_deviceCount','session_sampleCount','session_samples_recomputed','session_device_fingerprints','gps_samples','gps_coverage_pct','gps_accuracy_median','gps_accuracy_p90','gps_accuracy_max','rssi_min','rssi_max','rssi_median','sample_firstAt','sample_lastAt','sample_spanMs','sample_max_global_gapMs','device_max_gap_over_30s_count','device_max_gap_over_5m_count','device_max_gap_maxMs','identity_candidate_count','review_queue_count','room_integrity','room_session_samples','export_room_session_sample_count_match']:
    print(f'{k}={summary.get(k)}')
print('room_counts='+json.dumps(counts,sort_keys=True))
print('room_ranges='+json.dumps(ranges,sort_keys=True))
print('--- INGEST ---')
for k in ['rawBleCallbacksTotal','enqueueAcceptedTotal','coalescedTotal','enqueueRejectedTotal','queueDroppedTotal','processingStartedTotal','processingSucceededTotal','processingFailedTotal','provisionalDiscardedTotal','persistedDeviceUpdatesTotal','deviceUpdateThrottledTotal','signalSamplesWrittenTotal','signalSamplesThrottledTotal','signalSampleWriteFailuresTotal','queueDepth','queueHighWaterMark','maxQueueWaitMs','maxProcessingDurationMs']:
    print(f'{k}={ing.get(k)}')
print('raw_equation_pass='+str(summary['eq_raw']['pass']))
print('processing_inflight='+str(summary['eq_processing']['inflight']))
print('processing_equation_pass='+str(summary['eq_processing']['pass']))
print('sample_outcome_equation_pass='+str(summary['eq_samples']['pass_for_persisted_path']))
print('device_outcome_equation_pass='+str(summary['eq_devices']['pass_for_persisted_path']))
print('scanner_state='+str(diag.get('state')))
print('scanner_startedAt='+str(diag.get('startedAt')))
print('scanner_lastBleSeenAt='+str(diag.get('lastBleSeenAt')))
print('scanner_lastError='+str(diag.get('lastError')))
PY

# Runtime/logcat aggregate scan; no raw logs emitted.
python3 - "$OUT" <<'PY'
import os,re,sys,json
out=sys.argv[1]
files=['logcat-before.txt','logcat-after-export.txt','logcat-final.txt','logcat-app-before.txt','logcat-app-after-export.txt']
patterns={
 'fatal_exception':r'FATAL EXCEPTION',
 'oom':r'OutOfMemoryError',
 'security_exception':r'SecurityException',
 'illegal_state':r'IllegalStateException',
 'sqlite_exception':r'(SQLiteException|SQLiteDatabaseCorruptException)',
 'scan_processing_failed':r'Scan processing failed|Error processing scan event',
 'ingest_rejected':r'Rejected [0-9]+ scan event',
 'scanner_failed':r'SCANNER_FAILED|Scan failed|onScanFailed',
}
agg={k:0 for k in patterns}
for fn in files:
 p=os.path.join(out,fn)
 if not os.path.exists(p): continue
 text=open(p,errors='replace').read()
 for k,pat in patterns.items(): agg[k]+=len(re.findall(pat,text,re.I))
json.dump(agg,open(os.path.join(out,'logcat-analysis-safe.json'),'w'),indent=2)
print('logcat_counts='+json.dumps(agg,sort_keys=True))
PY

echo "analysis_checkpoint=$OUT"
echo 'PHASE3_FIELD_REACCEPTANCE_ANALYSIS_PASS'
