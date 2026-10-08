// Random-access PCM: headers and small slices only, never a whole multitrack in RAM.
const text=(v,p,n)=>String.fromCharCode(...new Uint8Array(v.buffer,v.byteOffset+p,n));
export async function wavInfo(blob){
 const head=new DataView(await blob.slice(0,12).arrayBuffer());if(head.byteLength<12||!['RIFF','RF64'].includes(text(head,0,4))||text(head,8,4)!=='WAVE')return null;
 let format=null,dataSize64=null;
 for(let offset=12;offset+8<=blob.size;){
  const h=new DataView(await blob.slice(offset,offset+8).arrayBuffer()),tag=text(h,0,4),size=h.getUint32(4,true),start=offset+8;
  if(tag==='ds64'){const d=new DataView(await blob.slice(start,start+28).arrayBuffer());dataSize64=Number(d.getBigUint64(8,true));}
  if(tag==='fmt '){const f=new DataView(await blob.slice(start,start+Math.min(size,64)).arrayBuffer());let encoding=f.getUint16(0,true);if(encoding===65534&&size>=40)encoding=f.getUint16(24,true);format={encoding,channels:f.getUint16(2,true),rate:f.getUint32(4,true),align:f.getUint16(12,true),bits:f.getUint16(14,true)};}
  if(tag==='data'&&format){const sizeBytes=size===0xffffffff?dataSize64:size;if(!Number.isSafeInteger(sizeBytes)||start+sizeBytes>blob.size)throw Error('WAV incompleto. Confira o arquivo original.');const {encoding,channels,rate,align,bits}=format;if(![1,3].includes(encoding)||![1,2].includes(channels)||rate<8000||rate>384000||![8,16,24,32,64].includes(bits)||align!==channels*bits/8||(encoding===3&&![32,64].includes(bits)))throw Error('Formato WAV não suportado. Use PCM mono ou estéreo.');return {...format,offset:start,frames:Math.floor(sizeBytes/align),duration:Math.floor(sizeBytes/align)/rate};}
  offset=start+size+(size%2);
 }
 throw Error('WAV sem dados de áudio válidos.');
}
export async function readPCM(source,start,count){
 const f=source.format,out=[new Float32Array(count),new Float32Array(count)];const available=Math.max(0,Math.min(count,f.frames-start));if(!available)return out;
 const bytes=await source.blob.slice(f.offset+start*f.align,f.offset+(start+available)*f.align).arrayBuffer(),v=new DataView(bytes),step=f.bits/8;
 const sample=p=>{let x;if(f.encoding===3)x=f.bits===32?v.getFloat32(p,true):v.getFloat64(p,true);else if(f.bits===8)x=(v.getUint8(p)-128)/128;else if(f.bits===16)x=v.getInt16(p,true)/32768;else if(f.bits===24)x=((v.getUint8(p)|(v.getUint8(p+1)<<8)|(v.getInt8(p+2)<<16)))/8388608;else if(f.bits===32)x=v.getInt32(p,true)/2147483648;else throw Error('PCM inteiro de 64 bits não suportado.');return Number.isFinite(x)?x:0;};
 for(let i=0;i<available;i++){out[0][i]=sample(i*f.align);out[1][i]=f.channels===1?out[0][i]:sample(i*f.align+step);}return out;
}
const kernels=new Map();
function resampleKernel(ratio){if(kernels.has(ratio))return kernels.get(ratio);const phases=1024,taps=32,cutoff=Math.min(1,1/ratio)*.94,table=new Float32Array(phases*taps);for(let p=0;p<phases;p++){let sum=0;for(let k=0;k<taps;k++){const x=k-15-p/phases,v=Math.abs(x)<1e-9?cutoff:Math.sin(Math.PI*cutoff*x)/(Math.PI*x),w=.42+.5*Math.cos(Math.PI*x/16)+.08*Math.cos(2*Math.PI*x/16);table[p*taps+k]=v*w;sum+=v*w;}for(let k=0;k<taps;k++)table[p*taps+k]/=sum;}kernels.set(ratio,table);return table;}
export async function readAtRate(source,start,count,rate){
 const ratio=source.format.rate/rate;if(ratio===1)return readPCM(source,start,count);
 const first=Math.max(0,Math.floor(start*ratio)-16),data=await readPCM(source,first,Math.max(0,Math.ceil((start+count)*ratio)+17-first)),kernel=resampleKernel(ratio),out=[new Float32Array(count),new Float32Array(count)];
 for(let i=0;i<count;i++){const x=(start+i)*ratio-first,base=Math.floor(x),phase=Math.min(1023,Math.floor((x-base)*1024))*32;for(let c=0;c<2;c++){let v=0;for(let k=0;k<32;k++)v+=(data[c][base+k-15]||0)*kernel[phase+k];out[c][i]=v;}}return out;
}
export async function scanPeaks(source,progress=()=>{}){
 const peaks=new Array(512).fill(0),f=source.format,block=65536;
 for(let pos=0;pos<f.frames;pos+=block){const data=await readPCM(source,pos,Math.min(block,f.frames-pos));for(let i=0;i<data[0].length;i++){const j=Math.min(511,Math.floor((pos+i)*512/f.frames));peaks[j]=Math.max(peaks[j],Math.min(1,Math.abs(data[0][i])),Math.min(1,Math.abs(data[1][i])));}progress((pos+data[0].length)/f.frames);}return peaks;
}
