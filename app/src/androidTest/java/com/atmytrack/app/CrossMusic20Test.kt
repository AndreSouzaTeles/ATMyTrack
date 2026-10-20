package com.atmytrack.app

import android.net.Uri
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.atmytrack.app.data.*
import org.junit.*
import org.junit.Assert.*
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import kotlin.math.*

class CrossMusic20Test {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private fun model()=ViewModelProvider(compose.activity)[PlayerViewModel::class.java]
    private fun awaitState(timeout:Long,condition:()->Boolean) {
        try { compose.waitUntil(timeout,condition) } catch(e:Throwable) {
            val vm=model();throw AssertionError("Cross=${vm.crossMusic.value}; audio=${vm.playback.value}; selected=${vm.library.value.selected}; message=${vm.library.value.message}; background=${vm.library.value.background}",e)
        }
    }
    private fun wav(name:String,seconds:Int):Uri {
        val rate=48000;val frames=rate*seconds
        val data=ByteBuffer.allocate(44+frames*2).order(ByteOrder.LITTLE_ENDIAN)
        data.put("RIFF".toByteArray());data.putInt(36+frames*2);data.put("WAVEfmt ".toByteArray());data.putInt(16);data.putShort(1);data.putShort(1);data.putInt(rate);data.putInt(rate*2);data.putShort(2);data.putShort(16);data.put("data".toByteArray());data.putInt(frames*2)
        repeat(frames){data.putShort((sin(2*PI*220*it/rate)*3000).toInt().toShort())}
        return Uri.fromFile(File(context.cacheDir,"$name-${UUID.randomUUID()}.wav").apply { writeBytes(data.array()) })
    }
    private fun imported(name:String,key:String="C",seconds:Int=20,closeEditor:Boolean=true):Project {
        val vm=model();awaitState(30000){vm.library.value.busy==null}
        compose.runOnUiThread { vm.endCrossMusic();vm.stop();vm.pad.stop();vm.dismissError();vm.dismissImportReport() }
        val previous=vm.library.value.selected;val sources=listOf(wav("Piano",seconds),wav("Voz",seconds))
        compose.runOnUiThread { vm.import(uris=sources) }
        awaitState(60000){vm.library.value.busy==null && vm.library.value.selected!=previous && vm.playback.value.projectId==vm.library.value.selected && vm.playback.value.ready}
        compose.onNodeWithText("Editar projeto").assertExists()
        if(closeEditor)compose.onNodeWithText("FECHAR").performClick()
        compose.runOnUiThread { vm.dismissImportReport();vm.update { it.copy(name=name,key=key,keyAnalyzed=true,masterFaderLock=false,lockedFaders=emptyList(),unlockedFaders=emptyList()) } }
        return vm.library.value.current!!
    }
    @After fun cleanTransport() { compose.runOnUiThread { model().endCrossMusic();model().stop();model().pad.stop();model().dismissError() } }

    @Test fun importOpensEditorColorsPersistAndTimelineStaysFixedWithRotaryPan() {
        val p=imported("Editor de teste",closeEditor=false);val vm=model();val track=p.stems.first()
        compose.onNodeWithContentDescription("Cor da faixa ${track.name}").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Cor Violeta").performClick();compose.onNodeWithText("PRONTO").performClick()
        compose.onNodeWithText("SALVAR").performClick()
        awaitState(5000){vm.library.value.current!!.stems.first().color==0xFFB29CDA}
        awaitState(5000){runCatching { ProjectStore(context.filesDir).load().first { it.id==p.id }.stems.first().color==0xFFB29CDA }.getOrDefault(false)}
        compose.onNodeWithText("Playback").assertDoesNotExist();compose.onNodeWithText("Mixer").assertDoesNotExist()
        val timeline=compose.onNodeWithContentDescription("Posição da música").fetchSemanticsNode().boundsInRoot
        compose.onNodeWithContentDescription("Canais").performScrollTo()
        assertEquals(timeline,compose.onNodeWithContentDescription("Posição da música").fetchSemanticsNode().boundsInRoot)
        compose.onNodeWithContentDescription("Pan ${track.name}").performScrollTo().performSemanticsAction(SemanticsActions.SetProgress){it(.6f)}
        awaitState(3000){abs(vm.library.value.current!!.stems.first().pan-.6f)<.001f}
        val centralize=compose.onNodeWithContentDescription("Pan ${track.name}").fetchSemanticsNode().config[SemanticsActions.CustomActions].single { it.label=="Centralizar PAN" }.action
        compose.runOnUiThread { assertTrue(centralize()) }
        awaitState(3000){vm.library.value.current!!.stems.first().pan==0f}
        compose.onNodeWithContentDescription("Pan ${track.name}").performTouchInput {
            down(Offset(center.x,center.y-height*.32f));moveTo(Offset(center.x+width*.32f,center.y),500);up()
        }
        awaitState(3000){vm.library.value.current!!.stems.first().pan>.3f}
        compose.openTool("METRÔNOMO");compose.onNodeWithContentDescription("Smart Click").assertIsOff();compose.onNodeWithText("FECHAR").performClick()
    }
    @Test fun naturalEndAdvancesButPauseAndConfirmedExitPreserveCurrentAudio() {
        val a=imported("Cross A","C");val b=imported("Cross B","Bb");val vm=model()
        val entries=listOf(CrossEntry.from(a).copy(pad=CrossPad(enabled=true)),CrossEntry.from(b).copy(tracks=b.stems.associate { it.id to CrossTrack(.35f,it.id==b.stems.last().id) },pad=CrossPad(enabled=true,sound="Air")))
        compose.runOnUiThread { vm.startCrossMusic(entries) }
        awaitState(45000){vm.crossMusic.value.running && !vm.crossMusic.value.preparing && vm.playback.value.playing && vm.library.value.selected==a.id}
        compose.runOnUiThread { vm.pause() };awaitState(5000){!vm.playback.value.playing}
        val serial=vm.playback.value.completionSerial;Thread.sleep(300);assertEquals(a.id,vm.library.value.selected);assertTrue(vm.crossMusic.value.running)
        compose.runOnUiThread { vm.play() };awaitState(5000){vm.playback.value.playing}
        compose.runOnUiThread { vm.seek(a.timelineFrames-a.sampleRate/10) }
        awaitState(45000){vm.library.value.selected==b.id && vm.playback.value.playing && !vm.crossMusic.value.preparing}
        assertTrue(vm.playback.value.completionSerial>serial);assertEquals(10,vm.pad.state.value.note);assertEquals(144000,vm.pad.state.value.transitionFrames)
        assertTrue(vm.library.value.current!!.stems.last().mute);assertEquals(.35f,vm.library.value.current!!.stems.first().volume,0f)
        compose.activityRule.scenario.recreate()
        awaitState(5000){model().crossMusic.value.running && model().playback.value.playing}
        compose.onNodeWithContentDescription("Encerrar CrossMusic").performClick();compose.onNodeWithText("Tem certeza que deseja encerrar o CrossMusic?").assertExists();compose.onNodeWithText("Não").performClick()
        assertTrue(vm.crossMusic.value.running);assertTrue(vm.playback.value.playing)
        compose.onNodeWithContentDescription("Encerrar CrossMusic").performClick();compose.onNodeWithText("Sim").performClick()
        assertFalse(vm.crossMusic.value.running);assertTrue(vm.playback.value.playing);assertTrue(vm.pad.state.value.playing)
        val frame=vm.playback.value.frame;awaitState(5000){vm.playback.value.frame>frame+1000}
        compose.runOnUiThread { vm.seek(b.timelineFrames-b.sampleRate/10) };awaitState(10000){!vm.playback.value.playing}
        assertEquals(b.id,vm.library.value.selected);assertTrue(vm.pad.state.value.playing)
    }
    @Test fun endingAutomationDoesNotCancelPitchPreparationAndStopDoesNotAdvance() {
        val a=imported("DSP Cross","C");val vm=model()
        compose.runOnUiThread { vm.startCrossMusic(listOf(CrossEntry.from(a).copy(pad=CrossPad(enabled=true)))) }
        awaitState(45000){vm.playback.value.playing && !vm.crossMusic.value.preparing}
        compose.runOnUiThread { vm.applyPitch("C","D",2,a.stems.map { it.id });vm.endCrossMusic() }
        assertFalse(vm.crossMusic.value.running)
        awaitState(90000){vm.library.value.background==null && vm.library.value.current!!.semitones==2}
        assertTrue(vm.playback.value.playing);assertTrue(vm.pad.state.value.playing)
        val serial=vm.playback.value.completionSerial
        compose.runOnUiThread { vm.stop() };awaitState(5000){!vm.playback.value.playing}
        assertEquals(serial,vm.playback.value.completionSerial)
        compose.runOnUiThread { vm.update { it.copy(semitones=0,targetKey="",pitchTracks=emptyList(),stems=it.stems.map { s->s.copy(pitchFile="",pitchApplied=0,dspSpeed=100) }) };vm.startCrossMusic(listOf(CrossEntry.from(vm.library.value.current!!).copy(pad=CrossPad(enabled=true)))) }
        awaitState(45000){vm.playback.value.playing && !vm.crossMusic.value.preparing}
        compose.runOnUiThread { vm.seek(vm.library.value.current!!.timelineFrames-4800) }
        awaitState(10000){!vm.crossMusic.value.running && !vm.playback.value.playing && !vm.pad.state.value.playing}
    }
    @Test fun projectCardsDragToReorderAndCrossMusicCanStartFromItsEditor() {
        val p=imported("Plano UI ${UUID.randomUUID().toString().take(8)}","C");val vm=model()
        val original=vm.library.value.projects
        compose.onNodeWithContentDescription("Projetos").performScrollTo().performScrollToIndex(0)
        compose.onNodeWithContentDescription("Projetos").performTouchInput { down(Offset(width*.22f,height*.6f)) }
        compose.mainClock.advanceTimeBy(800)
        compose.onNodeWithContentDescription("Projetos").performTouchInput { moveTo(Offset(width*.82f,height*.6f),600);up() }
        awaitState(5000){vm.library.value.projects.indexOfFirst { it.id==original.first().id }>0}
        awaitState(5000){ProjectStore(context.filesDir).load().map { it.id }==vm.library.value.projects.map { it.id }}
        compose.runOnUiThread { vm.reorderProject(original.first().id,0);vm.saveCrossMusic(emptyList()) }
        compose.openTool("CROSSMUSIC")
        compose.onNodeWithContentDescription("Projetos CrossMusic").performScrollToNode(hasContentDescription("Incluir ${p.name} no CrossMusic"))
        compose.onNodeWithContentDescription("Incluir ${p.name} no CrossMusic").performClick()
        compose.onNodeWithContentDescription("Faixas de ${p.name}").performClick()
        compose.onNodeWithContentDescription("Mute CrossMusic ${p.name} / ${p.stems.first().name}").performScrollTo().performClick()
        compose.onNodeWithContentDescription("PAD CrossMusic ${p.name}").performScrollTo().performClick()
        compose.onNodeWithText("▶ INICIAR CROSSMUSIC · 1").performClick()
        awaitState(45000){vm.crossMusic.value.running && !vm.crossMusic.value.preparing && vm.playback.value.playing}
        assertTrue(vm.library.value.current!!.stems.first().mute);assertTrue(vm.pad.state.value.playing)
    }
    @Test fun globeStartsWithAndreAndShowsRequestedContributionAndBeneficiary() {
        val vm=model();awaitState(30000){vm.library.value.busy==null}
        compose.runOnUiThread { vm.dismissError();vm.dismissImportReport() }
        compose.onNodeWithContentDescription("Abrir menu principal").performClick();compose.onNodeWithText("DEIXE SUA MARCA").performClick()
        compose.onNodeWithContentDescription("Globo de nomes; arraste para girar").assert(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.StateDescription,"Nome em destaque: André Teles"))
        compose.onNodeWithText("André Teles").assertExists()
        compose.onNodeWithText("Contribua com um valor acima de U$ 5,00 (Cinco dólares) e deixe sua marca no Globo ATMyTrack").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Beneficiário informado: 64.876.586 ANDRE SOUZA TELES. Confira os dados no aplicativo do seu banco antes de confirmar.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("FECHAR").performClick()
    }
}
