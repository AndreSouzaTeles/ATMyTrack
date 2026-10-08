import {gain,globalRate,duration} from './music.js';
import {getAudio} from './storage.js';

// All tracks share one AudioContext clock. At unity, native buffer sources bypass DSP.
export class Engine {
  constructor(){this.buffers=new Map();this.cache=new Map();this.nodes=[];this.playing=false;this.position=0;this.epoch=0;this.busy=false;this.generation=0;}
  async context(){if(!this.ctx){this.ctx=new AudioContext({latencyHint:'playback'});this.master=this.ctx.createGain();this.limiter=this.ctx.createDynamicsCompressor();this.limiter.threshold.value=-1;this.limiter.knee.value=0;this.limiter.ratio.value=20;this.limiter.attack.value=.002;this.limiter.release.value=.1;this.master.connect(this.limiter);this.limiter.connect(this.ctx.destination);this.outputMeter=this.ctx.createAnalyser();this.outputMeter.fftSize=256;this.outputMeterData=new Float32Array(256);this.master.connect(this.outputMeter);this.ctx.addEventListener('statechange',()=>{if(this.playing&&this.ctx.state!=='running'){this.pause();this.onInterrupted?.();}});}return this.ctx;}
  async decode(blob){const c=await this.context();return c.decodeAudioData(await blob.arrayBuffer());}
  async load(p,status=()=>{}){this.pause();this.ready=false;this.project=p;this.position=0;this.buffers.clear();this.cache.clear();let bytes=0;for(let i=0;i<p.tracks.length;i++){const t=p.tracks[i];status(`Preparando ${i+1}/${p.tracks.length} · ${t.name}`);const blob=await getAudio(t.id);if(!blob)throw Error(`Arquivo não encontrado: ${t.name}. Restaure um backup.`);const buffer=await this.decode(blob);bytes+=buffer.length*buffer.numberOfChannels*4;if(bytes>384*1024*1024)throw Error('Este projeto ultrapassa o limite de 384 MB de áudio decodificado da versão web. Use menos tracks ou arquivos mais curtos.');this.buffers.set(t.id,buffer);}this.bytes=bytes;this.ready=true;}
  current(){if(!this.playing)return this.position;let t=this.position+Math.max(0,this.ctx.currentTime-this.epoch);const loop=this.loopBounds();if(loop&&t>=loop[1])t=loop[0]+(t-loop[0])%(loop[1]-loop[0]);return Math.min(t,duration(this.project));}
  loopBounds(){const p=this.project;if(!p?.loop)return null;const m=p.markers.find(x=>x.id===p.selectedMarker),r=globalRate(p);return m?[m.start/r,m.end/r]:[0,duration(p)];}
  async prepare(track,buffer,generation){
    const speed=(track.speed||100)/100,pitch=track.pitch||0;
    if(speed===1&&pitch===0){this.cache.delete(track.id);return buffer;}
    const key=`${speed}:${pitch}`,cached=this.cache.get(track.id);
    if(cached?.key===key)return cached.buffer;
    this.cache.delete(track.id);
    const bytes=(this.bytes||0)+[...this.cache.values()].reduce((n,x)=>n+x.buffer.length*x.buffer.numberOfChannels*4,0)+Math.round(buffer.length/speed)*buffer.numberOfChannels*4;
    if(bytes>384*1024*1024)throw Error('Este processamento ultrapassa a memória reservada na web. Reduza o projeto ou volte a 100%.');
    const worker=new Worker(new URL('./dsp-worker.js',import.meta.url),{type:'module'});
    const channels=Array.from({length:buffer.numberOfChannels},(_,i)=>buffer.getChannelData(i).slice());
    const processed=await new Promise((resolve,reject)=>{
      this.cancelPreparation=()=>{worker.terminate();reject(new Error('Preparação cancelada.'));};
      worker.onmessage=({data})=>{
        if(data.progress!==undefined)this.onPrepare?.(`${track.name} · ${Math.round(data.progress*100)}%`);
        if(data.error){worker.terminate();reject(new Error(track.name+': '+data.error));}
        if(data.channels){worker.terminate();resolve(data.channels);}
      };
      worker.onerror=()=>{worker.terminate();reject(new Error('Falha ao preparar '+track.name));};
      worker.postMessage({channels,rate:buffer.sampleRate,speed,pitch},channels.map(x=>x.buffer));
    });
    this.cancelPreparation=null;
    if(generation!==this.generation)throw Error('Preparação cancelada.');
    const result=this.ctx.createBuffer(processed.length,processed[0].length,buffer.sampleRate);
    processed.forEach((data,i)=>result.copyToChannel(data,i));
    this.cache.set(track.id,{key,buffer:result});return result;
  }
  async play(){
    if(this.playing||this.busy||!this.project?.tracks.length)return;
    this.busy=true;const generation=++this.generation,prepared=[];
    try{
      const c=await this.context();await c.resume();
      if(!this.clickModule){await c.audioWorklet.addModule(new URL('./click-worklet.js',import.meta.url));this.clickModule=true;}
      if(this.position>=duration(this.project))this.position=0;
      for(const t of this.project.tracks){
        const original=this.buffers.get(t.id);if(!original)throw Error('Importe novamente a track '+t.name);
        const b=await this.prepare(t,original,generation);if(generation!==this.generation)return;
        const source=c.createBufferSource();source.buffer=b;
        const loop=this.loopBounds();
        if(loop&&loop[1]>b.duration){
          const length=Math.ceil(loop[1]*c.sampleRate);
          if((this.bytes||0)+length*b.numberOfChannels*4>384*1024*1024)throw Error('Loop exige memória além do limite da versão web.');
          const padded=c.createBuffer(b.numberOfChannels,length,c.sampleRate);
          for(let i=0;i<b.numberOfChannels;i++)padded.copyToChannel(b.getChannelData(i),i);source.buffer=padded;
        }
        prepared.push({t,source});
      }
      const when=c.currentTime+.1,loop=this.loopBounds();this.nodes=prepared;this.busNodes=new Map();
      for(const b of this.project.buses){const g=c.createGain();this.busNodes.set(b.id,g);this.route(g,b.route||'master');}
      for(const n of this.nodes){
        n.gain=c.createGain();n.pan=c.createStereoPanner();n.analyser=c.createAnalyser();n.analyser.fftSize=256;n.meterData=new Float32Array(256);
        n.source.connect(n.gain).connect(n.pan).connect(n.analyser);this.route(n.analyser,n.t.route||'master');
        if(loop){n.source.loop=true;n.source.loopStart=loop[0];n.source.loopEnd=Math.min(loop[1],n.source.buffer.duration);}
        if(this.position<n.source.buffer.duration)n.source.start(when,this.position);
      }
      this.epoch=when;this.playing=true;this.updateMix();this.startClick(when);
      this.timer=setInterval(()=>{if(!this.loopBounds()&&this.current()>=duration(this.project))this.pause();},100);
    }catch(e){for(const n of prepared)n.source.disconnect();this.dispose();this.playing=false;throw e;}
    finally{this.busy=false;this.cancelPreparation=null;}
  }
  route(node,destination){if(this.busNodes?.has(destination)){node.connect(this.busNodes.get(destination));return;}if(destination==='left'||destination==='right'){const mono=this.ctx.createGain();mono.channelCount=1;mono.channelCountMode='explicit';const merger=this.ctx.createChannelMerger(2);node.connect(mono);mono.connect(merger,0,destination==='left'?0:1);merger.connect(this.master);(this.routes ||= []).push(mono,merger);}else node.connect(this.master);}
  updateMix(){if(!this.ctx||!this.project)return;const p=this.project,now=this.ctx.currentTime,solo=p.tracks.some(t=>t.solo);this.master.gain.setTargetAtTime(p.masterMute?0:gain(p.master),now,.01);for(const n of this.nodes){const t=p.tracks.find(t=>t.id===n.t.id);if(!t)continue;let g=t.mute||(solo&&!t.solo)?0:gain(t.db);for(const d of p.dcas)if(d.members.includes(t.id))g*=d.mute?0:gain(d.db);n.gain?.gain.setTargetAtTime(g,now,.01);n.pan?.pan.setTargetAtTime(t.pan,now,.01);}for(const b of p.buses)this.busNodes?.get(b.id)?.gain.setTargetAtTime(b.mute?0:gain(b.db),now,.01);}
  startClick(when){const p=this.project;if(!p.click)return;this.click=new AudioWorkletNode(this.ctx,'atmytrack-click',{numberOfInputs:0,numberOfOutputs:1,outputChannelCount:[1],processorOptions:{bpm:p.bpm*globalRate(p)*p.clickDivision,meter:p.meter,accent:p.clickAccent,tone:[1,.65,1.4,.8,1.8,.5,1.15][p.clickTone||0],gain:gain(p.clickDb),when,position:this.position,loop:this.loopBounds()}});this.route(this.click,p.clickRoute);}
  meters(){const readings=this.nodes.map(n=>{n.analyser.getFloatTimeDomainData(n.meterData);let peak=0;for(const x of n.meterData)peak=Math.max(peak,Math.abs(x));return [n.t.id,peak];});if(this.outputMeter){this.outputMeter.getFloatTimeDomainData(this.outputMeterData);let peak=0;for(const x of this.outputMeterData)peak=Math.max(peak,Math.abs(x));readings.push(["master",peak]);}return readings;}
  dispose(){clearInterval(this.timer);for(const n of this.nodes){try{n.dsp?n.source.stop():n.source.stop();}catch{}n.source.disconnect();n.source.port?.close();n.gain?.disconnect();n.pan?.disconnect();n.analyser?.disconnect();}this.nodes=[];this.click?.port?.postMessage("stop");this.click?.port?.close();this.click?.disconnect();this.click=null;for(const n of this.busNodes?.values()||[])n.disconnect();for(const n of this.routes||[])n.disconnect();this.routes=[];}
  pause(){this.cancelPreparation?.();this.cancelPreparation=null;this.position=this.current();this.playing=false;this.generation++;this.dispose();}
  stop(){this.pause();this.position=0;}
  async seek(seconds){const playing=this.playing;this.pause();this.position=Math.max(0,Math.min(duration(this.project),seconds));if(playing)await this.play();}
  async rebuild(){const playing=this.playing;this.pause();if(playing)await this.play();}
}
