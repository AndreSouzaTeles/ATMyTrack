package com.atmytrack.app

import com.atmytrack.app.audio.RhythmEngine
import com.atmytrack.app.data.Project
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],application=android.app.Application::class)
class RhythmLinkPersistenceTest {
    @Test fun linkFlagPersistsAndBothEditorsPropagateProjectState() {
        val context=RuntimeEnvironment.getApplication();context.getSharedPreferences("rhythm",0).edit().clear().commit()
        var engine=RhythmEngine(context)
        try {
            assertTrue(engine.state.value.linked)
            engine.configureMetronome { it.copy(bpm=123.0) };assertEquals(123.0,engine.state.value.percussion.bpm,0.0)
            engine.configure { it.copy(bpm=87.0) };assertEquals(87.0,engine.state.value.metronome.bpm,0.0)
            engine.setLinked(false);engine.close();engine=RhythmEngine(context);assertFalse(engine.state.value.linked)
            engine.configure { it.copy(bpm=99.0) };assertEquals(87.0,engine.state.value.metronome.bpm,0.0)
            engine.setLinked(true,fromPercussion=true);assertEquals(99.0,engine.state.value.metronome.bpm,0.0)
            val p=Project(name="Linked",sampleRate=48000,stems=emptyList(),bpm=130.0,clickFollow=true)
            engine.syncProject(p);var changed:Project?=null;engine.onLinkedProjectChange={changed=it}
            engine.configure { it.copy(enabled=true) };assertTrue(changed!!.click);assertTrue(changed!!.clickFollow)
            engine.configure { it.copy(follow=false,bpm=111.0) };assertEquals(111.0,changed!!.bpm,0.0);assertFalse(changed!!.clickFollow)
            engine.armMetronome(false);assertFalse(engine.state.value.percussion.enabled)
        } finally {engine.close()}
    }
}
