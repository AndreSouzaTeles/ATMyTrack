import {wavInfo,readAtRate} from './pcm.js';
async function root(){if(!navigator.storage?.getDirectory)throw Error('Este navegador não oferece o armazenamento necessário para preparar áudio. Use um navegador atualizado.');return (await navigator.storage.getDirectory()).getDirectoryHandle('atmytrack-pcm',{create:true});}
export async function clearAudioCache(id,keep=[]){if(!navigator.storage?.getDirectory)return;const dir=await root();for await(const [name] of dir.entries())if((name.startsWith('original-'+id+'.')||name.startsWith('dsp-'+id+'-'))&&!keep.includes(name))await dir.removeEntry(name).catch(()=>{});}
export async function sourceFor(blob,id,decode,status=()=>{}){
 const format=await wavInfo(blob);if(format)return {stream:true,blob,format,duration:format.duration};
 // Compressed input is decoded one track at a time, then retained on disk, never as a project of AudioBuffers.
 if(!navigator.storage?.getDirectory)throw Error('Este navegador precisa de armazenamento local compatível para preparar áudio comprimido. Use WAV PCM ou um navegador atualizado.');
 const dir=await root(),name='original-'+id;
 try{const meta=JSON.parse(await (await (await dir.getFileHandle(name+'.json')).getFile()).text()),file=await (await dir.getFileHandle(name+'.pcm')).getFile();if(meta.sourceSize===blob.size&&file.size===meta.frames*8)return {stream:true,blob:file,format:meta,duration:meta.frames/meta.rate};}catch{}
 status('Decodificando arquivo');const buffer=await decode(blob),meta={encoding:3,bits:32,channels:2,align:8,offset:0,rate:buffer.sampleRate,frames:buffer.length,sourceSize:blob.size};
 const file=await dir.getFileHandle(name+'.pcm',{create:true}),writer=await file.createWritable();
 try{for(let pos=0;pos<buffer.length;pos+=65536){const n=Math.min(65536,buffer.length-pos),block=new Float32Array(n*2),l=buffer.getChannelData(0),r=buffer.getChannelData(Math.min(1,buffer.numberOfChannels-1));for(let i=0;i<n;i++){block[i*2]=l[pos+i];block[i*2+1]=r[pos+i];}await writer.write(block);status(`Preparando áudio ${Math.round((pos+n)/buffer.length*100)}%`);}await writer.close();}catch(e){await writer.abort().catch(()=>{});throw e;}
 const mw=await (await dir.getFileHandle(name+'.json',{create:true})).createWritable();await mw.write(JSON.stringify(meta));await mw.close();return {stream:true,blob:await file.getFile(),format:meta,duration:buffer.duration};
}
export function analyzeSource(source,progress=()=>{}){return new Promise((resolve,reject)=>{const worker=new Worker(new URL('./source-analysis-worker.js',import.meta.url),{type:'module'});worker.onmessage=({data})=>{if(data.progress!==undefined)progress(`Desenhando waveform ${Math.round(data.progress*100)}%`);if(data.peaks){worker.terminate();resolve(data.peaks);}if(data.error){worker.terminate();reject(Error(data.error));}};worker.onerror=()=>{worker.terminate();reject(Error('Não foi possível analisar o áudio.'));};worker.postMessage(source);});}
export async function previewBuffer(source,ctx,seconds=120){const channels=await readAtRate(source,0,Math.min(Math.ceil(source.duration*ctx.sampleRate),seconds*ctx.sampleRate),ctx.sampleRate),b=ctx.createBuffer(2,channels[0].length,ctx.sampleRate);channels.forEach((c,i)=>b.copyToChannel(c,i));return b;}
export async function renderSource(source,track,progress=()=>{},cancel=()=>{}){
 const speed=(track.speed||100)/100,pitch=track.pitch||0;if(speed===1&&pitch===0)return source;
 const name=`dsp-${track.id}-${speed}-${pitch}.pcm`,dir=await root();
 // Keep only the current variant per track. User originals are held separately in IndexedDB.
 for await(const [old]of dir.entries())if(old.startsWith('dsp-'+track.id+'-'))await dir.removeEntry(old).catch(()=>{});
 const worker=new Worker(new URL('./disk-dsp-worker.js',import.meta.url),{type:'module'});
 const format=await new Promise((resolve,reject)=>{cancel(()=>{worker.terminate();reject(Error('Preparação cancelada.'));});worker.onmessage=({data})=>{if(data.progress!==undefined)progress(`${track.name} · ${Math.round(data.progress*100)}%`);if(data.format){worker.terminate();resolve(data.format);}if(data.error){worker.terminate();reject(Error(data.error));}};worker.onerror=()=>{worker.terminate();reject(Error('Não foi possível preparar '+track.name));};worker.postMessage({source,speed,pitch,name});});
 return {stream:true,blob:await (await dir.getFileHandle(name)).getFile(),format,duration:format.frames/format.rate};
}
