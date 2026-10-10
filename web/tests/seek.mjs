import assert from 'node:assert/strict';
import {Engine} from '../dist/engine.js';
import {newProject} from '../dist/music.js';
const e=new Engine();e.project=newProject('seek');e.project.tracks=[{duration:100,speed:100}];e.playing=true;const gates=[];let plays=0;
e.pause=()=>{e.playing=false};e.play=async()=>{plays++;await new Promise(resolve=>gates.push(resolve));e.playing=true};
const first=Array.from({length:1000},(_,i)=>e.seek(i/10));await Promise.resolve();assert.equal(plays,1);
const second=Array.from({length:1000},(_,i)=>e.seek(i===999?88:i/10));assert.equal(plays,1);gates.shift()();await new Promise(r=>setImmediate(r));assert.equal(plays,2);assert.equal(e.position,88);gates.shift()();await Promise.all([...first,...second]);assert.equal(plays,2);assert.equal(e.position,88);assert.equal(e.playing,true);
console.log('2000 seeks coalesced into 2 operations; final target and playback preserved');
