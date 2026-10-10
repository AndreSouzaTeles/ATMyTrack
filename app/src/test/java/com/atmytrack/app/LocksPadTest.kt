package com.atmytrack.app
import com.atmytrack.app.data.*
import com.atmytrack.app.audio.PadSynth
import org.junit.Test
import org.junit.Assert.*
import kotlin.math.*
class LocksPadTest {
 @Test fun individualUnlockOverridesMasterAndRelockRestoresProtection() {
  val p=Project(name="Locks",sampleRate=48000,stems=listOf(Stem(id="A",name="A",pcm="",frames=1)),masterFaderLock=true)
  val unlocked=p.toggleGainLock("A")
  assertTrue(unlocked.masterFaderLock);assertFalse(unlocked.gainLocked("A"));assertTrue(unlocked.gainLocked("B"))
  assertEquals(.25f,unlocked.protectGains(unlocked.copy(stems=unlocked.stems.map{it.copy(volume=.25f)})).stems[0].volume,0f)
  assertTrue(unlocked.toggleGainLock("A").gainLocked("A"));assertTrue(unlocked.toggleGainLock("MASTER").toggleGainLock("MASTER").gainLocked("A"))
 }
 @Test fun padPresetsAreDistinctAndBoundedAndWoodblockIsReal() {
  val signals=PadSynth.sounds.map { (id,_) -> FloatArray(48000).also { PadSynth().fill(it,24000,0,false,1f,id) } }
  for(a in signals)assertTrue(a.all{it.isFinite()&&abs(it)<=.651f})
  for(i in signals.indices)for(j in 0 until i)assertFalse(signals[i].contentEquals(signals[j]))
  val wood=FloatArray(2400){com.atmytrack.app.audio.MixMath.click(it.toLong(),48000,72.0,1.0,4,"Woodblock")}
  assertTrue(wood.any{abs(it)>.1f});assertEquals(0f,wood.last(),0f)
 }
 @Test fun lockedGainsSurviveEveryEditPathAndGlobalUnlockKeepsIndividualLocks() {
    val p=Project(name="Locks",sampleRate=48000,stems=listOf(Stem(id="A",name="A",pcm="",frames=1,volume=.5f),Stem(id="B",name="B",pcm="",frames=1)),dcas=listOf(Dca(id="D",name="D",members=emptyList())),buses=listOf(Bus(id="G",name="G")),lockedFaders=listOf("A"))
    val change=p.copy(master=.2f,stems=p.stems.map { it.copy(volume=.1f,pan=.2f) },dcas=p.dcas.map { it.copy(volume=.1f) },buses=p.buses.map { it.copy(volume=.1f) })
    val individual=p.protectGains(change)
    assertEquals(.5f,individual.stems[0].volume,0f);assertEquals(.1f,individual.stems[1].volume,0f);assertEquals(.2f,individual.stems[0].pan,0f)
    val global=p.copy(masterFaderLock=true).protectGains(change)
    assertEquals(p.master,global.master,0f);assertEquals(p.stems.map { it.volume },global.stems.map { it.volume });assertEquals(1f,global.dcas[0].volume,0f);assertEquals(1f,global.buses[0].volume,0f)
    assertTrue(global.gainLocked("A"));assertTrue(global.copy(masterFaderLock=false).gainLocked("A"));assertFalse(global.copy(masterFaderLock=false).gainLocked("B"))
 }
 @Test fun padHasMusicalTriadEnergyAndSmoothVolumeDecay() {
    fun energy(data:FloatArray,hz:Double):Double { var a=0.0;var b=0.0;for(i in 0 until data.size/2){a+=data[i*2]*cos(2*PI*hz*i/48000);b+=data[i*2]*sin(2*PI*hz*i/48000)};return hypot(a,b)/(data.size/2) }
    for(minor in listOf(false,true))for(note in 0..11){val synth=PadSynth();val data=FloatArray(96000);synth.fill(data,48000,note,minor,.5f)
        for(midi in listOf(48+note,48+note+if(minor)3 else 4,55+note))assertTrue(energy(data,440.0*2.0.pow((midi-69)/12.0))>.005)
        assertTrue(data.all { it.isFinite() && abs(it)<.5f });synth.fill(data,48000,note,minor,0f);assertTrue(data.takeLast(1000).all { abs(it)<.00001f })
    }
 }
 @Test fun fullPadLevelIsStrongerThanPreviousAndKeepsHeadroom() {
    var smallest=Double.MAX_VALUE;var largest=0.0
    for(minor in listOf(false,true))for(note in 0..11){
      val old=FloatArray(96000);val new=FloatArray(96000)
      com.atmytrack.app.audio.LegacyPadSynth().fill(old,48000,note,minor,1f);PadSynth().fill(new,48000,note,minor,1f)
      fun rms(a:FloatArray)=sqrt(a.drop(4096).sumOf { it.toDouble()*it }/(a.size-4096))
      val gain=rms(new)/rms(old);smallest=minOf(smallest,gain);largest=maxOf(largest,new.maxOf { abs(it).toDouble() })
      assertTrue("RMS boost $gain",gain>1.8);assertTrue(new.all { it.isFinite() && abs(it)<=.951f })
    }
    println("PAD 24 chords full slider: min RMS gain=$smallest max peak=$largest")
 }

}
