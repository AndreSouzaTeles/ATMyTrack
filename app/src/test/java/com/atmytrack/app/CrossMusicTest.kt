package com.atmytrack.app

import com.atmytrack.app.audio.*
import com.atmytrack.app.data.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.nio.file.Files
import kotlin.math.abs

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],application=android.app.Application::class)
class CrossMusicTest {
    @Test fun trackColorsAndLegacyAbsencePreserveEverythingElse() {
        val root=Files.createTempDirectory("track-colors").toFile()
        try {
            val project=Project(name="Mix",sampleRate=48000,stems=listOf(Stem(id="a",name="Bass",pcm="bass.wav",frames=48000,volume=.45f,pan=-.2f,color=0xFFA137CF),Stem(id="b",name="Drums",pcm="drums.wav",frames=48000)))
            val store=ProjectStore(root);store.save(listOf(project));assertEquals(project,store.load().single())
            val file=java.io.File(root,"library.json");val doc=org.json.JSONObject(file.readText());doc.getJSONArray("projects").getJSONObject(0).getJSONArray("stems").getJSONObject(0).remove("color");file.writeText(doc.toString())
            assertEquals(project.copy(stems=project.stems.map { it.copy(color=null) }),store.load().single())
            assertFalse(Project(name="New",sampleRate=48000,stems=emptyList()).smartClick)
        } finally { root.deleteRecursively() }
    }
    @Test fun sequenceOrderTrackMixAndPadChoicesRoundTripIndependently() {
        val root=Files.createTempDirectory("cross-plan").toFile()
        try {
            val plan=listOf(CrossEntry("second",mapOf("voice" to CrossTrack(.3f,true)),CrossPad(true,false,10,true,"Air",.4f)),CrossEntry("first",mapOf("guitar" to CrossTrack(1.4f,false)),CrossPad(true,true,sound="Strings")))
            val store=CrossMusicStore(root);store.save(plan);assertEquals(plan,store.load())
            val source=Project(name="Second",sampleRate=48000,stems=listOf(Stem(id="voice",name="Voice",pcm="file",frames=48000,solo=true,color=0xFF89ACF2)))
            val mixed=plan[0].apply(source)
            assertEquals(.3f,mixed.stems.single().volume,0f);assertTrue(mixed.stems.single().mute);assertFalse(mixed.stems.single().solo)
            assertEquals(source.stems.single().color,mixed.stems.single().color);assertTrue(source.stems.single().solo)
            assertFalse(java.io.File(root,"library.json").exists())
        } finally { root.deleteRecursively() }
    }
    @Test fun padFollowsEnharmonicAndTransposedKeysOrAnExplicitOverride() {
        assertEquals(1 to false,PadKey.parse("Db"));assertEquals(10 to true,PadKey.parse("B♭m"));assertEquals(6 to false,PadKey.parse("F#"))
        assertNull(PadKey.parse("—"));assertNull(PadKey.parse("unknown"))
        val p=Project(name="P",sampleRate=48000,stems=emptyList(),key="Bb",semitones=2)
        assertEquals(0 to false,PadKey.resolve(p,CrossPad(followProjectKey=true)))
        assertEquals(4 to true,PadKey.resolve(p.copy(targetKey="Em"),CrossPad(followProjectKey=true)))
        assertEquals(7 to false,PadKey.resolve(p,CrossPad(followProjectKey=false,note=7)))
    }
    @Test fun padChangesChordOverExactlyThreeSecondsWithoutPitchGlidesOrClicks() {
        val render=PadCrossfade();val old=FloatArray(48000*2);render.target(PadTone(0,false,"Warm"));render.fill(old,48000,.5f)
        render.target(PadTone(6,true,"Strings"),144000);assertEquals(144000,render.remainingFrames)
        val block=FloatArray(1024);var previous=old.last();var maximumJump=0f
        repeat(281) { render.fill(block,512,.5f);for(v in block) { maximumJump=maxOf(maximumJump,abs(v-previous));previous=v;assertTrue(v.isFinite() && abs(v)<.65f) } }
        assertEquals(128,render.remainingFrames);render.fill(block,128,.5f);assertEquals(0,render.remainingFrames)
        assertTrue("Crossfade discontinuity $maximumJump",maximumJump<.2f)
        // Retarget during a transition preserves an audible outgoing chord.
        render.target(PadTone(2,false,"Air"),144000);render.fill(block,512,.5f);render.target(PadTone(9,true,"Organ"),144000);render.fill(block,512,.5f)
        assertTrue(block.any { abs(it)>.0001f });assertTrue(block.all { it.isFinite() && abs(it)<.65f })
    }
}
