"""Deterministic original drum and strummed-chord signals for Smart Click tests."""
from pathlib import Path
import math, random, struct, wave

root=Path(__file__).resolve().parents[1]/'verification'/'fixtures-0.4'/'tempo'
root.mkdir(parents=True,exist_ok=True)
rate=48000
for name in ('Drums','Guitar'):
    rng=random.Random(404)
    samples=[]
    for i in range(rate):
        t=(i/rate)%.5
        if name=='Drums':
            kick=.65*math.sin(2*math.pi*(55*t+1.8*(1-math.exp(-30*t))))*math.exp(-18*t)
            snare=.22*rng.uniform(-1,1)*math.exp(-35*t) if i>=rate//2 else 0
            sample=kick+snare
        else:
            sample=.18*sum(math.sin(2*math.pi*f*t) for f in (110,138.591,164.814,220))*math.exp(-7*t)*min(1,t/.003)
        samples.append(struct.pack('<h',round(max(-1,min(1,sample))*32767)))
    with wave.open(str(root/(name+'.wav')),'wb') as out:
        out.setparams((1,2,rate,0,'NONE','not compressed'))
        second=b''.join(samples)
        for _ in range(24):out.writeframesraw(second)
