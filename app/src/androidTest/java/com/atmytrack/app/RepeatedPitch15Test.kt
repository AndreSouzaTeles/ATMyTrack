package com.atmytrack.app
import android.net.Uri
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import com.atmytrack.app.audio.*
import com.atmytrack.app.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.junit.Test
import org.junit.Assert.*
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.*
class RepeatedPitch15Test {
 external fun legacyCreate(rate:Int,semitones:Int):Long
 @Test fun consecutiveColdPitchChangesFromOriginalAndCache():Unit=runBlocking {
  val c=InstrumentationRegistry.getInstrumentation().targetContext
  val original=AudioImporter(c).import(listOf(Uri.fromFile(ImportBenchmark().wav(20,2,24))),"D to G to E") {}
  val p=original.copy(key="D",stems=List(8){original.stems.first().copy(id="repeat-$it")})
  // Match the preceding renderer's four slots, PCM block size, IO and profile.
  suspend fun oldRender(shift:Int)=coroutineScope {
   val slots=Semaphore(4)
   p.stems.map { s->async(Dispatchers.Default){slots.withPermit {
    val init=NativePitch.create(48000,0);NativePitch.destroy(init)
    val dsp=legacyCreate(48000,shift);val latency=NativePitch.stretchLatency(dsp,1.0)
    val input=FloatArray(8192);val output=FloatArray(8192);val bytes=ByteBuffer.allocate(32768).order(ByteOrder.LITTLE_ENDIAN);val floats=bytes.asFloatBuffer()
    val target=File(c.cacheDir,"legacy-repeat-${s.id}.pcm")
    try {PlaybackCache(c).open(s.copy(pitchFile="",pitchApplied=0),48000).use {r-> target.outputStream().buffered(262144).use {out->
      var pos=0L;var written=0L
      while(written<s.frames){r.read(pos,2048,input);NativePitch.stretch(dsp,input,output,2048,2048);val skip=(latency-pos).coerceIn(0,2048).toInt();val take=minOf(2048-skip,(s.frames-written).toInt());floats.clear();floats.put(output,skip*2,take*2);out.write(bytes.array(),0,take*8);pos+=2048;written+=take}
    }}}finally{NativePitch.destroy(dsp);target.delete()}
   }}}.awaitAll()
  }
  val baseline=mutableListOf<Long>();val fresh=mutableListOf<Long>();val engine=(c.applicationContext as TrackApplication).engine
  for(shift in listOf(5,2)){val start=SystemClock.elapsedRealtime();oldRender(shift);baseline.add(SystemClock.elapsedRealtime()-start)}
  var current=p
  engine.load(p);val deadline=SystemClock.elapsedRealtime()+30000;while(!engine.state.value.ready||engine.state.value.projectId!=p.id){check(SystemClock.elapsedRealtime()<deadline);Thread.sleep(20)}
  engine.play();Thread.sleep(250)
  for(shift in listOf(5,2,3)){
   val start=SystemClock.elapsedRealtime();val rendered=PitchRenderer(c).render(current,shift,current.stems.map{it.id}) {};val elapsed=SystemClock.elapsedRealtime()-start;fresh.add(elapsed)
   current=current.copy(stems=rendered,semitones=shift,pitchTracks=current.stems.map{it.id})
   val commit=SystemClock.elapsedRealtime();engine.applyPreparedPitch(current);assertTrue(engine.state.value.playing);assertNull(engine.state.value.error)
   val samples=FloatArray(96000);Readers.open(c,rendered.first(),48000,48000).use{it.read(48000,48000,samples)}
   var crossings=0;for(i in 1 until 48000)if(samples[2*(i-1)]<=0&&samples[2*i]>0)crossings++
   assertEquals(220*2.0.pow(shift/12.0),crossings.toDouble(),2.0)
   assertEquals(p.stems.first().frames*8,File(c.filesDir,rendered.first().pitchFile).length())
   android.util.Log.i("REPEAT15","shift=$shift coldMs=$elapsed commitMs=${SystemClock.elapsedRealtime()-commit} frequency=$crossings playing=${engine.state.value.playing} underruns=${engine.state.value.underruns}")
  }
  val start=SystemClock.elapsedRealtime();val cached=PitchRenderer(c).render(current,5,current.stems.map{it.id}) {error("Must reuse cache")};val warm=SystemClock.elapsedRealtime()-start
  assertEquals(p.stems.map{it.frames},cached.map{it.frames});engine.stop();engine.unload()
  android.util.Log.i("REPEAT15","8 independent readers same synthetic PCM24 stereo 48kHz 20s baseline10ms=$baseline adaptive15ms=$fresh warmMs=$warm")
  File(c.filesDir,"pitch/${p.id}").deleteRecursively()
 }
}
