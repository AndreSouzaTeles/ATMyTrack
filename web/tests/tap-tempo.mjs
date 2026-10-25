import assert from 'node:assert/strict';
import {TapTempo} from '../dist/tap-tempo.js';
let tap=new TapTempo();assert.equal(tap.tap(1000),null);assert.equal(tap.tap(1500),120);assert.equal(tap.tap(1550),null);assert.equal(tap.tap(2000),120);assert.equal(tap.tap(5000),null);assert.equal(tap.tap(5600),100);
tap=new TapTempo();tap.tap(100);assert.equal(tap.tap(250),300);tap=new TapTempo();tap.tap(100);assert.equal(tap.tap(2200),30);
console.log('TAP BPM averaging, bounce rejection, reset and limits passed');
