export class RhythmLink {
 constructor(rhythm,getMetro,save){this.rhythm=rhythm;this.getMetro=getMetro;this.save=save;this.linked=localStorage.getItem('rhythm-linked')!=='false';rhythm.linkController=this;}
 sync(source){if(!this.linked)return;const r=this.rhythm,q=this.getMetro(),s=r.settings;if(source==='percussion'){q.bpm=s.bpm;q.meter=s.meter;q.denominator=s.denominator;q.clickFollow=s.follow;q.click=r.enabled;}else{s.bpm=q.bpm;s.meter=q.meter;s.denominator=q.denominator||4;s.follow=q.clickFollow!==false;r.enabled=!!q.click;}r.metroArmed=!!q.click;this.save();r.save();}
 async setLinked(value,source){this.linked=value;localStorage.setItem('rhythm-linked',String(value));if(value){this.sync(source);await this.run(source);} }
 async run(source,startMusic=false){
  if(!this.linked)return;this.sync(source);const r=this.rhythm,q=this.getMetro(),e=r.engine;const revision=this.revision=(this.revision||0)+1;
  r.pairPending=true;r.disconnect('beat');r.disconnect('metro');
  try{
   const c=await e.context();await c.resume();if(!r.clickModule){await c.audioWorklet.addModule(new URL('./click-worklet.js',import.meta.url));r.clickModule=true;}
   await r.refresh();if(revision!==this.revision)return;
   if(e.playing)await e.rebuild();else if(startMusic&&q.clickFollow!==false&&q.click&&e.project)await e.play();
   if(revision!==this.revision)return;r.pairPending=false;r.pairEpoch=c.currentTime+.10;r.startPrepared();await r.click(q);
  }catch(error){if(revision===this.revision){q.click=false;r.enabled=false;r.stopClick();r.disconnect('beat');this.save();}throw error;}
  finally{if(revision===this.revision)r.pairPending=false;}
 }
 async transport(){const r=this.rhythm;if(!this.linked)return r.settings.follow?r.refresh():undefined;if(this.rhythm.pairPending)return;if(!r.enabled){r.disconnect('beat');r.disconnect('metro');return;}
  if(r.settings.follow){if(r.engine.playing){this.sync('metronome');await r.refresh();}else r.disconnect('beat');}
 }
}
