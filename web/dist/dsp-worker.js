import {SignalsmithWasm} from './vendor/SignalsmithStretch.mjs';
// Offline preparation: no heavy DSP remains on the real-time audio thread.
onmessage=async({data})=>{try{
  const {channels,rate,speed,pitch}=data,wasm=await SignalsmithWasm();
  wasm._configure(channels.length,Math.round(rate*.12),Math.round(rate*.01),false);
  wasm._reset();wasm._setTransposeSemitones(pitch,8000/rate);
  const capacity=2048,pointer=wasm._setBuffers(channels.length,capacity),block=512;
  const latency=Math.round(wasm._inputLatency()/speed+wasm._outputLatency());
  const length=Math.round(channels[0].length/speed),result=channels.map(()=>new Float32Array(length));
  let output=0,input=0,lastProgress=-1;
  while(output<length+latency){
    const count=Math.min(block,length+latency-output),end=Math.round((output+count)*speed),consumed=end-input;
    for(let c=0;c<channels.length;c++){const target=new Float32Array(wasm.HEAP8.buffer,pointer+c*capacity*4,capacity);target.fill(0);if(input<channels[c].length)target.set(channels[c].subarray(input,Math.min(end,channels[c].length)));}
    wasm._process(consumed,count);
    const skip=Math.max(0,latency-output),take=count-skip;
    if(take>0)for(let c=0;c<channels.length;c++){const processed=new Float32Array(wasm.HEAP8.buffer,pointer+(c+channels.length)*capacity*4,count);result[c].set(processed.subarray(skip),Math.max(0,output-latency));}
    output+=count;input=end;
    const progress=Math.floor(output/(length+latency)*10);if(progress!==lastProgress){postMessage({progress:progress/10});lastProgress=progress;}
  }
  postMessage({channels:result},result.map(c=>c.buffer));
}catch(error){postMessage({error:'Não foi possível preparar esta track.'});console.error(error);}};
