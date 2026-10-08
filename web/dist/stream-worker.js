import {readAtRate} from './pcm.js';
let sources,rate,chunk,position,loop,port,queue=Promise.resolve();
onmessage=({data})=>{({sources,rate,chunk,position,loop,port}=data);port.onmessage=({data})=>enqueue(data.seq);port.start();for(let i=0;i<4;i++)enqueue(i);};
function enqueue(seq){queue=queue.then(()=>produce(seq)).catch(e=>{console.error(e);port.postMessage({error:'Não foi possível ler o áudio do armazenamento. Pare e abra o projeto novamente.'});});}
async function produce(seq){const channels=[];for(const source of sources){const out=[new Float32Array(chunk),new Float32Array(chunk)];let done=0;while(done<chunk){let frame=position+seq*chunk+done;if(loop&&frame>=loop[1])frame=loop[0]+(frame-loop[0])%(loop[1]-loop[0]);const n=Math.min(chunk-done,loop?Math.max(1,loop[1]-frame):chunk-done);const data=await readAtRate(source,frame,n,rate);for(let c=0;c<2;c++)out[c].set(data[c],done);done+=n;}channels.push(...out);}port.postMessage({seq,channels},channels.map(c=>c.buffer));}
