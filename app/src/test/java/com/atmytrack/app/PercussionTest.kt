package com.atmytrack.app

import com.atmytrack.app.audio.*
import com.atmytrack.app.data.Project
import org.junit.Test
import org.junit.Assert.*
import kotlin.math.abs

class PercussionTest {
    private val kit by lazy { AcousticKit.load { java.io.File("src/main/assets",it).inputStream() } }
    @Test fun tenAcousticPatternsAreDistinctFiniteAndBounded() {
        assertEquals(10,PercussionSynth.patterns.size)
        val signatures=PercussionSynth.patterns.map { name ->
            val audio=PercussionSynth.render(name,120.0,4,4,kit=kit)
            assertEquals(96000*4*2,audio.size)
            assertTrue(audio.all { it.isFinite() && abs(it)<=.901f })
            assertTrue(audio.any { abs(it)>.05f })
            audio.contentHashCode()
        }
        assertEquals(10,signatures.distinct().size)
    }
    @Test fun recordingsAndFourBarVariationArePresent() {
        assertEquals(12,kit.samples.size)
        for(variants in kit.samples.values) {
            assertEquals(2,variants.size);assertFalse(variants[0].contentEquals(variants[1]))
            assertTrue(variants.all { samples -> samples.all { it.isFinite() } && samples.any { abs(it)>.05f } })
        }
        val loop=PercussionSynth.render("Pop / Rock",120.0,4,4,kit=kit)
        assertFalse(loop.copyOfRange(0,192000).contentEquals(loop.copyOfRange(192000,384000)))
    }
    @Test fun meterAndBpmDetermineExactBarDuration() {
        assertEquals(96000,PercussionSynth.barFrames(48000,120.0,4,4))
        assertEquals(72000,PercussionSynth.barFrames(48000,120.0,3,4))
        assertEquals(72000,PercussionSynth.barFrames(48000,120.0,6,8))
        assertEquals(48000,PercussionSynth.barFrames(48000,240.0,4,4))
    }
    @Test fun seekingAndBlockSplitsUseTheSameLoopClock() {
        val loop=PercussionSynth.render("Funk",120.0,4,4,kit=kit)
        val start=loop.size/2-93L
        val whole=FloatArray(1024);PercussionSynth.add(loop,whole,512,start,48000,.3f)
        val first=FloatArray(256);val second=FloatArray(768)
        PercussionSynth.add(loop,first,128,start,48000,.3f);PercussionSynth.add(loop,second,384,start+128,48000,.3f)
        assertArrayEquals(whole,first+second,0f)
        val silent=FloatArray(1024);PercussionSynth.add(loop,silent,512,start,48000,0f)
        assertTrue(silent.all { it==0f })
    }
    @Test fun independentMetronomeIsNotDuplicatedInTheMusicMix() {
        val p=Project(name="Click",sampleRate=48000,stems=emptyList(),click=true,bpm=120.0)
        val followed=FloatArray(1024);val independent=FloatArray(1024)
        MixMath.finish(followed,512,0,p)
        MixMath.finish(independent,512,0,p.copy(clickFollow=false))
        assertTrue(followed.any { abs(it)>.05f });assertTrue(independent.all { it==0f })
    }
}
