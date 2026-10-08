"""Creates 30 WAVs / 5.47 GB for large-project.cjs. Run explicitly; needs disk space."""
from pathlib import Path
import math, struct
root=Path(__file__).resolve().parents[2]/'verification'/'fixtures-web-large'
root.mkdir(exist_ok=True)
rate=48000; seconds=950; size=rate*seconds*4
header=b'RIFF'+struct.pack('<I',size+36)+b'WAVEfmt '+struct.pack('<IHHIIHH',16,1,2,rate,rate*4,4,16)+b'data'+struct.pack('<I',size)
block=bytearray(rate*4)
for i in range(rate):
    sample=int(math.sin(2*math.pi*220*i/rate)*1500)
    struct.pack_into('<hh',block,i*4,sample,sample)
for i in range(30):
    path=root/f'Track-{i+1:02d}.wav'
    if path.exists() and path.stat().st_size==size+44: continue
    with path.open('wb',buffering=1024*1024) as output:
        output.write(header)
        for _ in range(seconds): output.write(block)
print(f'30 files, {30*(size+44)} bytes in {root}')
