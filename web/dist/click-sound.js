// Timbres compartilhados com MixMath.click do aplicativo Android.
export const clickSounds=['Classic','Wood','High Tick','Soft','Digital','Low Tick','Cowbell','Woodblock'];
export function clickSample(seconds,sound,accent){
 if(seconds<0||seconds>=.022)return 0;
 const hz=({Digital:2200,Wood:650,Woodblock:880,Cowbell:540,Soft:750,'High Tick':3200,'Low Tick':400}[sound]||1100)*(accent?1.45:1),phase=2*Math.PI*hz*seconds;
 const wave=sound==='Digital'?Math.tanh(3*Math.sin(phase)):sound==='Wood'?Math.sin(phase)+.4*Math.sin(phase*2.76):sound==='Woodblock'?.65*Math.sin(phase)+.35*Math.sin(phase*2.13):sound==='Cowbell'?.5*(Math.sin(phase)+Math.sin(phase*1.48)):Math.sin(phase);
 return wave*Math.exp(-seconds/(sound==='Woodblock'?.007:sound==='Soft'?.0025:sound==='Cowbell'?.009:.005))*(accent?1:.7);
}
