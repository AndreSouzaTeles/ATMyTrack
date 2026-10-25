let pending;
export async function loadAcousticKit(){
 if(!pending)pending=(async()=>{const response=await fetch(new URL('./assets/drumkit/bank.tsv',import.meta.url));if(!response.ok)throw Error('Não foi possível carregar a bateria acústica.');const rows=(await response.text()).trim().split('\n').map(x=>x.trim().split('\t')),kit={};
  const samples=await Promise.all(rows.map(async([role,file])=>{const response=await fetch(new URL('./assets/drumkit/'+file,import.meta.url));if(!response.ok)throw Error('Gravação de instrumento não disponível.');const data=await response.arrayBuffer(),v=new DataView(data);if(v.getUint16(20,true)!==1||v.getUint16(22,true)!==2||v.getUint32(24,true)!==48000||v.getUint16(34,true)!==16||v.getUint32(40,true)!==data.byteLength-44)throw Error('Gravação de instrumento inválida.');const audio=[new Float32Array((data.byteLength-44)/4),new Float32Array((data.byteLength-44)/4)];for(let i=0;i<audio[0].length;i++)for(let c=0;c<2;c++)audio[c][i]=v.getInt16(44+i*4+c*2,true)/32768;return {role,audio};}));
  for(const {role,audio}of samples)(kit[role] ||= []).push(audio);return kit;
 })().catch(e=>{pending=null;throw e;});return pending;
}
