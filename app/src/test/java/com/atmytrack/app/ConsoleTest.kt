package com.atmytrack.app

import com.atmytrack.app.audio.*
import com.atmytrack.app.data.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*

class ConsoleTest {
    @Test fun bandDcaRestoresThreeIndependentFadersAndBusMuteIsIsolated() {
        val stems=listOf("Drums","Bass","Guitar").mapIndexed { i,n -> Stem(name=n,pcm="",frames=100,volume=ConsoleMath.gain(-2f*(i+1))) }
        val dca=Dca(name="BAND",members=stems.map { it.id },volume=ConsoleMath.gain(-5f))
        val p=Project(name="A",sampleRate=48000,stems=stems,dcas=listOf(dca))
        stems.forEachIndexed { i,s->assertEquals(ConsoleMath.gain(-2f*(i+1)-5),s.volume*ConsoleMath.dcaGain(s,p),.00001f) }
        val reset=p.copy(dcas=listOf(dca.copy(volume=1f)))
        assertEquals(stems,reset.stems)
        stems.forEachIndexed { i,s->assertEquals(-2f*(i+1),ConsoleMath.db(s.volume*ConsoleMath.dcaGain(s,reset)),.00001f) }
        val click=floatArrayOf(.4f,.4f);val guide=floatArrayOf(.2f,.2f);val band=floatArrayOf(.5f,.5f)
        val bus=FloatArray(2);ConsoleMath.route(click,bus,1,1f,0f,"BOTH");ConsoleMath.route(guide,bus,1,1f,0f,"BOTH")
        for(gain in listOf(1f,.5f,0f)) {
            val mix=FloatArray(2);ConsoleMath.route(bus,mix,1,gain,0f,"LEFT");ConsoleMath.route(band,mix,1,1f,0f,"RIGHT")
            assertEquals(.6f*gain,mix[0],.00001f);assertEquals(.5f,mix[1],.00001f)
        }
    }
    @Test fun metronomeMatrixKeepsTempoAndMuteSilencesOnlyClick() {
        for(beats in listOf(3,4,6))for(multiplier in listOf(.5,1.0,2.0))for(accent in listOf(false,true)) {
            val period=MusicTime.beatFrames(48000,120.0,multiplier).toLong()
            assertEquals(MixMath.click(20,48000,120.0,multiplier,beats,"Classic",accent),MixMath.click(20+period*beats,48000,120.0,multiplier,beats,"Classic",accent),.00001f)
            val p=Project(name="Click",sampleRate=48000,stems=emptyList(),bpm=120.0,beats=beats,multiplier=multiplier,accent=accent,master=1f,click=false)
            val dry=FloatArray(2000){.1f};MixMath.finish(dry,1000,0,p);assertTrue(dry.all { it==.1f })
            val wet=FloatArray(2000){.1f};MixMath.finish(wet,1000,0,p.copy(click=true));assertFalse(wet.contentEquals(dry));assertEquals(120.0,p.bpm,0.0)
        }
        assertEquals(1,ConsoleMath.transpose("C","C#"));assertEquals(2,ConsoleMath.transpose("C","D"));assertEquals(-3,ConsoleMath.transpose("A","F#"))
    }
    @Test fun faderHasConsoleTaperAndTenDbHeadroom() {
        assertEquals(0f,ConsoleMath.fromPosition(0f),0f)
        assertEquals(1f,ConsoleMath.fromPosition(.78f),.00001f)
        assertEquals(10f.pow(.5f),ConsoleMath.fromPosition(1f),.00001f)
        for(db in listOf(-60f,-40f,-30f,-20f,-10f,-5f,0f,5f,10f)) {
            val gain=ConsoleMath.gain(db)
            assertEquals(gain,ConsoleMath.fromPosition(ConsoleMath.position(gain)),.00001f)
        }
        assertTrue(ConsoleMath.position(1f)-ConsoleMath.position(.31622776f) > ConsoleMath.position(.01f)-ConsoleMath.position(.001f))
    }
    @Test fun dcaGainIsRelativeAndMuteDoesNotOverwriteStem() {
        val s=Stem(name="Bass",pcm="",frames=100,volume=ConsoleMath.gain(-3f))
        val d=Dca(name="Band",members=listOf(s.id),volume=ConsoleMath.gain(-5f))
        val p=Project(name="Test",sampleRate=48000,stems=listOf(s),dcas=listOf(d))
        assertEquals(ConsoleMath.gain(-8f),s.volume*ConsoleMath.dcaGain(s,p),.00001f)
        assertEquals(ConsoleMath.gain(-3f),p.stems.single().volume,0f)
        assertEquals(0f,ConsoleMath.dcaGain(s,p.copy(dcas=listOf(d.copy(mute=true)))),0f)
        assertEquals(1f,ConsoleMath.dcaGain(s,p.copy(dcas=listOf(d.copy(volume=1f)))),0f)
    }
    @Test fun stereoSplitAndBusGainsProduceActualSamples() {
        val input=floatArrayOf(.8f,.4f); val bus=FloatArray(2); val master=FloatArray(2)
        ConsoleMath.route(input,bus,1,.5f,0f,"LEFT")
        ConsoleMath.route(bus,master,1,.5f,0f,"BOTH")
        assertArrayEquals(floatArrayOf(.15f,0f),master,.00001f)
        val right=FloatArray(2); ConsoleMath.route(input,right,1,1f,0f,"RIGHT")
        assertArrayEquals(floatArrayOf(0f,.6f),right,.00001f)
    }
    @Test fun keysSupportEnharmonicsAndOctaves() {
        assertEquals(3,ConsoleMath.transpose("E","G")); assertEquals(ConsoleMath.note("C#"),ConsoleMath.note("Db"))
        assertEquals("E",ConsoleMath.target("E",12)); assertEquals("E",ConsoleMath.target("E",-12))
    }
    @Test fun tempoFindsPeriodicTransientsAndRejectsSilence() {
        for(bpm in listOf(60.0,71.96,96.0,120.0,145.0)) {
            val envelope=FloatArray(12000)
            var beat=0.0
            while(beat<envelope.size) { val index=beat.roundToInt(); if(index<envelope.size) envelope[index]=1f; beat+=6000/bpm }
            val result=TempoDetector.detect(envelope)
            assertEquals("$bpm detected as ${result.bpm}",bpm,result.bpm,1.0)
            assertTrue(result.confidence>.3)
        }
        assertEquals(0.0,TempoDetector.detect(FloatArray(12000)).bpm,0.0)
        val stems=listOf("Pad","Percussion","Drums","Click").map { Stem(name=it,pcm="",frames=1) }
        assertEquals("Click",TempoDetector.reference(stems).name)
        assertEquals("Drums",TempoDetector.reference(stems.dropLast(1)).name)
    }
    @Test fun synthesizedClickTimbresDifferAndAccentCanBeDisabled() {
        val sounds=listOf("Classic","Digital","Wood","Cowbell","Soft","High Tick","Low Tick")
        val blocks=sounds.map { sound -> FloatArray(1000) { MixMath.click(it.toLong(),48000,120.0,1.0,4,sound,false) } }
        for(i in blocks.indices)for(j in 0 until i)assertFalse(blocks[i].contentEquals(blocks[j]))
        for(i in 0..999) {
            assertEquals(MixMath.click(i.toLong(),48000,120.0,1.0,4,"Wood",false),MixMath.click(i+24000L,48000,120.0,1.0,4,"Wood",false),.00001f)
        }
        assertNotEquals(MixMath.click(20,48000,120.0,1.0,4,"Wood",true),MixMath.click(24020,48000,120.0,1.0,4,"Wood",true))
    }
}
