class StreamPlayer extends AudioWorkletProcessor {
 constructor(options){super();this.chunk=options.processorOptions.chunk;this.count=options.processorOptions.count;this.blocks=new Map();this.startFrame=Infinity;this.active=true;this.last=-1;this.port.onmessage=({data})=>{if(data.port){this.reader=data.port;this.reader.onmessage=({data:b})=>{if(b.error){this.port.postMessage({error:b.error});return;}this.blocks.set(b.seq,b.channels);if(this.blocks.size===4&&this.startFrame===Infinity)this.port.postMessage({ready:true});};this.reader.start();}if(data.when!==undefined)this.startFrame=Math.round(data.when*sampleRate);if(data.stop)this.active=false;};}
 process(_,outputs){if(!this.active)return false;if(currentFrame+128<=this.startFrame)return true;
  for(let j=0;j<128;j++){const frame=currentFrame+j-this.startFrame;if(frame<0)continue;const seq=Math.floor(frame/this.chunk),offset=frame%this.chunk,data=this.blocks.get(seq);if(!data){this.port.postMessage({underrun:1,frame});this.active=false;return false;}
   if(seq!==this.last){if(this.last>=0){this.blocks.delete(this.last);this.reader.postMessage({seq:seq+3});}this.last=seq;}
   for(let t=0;t<this.count;t++){outputs[t][0][j]=data[t*2][offset];outputs[t][1][j]=data[t*2+1][offset];}
  }return true;
 }
}
registerProcessor('atmytrack-stream',StreamPlayer);
