import {scanPeaks} from './pcm.js';
onmessage=async({data})=>{try{let last=-1;const peaks=await scanPeaks(data,p=>{const step=Math.floor(p*20);if(step!==last){last=step;postMessage({progress:p});}});postMessage({peaks});}catch(e){console.error(e);postMessage({error:'Não foi possível analisar esta track.'});}};
