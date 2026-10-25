import {clickSample,clickSounds} from './click-sound.js';
class Click extends AudioWorkletProcessor {
 constructor(options){super();this.config=options.processorOptions;this.active=true;this.port.onmessage=()=>this.active=false;}
 process(inputs,outputs){if(!this.active)return false;const out=outputs[0][0],c=this.config,beat=60/c.bpm;for(let i=0;i<out.length;i++){const now=(currentFrame+i)/sampleRate;if(now<c.when){out[i]=0;continue;}let position=c.position+now-c.when;if(c.loop&&position>=c.loop[1])position=c.loop[0]+(position-c.loop[0])%(c.loop[1]-c.loop[0]);const index=Math.floor((position+1e-9)/beat),local=Math.max(0,position-index*beat);out[i]=clickSample(local,clickSounds[c.kind||0],c.accent&&index%c.meter===0)*c.gain;}return true;}
}
registerProcessor('atmytrack-click',Click);
