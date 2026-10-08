import {SignalsmithWasm} from './vendor/SignalsmithStretch.mjs';
import {readPCM} from './pcm.js';
onmessage=async({data})=>{let handle;try{
 const {source,speed,pitch,name}=data,rate=source.format.rate,wasm=await SignalsmithWasm();
 const dir=await (await navigator.storage.getDirectory()).getDirectoryHandle('atmytrack-pcm',{create:true});handle=await (await dir.getFileHandle(name,{create:true})).createSyncAccessHandle();handle.truncate(0);
 wasm._configure(2,Math.round(rate*.12),Math.round(rate*.01),false);wasm._reset();wasm._setTransposeSemitones(pitch,8000/rate);
 const capacity=2048,pointer=wasm._setBuffers(2,capacity),block=512,latency=Math.round(wasm._inputLatency()/speed+wasm._outputLatency()),length=Math.round(source.format.frames/speed);
 let output=0,input=0,last=-1,readStart=-1,readData;
 while(output<length+latency){const count=Math.min(block,length+latency-output),end=Math.round((output+count)*speed),consumed=end-input;
  if(readStart<0||end>readStart+readData[0].length){readStart=input;readData=await readPCM(source,input,65536);}
  for(let c=0;c<2;c++){const target=new Float32Array(wasm.HEAP8.buffer,pointer+c*capacity*4,capacity);target.fill(0);target.set(readData[c].subarray(input-readStart,input-readStart+consumed));}
  wasm._process(consumed,count);const skip=Math.max(0,latency-output),take=count-skip;
  if(take>0){const result=new Float32Array(take*2);for(let c=0;c<2;c++){const samples=new Float32Array(wasm.HEAP8.buffer,pointer+(c+2)*capacity*4,count);for(let i=0;i<take;i++)result[i*2+c]=samples[skip+i];}const written=handle.write(result,{at:Math.max(0,output-latency)*8});if(written!==result.byteLength)throw Error('Disco cheio');}
  output+=count;input=end;const step=Math.floor(output/(length+latency)*100);if(step!==last){last=step;postMessage({progress:step/100});}
 }
 handle.flush();handle.close();handle=null;postMessage({format:{encoding:3,bits:32,channels:2,align:8,offset:0,rate,frames:length}});
}catch(e){console.error(e);postMessage({error:'Não foi possível preparar o áudio. Confira o espaço de armazenamento do navegador e tente novamente.'});}finally{handle?.close();}};
