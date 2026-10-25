package com.atmytrack.app

import com.atmytrack.app.audio.*
import com.atmytrack.app.data.Project
import org.junit.Test
import org.junit.Assert.*
import kotlin.math.abs

class PercussionTest {
    @Test fun tenOriginalPatternsAreDistinctFiniteAndBounded() {
        assertEquals(10,PercussionSynth.patterns.size)
        val signatures=PercussionSynth.patterns.map { name ->
            val audio=PercussionSynth.render(name,120.0,4,4)
            assertEquals(96000*2,audio.size)
            assertTrue(audio.all { it.isFinite() && abs(it)<=.651f })
            assertTrue(audio.any { abs(it)>.05f })
            audio.contentHashCode()
        }
        assertEquals(10,signatures.distinct().size)
    }
    @Test fun meterAndBpmDetermineExactBarDuration() {
        assertEquals(96000,PercussionSynth.barFrames(48000,120.0,4,4))
        assertEquals(72000,PercussionSynth.barFrames(48000,120.0,3,4))
        assertEquals(72000,PercussionSynth.barFrames(48000,120.0,6,8))
        assertEquals(48000,PercussionSynth.barFrames(48000,240.0,4,4))
    }
    @Test fun seekingAndBlockSplitsUseTheSameLoopClock() {
        val loop=PercussionSynth.render("Funk",120.0,4,4)
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
