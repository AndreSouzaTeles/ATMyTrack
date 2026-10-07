package com.atmytrack.app

import android.net.Uri
import android.os.*
import androidx.test.platform.app.InstrumentationRegistry
import com.atmytrack.app.audio.*
import com.atmytrack.app.data.*
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.Assert.*
import java.io.File
import kotlin.math.*

class Speed07AudioTest {
 private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
 private val engine get()=(context.applicationContext as TrackApplication).engine
 private fun await(test:()->Boolean) { val end=SystemClock.elapsedRealtime()+90000;while(!test()) { check(SystemClock.elapsedRealtime()<end){engine.state.value.toString()};Thread.sleep(20) } }
 private suspend fun prepared(p:Project,speed:Int,pitch:Int=0,ids:List<String> = p.stems.map { it.id }):Project {
    val q=p.copy(speed=speed,speedTracks=ids,semitones=pitch,pitchTracks=listOf(p.stems.first().id))
    return q.copy(stems=PitchRenderer(context).render(q,pitch,q.pitchTracks) {})
 }
 @Test fun realSpeedPitchSelectionDurationBypassAndClick()=runBlocking {
    val p=AudioImporter(context).import(List(2){Uri.fromFile(ImportBenchmark().wav(6,1,16))},"DSP 07") {}
    val report=StringBuilder()
    for(speed in listOf(50,75,80,90,100,110,120,125,150,200))for(pitch in listOf(0,2)) {
        val q=prepared(p,speed,pitch)
        val s=q.stems.first();val frames=(s.frames/(speed/100.0)).roundToLong()
        if(speed==100 && pitch==0)assertTrue(s.pitchFile.isEmpty()) else assertEquals(frames*8,File(context.filesDir,s.pitchFile).length())
        val samples=FloatArray(96000)
        Readers.open(context,s,48000,48000).use { it.read(48000,48000,samples) }
        var crossings=0;for(i in 1 until 48000)if(samples[(i-1)*2]<=0 && samples[i*2]>0)crossings++
        val expected=220*2.0.pow(pitch/12.0)
        val detector=com.atmytrack.app.tuner.YinDetector()
        val measured=(0..4).map { offset -> detector.detect(FloatArray(4096) { i->(samples[(i*2+offset*4096)*2]+samples[(i*2+offset*4096+1)*2])*.5f }).frequency }.sorted()[2]
        assertEquals("speed=$speed pitch=$pitch",expected,measured,.65)
        assertTrue(samples.all { it.isFinite() });assertTrue(samples.maxOrNull()!!>.1)
        if(pitch==2)assertEquals(0,q.stems.last().pitchApplied)
        report.append("speed=$speed pitch=$pitch expectedHz=$expected measuredHz=$measured zeroCrossings=$crossings frames=$frames\n")
    }
    val partial=prepared(p,80,2,listOf(p.stems.first().id))
    assertEquals(100,partial.stems.last().dspSpeed);assertTrue(partial.stems.last().pitchFile.isEmpty())
    val resetPitch=prepared(partial,80,0);assertTrue(resetPitch.stems.all { it.pitchApplied==0 && it.dspSpeed==80 })
    val resetSpeed=prepared(resetPitch,100,0);assertTrue(resetSpeed.stems.all { it.pitchFile.isEmpty() })
    for(speed in listOf(50,75,100,125,150)) {
        val e=p.copy(bpm=72.0,speed=speed).engineView()
        val period=MusicTime.beatFrames(e.sampleRate,e.bpm,1.0)
        assertEquals(72*speed/100.0,e.bpm,1e-6)
        val pulse=MixMath.click(period.roundToLong()+15,e.sampleRate,e.bpm,1.0,4)
        assertTrue(abs(pulse)>.05)
        assertEquals(0f,MixMath.click((period/2).toLong(),e.sampleRate,e.bpm,1.0,4),0f)
    }
    File(context.getExternalFilesDir(null),"speed-dsp-0.7.txt").writeText(report.toString())
 }
 @Test fun importedClickRetainsMusicalBeatPositions()=runBlocking {
    val frames=48000*6
    val bytes=java.nio.ByteBuffer.allocate(frames*8).order(java.nio.ByteOrder.LITTLE_ENDIAN)
    repeat(frames) { i ->
        val t=(i%24000)/48000.0
        val v=if(t<.025)(.5*sin(2*PI*1100*t)*exp(-t/.004)).toFloat() else 0f
        bytes.putFloat(v);bytes.putFloat(v)
    }
    val header=java.nio.ByteBuffer.allocate(44).order(java.nio.ByteOrder.LITTLE_ENDIAN)
    header.put("RIFF".toByteArray());header.putInt(36+frames*8);header.put("WAVEfmt ".toByteArray());header.putInt(16)
    header.putShort(3);header.putShort(2);header.putInt(48000);header.putInt(48000*8);header.putShort(8);header.putShort(32)
    header.put("data".toByteArray());header.putInt(frames*8)
    val source=File(context.cacheDir,"imported-click07.wav")
    source.outputStream().use { it.write(header.array());it.write(bytes.array()) }
    val original=AudioImporter(context).import(List(2){Uri.fromFile(source)},"Click alignment") {}.copy(bpm=120.0)
    val log=StringBuilder()
    for(speed in listOf(80,120)) {
        val q=prepared(original,speed)
        val data=FloatArray(8192)
        val peaks=mutableListOf<Long>()
        Readers.open(context,q.stems[0],48000,4096).use { reader ->
            for(beat in 1..10) {
                val expected=(beat*24000/(speed/100.0)).roundToLong()
                val start=expected-2048
                reader.read(start,4096,data)
                val index=(0 until 4096).maxBy { abs(data[it*2]) }
                peaks+=start+index
                val error=(start+index-expected)/48.0
                log.append("speed=$speed beat=$beat peakOffsetMs=$error\n")
                assertTrue("Click alignment $error ms",abs(error)<2)
            }
        }
        Readers.open(context,q.stems[1],48000,4096).use { reader ->
            peaks.forEach { peak ->reader.read(peak-2048,4096,data);val index=(0 until 4096).maxBy { abs(data[it*2]) };assertEquals(2048,index) }
        }
    }
    File(context.getExternalFilesDir(null),"click-speed-0.7.txt").writeText(log.toString())
 }
 @Test fun progressivePerformanceAndLiveTransitions()=runBlocking {
    val base=AudioImporter(context).import(List(19){Uri.fromFile(ImportBenchmark().wav(8,1,16))},"Speed profile") {}.copy(master=.025f,loop=true)
    val log=File(context.getExternalFilesDir(null),"speed-profile-0.7.txt")
    log.writeText("${Build.MODEL} API ${Build.VERSION.SDK_INT}; 8s mono WAV 48k PCM16 synthetic 220Hz; 6s per playback; process CPU (one core=100%); independent readers/caches\n")
    for(speed in listOf(100,80,120,90,110)) {
        val start=SystemClock.elapsedRealtime();val rendered=prepared(base,speed)
        val preparation=SystemClock.elapsedRealtime()-start
        for(n in listOf(2,4,8,12,19)) {
            val p=rendered.copy(stems=rendered.stems.take(n),speedTracks=rendered.stems.take(n).map { it.id })
            engine.load(p);await { engine.state.value.projectId==p.id && engine.state.value.ready && !engine.state.value.preparing }
            val cpu=Process.getElapsedCpuTime();val wall=SystemClock.elapsedRealtime()
            engine.play();await { engine.state.value.playing };Thread.sleep(6000)
            val state=engine.state.value;val info=Debug.MemoryInfo();Debug.getMemoryInfo(info)
            log.appendText("speed=$speed n=$n prep19=${preparation}ms wall=${SystemClock.elapsedRealtime()-wall}ms cpu=${Process.getElapsedCpuTime()-cpu}ms pss=${info.totalPss}KiB underruns=${state.underruns} starvation=${state.starvation} maxRead=${engine.maxReadNanos/1e6}ms\n")
            assertNull(state.error);assertTrue(state.playing);assertEquals(0,state.underruns)
            assertEquals(n,state.peaks.size);assertTrue(state.peaks.all { abs(it-state.peaks.first())<.0001 })
            engine.stop();await { !engine.state.value.playing };engine.unload();await { engine.state.value.projectId.isBlank() }
        }
    }
    var current=base.copy(stems=base.stems.take(4),loop=false)
    engine.load(current);await { engine.state.value.ready && engine.state.value.projectId==current.id };engine.play();await { engine.state.value.playing }
    for(speed in listOf(90,80,110,100)) {
        val next=prepared(current,speed,2)
        val before=engine.state.value.frame
        engine.applyPreparedPitch(next);current=next
        val after=engine.state.value.frame
        assertTrue("position reset $before $after",after>=before-4800)
        assertTrue(engine.state.value.playing);assertNull(engine.state.value.error)
        engine.update(current.copy(stems=current.stems.mapIndexed { i,s->s.copy(volume=.3f,pan=if(i==1).4f else 0f,mute=i==2,solo=i==0) }))
        for(fraction in listOf(.25,.5,.75)) {
            val frame=(base.frames*fraction).toLong();engine.seek(frame);Thread.sleep(100)
            assertTrue(abs(engine.state.value.frame-frame)<24000)
        }
        engine.seek(0);Thread.sleep(100)
    }
    for(speed in listOf(100,80,120)) {
        val m=Marker(name="REFRÃO",start=48000,end=96000)
        current=prepared(base.copy(stems=base.stems.take(4),markers=listOf(m),selectedMarker=m.id,loop=true),speed)
        engine.load(current);await { engine.state.value.ready && engine.state.value.projectId==current.id && !engine.state.value.preparing };engine.play();await { engine.state.value.playing }
        repeat(50) { Thread.sleep(50);assertTrue("loop $speed ${engine.state.value.frame}",engine.state.value.frame in 48000 until 96001) }
        assertEquals(0,engine.state.value.underruns);engine.stop();await { !engine.state.value.playing }
    }
    engine.unload();await { engine.state.value.projectId.isBlank() }
    log.appendText("Live 100→90→80→110→100 +2 pitch; mixer/pan/mute/solo; seek25/50/75%; loops100/80/120 passed.\n")
 }
}
