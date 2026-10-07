"""Original synthetic fixtures. FFmpeg is a host-only test tool, never an APK dependency."""
from pathlib import Path
import math, struct, wave, subprocess, sys, json
sys.path.insert(0, str(Path(__file__).parent / 'fixture-runtime'))
import imageio_ffmpeg
root=Path(__file__).resolve().parents[1]/'verification'/'fixtures-0.4'
root.mkdir(parents=True,exist_ok=True)
rate=48000
with wave.open(str(root/'Aligned.wav'),'wb') as out:
    out.setparams((1,2,rate,0,'NONE','not compressed'))
    second=b''.join(struct.pack('<h',int(32767*(.22*math.sin(2*math.pi*220*i/rate)+.5*math.sin(2*math.pi*1000*i/rate)*math.exp(-90*((i/rate)%.5))))) for i in range(rate))
    for _ in range(180):out.writeframesraw(second)
ffmpeg=imageio_ffmpeg.get_ffmpeg_exe()
for ext,args in [('mp3',['-c:a','libmp3lame','-b:a','192k']),('m4a',['-c:a','aac','-b:a','192k']),('aac',['-c:a','aac','-b:a','192k','-f','adts']),('flac',['-c:a','flac']),('ogg',['-c:a','libvorbis','-q:a','5']),('opus',['-c:a','libopus','-b:a','128k'])]:
    subprocess.run([ffmpeg,'-hide_banner','-loglevel','error','-y','-i',str(root/'Aligned.wav'),*args,str(root/('Aligned.'+ext))],check=True)
print(json.dumps({p.name:p.stat().st_size for p in root.iterdir()},indent=2))
