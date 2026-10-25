"""Reproduce the bundled acoustic kit. Requires numpy, scipy and soundfile.
Downloads pinned CC0 recordings, resamples to stereo PCM16/48 kHz, trims silence,
and levels each instrument group together (preserving variation/velocity).
"""
import concurrent.futures, hashlib, json, math, os, pathlib, urllib.request, urllib.parse, wave
import numpy as np
import soundfile as sf
from scipy.signal import resample_poly

ROOT=pathlib.Path(__file__).resolve().parents[1]
OUT=ROOT/'app/src/main/assets/drumkit'
CACHE=pathlib.Path.home()/'.cache/atmytrack-android/drum-sources'
KIT=('sfzinstruments/virtuosity_drums','9f04cf9a734527edfbb0a4eee1f674e45bbf71bc')
PERC=('sgossner/VCSL','c1ea7bcc3c7309650ab0da9d15c9cd1fbc4a4c7e')
entries=[]
def add(role,repo,path): entries.append(dict(role=role,repository=repo[0],commit=repo[1],path=path,file=f'{role}-{sum(e["role"]==role for e in entries)}.wav'))
for rr in (1,2):
 add('kick',KIT,f'Samples/mid/kick/mid_kick_snoff_vl3_rr{rr}.flac')
 add('hat',KIT,f'Samples/mid/hh/mid_hh_closed_vl3_rr{rr}.flac')
 add('openhat',KIT,f'Samples/mid/hh/mid_hh_open_vl2_rr{rr}.flac')
 add('crash',KIT,f'Samples/mid/crash/mid_crash_crash_vl2_rr{rr}.flac')
 add('ride',KIT,f'Samples/mid/ride/mid_ride_ride_vl2_rr{rr}.flac')
for level in (24,27): add('snare',KIT,f'Samples/mid/snare/mid_snare_center_vl{level}.flac')
for level in (8,10): add('ghost',KIT,f'Samples/mid/snare/mid_snare_center_vl{level}.flac')
for level in (12,14): add('stick',KIT,f'Samples/mid/snare/mid_snare_crossstick_vl{level}.flac')
for level in (11,13): add('tom',KIT,f'Samples/mid/ltom/mid_ltom_center_vl{level}.flac')
for rr in (1,2):
 add('conga',PERC,f'Membranophones/Struck Membranophones/Conga/Conga_HitN_v2_rr{rr}_Sum.wav')
 add('shaker',PERC,f'Idiophones/Struck Idiophones/Shaker, Small/Mid_ShakerDouble_Down_rr{rr}.wav')
 add('tamb',PERC,f'Idiophones/Struck Idiophones/Tambourine 1/Tamb1_Shake_rr{rr}_Mid.wav')

def fetch(entry):
 url=f'https://raw.githubusercontent.com/{entry["repository"]}/{entry["commit"]}/'+urllib.parse.quote(entry['path'])
 path=CACHE/(hashlib.sha256(url.encode()).hexdigest()+pathlib.Path(entry['path']).suffix)
 if not path.exists():
  with urllib.request.urlopen(url,timeout=60) as response: path.write_bytes(response.read())
 data,rate=sf.read(path,dtype='float32',always_2d=True)
 if data.shape[1]==1:data=np.repeat(data,2,axis=1)
 data=data[:,:2]
 if rate!=48000:
  g=math.gcd(rate,48000);data=resample_poly(data,48000//g,rate//g,axis=0)
 # Retain 1 ms pre-attack and natural tails up to 3.5 s (cymbals 5 s).
 peak=float(np.max(np.abs(data)));active=np.flatnonzero(np.max(np.abs(data),axis=1)>max(peak*.0008,.00001))
 if not active.size:raise ValueError('Silent recording: '+entry['path'])
 start=max(0,int(active[0])-48);end=min(len(data),int(active[-1])+240,start+int(48000*(5 if entry['role'] in ('crash','ride') else 3.5)))
 data=data[start:end].copy();fade=min(480,len(data));data[-fade:]*=np.linspace(1,0,fade)[:,None]
 entry.update(source_sha256=hashlib.sha256(path.read_bytes()).hexdigest(),source_rate=rate,trim_start_frames=start)
 return entry,data

if __name__=='__main__':
 OUT.mkdir(parents=True,exist_ok=True);CACHE.mkdir(parents=True,exist_ok=True)
 with concurrent.futures.ThreadPoolExecutor(max_workers=6) as pool: prepared=list(pool.map(fetch,entries))
 targets={'kick':.82,'snare':.68,'ghost':.18,'hat':.27,'openhat':.29,'stick':.45,'conga':.53,'shaker':.22,'tamb':.27,'tom':.65,'crash':.35,'ride':.30}
 group_peaks={role:max(float(np.max(np.abs(data))) for e,data in prepared if e['role']==role) for role in targets}
 for entry,data in prepared:
  data*=targets[entry['role']]/group_peaks[entry['role']]
  with wave.open(str(OUT/entry['file']),'wb') as out:
   out.setparams((2,2,48000,0,'NONE','not compressed'));out.writeframes(np.round(np.clip(data,-1,1)*32767).astype('<i2').tobytes())
  entry.update(frames=len(data),sha256=hashlib.sha256((OUT/entry['file']).read_bytes()).hexdigest())
 for repo,name in ((KIT,'Virtuosity'),(PERC,'VCSL')):
  with urllib.request.urlopen(f'https://raw.githubusercontent.com/{repo[0]}/{repo[1]}/LICENSE') as r:(OUT/f'LICENSE-{name}-CC0.txt').write_bytes(r.read())
 (OUT/'bank.tsv').write_text('\n'.join(f'{e["role"]}\t{e["file"]}' for e in entries)+'\n',encoding='utf8')
 (OUT/'provenance.json').write_text(json.dumps({'license':'CC0-1.0','rate':48000,'channels':2,'samples':entries},indent=2)+'\n',encoding='utf8')
 print(json.dumps({'samples':len(entries),'bytes':sum((OUT/e['file']).stat().st_size for e in entries)},indent=2))
