import assert from 'node:assert/strict';
import {migrate,dbToPosition,positionToDb,targetKey,keyParts,normalize} from '../dist/parity.js';
import {estimateKey} from '../dist/key-worker.js';
const old={tracks:[{db:-60},{db:-20}],master:-60,buses:[{db:-60}],dcas:[]};
migrate(old);assert.equal(old.tracks[0].db,-80);assert.equal(old.tracks[1].db,-20);assert.equal(old.master,-80);assert.equal(old.buses[0].db,-80);assert.equal(old.clickFollow,true);assert.equal(old.denominator,4);
old.tracks[0].db=-65;migrate(old);assert.equal(old.tracks[0].db,-65);
for(let db=-80;db<=10;db+=.1)assert.ok(Math.abs(positionToDb(dbToPosition(db))-db)<.001);
assert.equal(dbToPosition(0),.78);assert.equal(normalize('Gratidão'),'gratidao');
assert.deepEqual(keyParts('Bb minor'),keyParts('A#m'));assert.equal(targetKey({key:'C',pitch:2}),'D');assert.equal(keyParts('Click'),null);
assert.equal(estimateKey(new Float32Array(48000),48000),null);
function chord(notes){const data=new Float32Array(48000*4);for(let i=0;i<data.length;i++)for(const note of notes)data[i]+=.12*Math.sin(2*Math.PI*440*2**((note-69)/12)*i/48000);return data;}
assert.equal(estimateKey(chord([48,52,55,60]),48000)?.name,'C');
assert.equal(estimateKey(chord([45,48,52,57]),48000)?.name,'Am');
console.log('Migration, fader law, normalized keys and FFT major/minor estimation passed');
