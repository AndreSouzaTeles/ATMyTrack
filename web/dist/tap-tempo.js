export class TapTempo {
 constructor(){this.taps=[];}
 tap(now){const previous=this.taps.at(-1);if(previous!==undefined&&(now-previous>2200||now<=previous))this.taps=[];if(this.taps.length&&now-this.taps.at(-1)<120)return null;this.taps.push(now);if(this.taps.length>7)this.taps.shift();return this.taps.length<2?null:Math.max(30,Math.min(300,60000*(this.taps.length-1)/(now-this.taps[0])));}
}
