import {clearAudioCache} from './audio-source.js';
let dbPromise;
function db(){return dbPromise ||=new Promise((resolve,reject)=>{const r=indexedDB.open('ATMyTrack',1);r.onupgradeneeded=()=>{r.result.createObjectStore('projects',{keyPath:'id'});r.result.createObjectStore('audio');};r.onsuccess=()=>resolve(r.result);r.onerror=()=>reject(r.error);});}
async function transaction(store,mode,action){const d=await db();return new Promise((resolve,reject)=>{const tx=d.transaction(store,mode),r=action(tx.objectStore(store));tx.oncomplete=()=>resolve(r?.result);tx.onerror=()=>reject(tx.error);tx.onabort=()=>reject(tx.error||new Error('Armazenamento indisponível'));});}
export const allProjects=()=>transaction('projects','readonly',s=>s.getAll());
export const saveProject=p=>transaction('projects','readwrite',s=>s.put(structuredClone(p)));
export const deleteProject=p=>Promise.all([transaction('projects','readwrite',s=>s.delete(p.id)),...p.tracks.map(t=>transaction('audio','readwrite',s=>s.delete(t.id))),...p.tracks.map(t=>clearAudioCache(t.id))]);
export const putAudio=(id,blob)=>transaction('audio','readwrite',s=>s.put(blob,id));
export const getAudio=id=>transaction('audio','readonly',s=>s.get(id));
export function download(blob,name){const url=URL.createObjectURL(blob),a=document.createElement('a');a.href=url;a.download=name;a.click();setTimeout(()=>URL.revokeObjectURL(url),10000);}
// Binary backup references file-backed Blobs instead of materializing gigabytes of base64/JSON.
export async function exportProject(p){const blobs=[],entries=[];for(const t of p.tracks){const b=await getAudio(t.id);if(!b)throw Error('Áudio ausente: '+t.name);blobs.push(b);entries.push({id:t.id,size:b.size});}const metadata=new TextEncoder().encode(JSON.stringify({format:'ATMyTrack-Web',version:2,project:p,entries})),header=new Uint8Array(12);header.set(new TextEncoder().encode('ATMTWEB2'));new DataView(header.buffer).setUint32(8,metadata.length,true);download(new Blob([header,metadata,...blobs],{type:'application/octet-stream'}),p.name+'.atmytrack');}
export async function importBackup(file,status=()=>{}){
 const head=await file.slice(0,12).arrayBuffer();let x,getBlob;
 if(new TextDecoder().decode(head.slice(0,8))==='ATMTWEB2'){
  const length=new DataView(head).getUint32(8,true);if(length>16*1024*1024||length+12>file.size)throw Error('Backup inválido.');x=JSON.parse(await file.slice(12,12+length).text());let offset=12+length;const files=new Map();if(!Array.isArray(x.entries))throw Error('Backup inválido.');for(const e of x.entries){if(!Number.isSafeInteger(e.size)||e.size<0||offset+e.size>file.size)throw Error('Backup incompleto.');files.set(e.id,file.slice(offset,offset+e.size));offset+=e.size;}if(offset!==file.size)throw Error('Backup inválido.');getBlob=id=>files.get(id);
 }else{
  x=JSON.parse(await file.text());getBlob=id=>{const raw=x.audio?.[id];if(typeof raw!=='string'||!raw.startsWith('data:')||!raw.includes(';base64,'))throw Error('Backup com áudio inválido.');return new Blob([Uint8Array.from(atob(raw.split(',')[1]),c=>c.charCodeAt(0))]);};
 }
 if(x.format!=='ATMyTrack-Web'||![1,2].includes(x.version)||!Array.isArray(x.project?.tracks))throw Error('Backup inválido. Use um arquivo exportado pela versão web.');
 const p=x.project;p.id=crypto.randomUUID();const map=new Map();for(let i=0;i<p.tracks.length;i++){const t=p.tracks[i],old=t.id,blob=getBlob(old);if(!blob)throw Error('Backup incompleto: '+t.name);status(i+1,p.tracks.length,t.name);t.id=crypto.randomUUID();map.set(old,t.id);await putAudio(t.id,blob);}p.pitchTracks=p.pitchTracks?.map(x=>map.get(x)).filter(Boolean);p.speedTracks=p.speedTracks?.map(x=>map.get(x)).filter(Boolean);for(const d of p.dcas||[])d.members=d.members.map(x=>map.get(x)).filter(Boolean);await saveProject(p);return p;
}
