import assert from 'node:assert/strict';
import {peakEnvelope,projectEnvelope} from '../dist/waveform.js';
const rate=48000,n=rate*4,left=new Float32Array(n),right=new Float32Array(n);
for(let i=0;i<n;i++)if(i<rate||i>=rate*2)right[i]=.4*Math.sin(2*Math.PI*220*i/rate);
const peaks=peakEnvelope([left,right]);assert.ok(peaks.slice(0,128).every(v=>v>.39));assert.ok(peaks.slice(128,256).every(v=>v===0));assert.ok(peaks.slice(256).every(v=>v>.39));
const combined=projectEnvelope([{duration:4,peaks:peakEnvelope([left])},{duration:4,peaks}]);assert.deepEqual(combined,peaks);
const impulse=new Float32Array(n);impulse[12345]=.7;assert.ok(peakEnvelope([impulse]).some(v=>v>.69));
console.log('Waveform: stereo, silence, second track and short transient passed');
