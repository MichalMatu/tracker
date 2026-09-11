#!/usr/bin/env bash
set -euo pipefail
ROOT="$HOME/agent-workspace/repos/tracker/checkpoints/phase3-field-reacceptance-20260911"
STAMP="$(cat "$ROOT/LATEST")"
OUT="$ROOT/$STAMP"
test -s "$OUT/blueeye-session-export.json"
test -s "$OUT/phase3-hci-correlation-private.json"

python3 - "$OUT" <<'PY'
import collections, datetime as dt, hashlib, json, math, os, re, struct, subprocess, sys
from zoneinfo import ZoneInfo
out=sys.argv[1]; EPOCH=0x00dcddb30f2f8000
D=json.load(open(os.path.join(out,'blueeye-session-export.json')))
samples=sorted([x for x in (D.get('signalSamples') or []) if int(x.get('timestamp') or 0)>0],key=lambda x:int(x['timestamp']))
start_i=0
for i in range(1,len(samples)):
 if int(samples[i]['timestamp'])-int(samples[i-1]['timestamp'])>15*60*1000:start_i=i
burst=samples[start_i:]; bs=int(burst[0]['timestamp']); be=int(burst[-1]['timestamp'])
priv=json.load(open(os.path.join(out,'phase3-hci-correlation-private.json')))

def inspect(path):
 data=open(path,'rb').read(); pos=16; ptypes=collections.Counter(); ev=collections.Counter(); sub=collections.Counter(); rec_times=[]; rec_in_burst=0; evt_in_burst=0
 while pos+24<=len(data):
  orig,inc,flags,drops,stamp=struct.unpack('>IIIIQ',data[pos:pos+24]); pos+=24
  if inc>len(data)-pos:break
  pkt=data[pos:pos+inc];pos+=inc
  t=(stamp-EPOCH)/1000.0;rec_times.append(t)
  if bs<=t<=be:rec_in_burst+=1
  if not pkt:continue
  typ=pkt[0];ptypes[typ]+=1
  if typ==4 and len(pkt)>=3:
   code=pkt[1];ev[code]+=1
   if bs<=t<=be:evt_in_burst+=1
   if code==0x3e and len(pkt)>=4:sub[pkt[3]]+=1
 return {'packet_types':dict(ptypes),'event_codes':dict(ev),'le_subevents':dict(sub),'start_ms':min(rec_times) if rec_times else None,'end_ms':max(rec_times) if rec_times else None,'record_count':len(rec_times),'records_in_walk_burst':rec_in_burst,'events_in_walk_burst':evt_in_burst,'record_overlap_ms':max(0,min(max(rec_times),be)-max(min(rec_times),bs)) if rec_times else 0}

hci=[]
for c in priv.get('hci_candidates',[]):
 path=c['path']; ins=inspect(path)
 hci.append({'entry':c.get('entry'),'basename':os.path.basename(c.get('entry','')),'sha256':c['sha256'],'bytes':c['bytes'],'inspect':ins})

# Try tshark if already installed; never install or change host software.
tshark=None
for p in (shutil_path:=[],): pass
candidates=[]
try:
 p=subprocess.check_output(['bash','-lc','command -v tshark || true'],text=True).strip()
 if p:candidates.append(p)
except Exception:pass
candidates += ['/Applications/Wireshark.app/Contents/MacOS/tshark','/opt/homebrew/bin/tshark','/usr/local/bin/tshark']
for p in candidates:
 if p and os.path.isfile(p) and os.access(p,os.X_OK):tshark=p;break
tshark_safe=[]
if tshark:
 for x in hci:
  path=next(c['path'] for c in priv['hci_candidates'] if c['sha256']==x['sha256'])
  try:
   outp=subprocess.check_output([tshark,'-r',path,'-T','fields','-e','frame.time_epoch','-e','bthci_evt.code','-e','bthci_evt.le_meta_subevent'],stderr=subprocess.DEVNULL,text=True,timeout=60)
   lines=[ln for ln in outp.splitlines() if ln.strip()]
   le=sum(1 for ln in lines if '\t62\t' in ('\t'+ln+'\t') or '\t0x3e\t' in ('\t'+ln+'\t'))
   tshark_safe.append({'frames_with_fields':len(lines),'le_meta_rows':le})
  except Exception as e:tshark_safe.append({'error':type(e).__name__})

# Known-tracker and root evidence consistency, pseudonymized.
byfp=collections.defaultdict(list)
for s in burst:byfp[str(s.get('deviceFingerprint') or '')].append(s)
known=[]
for d in D.get('devices') or []:
 typ=str(d.get('deviceType') or '').upper()
 if not any(k in typ for k in ('TRACKER','AIRTAG','SMARTTAG','TILE')):continue
 fp=str(d.get('fingerprint') or '');rows=byfp.get(fp,[]);rss=[int(x['rssi']) for x in rows if x.get('rssi') is not None]
 known.append({'id':hashlib.sha256(fp.encode()).hexdigest()[:10],'deviceType':d.get('deviceType'),'trackingStatus':d.get('trackingStatus'),'followingScore':d.get('followingScore'),'evidenceCount':len(d.get('evidence') or []),'evidenceSources':sorted({str(e.get('source')) for e in (d.get('evidence') or [])}),'evidenceConfidences':sorted({str(e.get('confidence')) for e in (d.get('evidence') or [])}),'rawContext':bool(d.get('rawData')),'sampleCount':len(rows),'rssiMin':min(rss) if rss else None,'rssiMax':max(rss) if rss else None,'distinctObservedMacs':len({str(x.get('observedMac') or '').upper() for x in rows if x.get('observedMac')})})

# MAC rotation/reappearance structure without exposing MACs.
rotation=[]
for fp,rows in byfp.items():
 rows=sorted(rows,key=lambda x:int(x['timestamp'])); macs=[str(x.get('observedMac') or '').upper() for x in rows]
 distinct={m for m in macs if m}
 if len(distinct)<=1:continue
 long_switches=0; switches=0
 for a,b in zip(rows,rows[1:]):
  ma=str(a.get('observedMac') or '').upper();mb=str(b.get('observedMac') or '').upper()
  if ma and mb and ma!=mb:
   switches+=1
   if int(b['timestamp'])-int(a['timestamp'])>30000:long_switches+=1
 rotation.append({'id':hashlib.sha256(fp.encode()).hexdigest()[:10],'distinctMacs':len(distinct),'switches':switches,'longSwitchesOver30s':long_switches,'sampleCount':len(rows)})
rotation.sort(key=lambda x:(x['longSwitchesOver30s'],x['distinctMacs'],x['sampleCount']),reverse=True)

# Direct app process kill/death lines and their timing relative to end of persisted walk burst.
lines=[]
for n in ('logcat-before.txt','logcat-after-export.txt','logcat-final.txt'):
 p=os.path.join(out,n)
 if os.path.exists(p):lines+=open(p,errors='replace').read().splitlines()
kill_rx=re.compile(r'(Killing|has died|am_kill|Force stopping|lowmemory|lmkd)',re.I); app_rx=re.compile(r'io\.blueeye',re.I)
deltas=[]; direct=0
for line in lines:
 if app_rx.search(line) and kill_rx.search(line):
  direct+=1
  m=re.match(r'^(\d\d)-(\d\d) (\d\d):(\d\d):(\d\d)\.(\d+)',line)
  if m:
   mo,da,hh,mm,ss,frac=m.groups(); us=int((frac+'000000')[:6]); stamp=dt.datetime(2026,int(mo),int(da),int(hh),int(mm),int(ss),us,tzinfo=ZoneInfo('Europe/Warsaw')); deltas.append(int(stamp.timestamp()*1000)-be)

safe={'hci':[{'basename':x['basename'],'sha256':x['sha256'],'bytes':x['bytes'],'packetTypes':x['inspect']['packet_types'],'eventCodes':x['inspect']['event_codes'],'leSubevents':x['inspect']['le_subevents'],'recordOverlapWithWalkMs':x['inspect']['record_overlap_ms'],'recordsInWalk':x['inspect']['records_in_walk_burst'],'eventsInWalk':x['inspect']['events_in_walk_burst'],'startMinusWalkStartMs':x['inspect']['start_ms']-bs if x['inspect']['start_ms'] is not None else None,'endMinusWalkEndMs':x['inspect']['end_ms']-be if x['inspect']['end_ms'] is not None else None} for x in hci],'tsharkAvailable':bool(tshark),'tsharkChecks':tshark_safe,'knownTrackers':known,'rotation':{'fingerprintsWithMultipleObservedMacs':len(rotation),'withLongMacSwitchOver30s':sum(1 for x in rotation if x['longSwitchesOver30s']>0),'topPseudonymous':rotation[:8]},'processDeath':{'directKillDeathLines':direct,'earliestDeltaFromWalkEndMs':min(deltas) if deltas else None,'latestDeltaFromWalkEndMs':max(deltas) if deltas else None}}
json.dump(safe,open(os.path.join(out,'phase3-analysis-stage3-safe.json'),'w'),indent=2)
print('PHASE3_STAGE3_ANALYSIS')
print('tshark_available='+str(bool(tshark))+' tshark_checks='+json.dumps(tshark_safe,sort_keys=True))
for i,x in enumerate(safe['hci'],1):print('hci%d basename=%s packet_types=%s event_codes=%s le_subevents=%s overlap_walk_ms=%s records_in_walk=%s events_in_walk=%s start_delta_ms=%s end_delta_ms=%s' % (i,x['basename'],json.dumps(x['packetTypes'],sort_keys=True),json.dumps(x['eventCodes'],sort_keys=True),json.dumps(x['leSubevents'],sort_keys=True),x['recordOverlapWithWalkMs'],x['recordsInWalk'],x['eventsInWalk'],x['startMinusWalkStartMs'],x['endMinusWalkEndMs']))
print('known_trackers='+json.dumps(known,sort_keys=True))
print('rotation='+json.dumps(safe['rotation'],sort_keys=True))
print('process_death='+json.dumps(safe['processDeath'],sort_keys=True))
print('stage3_safe_report='+os.path.join(out,'phase3-analysis-stage3-safe.json'))
PY
