import assert from 'node:assert/strict';
import {frequency,yin,tunerReading,noteName,globalRate,duration} from '../dist/music.js';
let maxError=0;
for(let midi=23;midi<=88;midi++)for(const offset of [-35,0,35]){const f=frequency(midi)*2**(offset/1200),signal=Float32Array.from({length:4096},(_,i)=>.3*Math.sin(2*Math.PI*f*i/24000)),r=yin(signal,24000);for(const manual of [-1,0,5]){const d=tunerReading(r.frequency,[40,45,50,55,59,64],manual);assert.equal(d.midi,midi);maxError=Math.max(maxError,Math.abs(d.cents-offset));assert.ok(Math.abs(d.cents-offset)<1,`${midi} ${offset} ${d.cents}`);}}
assert.equal(noteName(tunerReading(frequency(66),[40,45,50,55,59,64],5).midi),'F#4');assert.equal(yin(new Float32Array(4096),24000).frequency,0);assert.equal(globalRate({tracks:[{speed:80},{speed:80}]}),.8);assert.equal(duration({tracks:[{duration:8,speed:80}]}),10);console.log(JSON.stringify({cases:594,maxCentsError:maxError,status:'passed'}));
