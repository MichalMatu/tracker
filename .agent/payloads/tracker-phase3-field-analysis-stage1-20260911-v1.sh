#!/usr/bin/env bash
set -euo pipefail
ROOT="$HOME/agent-workspace/repos/tracker/checkpoints/phase3-field-reacceptance-20260911"
STAMP="$(cat "$ROOT/LATEST")"
OUT="$ROOT/$STAMP"
test -s "$OUT/blueeye-session-export.json"
test -s "$OUT/room/tracker_database"
test -s "$OUT/bugreport.zip"

git fetch --no-tags origin agent-control >/dev/null
git show origin/agent-control:.agent/results/tracker-phase3-field-reacceptance-collect-20260911-v2.json > "$OUT/collection-result-public.json"

python3 - "$OUT" <<'PY'
import collections, contextlib, copy, hashlib, io, json, math, os, re, shutil, sqlite3, statistics, sys, tempfile, zipfile
out=sys.argv[1]
export_path=os.path.join(out,'blueeye-session-export.json')
with open(export_path) as f: d=json.load(f)
session=d.get('session') or {}
samples=d.get('signalSamples') or []
devices=d.get('devices') or []
start=int(session.get('startedAt') or 0)
export_date=int(d.get('exportDate') or 0)
sess=[s for s in samples if int(s.get('timestamp') or 0)>=start] if start else []

def q(vals,p):
    vals=sorted(float(x) for x in vals if x is not None)
    if not vals: return None
    if len(vals)==1:return vals[0]
    x=(len(vals)-1)*p; lo=int(math.floor(x)); hi=int(math.ceil(x))
    return vals[lo] if lo==hi else vals[lo]+(vals[hi]-vals[lo])*(x-lo)

def bins50(rows):
    b=set()
    for s in rows:
        lat=s.get('latitude'); lon=s.get('longitude')
        if lat is None or lon is None: continue
        lat=float(lat); lon=float(lon)
        dy=lat/0.00045
        scale=max(0.2,math.cos(math.radians(lat)))
        dx=lon/(0.00045/scale)
        b.add((round(dy),round(dx)))
    return b

def hav(a,b):
    R=6371000.0
    p1,p2=math.radians(a[0]),math.radians(b[0])
    dp=math.radians(b[0]-a[0]); dl=math.radians(b[1]-a[1])
    h=math.sin(dp/2)**2+math.cos(p1)*math.cos(p2)*math.sin(dl/2)**2
    return 2*R*math.asin(min(1,math.sqrt(h)))

gps=[s for s in sess if s.get('latitude') is not None and s.get('longitude') is not None]
acc=[s.get('locationAccuracy') for s in gps if s.get('locationAccuracy') is not None]
fixes=[]
seen=set()
for s in sorted(gps,key=lambda x:int(x.get('timestamp') or 0)):
    key=(int(s.get('timestamp') or 0),round(float(s['latitude']),6),round(float(s['longitude']),6))
    if key not in seen:
        seen.add(key); fixes.append((float(s['latitude']),float(s['longitude']),key[0]))
route_m=sum(hav(fixes[i-1][:2],fixes[i][:2]) for i in range(1,len(fixes))) if fixes else 0.0
max_from_first=max((hav(fixes[0][:2],x[:2]) for x in fixes),default=0.0) if fixes else 0.0

byfp=collections.defaultdict(list)
for s in sess: byfp[str(s.get('deviceFingerprint') or '')].append(s)
continuity=[]
for fp,rows in byfp.items():
    rows=sorted(rows,key=lambda x:int(x.get('timestamp') or 0))
    bs=bins50(rows); rss=[int(x.get('rssi')) for x in rows if x.get('rssi') is not None]
    if len(bs)>=2 and len(rows)>=2:
        continuity.append({'id':hashlib.sha256(fp.encode()).hexdigest()[:10],'samples':len(rows),'bins50m':len(bs),'span_ms':int(rows[-1].get('timestamp') or 0)-int(rows[0].get('timestamp') or 0),'rssi_min':min(rss) if rss else None,'rssi_max':max(rss) if rss else None,'rssi_median':statistics.median(rss) if rss else None})
continuity=sorted(continuity,key=lambda x:(x['bins50m'],x['samples']),reverse=True)

# Process-lifetime diagnostics and collection process-state evidence.
collection=json.load(open(os.path.join(out,'collection-result-public.json')))
stdout='\n'.join(c.get('output','') for c in collection.get('commands',[]))
pre_alive='process_alive_before=1' in stdout
scanner=((d.get('fieldMvpDiagnostics') or {}).get('scanner') or {})
ingest=scanner.get('ingest') or {}
raw=int(ingest.get('rawBleCallbacksTotal') or 0); accepted=int(ingest.get('enqueueAcceptedTotal') or 0); coal=int(ingest.get('coalescedTotal') or 0); rej=int(ingest.get('enqueueRejectedTotal') or 0)
started=int(ingest.get('processingStartedTotal') or 0); succ=int(ingest.get('processingSucceededTotal') or 0); fail=int(ingest.get('processingFailedTotal') or 0)
written=int(ingest.get('signalSamplesWrittenTotal') or 0); throttled=int(ingest.get('signalSamplesThrottledTotal') or 0); swfail=int(ingest.get('signalSampleWriteFailuresTotal') or 0)
walk_diag_usable=pre_alive

# Room analysis on a disposable host-side duplicate so originals remain untouched.
with tempfile.TemporaryDirectory(prefix='phase3-room-') as td:
    for n in ('tracker_database','tracker_database-wal','tracker_database-shm'):
        p=os.path.join(out,'room',n)
        if os.path.exists(p): shutil.copy2(p,os.path.join(td,n))
    dbp=os.path.join(td,'tracker_database')
    con=sqlite3.connect(dbp)
    integrity=con.execute('PRAGMA integrity_check').fetchone()[0]
    tables={r[0] for r in con.execute("select name from sqlite_master where type='table'")}
    def count(table): return con.execute(f'SELECT count(*) FROM "{table}"').fetchone()[0] if table in tables else None
    room_counts={t:count(t) for t in ('devices','signal_samples','follow_me_observations','alert_evidence_events','identity_continuity_candidates')}
    room_range=None; room_session_count=None; room_pre_export_count=None
    if 'signal_samples' in tables:
        room_range=con.execute('select min(timestamp),max(timestamp) from signal_samples').fetchone()
        room_pre_export_count=con.execute('select count(*) from signal_samples where timestamp<=?',(export_date,)).fetchone()[0]
        room_session_count=con.execute('select count(*) from signal_samples where timestamp>=? and timestamp<=?',(start,export_date)).fetchone()[0] if start else None
    con.close()

# Identity candidates: evidence-only long-gap review, never infer a merge from a candidate alone.
cands=session.get('identityContinuityCandidates') or []
fp_times={fp:sorted(int(x.get('timestamp') or 0) for x in rows) for fp,rows in byfp.items()}
long_gap=[]
for c in cands:
    a=str(c.get('deviceFingerprint') or ''); b=str(c.get('candidateFingerprint') or ''); t=int(c.get('timestamp') or 0)
    aa=[x for x in fp_times.get(a,[]) if x<=t]; bb=[x for x in fp_times.get(b,[]) if x>=t]
    if aa and bb:
        gap=bb[0]-aa[-1]
        if gap>30000:
            long_gap.append({'gap_ms':gap,'verdict':c.get('verdict'),'reasonCode':c.get('reasonCode'),'both_fingerprints_persisted':a in byfp and b in byfp})
verdicts=collections.Counter(str(c.get('verdict')) for c in cands)
reasons=collections.Counter(str(c.get('reasonCode')) for c in cands)

# UI/export conclusions against persisted evidence context.
summ=session.get('deviceSummaries') or []
tracking=collections.Counter(str(x.get('trackingStatus')) for x in summ)
review=collections.Counter(str(x.get('reviewCategory')) for x in summ)
attention=[x for x in summ if str(x.get('trackingStatus')) in ('SUSPICIOUS','DANGEROUS')]
attention_without_evidence=sum(1 for x in attention if int(x.get('evidenceCount') or 0)==0 or x.get('strongestEvidence') in (None,{}))
known=[]
for dev in devices:
    typ=str(dev.get('deviceType') or '').upper()
    if any(k in typ for k in ('TRACKER','AIRTAG','SMARTTAG','TILE')):
        known.append(dev)
known_without_context=sum(1 for x in known if not (x.get('rawData') or x.get('evidence')))

# Logcat aggregate error signals only; raw lines remain private.
log=''
for n in ('logcat-before.txt','logcat-after-export.txt','logcat-final.txt'):
    p=os.path.join(out,n)
    if os.path.exists(p):
        with open(p,errors='replace') as f: log+='\n'+f.read()
patterns={
 'fatal_exception':r'FATAL EXCEPTION',
 'oom':r'OutOfMemoryError',
 'security_exception':r'SecurityException',
 'sqlite_exception':r'SQLite(?:Exception|Constraint|DiskIOException|FullException)',
 'ingest_rejected':r'Rejected \d+ scan event',
 'scan_processing_failed':r'(Scan processing failed|Error processing scan event)',
 'watchdog_refresh':r'Passive BLE scan registration refreshed',
}
log_counts={k:len(re.findall(v,log,re.I)) for k,v in patterns.items()}

# Bugreport HCI discovery by both names and btsnoop magic; this catches vendor-renamed artifacts.
bug=os.path.join(out,'bugreport.zip')
hci_path=os.path.join(out,'btsnoop_hci.log')
name_matches=[]; magic_matches=[]; nested_matches=[]; text_mentions=0
with zipfile.ZipFile(bug) as z:
    infos=[i for i in z.infolist() if not i.is_dir()]
    for i in infos:
        if re.search(r'(bluetooth|btsnoop|snoop|hci|/bt/|bt_)',i.filename,re.I): name_matches.append((i.filename,i.file_size))
        try:
            if i.file_size>=16:
                with z.open(i) as f:
                    if f.read(8)==b'btsnoop\x00': magic_matches.append(i)
        except Exception: pass
    if magic_matches:
        pick=max(magic_matches,key=lambda i:i.file_size)
        with z.open(pick) as src, open(hci_path,'wb') as dst: shutil.copyfileobj(src,dst)
    # Search text report for snoop-path evidence without publishing report contents.
    for i in infos:
        if i.file_size>0 and i.file_size<80_000_000 and (i.filename.endswith('.txt') or 'bugreport-' in os.path.basename(i.filename)):
            try:
                txt=z.read(i).decode('utf-8','ignore')
                text_mentions += len(re.findall(r'(?:btsnoop|snoop_logger_tracing|Bluetooth HCI snoop)',txt,re.I))
            except Exception: pass
    # Inspect nested ZIP member names for a hidden attachment container.
    for i in infos:
        if i.filename.lower().endswith('.zip') and 0<i.file_size<200_000_000:
            try:
                data=z.read(i)
                with zipfile.ZipFile(io.BytesIO(data)) as nz:
                    for ni in nz.infolist():
                        if re.search(r'(bluetooth|btsnoop|snoop|hci|/bt/|bt_)',ni.filename,re.I): nested_matches.append((i.filename+'::'+ni.filename,ni.file_size))
            except Exception: pass

hci_meta=None
if os.path.exists(hci_path) and os.path.getsize(hci_path)>0:
    h=hashlib.sha256()
    with open(hci_path,'rb') as f:
        for b in iter(lambda:f.read(1024*1024),b''): h.update(b)
    hci_meta={'bytes':os.path.getsize(hci_path),'sha256':h.hexdigest(),'magic_candidates':len(magic_matches)}

safe={
 'process_alive_before_collection':pre_alive,
 'walk_ingest_diagnostics_usable':walk_diag_usable,
 'export':{'schemaVersion':d.get('schemaVersion'),'deviceCount':d.get('deviceCount'),'sampleCount':d.get('sampleCount'),'sessionDeviceCount':session.get('deviceCount'),'sessionSampleCount':session.get('sampleCount'),'sessionDurationMs':session.get('durationMs'),'sessionStartedAt':start,'exportDate':export_date},
 'ingest':{'raw':raw,'accepted':accepted,'coalesced':coal,'rejected':rej,'raw_accounting_ok':raw==accepted+coal+rej,'queueDroppedTotal':ingest.get('queueDroppedTotal'),'processingStarted':started,'processingSucceeded':succ,'processingFailed':fail,'processing_balance':started-succ-fail,'signalWritten':written,'signalThrottled':throttled,'signalFailed':swfail,'signal_balance_vs_succeeded':succ-written-throttled-swfail,'queueDepth':ingest.get('queueDepth'),'queueHighWaterMark':ingest.get('queueHighWaterMark'),'maxQueueWaitMs':ingest.get('maxQueueWaitMs'),'maxProcessingDurationMs':ingest.get('maxProcessingDurationMs'),'scannerStartedAt':scanner.get('startedAt'),'scannerState':scanner.get('state'),'lastError':scanner.get('lastError')},
 'room':{'integrity':integrity,'counts':room_counts,'signalRange':room_range,'samplesAtOrBeforeExport':room_pre_export_count,'sessionSamplesAtOrBeforeExport':room_session_count,'exportRootSampleDeltaVsRoomPreExport':None if room_pre_export_count is None else room_pre_export_count-int(d.get('sampleCount') or 0),'exportSessionSampleDeltaVsRoom':None if room_session_count is None else room_session_count-int(session.get('sampleCount') or 0)},
 'gps':{'sessionSamples':len(sess),'gpsSamples':len(gps),'coveragePct':round(100*len(gps)/len(sess),2) if sess else 0,'accuracyP50m':q(acc,.5),'accuracyP90m':q(acc,.9),'accuracyP95m':q(acc,.95),'accuracyMaxm':max(acc) if acc else None,'unique50mBins':len(bins50(gps)),'uniqueFixes':len(fixes),'routeStepSumM':round(route_m,1),'maxDistanceFromFirstM':round(max_from_first,1)},
 'rssiContinuity':{'devicesAcrossAtLeast2x50mBins':len(continuity),'topPseudonymous':continuity[:8]},
 'identity':{'candidateCount':len(cands),'verdictCounts':dict(verdicts),'reasonCounts':dict(reasons),'longGapOver30sCount':len(long_gap),'longGapAllKeepBothFingerprints':all(x['both_fingerprints_persisted'] for x in long_gap) if long_gap else None,'longGapMaxMs':max((x['gap_ms'] for x in long_gap),default=None)},
 'uiEvidence':{'trackingStatusCounts':dict(tracking),'reviewCategoryCounts':dict(review),'attentionDevices':len(attention),'attentionWithoutEvidence':attention_without_evidence,'knownTrackerDevices':len(known),'knownTrackerWithoutRawOrEvidenceContext':known_without_context},
 'logcatCounts':log_counts,
 'bugreportHciDiscovery':{'nameMatchCount':len(name_matches),'magicMatchCount':len(magic_matches),'nestedNameMatchCount':len(nested_matches),'textMentionCount':text_mentions,'hciExtractedByMagic':hci_meta},
}
json.dump(safe,open(os.path.join(out,'phase3-analysis-stage1-safe.json'),'w'),indent=2)
json.dump({'name_matches':name_matches,'nested_matches':nested_matches},open(os.path.join(out,'bugreport-hci-discovery-private.json'),'w'),indent=2)
print('PHASE3_STAGE1_ANALYSIS')
print('process_alive_before_collection='+str(pre_alive))
print('walk_ingest_diagnostics_usable='+str(walk_diag_usable))
print('export_counts devices=%s samples=%s session_devices=%s session_samples=%s' % (d.get('deviceCount'),d.get('sampleCount'),session.get('deviceCount'),session.get('sampleCount')))
print('ingest raw=%d accepted=%d coalesced=%d rejected=%d accounting_ok=%s drops=%s processing=%d/%d/%d sample_outcomes=%d/%d/%d' % (raw,accepted,coal,rej,raw==accepted+coal+rej,ingest.get('queueDroppedTotal'),started,succ,fail,written,throttled,swfail))
print('room integrity=%s counts=%s root_delta_pre_export=%s session_delta=%s' % (integrity,json.dumps(room_counts,sort_keys=True),safe['room']['exportRootSampleDeltaVsRoomPreExport'],safe['room']['exportSessionSampleDeltaVsRoom']))
print('gps coverage_pct=%s p50_m=%s p90_m=%s p95_m=%s bins50m=%s max_from_first_m=%s' % (safe['gps']['coveragePct'],safe['gps']['accuracyP50m'],safe['gps']['accuracyP90m'],safe['gps']['accuracyP95m'],safe['gps']['unique50mBins'],safe['gps']['maxDistanceFromFirstM']))
print('rssi_continuity_devices='+str(len(continuity)))
print('identity candidates=%d long_gap_gt30s=%d verdicts=%s reasons=%s' % (len(cands),len(long_gap),json.dumps(dict(verdicts),sort_keys=True),json.dumps(dict(reasons),sort_keys=True)))
print('ui tracking=%s attention=%d attention_without_evidence=%d known_trackers=%d known_without_context=%d' % (json.dumps(dict(tracking),sort_keys=True),len(attention),attention_without_evidence,len(known),known_without_context))
print('logcat_counts='+json.dumps(log_counts,sort_keys=True))
print('bugreport_hci name_matches=%d magic_matches=%d nested_matches=%d text_mentions=%d extracted=%s' % (len(name_matches),len(magic_matches),len(nested_matches),text_mentions,bool(hci_meta)))
if hci_meta: print('hci_sha256=%s hci_bytes=%s' % (hci_meta['sha256'],hci_meta['bytes']))
print('stage1_safe_report='+os.path.join(out,'phase3-analysis-stage1-safe.json'))
PY
