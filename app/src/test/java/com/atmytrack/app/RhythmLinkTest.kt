package com.atmytrack.app

import com.atmytrack.app.audio.*
import org.junit.Assert.*
import org.junit.Test

class RhythmLinkTest {
    @Test fun defaultLinkAndMetronomeChangesReachPercussion() {
        val s=RhythmState();assertTrue(s.linked)
        val next=RhythmLink.fromMetronome(s,s.metronome.copy(bpm=137.5,click=true,clickFollow=false,beats=7,denominator=8),true)
        assertEquals(137.5,next.percussion.bpm,0.0);assertTrue(next.percussion.enabled);assertFalse(next.percussion.follow)
        assertEquals(7,next.percussion.beats);assertEquals(8,next.percussion.denominator)
        assertFalse(RhythmLink.fromMetronome(next,next.metronome.copy(click=false),false).percussion.enabled)
    }
    @Test fun percussionControlsBothInEitherTransportMode() {
        for(follow in listOf(false,true)) {
            val s=RhythmState();val next=RhythmLink.fromPercussion(s,s.percussion.copy(bpm=92.0,follow=follow,enabled=true))
            assertEquals(92.0,next.metronome.bpm,0.0);assertTrue(next.metronome.click);assertTrue(next.metronomeArmed)
            assertEquals(follow,next.metronome.clickFollow)
            assertFalse(RhythmLink.fromPercussion(next,next.percussion.copy(enabled=false)).metronome.click)
        }
    }
    @Test fun unlinkedChangesAreIndependentAndKeepVolumes() {
        val s=RhythmState(linked=false)
        val a=RhythmLink.fromPercussion(s,s.percussion.copy(bpm=88.0,enabled=true))
        assertEquals(s.metronome,a.metronome)
        val b=RhythmLink.fromMetronome(a,a.metronome.copy(bpm=144.0),true)
        assertEquals(a.percussion,b.percussion)
        val c=RhythmLink.fromMetronome(b.copy(linked=true),b.metronome,true)
        assertEquals(144.0,c.percussion.bpm,0.0);assertEquals(s.percussion.volume,c.percussion.volume)
        assertEquals(s.metronome.clickVolume,c.metronome.clickVolume)
    }
}
