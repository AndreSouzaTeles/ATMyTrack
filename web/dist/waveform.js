// Peak envelope scans every sample and both channels; zero bins remain silent.
export function peakEnvelope(channels,bins=512) {
 const length=channels[0]?.length||0,peaks=new Array(bins).fill(0);
 for(const samples of channels)for(let i=0;i<samples.length;i++){const v=Math.abs(samples[i]);if(Number.isFinite(v)){const bin=Math.min(bins-1,Math.floor(i*bins/length));if(v>peaks[bin])peaks[bin]=Math.min(1,v);}}
 return peaks;
}
export function projectEnvelope(tracks,bins=512) {
 const duration=Math.max(0,...tracks.map(t=>t.duration)),out=new Array(bins).fill(0);
 for(const t of tracks){const peaks=t.peaks||[];for(let i=0;i<peaks.length;i++){const from=Math.floor(i/peaks.length*t.duration/duration*bins),to=Math.ceil((i+1)/peaks.length*t.duration/duration*bins);for(let j=from;j<Math.min(bins,to);j++)out[j]=Math.max(out[j],peaks[i]);}}
 return out;
}
export function drawEnvelope(ctx,peaks,width,height) {
 ctx.fillStyle='#8198bb';ctx.beginPath();ctx.moveTo(0,height/2);
 peaks.forEach((v,i)=>{ctx.lineTo(i/peaks.length*width,height*(.5-v*.43));ctx.lineTo((i+1)/peaks.length*width,height*(.5-v*.43));});
 for(let i=peaks.length-1;i>=0;i--){ctx.lineTo((i+1)/peaks.length*width,height*(.5+peaks[i]*.43));ctx.lineTo(i/peaks.length*width,height*(.5+peaks[i]*.43));}ctx.closePath();ctx.fill();
}
