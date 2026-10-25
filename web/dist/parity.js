export const NOTES=['C','C#','D','Eb','E','F','F#','G','Ab','A','Bb','B'];
export const COLORS=['#89acf2','#81bdc8','#b29cda','#88b9a0','#dc938a','#d0ad74'];
export const normalize=s=>String(s||'').normalize('NFD').replace(/[\u0300-\u036f]/g,'').toLowerCase();
export function keyParts(key){const m=String(key||'').trim().match(/^([A-Ga-g])([#b]?)\s*(m|minor|menor|major|maior)?$/);if(!m)return null;let n={C:0,D:2,E:4,F:5,G:7,A:9,B:11}[m[1].toUpperCase()]+(m[2]==='#'?1:m[2]==='b'?-1:0);return {note:(n+12)%12,minor:['m','minor','menor'].includes(m[3])};}
export function targetKey(p){const k=keyParts(p.key);return k?NOTES[(k.note+(p.pitch||0)+120)%12]+(k.minor?'m':''):p.key||'';}
export function transpose(a,b){const x=keyParts(a),y=keyParts(b);if(!x||!y)return 0;let d=y.note-x.note;return d>6?d-12:d< -6?d+12:d;}
export const CURVE=[[0,-80],[.06,-60],[.14,-40],[.23,-30],[.35,-20],[.5,-10],[.64,-5],[.78,0],[.89,5],[1,10]];
function interpolate(x,points){let i=points.findIndex((p,i)=>i&&x<=p[0]);if(i<1)i=points.length-1;const [a,b]=[points[i-1],points[i]];return a[1]+(b[1]-a[1])*Math.max(0,Math.min(1,(x-a[0])/(b[0]-a[0])));}
export const dbToPosition=db=>interpolate(db,CURVE.map(([p,d])=>[d,p]));
export const positionToDb=p=>Math.round(interpolate(p,CURVE)*10)/10;
export function migrate(p){
 p.denominator??=4;p.clickFollow??=true;p.smart??=false;p.markers??=[];p.dcas??=[];p.buses??=[];p.unlockedFaders??=[];
 if(!p.consoleVersion){for(const t of [...p.tracks,...p.dcas,...p.buses])if(t.db<=-60)t.db=-80;if(p.master<=-60)p.master=-80;p.consoleVersion=1;}
 return p;
}
export const meterLabel=p=>`${p.meter||4}/${p.denominator||4}`;
export const dbText=db=>db<=-79.9?'−∞':`${db>=0?'+':''}${db.toFixed(1)}`;
