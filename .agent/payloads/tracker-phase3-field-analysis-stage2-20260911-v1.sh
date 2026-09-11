#!/usr/bin/env bash
set -euo pipefail
ROOT="$HOME/agent-workspace/repos/tracker/checkpoints/phase3-field-reacceptance-20260911"
STAMP="$(cat "$ROOT/LATEST")"
OUT="$ROOT/$STAMP"
test -s "$OUT/blueeye-session-export.json"
test -s "$OUT/bugreport.zip"
mkdir -p "$OUT/hci"
chmod 700 "$OUT/hci"

python3 - "$OUT" <<'PY'
import bisect, collections, datetime as dt, hashlib, json, math, os, re, shutil, struct, sys, zipfile
out=sys.argv[1]
EPOCH_DELTA=0x00dcddb30f2f8000

def sha256(path):
 h=hashlib.sha256()
 with open(path,'rb') as f:
  for b in iter(lambda:f.read(1024*1024),b''): h.update(b)
 return h.hexdigest()

def norm_mac(b): return ':'.join(f'{x:02X}' for x in b[::-1])

def parse_btsnoop(path):
 data=open(path,'rb').read()
 if len(data)<16 or data[:8]!=b'btsnoop\x00': raise ValueError('not btsnoop')
 version,datalink=struct.unpack('>II',data[8:16])
 pos=16; records=0; times=[]; reports=[]; le_meta=collections.Counter(); malformed=0
 while pos+24<=len(data):
  orig,inc,flags,drops,stamp=struct.unpack('>IIIIQ',data[pos:pos+24]); pos+=24
  if inc>len(data)-pos: malformed+=1; break
  pkt=data[pos:pos+inc]; pos+=inc; records+=1
  unix_us=stamp-EPOCH_DELTA
  t_ms=unix_us/1000.0
  times.append(t_ms)
  if not pkt: continue
  if pkt[0]==4: body=pkt[1:]
  elif pkt[0]==0x3e: body=pkt
  else: continue
  if len(body)<3 or body[0]!=0x3e: continue
  plen=body[1]; params=body[2:2+plen]
  if not params: continue
  sub=params[0]; le_meta[sub]+=1; p=params[1:]
  try:
   if sub==0x02 and p:
    n=p[0]; j=1
    for _ in range(n):
     if j+9>len(p): break
     ev=p[j]; at=p[j+1]; addr=p[j+2:j+8]; dl=p[j+8]; j+=9
     if j+dl+1>len(p): break
     adv=p[j:j+dl]; j+=dl; rssi=struct.unpack('b',p[j:j+1])[0]; j+=1
     reports.append({'t':t_ms,'mac':norm_mac(addr),'rssi':rssi,'addr_type':at,'subevent':sub,'data_len':dl})
   elif sub==0x0d and p:
    n=p[0]; j=1
    for _ in range(n):
     if j+24>len(p): break
     ev=int.from_bytes(p[j:j+2],'little'); at=p[j+2]; addr=p[j+3:j+9]
     tx=struct.unpack('b',p[j+12:j+13])[0]; rssi=struct.unpack('b',p[j+13:j+14])[0]
     dl=p[j+23]; j+=24
     if j+dl>len(p): break
     adv=p[j:j+dl]; j+=dl
     reports.append({'t':t_ms,'mac':norm_mac(addr),'rssi':rssi,'addr_type':at,'subevent':sub,'data_len':dl})
   elif sub==0x0b and p:
    n=p[0]; j=1
    for _ in range(n):
     if j+16>len(p): break
     at=p[j+1]; addr=p[j+2:j+8]; rssi=struct.unpack('b',p[j+15:j+16])[0]; j+=16
     reports.append({'t':t_ms,'mac':norm_mac(addr),'rssi':rssi,'addr_type':at,'subevent':sub,'data_len':0})
  except Exception:
   malformed+=1
 return {'version':version,'datalink':datalink,'records':records,'start_ms':min(times) if times else None,'end_ms':max(times) if times else None,'duration_ms':(max(times)-min(times)) if times else 0,'reports':reports,'le_meta':dict(le_meta),'malformed':malformed}

# Extract every btsnoop-magic attachment from the private bugreport.
bug=os.path.join(out,'bugreport.zip'); extracted=[]
with zipfile.ZipFile(bug) as z:
 magic=[]
 for i in z.infolist():
  if i.is_dir() or i.file_size<16: continue
  try:
   with z.open(i) as f:
    if f.read(8)==b'btsnoop\x00': magic.append(i)
  except Exception: pass
 for idx,i in enumerate(sorted(magic,key=lambda x:x.filename),1):
  p=os.path.join(out,'hci',f'candidate-{idx}.btsnoop')
  with z.open(i) as src,open(p,'wb') as dst: shutil.copyfileobj(src,dst)
  parsed=parse_btsnoop(p)
  extracted.append({'private_entry':i.filename,'path':p,'bytes':os.path.getsize(p),'sha256':sha256(p),'parsed':parsed})

# Load all persisted samples. No active Session window survived, so derive latest contiguous activity burst.
d=json.load(open(os.path.join(out,'blueeye-session-export.json')))
samples=[s for s in (d.get('signalSamples') or []) if int(s.get('timestamp') or 0)>0]
samples.sort(key=lambda x:int(x.get('timestamp') or 0))
start_idx=0
for i in range(1,len(samples)):
 if int(samples[i].get('timestamp') or 0)-int(samples[i-1].get('timestamp') or 0)>15*60*1000: start_idx=i
burst=samples[start_idx:]
burst_start=int(burst[0]['timestamp']) if burst else None; burst_end=int(burst[-1]['timestamp']) if burst else None
collection_start=None
try:
 collection_start=int(dt.datetime.fromisoformat(open(os.path.join(out,'collection-start-utc.txt')).read().strip().replace('Z','+00:00')).timestamp()*1000)
except Exception: pass

def grid50(s):
 lat=s.get('latitude'); lon=s.get('longitude')
 if lat is None or lon is None:return None
 lat=float(lat); lon=float(lon); scale=max(.2,math.cos(math.radians(lat)))
 return (round(lat/.00045),round(lon/(.00045/scale)))

def quant(vals,p):
 vals=sorted(float(x) for x in vals if x is not None)
 if not vals:return None
 k=(len(vals)-1)*p; lo=int(k); hi=min(lo+1,len(vals)-1); f=k-lo
 return vals[lo]*(1-f)+vals[hi]*f

def burst_stats(rows):
 gps=[x for x in rows if x.get('latitude') is not None and x.get('longitude') is not None]
 acc=[x.get('locationAccuracy') for x in gps if x.get('locationAccuracy') is not None]
 by5=collections.defaultdict(lambda:{'samples':0,'devices':set()})
 if rows:
  base=int(rows[0]['timestamp'])
  for x in rows:
   b=(int(x['timestamp'])-base)//300000; by5[b]['samples']+=1; by5[b]['devices'].add(str(x.get('deviceFingerprint') or ''))
 sample_rates=[v['samples'] for v in by5.values()]; dev_rates=[len(v['devices']) for v in by5.values()]
 byfp=collections.defaultdict(list)
 for x in rows:byfp[str(x.get('deviceFingerprint') or '')].append(x)
 moving=0; reappear30=0
 for fp,xs in byfp.items():
  bins={grid50(x) for x in xs if grid50(x) is not None}
  if len(bins)>=2:moving+=1
  ts=sorted(int(x['timestamp']) for x in xs)
  if any(ts[i]-ts[i-1]>30000 for i in range(1,len(ts))):reappear30+=1
 return {'samples':len(rows),'devices':len(byfp),'duration_ms':(int(rows[-1]['timestamp'])-int(rows[0]['timestamp'])) if rows else 0,'gps_samples':len(gps),'gps_coverage_pct':round(100*len(gps)/len(rows),2) if rows else 0,'gps_accuracy_p50_m':quant(acc,.5),'gps_accuracy_p90_m':quant(acc,.9),'gps_accuracy_p95_m':quant(acc,.95),'gps_bins_50m':len({grid50(x) for x in gps}),'five_min_buckets':len(by5),'samples_per_5m_min':min(sample_rates) if sample_rates else None,'samples_per_5m_median':quant(sample_rates,.5),'samples_per_5m_max':max(sample_rates) if sample_rates else None,'devices_per_5m_min':min(dev_rates) if dev_rates else None,'devices_per_5m_median':quant(dev_rates,.5),'devices_per_5m_max':max(dev_rates) if dev_rates else None,'devices_across_multiple_50m_bins':moving,'devices_with_gt30s_reappearance_gap':reappear30}
bs=burst_stats(burst)

# Root device/evidence consistency because session review summaries were empty after process/session loss.
devices=d.get('devices') or []
tracking=collections.Counter(str(x.get('trackingStatus')) for x in devices)
attention=[x for x in devices if str(x.get('trackingStatus')) in ('SUSPICIOUS','DANGEROUS')]
attention_no_evidence=sum(1 for x in attention if not x.get('evidence'))
known=[x for x in devices if any(k in str(x.get('deviceType') or '').upper() for k in ('TRACKER','AIRTAG','SMARTTAG','TILE'))]
known_no_context=sum(1 for x in known if not (x.get('rawData') or x.get('evidence')))
identity_verdicts=collections.Counter(str(x.get('identityCarryoverVerdict')) for x in devices)

# App-scoped logcat check: only count an exception when nearby context names BlueEye/app-specific tags.
all_lines=[]
for n in ('logcat-before.txt','logcat-after-export.txt','logcat-final.txt'):
 p=os.path.join(out,n)
 if os.path.exists(p):
  all_lines.extend(open(p,errors='replace').read().splitlines())
app_mark=re.compile(r'(io\.blueeye|ScanIngestPipeline|ScannerService|BleScanner|BlueEye)',re.I)
def contextual_count(rx):
 r=re.compile(rx,re.I); hits=0
 for i,line in enumerate(all_lines):
  if r.search(line) and any(app_mark.search(x) for x in all_lines[max(0,i-6):min(len(all_lines),i+7)]):hits+=1
 return hits
app_log={
 'fatal_exception':contextual_count(r'FATAL EXCEPTION'),
 'oom':contextual_count(r'OutOfMemoryError'),
 'security_exception':contextual_count(r'SecurityException'),
 'sqlite_exception':contextual_count(r'SQLite(?:Exception|Constraint|DiskIOException|FullException)'),
 'scan_processing_failed':contextual_count(r'(Scan processing failed|Error processing scan event)'),
 'queue_rejected':contextual_count(r'Rejected \d+ scan event'),
 'process_death_or_kill_mentions':contextual_count(r'(Killing|has died|am_kill|Force stopping|lowmemory|lmkd)'),
}

# HCI-vs-Tracker correlation, candidate by candidate and union across snoop rotations.
def hci_corr(parsed):
 reports=parsed['reports']
 if not reports:return {'adv_reports':0,'unique_hci_macs':0,'overlap_ms':0,'tracker_samples_in_overlap':0,'tracker_unique_macs_in_overlap':0,'unique_mac_intersection':0,'tracker_samples_with_hci_same_mac_5s':0,'tracker_sample_time_match_pct':None}
 hs=min(x['t'] for x in reports); he=max(x['t'] for x in reports)
 os_=max(float(burst_start or hs),hs); oe=min(float(burst_end or he),he)
 tr=[x for x in burst if os_<=int(x['timestamp'])<=oe] if oe>=os_ else []
 h_by=collections.defaultdict(list)
 for x in reports:h_by[x['mac']].append(x['t'])
 for v in h_by.values():v.sort()
 trmac={str(x.get('observedMac') or '').upper() for x in tr if x.get('observedMac')}
 inter=trmac & set(h_by)
 matched=0
 for x in tr:
  m=str(x.get('observedMac') or '').upper(); t=int(x['timestamp']); arr=h_by.get(m)
  if not arr:continue
  j=bisect.bisect_left(arr,t); near=[]
  if j<len(arr):near.append(abs(arr[j]-t))
  if j:near.append(abs(arr[j-1]-t))
  if near and min(near)<=5000:matched+=1
 return {'adv_reports':len(reports),'adv_start_ms':hs,'adv_end_ms':he,'unique_hci_macs':len(h_by),'overlap_ms':max(0,oe-os_),'tracker_samples_in_overlap':len(tr),'tracker_unique_macs_in_overlap':len(trmac),'unique_mac_intersection':len(inter),'tracker_samples_with_hci_same_mac_5s':matched,'tracker_sample_time_match_pct':round(100*matched/len(tr),2) if tr else None,'hci_rssi_p50':quant([x['rssi'] for x in reports],.5),'hci_rssi_p90':quant([x['rssi'] for x in reports],.9)}

hc=[]
for x in extracted:
 p=x['parsed']; corr=hci_corr(p)
 hc.append({'bytes':x['bytes'],'sha256':x['sha256'],'version':p['version'],'datalink':p['datalink'],'records':p['records'],'duration_ms':p['duration_ms'],'le_meta_counts':p['le_meta'],'malformed':p['malformed'],'correlation':corr})
# Union reports with simple dedupe by timestamp/mac/rssi.
union=[]; seen=set()
for x in extracted:
 for r in x['parsed']['reports']:
  k=(round(r['t'],3),r['mac'],r['rssi'],r['subevent'])
  if k not in seen:seen.add(k);union.append(r)
union_parsed={'reports':union}
union_corr=hci_corr(union_parsed)

# Save sensitive address-level correlation privately, safe aggregate separately.
private={'hci_candidates':[{'entry':x['private_entry'],'path':x['path'],'bytes':x['bytes'],'sha256':x['sha256'],'parsed':{'version':x['parsed']['version'],'datalink':x['parsed']['datalink'],'records':x['parsed']['records'],'start_ms':x['parsed']['start_ms'],'end_ms':x['parsed']['end_ms'],'duration_ms':x['parsed']['duration_ms'],'le_meta':x['parsed']['le_meta'],'malformed':x['parsed']['malformed'],'reports':x['parsed']['reports']}} for x in extracted]}
json.dump(private,open(os.path.join(out,'phase3-hci-correlation-private.json'),'w'),indent=2)

safe={
 'session_window_preserved':bool((d.get('session') or {}).get('startedAt')) and int((d.get('session') or {}).get('sampleCount') or 0)>0,
 'latestBurst':bs,
 'collectionDelayAfterLatestSampleMs':(collection_start-burst_end) if collection_start and burst_end else None,
 'rootTrackingStatusCounts':dict(tracking),
 'rootAttentionDevices':len(attention),
 'rootAttentionWithoutEvidence':attention_no_evidence,
 'knownTrackerDevices':len(known),
 'knownTrackerWithoutRawOrEvidence':known_no_context,
 'identityCarryoverVerdictCounts':dict(identity_verdicts),
 'appLogcat':app_log,
 'hciCandidateCount':len(hc),
 'hciCandidates':hc,
 'hciUnionCorrelation':union_corr,
 'bugreportDeletedAfterHciExtraction':False,
}
# Full bugreport is no longer needed after all btsnoop-magic attachments are extracted and parsed.
if extracted:
 os.remove(bug)
 safe['bugreportDeletedAfterHciExtraction']=True
json.dump(safe,open(os.path.join(out,'phase3-analysis-stage2-safe.json'),'w'),indent=2)

print('PHASE3_STAGE2_ANALYSIS')
print('session_window_preserved='+str(safe['session_window_preserved']))
print('latest_burst samples=%s devices=%s duration_ms=%s gps_pct=%s gps_p50=%s gps_p90=%s bins50m=%s five_min_buckets=%s devices_per_5m=%s/%s/%s sample_rate_5m=%s/%s/%s collection_delay_ms=%s' % (bs['samples'],bs['devices'],bs['duration_ms'],bs['gps_coverage_pct'],bs['gps_accuracy_p50_m'],bs['gps_accuracy_p90_m'],bs['gps_bins_50m'],bs['five_min_buckets'],bs['devices_per_5m_min'],bs['devices_per_5m_median'],bs['devices_per_5m_max'],bs['samples_per_5m_min'],bs['samples_per_5m_median'],bs['samples_per_5m_max'],safe['collectionDelayAfterLatestSampleMs']))
print('latest_burst continuity_devices=%s reappear_gt30s_devices=%s' % (bs['devices_across_multiple_50m_bins'],bs['devices_with_gt30s_reappearance_gap']))
print('root_tracking='+json.dumps(dict(tracking),sort_keys=True)+' attention_without_evidence='+str(attention_no_evidence)+' known_trackers='+str(len(known))+' known_without_context='+str(known_no_context))
print('identity_verdicts='+json.dumps(dict(identity_verdicts),sort_keys=True))
print('app_logcat='+json.dumps(app_log,sort_keys=True))
print('hci_candidates='+str(len(hc)))
for i,x in enumerate(hc,1):
 c=x['correlation']; print('hci%d sha256=%s bytes=%s records=%s duration_ms=%s adv_reports=%s unique_hci_macs=%s overlap_ms=%s tracker_samples_overlap=%s tracker_unique_macs=%s mac_intersection=%s sample_match_5s=%s match_pct=%s le_meta=%s malformed=%s' % (i,x['sha256'],x['bytes'],x['records'],x['duration_ms'],c['adv_reports'],c['unique_hci_macs'],c['overlap_ms'],c['tracker_samples_in_overlap'],c['tracker_unique_macs_in_overlap'],c['unique_mac_intersection'],c['tracker_samples_with_hci_same_mac_5s'],c['tracker_sample_time_match_pct'],json.dumps(x['le_meta_counts'],sort_keys=True),x['malformed']))
print('hci_union='+json.dumps(union_corr,sort_keys=True))
print('bugreport_deleted_after_hci_extraction='+str(safe['bugreportDeletedAfterHciExtraction']))
print('stage2_safe_report='+os.path.join(out,'phase3-analysis-stage2-safe.json'))
PY
