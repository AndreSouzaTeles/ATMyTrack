"""19 independent synthetic musical stems, 330 seconds. Not a live recording.
Host-only FFmpeg; its executable and generated audio are excluded from Git/APK.
"""
from pathlib import Path
import sys, subprocess, concurrent.futures, json
sys.path.insert(0,str(Path(__file__).parent/'fixture-runtime'))
import imageio_ffmpeg
ffmpeg=imageio_ffmpeg.get_ffmpeg_exe()
root=Path(__file__).resolve().parents[1]/'verification'/'session-0.5'
root.mkdir(parents=True,exist_ok=True)
names=['Click','Kick','Snare','HiHat','Tom','Shaker','Tambourine','Bass','GuitarL','GuitarR','Piano','Organ','Pad','Strings','Synth','Percussion','Lead','BackingL','BackingR']
def make(i):
    rate=44100 if i==10 else 96000 if i==14 else 48000
    ext=['wav','mp3','m4a','aac','flac'][i%5]
    frequency=[1200,55,180,6000,95,4500,3000,110,220,277.18,329.63,164.81,130.81,392,440,780,523.25,261.63,349.23][i]
    period=[.5,.5,1,.25,2,.25,.5,.5,1,1,.5,2,4,4,.25,.5,1,2,2][i]
    decay=90 if i<7 else 5 if i in [7,8,9,10,15] else .7
    # Distinct harmonic content, note changes every four seconds, rhythm and decay.
    freq=f'({frequency}*pow(2,mod(floor(t/4),4)/12))'
    left=f'.11*(sin(2*PI*{freq}*t)+.25*sin(4*PI*{freq}*t))*exp(-{decay}*mod(t,{period}))'
    if i==0:left='.25*sin(2*PI*1200*t)*exp(-100*mod(t,.5))'
    stereo=i>=8 and i%2==0
    expression=left+(' | '+left.replace('*t)', '*(t+.0003))') if stereo else '')
    args={'wav':['-c:a','pcm_s24le' if i%2 else 'pcm_s16le'],'mp3':['-c:a','libmp3lame','-b:a','128k'],'m4a':['-c:a','aac','-b:a','128k'],'aac':['-c:a','aac','-b:a','128k','-f','adts'],'flac':['-c:a','flac']}[ext]
    path=root/f'{i+1:02d}-{names[i]}.{ext}'
    subprocess.run([ffmpeg,'-hide_banner','-loglevel','error','-y','-f','lavfi','-i',f"aevalsrc='{expression}':s={rate}:d=330",*args,str(path)],check=True)
    return {'name':path.name,'rate':rate,'channels':2 if stereo else 1,'bytes':path.stat().st_size}
with concurrent.futures.ThreadPoolExecutor(max_workers=3) as pool:
    result=list(pool.map(make,range(19)))
(root/'manifest.json').write_text(json.dumps(result,indent=2))
print(json.dumps(result,indent=2))
