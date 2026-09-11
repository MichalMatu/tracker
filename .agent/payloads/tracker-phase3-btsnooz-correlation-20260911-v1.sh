#!/usr/bin/env bash
set -euo pipefail
ROOT="$HOME/agent-workspace/repos/tracker/checkpoints/phase3-field-reacceptance-20260911"
STAMP="$(cat "$ROOT/LATEST")"
OUT="$ROOT/$STAMP"
PKG='io.blueeye'
EXPECTED_MODEL='SM-S906B'
DEVS=( $(adb devices | awk 'NR>1 && $2=="device" {print $1}') )
test "${#DEVS[@]}" -eq 1
SERIAL="${DEVS[0]}"
test "$(adb -s "$SERIAL" shell getprop ro.product.model | tr -d '\r')" = "$EXPECTED_MODEL"

adb -s "$SERIAL" shell dumpsys bluetooth_manager > "$OUT/adb-bluetooth-before-btsnooz-bugreport.txt" || true
if grep -qi 'snoop_logger_tracing' "$OUT/adb-bluetooth-before-btsnooz-bugreport.txt"; then echo 'snoop_logger_tracing_marker=1'; else echo 'snoop_logger_tracing_marker=0'; fi

BUG="$OUT/bugreport-btsnooz.zip"
rm -f "$BUG"
adb -s "$SERIAL" bugreport "$BUG" > "$OUT/bugreport-btsnooz-command.txt" 2>&1
test -s "$BUG"
echo 'fresh_bugreport_captured=1'

python3 - "$OUT" <<'PY'
import base64,bisect,collections,hashlib,json,math,os,re,struct,sys,zipfile,zlib
out=sys.argv[1]
bug=os.path.join(out,'bugreport-btsnooz.zip')
export=os.path.join(out,'blueeye-session-export.json')
EPOCH=0x00dcddb30f2f8000
TYPE_IN_EVT=0x10; TYPE_IN_ACL=0x11; TYPE_IN_SCO=0x12; TYPE_IN_ISO=0x17
TYPE_OUT_CMD=0x20; TYPE_OUT_ACL=0x21; TYPE_OUT_SCO=0x22; TYPE_OUT_ISO=0x2d

def type_dir(t): return 1 if t in (TYPE_IN_EVT,TYPE_IN_ACL,TYPE_IN_SCO,TYPE_IN_ISO) else 0
def type_h4(t):
 if t==TYPE_OUT_CMD:return b'\x01'
 if t in (TYPE_IN_ACL,TYPE_OUT_ACL):return b'\x02'
 if t in (TYPE_IN_SCO,TYPE_OUT_SCO):return b'\x03'
 if t==TYPE_IN_EVT:return b'\x04'
 if t in (TYPE_IN_ISO,TYPE_OUT_ISO):return b'\x05'
 raise ValueError('unknown snooz type %r'%t)

def extract_section():
 with zipfile.ZipFile(bug) as z:
  # Prefer the primary bugreport text, regardless of size.
  infos=[i for i in z.infolist() if not i.is_dir()]
  txts=sorted([i for i in infos if os.path.basename(i.filename).startswith('bugreport-') and i.filename.lower().endswith('.txt')], key=lambda i:i.file_size, reverse=True)
  if not txts:
   txts=sorted([i for i in infos if i.filename.lower().endswith('.txt')],key=lambda i:i.file_size,reverse=True)
  checked=[]
  for info in txts:
   checked.append((info.filename,info.file_size))
   found=False; chunks=[]
   with z.open(info) as f:
    for raw in f:
     if not found:
      if b'--- BEGIN:BTSNOOP_LOG_SUMMARY' in raw:
       found=True
      continue
     if b'--- END:BTSNOOP_LOG_SUMMARY' in raw:
      return b''.join(chunks),info.filename,checked
     chunks.append(raw.strip())
  return None,None,checked

def decode_snooz(b64):
 snooz=base64.standard_b64decode(b64)
 if len(snooz)<9: raise ValueError('short btsnooz')
 version,last_ts=struct.unpack_from('=bQ',snooz)
 if version not in (1,2):raise ValueError('unsupported btsnooz version %r'%version)
 dec=zlib.decompress(snooz[9:])
 rec=[];off=0; first=last_ts+EPOCH
 if version==1:
  while off<len(dec):
   length,delta,t=struct.unpack_from('=HIb',dec,off); off+=7+length-1; first-=delta
  off=0
  while off<len(dec):
   length,delta,t=struct.unpack_from('=HIb',dec,off); first+=delta; off+=7
   payload=type_h4(t)+dec[off:off+length-1]; off+=length-1
   rec.append((length,length,type_dir(t),0,first,payload))
 else:
  while off<len(dec):
   length,packet_length,delta,t=struct.unpack_from('=HHIb',dec,off); off+=9+length-1; first-=delta
  off=0
  while off<len(dec):
   length,packet_length,delta,t=struct.unpack_from('=HHIb',dec,off); first+=delta; off+=9
   payload=type_h4(t)+dec[off:off+length-1]; off+=length-1
   rec.append((packet_length,length,type_dir(t),0,first,payload))
 outb=bytearray(b'btsnoop\x00'+struct.pack('>II',1,1002))
 for orig,inc,flags,drops,stamp,payload in rec:
  outb += struct.pack('>IIIIQ',orig,inc,flags,drops,stamp)+payload
 return version,bytes(outb)

def mac(addr):return ':'.join(f'{x:02X}' for x in addr[::-1])
def parse(path):
 data=open(path,'rb').read(); pos=16; reports=[]; ptypes=collections.Counter(); evs=collections.Counter(); subs=collections.Counter(); times=[]; malformed=0
 while pos+24<=len(data):
  orig,inc,flags,drops,stamp=struct.unpack('>IIIIQ',data[pos:pos+24]);pos+=24
  if inc>len(data)-pos:malformed+=1;break
  pkt=data[pos:pos+inc];pos+=inc; t=(stamp-EPOCH)/1000.0;times.append(t)
  if not pkt:continue
  ptypes[pkt[0]]+=1
  if pkt[0]!=4 or len(pkt)<3:continue
  code=pkt[1];evs[code]+=1
  if code!=0x3e:continue
  plen=pkt[2]; params=pkt[3:3+plen]
  if not params:continue
  sub=params[0];subs[sub]+=1;p=params[1:]
  try:
   if sub==0x02 and p:
    n=p[0];j=1
    for _ in range(n):
     if j+9>len(p):break
     at=p[j+1];addr=p[j+2:j+8];dl=p[j+8];j+=9
     if j+dl+1>len(p):break
     j+=dl;rssi=struct.unpack('b',p[j:j+1])[0];j+=1
     reports.append({'t':t,'mac':mac(addr),'rssi':rssi,'sub':sub})
   elif sub==0x0d and p:
    n=p[0];j=1
    for _ in range(n):
     if j+24>len(p):break
     addr=p[j+3:j+9];rssi=struct.unpack('b',p[j+13:j+14])[0];dl=p[j+23];j+=24
     if j+dl>len(p):break
     j+=dl;reports.append({'t':t,'mac':mac(addr),'rssi':rssi,'sub':sub})
   elif sub==0x0b and p:
    n=p[0];j=1
    for _ in range(n):
     if j+16>len(p):break
     addr=p[j+2:j+8];rssi=struct.unpack('b',p[j+15:j+16])[0];j+=16
     reports.append({'t':t,'mac':mac(addr),'rssi':rssi,'sub':sub})
  except Exception:malformed+=1
 return {'records':len(times),'start_ms':min(times) if times else None,'end_ms':max(times) if times else None,'packetTypes':dict(ptypes),'eventCodes':dict(evs),'leSubevents':dict(subs),'reports':reports,'malformed':malformed}

b64,entry,checked=extract_section()
res={'sectionFound':bool(b64),'textEntry':entry,'checkedTextEntries':len(checked)}
if not b64:
 json.dump(res,open(os.path.join(out,'phase3-btsnooz-safe.json'),'w'),indent=2)
 print('btsnooz_section_found=0 checked_text_entries='+str(len(checked)))
 sys.exit(0)
version,btsnoop=decode_snooz(b64)
path=os.path.join(out,'hci','bugreport-btsnooz.btsnoop')
os.makedirs(os.path.dirname(path),exist_ok=True)
open(path,'wb').write(btsnoop)
h=hashlib.sha256(btsnoop).hexdigest(); p=parse(path)
D=json.load(open(export)); ss=sorted([x for x in (D.get('signalSamples') or []) if int(x.get('timestamp') or 0)>0],key=lambda x:int(x['timestamp']))
start_i=0
for i in range(1,len(ss)):
 if int(ss[i]['timestamp'])-int(ss[i-1]['timestamp'])>15*60*1000:start_i=i
walk=ss[start_i:]; ws=int(walk[0]['timestamp']) if walk else 0; we=int(walk[-1]['timestamp']) if walk else 0
reports=p['reports']; hs=p['start_ms'];he=p['end_ms']; os_=max(ws,hs or ws);oe=min(we,he or we)
tr=[x for x in walk if oe>=os_ and os_<=int(x['timestamp'])<=oe]
by=collections.defaultdict(list)
for r in reports:by[r['mac']].append(r['t'])
for v in by.values():v.sort()
trmac={str(x.get('observedMac') or '').upper() for x in tr if x.get('observedMac')};inter=trmac&set(by);matched=0
for x in tr:
 arr=by.get(str(x.get('observedMac') or '').upper());t=int(x['timestamp'])
 if not arr:continue
 j=bisect.bisect_left(arr,t);near=[]
 if j<len(arr):near.append(abs(arr[j]-t))
 if j:near.append(abs(arr[j-1]-t))
 if near and min(near)<=5000:matched+=1
corr={'overlapMs':max(0,oe-os_) if hs is not None and he is not None else 0,'advReports':len(reports),'uniqueHciMacs':len(by),'trackerSamplesInOverlap':len(tr),'trackerUniqueMacsInOverlap':len(trmac),'uniqueMacIntersection':len(inter),'trackerSamplesWithSameMacWithin5s':matched,'sampleMatchPct':round(100*matched/len(tr),2) if tr else None}
res.update({'sectionFound':True,'btsnoozVersion':version,'btsnoopBytes':len(btsnoop),'btsnoopSha256':h,'records':p['records'],'startMs':p['start_ms'],'endMs':p['end_ms'],'startMinusWalkStartMs':(p['start_ms']-ws) if p['start_ms'] is not None and ws else None,'endMinusWalkEndMs':(p['end_ms']-we) if p['end_ms'] is not None and we else None,'packetTypes':p['packetTypes'],'eventCodes':p['eventCodes'],'leSubevents':p['leSubevents'],'malformed':p['malformed'],'correlation':corr})
json.dump(res,open(os.path.join(out,'phase3-btsnooz-safe.json'),'w'),indent=2)
print('btsnooz_section_found=1 version=%s bytes=%s sha256=%s records=%s' % (version,len(btsnoop),h,p['records']))
print('btsnooz_times start_minus_walk_ms=%s end_minus_walk_ms=%s' % (res['startMinusWalkStartMs'],res['endMinusWalkEndMs']))
print('btsnooz_hci packet_types=%s event_codes=%s le_subevents=%s malformed=%s' % (json.dumps(p['packetTypes'],sort_keys=True),json.dumps(p['eventCodes'],sort_keys=True),json.dumps(p['leSubevents'],sort_keys=True),p['malformed']))
print('btsnooz_correlation='+json.dumps(corr,sort_keys=True))
PY

# The full fresh bugreport is sensitive and no longer needed after extracting/attempting btsnooz.
rm -f "$BUG"
echo 'fresh_bugreport_deleted=1'
