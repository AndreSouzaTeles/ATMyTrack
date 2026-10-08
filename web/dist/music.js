export const noteNames=['C','C#','D','D#','E','F','F#','G','G#','A','A#','B'];
export const frequency=(m,a4=440)=>a4*2**((m-69)/12);
export const nearest=(f,a4=440)=>Math.round(69+12*Math.log2(f/a4));
export const cents=(f,m,a4=440)=>1200*Math.log2(f/frequency(m,a4));
export const noteName=m=>noteNames[((m%12)+12)%12]+(Math.floor(m/12)-1);
export const gain=db=>db<=-60?0:10**(db/20);
export const time=s=>`${Math.floor(Math.max(0,s)/60).toString().padStart(2,'0')}:${Math.floor(Math.max(0,s)%60).toString().padStart(2,'0')}`;
export const id=()=>crypto.randomUUID();
const guitar=[['Standard',[40,45,50,55,59,64]],['Drop D',[38,45,50,55,59,64]],['D Standard',[38,43,48,53,57,62]],['Drop C',[36,43,48,53,57,62]],['Half Step Down',[39,44,49,54,58,63]]];
export const instruments=[['Cromático',[]],['Violão',guitar],['Guitarra',guitar],['Baixo 4 cordas',[['Standard',[28,33,38,43]]]],['Baixo 5 cordas',[['Standard',[23,28,33,38,43]]]],['Baixo 6 cordas',[['Standard',[23,28,33,38,43,48]]]],['Ukulele',[['GCEA',[67,60,64,69]]]],['Violino',[['Standard',[55,62,69,76]]]],['Viola',[['Standard',[48,55,62,69]]]],['Violoncelo',[['Standard',[36,43,50,57]]]],['Contrabaixo acústico',[['Standard',[28,33,38,43]]]],['Cavaquinho',[['DGBD',[62,67,71,74]]]],['Bandolim',[['GDAE',[55,62,69,76]]]]];
export function tunerReading(f,notes=[],manual=-1,a4=440){const midi=nearest(f,a4);const target=manual>=0?notes[manual]:notes.reduce((best,n)=>best===null||Math.abs(cents(f,n,a4))<Math.abs(cents(f,best,a4))?n:best,null);return {midi,cents:cents(f,midi,a4),target,targetCents:target===null?null:cents(f,target,a4)};}
export function yin(samples,rate){const n=samples.length/2,mean=samples.reduce((a,b)=>a+b,0)/samples.length,rms=Math.sqrt(samples.reduce((a,b)=>a+(b-mean)**2,0)/samples.length);if(rms<.002)return {frequency:0,rms};const max=Math.min(n-2,Math.floor(rate/25)),min=Math.max(2,Math.floor(rate/1600)),d=new Float64Array(max+1),raw=new Float64Array(max+1);let sum=0;for(let t=1;t<=max;t++){let x=0;for(let i=0;i<n;i++){const q=samples[i]-samples[i+t];x+=q*q;}raw[t]=x;sum+=x;d[t]=sum?x*t/sum:1;}for(let t=min;t<max;t++){if(d[t]<.1){while(t+1<=max&&d[t+1]<d[t])t++;const a=raw[t-1],b=raw[t],c=raw[Math.min(max,t+1)],delta=Math.abs(a-2*b+c)>1e-12?Math.max(-1,Math.min(1,.5*(a-c)/(a-2*b+c))):0;return {frequency:rate/(t+delta),rms,confidence:1-d[t]};}}return {frequency:0,rms};}
export function globalRate(p){return p.tracks.length&&p.tracks.every(t=>(t.speed||100)===(p.tracks[0].speed||100))?(p.tracks[0].speed||100)/100:1;}
export function duration(p){return Math.max(0,...p.tracks.map(t=>t.duration/((t.speed||100)/100)));}
export function newProject(name){return {id:id(),name,key:'',bpm:72,meter:4,master:0,masterMute:false,tracks:[],markers:[],buses:[],dcas:[],pitch:0,speed:100,click:false,clickDb:-12,clickDivision:1,clickAccent:true,clickTone:0,clickRoute:'master',loop:false,selectedMarker:null};}
