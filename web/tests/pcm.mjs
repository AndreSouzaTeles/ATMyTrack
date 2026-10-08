import assert from 'node:assert/strict';
import {readAtRate,readPCM} from '../dist/pcm.js';
function source(rate,frequency){const samples=new Float32Array(rate);for(let i=0;i<rate;i++)samples[i]=Math.sin(i*2*Math.PI*frequency/rate);return {blob:new Blob([samples]),format:{rate,channels:1,bits:32,encoding:3,align:4,offset:0,frames:rate}};}
const s=source(44100,440),one=await readAtRate(s,1000,4800,48000),a=await readAtRate(s,1000,2400,48000),b=await readAtRate(s,3400,2400,48000);let error=0,boundary=0;for(let i=0;i<4800;i++){error=Math.max(error,Math.abs(one[0][i]-Math.sin((i+1000)*2*Math.PI*440/48000)));boundary=Math.max(boundary,Math.abs(one[0][i]-(i<2400?a[0][i]:b[0][i-2400])));}assert.ok(error<.002);assert.ok(boundary<.001);
const high=await readAtRate(source(96000,30000),1000,4800,48000),rms=Math.sqrt(high[0].reduce((n,x)=>n+x*x,0)/4800);assert.ok(rms<.02);
const ended=await readPCM(s,44100,128);assert.ok(ended.every(c=>c.every(x=>x===0)));console.log({resampleError:error,chunkBoundaryError:boundary,aliasRms:rms});
