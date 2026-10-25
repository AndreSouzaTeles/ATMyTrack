import {loadAcousticKit} from './acoustic-kit.js';
import {getAudio,putAudio} from './storage.js';
import {globalRate} from './music.js';
export const patterns=['Pop / Rock','Rock 16','Balada','Disco','Funk','Samba','Bossa nova','Reggae','Shuffle','Afro / 6-8'];
export function percussionBar(pattern,bpm,meter,denominator,rate=48000,kit){
 const index=patterns.indexOf(pattern);if(index<0||!Number.isFinite(bpm)||bpm<15||bpm>600)throw Error('Batida ou BPM inválido.');
 const bar=Math.round(rate*60/bpm*meter*4/denominator),frames=bar*4,steps=index>=8?12:16,out=[new Float32Array(frames),new Float32Array(frames)],hits=[];
 const add=(voice,...positions)=>positions.forEach(step=>hits.push([step,voice]));
 switch(index){
 case 0:add(0,0,8);add(1,4,12);add(2,0,2,4,6,8,10,12,14);break;
 case 1:add(0,0,6,8,10);add(1,4,12);add(2,...Array.from({length:16},(_,i)=>i));break;
 case 2:add(0,0,10);add(4,4,12);add(2,0,4,8,12);break;
 case 3:add(0,0,4,8,12);add(1,4,12);add(3,2,6,10,14);break;
 case 4:add(0,0,3,7,10);add(1,4,12);add(2,0,2,5,6,8,10,13,14);break;
 case 5:add(0,0,6,8,14);add(4,2,5,10,13);add(5,0,3,7,8,11,15);add(6,...Array.from({length:16},(_,i)=>i));add(7,0,4,8,12);break;
 case 6:add(0,0,6,8,14);add(4,4,12);add(2,0,2,4,6,8,10,12,14);add(5,3,10);break;
 case 7:add(0,0,8);add(1,8);add(3,2,6,10,14);add(4,4,12);break;
 case 8:add(0,0,6);add(1,3,9);add(2,0,2,3,5,6,8,9,11);break;
 case 9:add(0,0,6);add(5,2,4,8,10);add(4,3,9);add(6,...Array.from({length:12},(_,i)=>i));break;
 }
 const roles=['kick','snare','hat','openhat','stick','conga','shaker','tamb','tom','crash','ride','ghost'];
 if(rate!==48000)throw Error('A biblioteca acústica usa 48 kHz.');
 for(let cycle=0;cycle<4;cycle++){
  const phrase=hits.map(hit=>[...hit,1]);
  if([0,1,3,4,8].includes(index)){
   if(cycle%2===1)phrase.push([index>=8?8:11,index>=8?10:11,.42]);
   if(cycle===3)phrase.push([steps-2,8,.62],[steps-1,8,.76]);
   if(cycle===0)phrase.push([0,9,.42]);
  }
  phrase.forEach(([step,voice,gain],hitIndex)=>{
   const variants=kit[roles[voice]],audio=variants[(cycle+hitIndex+step)%variants.length],jitter=step===0?0:((hitIndex*7+cycle*11+voice*3)%7-3)*24,start=(cycle*bar+Math.floor(step*bar/steps)+jitter+frames)%frames;
   const accent=[2,6,7].includes(voice)?step%4===0?1:step%2===0?.78:.56:1,level=gain*accent*(.94+((cycle*3+hitIndex)%5)*.015)*.78;
   for(let i=0;i<audio[0].length;i++){const frame=(start+i)%frames;out[0][frame]+=audio[0][i]*level;out[1][frame]+=audio[1][i]*level;}
  });
 }
 let peak=.9;for(const channel of out)for(const v of channel)peak=Math.max(peak,Math.abs(v));if(peak>.9)for(const channel of out)for(let i=0;i<channel.length;i++)channel[i]*=.9/peak;return out;
}
export class Rhythm {
 constructor(engine){this.engine=engine;this.settings={source:patterns[0],bpm:100,meter:4,denominator:4,bars:1,volume:.4,follow:false,...JSON.parse(localStorage.getItem('percussion')||'{}')};this.library=JSON.parse(localStorage.getItem('percussion-library')||'[]');this.enabled=false;this.metroArmed=false;this.revision=0;this.preparing=false;}
 save(){localStorage.setItem('percussion',JSON.stringify(this.settings));}
 disconnect(which){const node=this[which];if(node){try{node.port?.postMessage('stop');node.port?.close();node.disconnect();}catch{}this[which]=null;}}
 async click(q){this.disconnect('metro');for(const node of this.metroConnections||[])node.disconnect();this.metroConnections=[];if(!this.metroArmed||!q.click||q.clickFollow!==false)return;const c=await this.engine.context();await c.resume();if(!this.clickModule){await c.audioWorklet.addModule(new URL('./click-worklet.js',import.meta.url));this.clickModule=true;}
 this.metro=new AudioWorkletNode(c,'atmytrack-click',{numberOfInputs:0,numberOfOutputs:1,outputChannelCount:[1],processorOptions:{bpm:q.bpm*q.clickDivision,meter:q.meter,accent:q.clickAccent,kind:q.clickTone||0,tone:[1,.65,1.4,.8,1.8,.5,1.15,.88][q.clickTone||0],gain:q.clickDb<=-79.9?0:10**(q.clickDb/20),when:this.linkController?.linked?this.pairEpoch||c.currentTime+.02:c.currentTime+.02,position:0}});
 const mono=c.createGain();mono.channelCount=1;mono.channelCountMode='explicit';const pan=c.createStereoPanner();pan.pan.value=q.clickRoute==='left'?-1:q.clickRoute==='right'?1:0;this.metro.connect(mono).connect(pan).connect(c.destination);this.metroConnections=[mono,pan];
 }
 stopClick(){this.metroArmed=false;this.disconnect('metro');for(const n of this.metroConnections||[])n.disconnect();this.metroConnections=[];}
 async import(file){const c=await this.engine.context(),b=await c.decodeAudioData(await file.arrayBuffer());if(b.duration<.05||b.duration>60)throw Error('Importe loops entre 0,05 e 60 segundos.');const id=crypto.randomUUID();await putAudio('percussion:'+id,file);this.library.push({id,name:file.name});localStorage.setItem('percussion-library',JSON.stringify(this.library));this.settings.source=id;this.settings.bars=1;this.enabled=false;this.save();await this.refresh();}
 async refresh(){
 const revision=++this.revision;this.cancelWork?.();this.cancelWork=null;this.disconnect('beat');this.save();this.preparing=false;if(!this.enabled)return;
 const s={...this.settings},p=this.engine.project;if(s.follow&&!p)return;
 this.preparing=true;this.onchange?.();
 try{const c=await this.engine.context();await c.resume();if(revision!==this.revision)return;const bpm=s.follow?p.bpm*globalRate(p):s.bpm,meter=s.follow?p.meter:s.meter,denominator=s.follow?p.denominator||4:s.denominator;const key=JSON.stringify([s.source,bpm,meter,denominator,s.bars,c.sampleRate]);if(this.prepared?.key===key){this.startPrepared();return;}let channels;
 if(patterns.includes(s.source))channels=percussionBar(s.source,bpm,meter,denominator,48000,await loadAcousticKit());
 else{const blob=await getAudio('percussion:'+s.source);if(!blob)throw Error('Loop não encontrado. Importe novamente.');const b=await c.decodeAudioData(await blob.arrayBuffer()),target=c.sampleRate*60/bpm*meter*4/denominator*s.bars,ratio=b.length/target;if(target>c.sampleRate*120||ratio<.125||ratio>8)throw Error('Ajuste BPM/compassos: até 120 segundos, velocidade de 0,125× a 8×.');channels=await new Promise((resolve,reject)=>{const worker=new Worker(new URL('./dsp-worker.js',import.meta.url),{type:'module'});this.cancelWork=()=>{worker.terminate();reject(Error('Preparação cancelada.'));};const samples=[b.getChannelData(0).slice(),b.getChannelData(Math.min(1,b.numberOfChannels-1)).slice()];worker.onmessage=({data})=>{if(data.channels){worker.terminate();resolve(data.channels);}if(data.error){worker.terminate();reject(Error(data.error));}};worker.onerror=()=>{worker.terminate();reject(Error('Não foi possível ajustar o loop.'));};worker.postMessage({channels:samples,rate:c.sampleRate,speed:ratio,pitch:0},samples.map(x=>x.buffer));});}
 if(revision!==this.revision)return;this.cancelWork=null;this.prepared={channels,rate:patterns.includes(s.source)?48000:c.sampleRate,key};if(!this.module){await c.audioWorklet.addModule(new URL('./rhythm-worklet.js',import.meta.url));this.module=true;}if(revision!==this.revision)return;
 this.startPrepared();
 }catch(e){if(revision!==this.revision)return;this.enabled=false;this.onerror?.(e);throw e;}finally{if(revision===this.revision){this.preparing=false;this.onchange?.();}}
 }
 startPrepared(){if(this.pairPending)return;this.disconnect('beat');if(!this.enabled||!this.prepared)return;const s=this.settings,e=this.engine,c=e.ctx;if(s.follow&&!e.playing)return;
 this.beat=new AudioWorkletNode(c,'atmytrack-rhythm',{numberOfInputs:0,numberOfOutputs:1,outputChannelCount:[2],processorOptions:{channels:this.prepared.channels,rate:this.prepared.rate,volume:s.volume,when:s.follow?e.epoch:this.linkController?.linked?this.pairEpoch||c.currentTime+.02:c.currentTime+.02,position:s.follow?e.position:0,loop:s.follow?e.loopBounds():null}});
 this.beat.connect(s.follow?e.master:c.destination);
 }
 transport(){if(this.linkController?.linked){this.linkController.transport().catch(e=>this.onerror?.(e));return;}if(this.settings.follow){if(this.engine.playing)this.refresh().catch(()=>{});else this.disconnect('beat');}}
 volume(v){this.settings.volume=v;this.save();this.beat?.port.postMessage({volume:v});}
 stop(){this.enabled=false;this.revision++;this.disconnect('beat');this.stopClick();this.onchange?.();}
}
