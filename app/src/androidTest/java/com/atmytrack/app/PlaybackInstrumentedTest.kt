package com.atmytrack.app

import android.Manifest
import android.content.Context
import android.net.Uri
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.atmytrack.app.audio.*
import com.atmytrack.app.data.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.*

@RunWith(AndroidJUnit4::class)
class PlaybackInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun wav(name: String, seconds: Int, rate: Int = 48000): Uri {
        val frames = seconds * rate
        val data = ByteBuffer.allocate(44 + frames * 2).order(ByteOrder.LITTLE_ENDIAN)
        data.put("RIFF".toByteArray()); data.putInt(36 + frames * 2); data.put("WAVEfmt ".toByteArray())
        data.putInt(16); data.putShort(1); data.putShort(1); data.putInt(rate); data.putInt(rate * 2); data.putShort(2); data.putShort(16)
        data.put("data".toByteArray()); data.putInt(frames * 2)
        repeat(frames) { data.putShort((sin(2 * PI * 220 * it / rate) * 8000).toInt().toShort()) }
        return Uri.fromFile(File(context.cacheDir, name).apply { writeBytes(data.array()) })
    }
    private fun model(): PlayerViewModel = ViewModelProvider(compose.activity)[PlayerViewModel::class.java]
    private fun prepared(names:List<String>):Pair<PlayerViewModel,Project> {
        val vm=model();compose.waitUntil(10000){vm.library.value.busy==null};val previous=vm.library.value.selected
        compose.runOnUiThread { vm.stop();vm.import(uris=names.map { wav("$it.wav",20) }) }
        compose.waitUntil(20000){vm.library.value.busy==null && vm.library.value.selected!=previous && vm.playback.value.projectId==vm.library.value.selected}
        compose.runOnUiThread { vm.dismissError();vm.dismissImportReport() }
        return vm to vm.library.value.current!!
    }
    @Test fun smartClickAnalysisAllowsPageNavigationAndRequiresBpmConfirmation() {
        val vm=model();compose.waitUntil(10000){vm.library.value.busy==null}
        val previous=vm.library.value.selected
        val file=File(context.getExternalFilesDir(null),"fixtures/Aligned.wav")
        compose.runOnUiThread { vm.import(uris=listOf(Uri.fromFile(file))) }
        compose.waitUntil(20000){vm.library.value.busy==null && vm.library.value.selected!=previous && vm.playback.value.projectId==vm.library.value.selected}
        compose.runOnUiThread { vm.dismissError();vm.dismissImportReport();vm.update { it.copy(bpm=72.0) };vm.analyzeTempo(true) }
        assertNull(vm.library.value.busy)
        compose.onNodeWithText("Mixer").performClick()
        compose.onNodeWithText("+ DCA").assertExists()
        compose.onNodeWithText("Playback").performClick()
        compose.waitUntil(15000){vm.library.value.background==null && vm.library.value.current!!.detectedBpm>0}
        assertEquals(120.0,vm.library.value.current!!.detectedBpm,1.0)
        assertEquals(72.0,vm.library.value.current!!.bpm,0.0)
    }
    @Test fun doubleTapFineGainAllFadersAndDragRemainIndependent() {
        val (vm,p)=prepared(listOf("FaderTrack"))
        compose.runOnUiThread { vm.update { it.copy(master=.3f,stems=it.stems.map { s->s.copy(volume=.4f) },dcas=listOf(Dca(name="Fine DCA",volume=.2f)),buses=listOf(Bus(name="Fine BUS",volume=.2f))) } }
        compose.onNodeWithText("Mixer").performClick()
        compose.onNodeWithContentDescription("Canais").performScrollTo()
        for(name in listOf("FaderTrack","Fine DCA","Fine BUS","MASTER")) {
            if(name!="MASTER")compose.onNodeWithContentDescription("Canais").performScrollToNode(hasContentDescription(if(name=="FaderTrack")"Arrastar $name" else name))
            compose.onNodeWithContentDescription("Volume $name").performScrollTo().performTouchInput { doubleClick(center) }
            try { compose.waitUntil(5000) {
                val q=vm.library.value.current!!
                when(name){"MASTER"->q.master==1f;"FaderTrack"->q.stems[0].volume==1f;"Fine DCA"->q.dcas[0].volume==1f;else->q.buses[0].volume==1f}
            } } catch(e:Exception) { throw AssertionError("Double tap $name: ${vm.library.value.current}",e) }
        }
        compose.onNodeWithContentDescription("Canais").performScrollToNode(hasContentDescription("Arrastar FaderTrack"))
        compose.onNodeWithContentDescription("Ajuste fino FaderTrack").performScrollTo().performClick()
        compose.onNode(hasSetTextAction()).performTextReplacement("-2.7")
        compose.onNodeWithText("+0.1").performClick();compose.onNodeWithText("APLICAR").performClick()
        compose.waitUntil { abs(ConsoleMath.db(vm.library.value.current!!.stems[0].volume)+2.6f)<.001f }
        compose.onNodeWithContentDescription("Volume FaderTrack").performScrollTo().performTouchInput { swipe(center,Offset(center.x,height*.35f),500) }
        compose.waitUntil { abs(ConsoleMath.db(vm.library.value.current!!.stems[0].volume)+2.6f)>.1f }
        assertEquals(p.stems.map { it.id },vm.library.value.current!!.stems.map { it.id })
        val final=vm.library.value.current!!
        compose.waitUntil(5000) { ProjectStore(context.filesDir).load().find { it.id==p.id }==final }
    }
    @Test fun fiveTracksReorderToFirstAndReopenProject() {
        val (vm,p)=prepared(listOf("Drums","Bass","Guitar","Piano","Click"))
        compose.onNodeWithContentDescription("Canais").performScrollToNode(hasContentDescription("Arrastar Click"))
        val header=compose.onNodeWithContentDescription("Arrastar Click");header.performScrollTo()
        header.performTouchInput { down(center) };compose.mainClock.advanceTimeBy(700)
        val edgeDelta=compose.onNodeWithContentDescription("Canais").fetchSemanticsNode().boundsInRoot.left+2f-header.fetchSemanticsNode().boundsInRoot.center.x
        header.performTouchInput { moveBy(Offset(edgeDelta,0f),300) }
        try { compose.waitUntil(8000){vm.library.value.current!!.stems.first().name=="Click"} }
        catch(e:Exception) { throw AssertionError("Final drag order: ${vm.library.value.current!!.stems.map { it.name }}",e) }
        compose.onRoot().performTouchInput { up() }
        val order=listOf("Click","Drums","Bass","Guitar","Piano")
        assertEquals(order,vm.library.value.current!!.stems.map { it.name })
        compose.waitUntil { ProjectStore(context.filesDir).load().find { it.id==p.id }?.stems?.map { it.name }==order }
        val other=vm.library.value.projects.first { it.id!=p.id }
        compose.runOnUiThread { vm.select(other) };compose.waitUntil { vm.playback.value.projectId==other.id }
        compose.runOnUiThread { vm.select(ProjectStore(context.filesDir).load().first { it.id==p.id }) }
        compose.waitUntil { vm.playback.value.projectId==p.id };assertEquals(order,vm.library.value.current!!.stems.map { it.name })
    }
    @Test fun threeProjectCardsPlusAndConfirmedDeletion() {
        val projects=listOf("Card A","Card B","Card C").map { prepared(listOf(it)).second }
        val vm=model()
        fun card(name:String) {
            compose.onNodeWithContentDescription("Projetos").performScrollToNode(hasContentDescription("Projeto $name"))
        }
        card("Card A");compose.onNodeWithContentDescription("Projeto Card A").performClick()
        compose.waitUntil { vm.library.value.selected==projects[0].id }
        compose.onNodeWithContentDescription("Excluir Card A").performClick()
        assertTrue(vm.library.value.projects.any { it.id==projects[0].id })
        compose.onNodeWithText("CANCELAR").performClick();assertTrue(vm.library.value.projects.any { it.id==projects[0].id })
        compose.onNodeWithContentDescription("Excluir Card A").performClick();compose.onNodeWithText("EXCLUIR").performClick()
        compose.waitUntil { vm.library.value.projects.none { it.id==projects[0].id } }
        compose.onNodeWithContentDescription("Projetos").performScrollToNode(hasContentDescription("Novo projeto"))
        compose.onNodeWithContentDescription("Novo projeto").performClick();compose.onNodeWithText("IMPORTAR ARQUIVOS").assertExists()
        compose.onNodeWithText("FECHAR").performClick()
    }
    @Test fun realImportPlaybackPauseSeekMixerAndSavedLibrary() {
        InstrumentationRegistry.getInstrumentation().uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        val vm = model()
        compose.waitUntil(10000) { vm.library.value.busy == null }
        val files = listOf(wav("01 Piano.wav", 8), wav("02 Bass.wav", 5))
        val previousId=vm.library.value.selected
        compose.runOnUiThread { vm.import(uris = files) }
        compose.waitUntil(30000) { vm.library.value.busy == null && vm.library.value.selected != previousId && vm.library.value.current?.stems?.size == 2 }
        val p = vm.library.value.current!!
        compose.waitUntil(10000) { vm.playback.value.projectId == p.id }
        assertEquals(48000, p.sampleRate)
        assertEquals(384000L, p.frames)
        assertEquals(240000L, p.stems[1].frames)
        compose.runOnUiThread { vm.dismissError(); vm.update { it.copy(name = "Ensaio • teste de áudio", bpm = 96.0, markers = listOf(Marker(name = "VERSO", start = 48000, end = 192000))) } }
        compose.onNodeWithText("▶ PLAY").assertIsDisplayed().performClick()
        compose.waitUntil(10000) { vm.playback.value.playing && vm.playback.value.frame > 16000 }
        val beforePage = vm.playback.value.frame
        val pageUnderruns=vm.playback.value.underruns
        compose.onNodeWithText("Mixer").performClick()
        compose.waitUntil(5000) { vm.playback.value.frame > beforePage + 5000 }
        assertEquals(p.id, vm.playback.value.projectId)
        assertTrue(vm.playback.value.playing)
        compose.onNodeWithText("Playback").performClick()
        repeat(5) {
            compose.onNodeWithText("Mixer").performClick();compose.onNodeWithText("Playback").performClick()
            assertTrue(vm.playback.value.playing);assertEquals(p.id,vm.playback.value.projectId)
        }
        assertEquals("Changing pages must not starve the output",pageUnderruns,vm.playback.value.underruns)
        assertTrue(vm.playback.value.frame>=beforePage)
        assertEquals(p.stems.map { it.volume },vm.library.value.current!!.stems.map { it.volume })
        compose.onNodeWithText("Ⅱ PAUSAR").assertIsDisplayed().performClick()
        compose.waitUntil(5000) { !vm.playback.value.playing }
        val paused = vm.playback.value.frame
        Thread.sleep(150)
        assertEquals(paused, vm.playback.value.frame)
        compose.runOnUiThread { vm.seek(96000) }
        compose.waitUntil(5000) { vm.playback.value.frame == 96000L }
        compose.runOnUiThread {
            vm.stem(p.stems[0].id) { it.copy(volume = .35f, pan = -.5f, solo = true) }
            vm.update { it.copy(click = true, master = .5f) }
        }
        compose.onNodeWithText("▶ PLAY").assertIsDisplayed().performClick()
        compose.waitUntil(5000) { vm.playback.value.frame > 110000L }
        compose.runOnUiThread { vm.stop() }
        compose.waitUntil(5000) { !vm.playback.value.playing && vm.playback.value.frame == 0L }
        compose.waitUntil(5000) { ProjectStore(context.filesDir).load().find { it.id == p.id }?.stems?.first()?.volume == .35f }
        val stored = ProjectStore(context.filesDir).load().first { it.id == p.id }
        assertTrue(stored.stems.first().solo)
        assertEquals(-.5f, stored.stems.first().pan, 0f)
        assertEquals(1, stored.markers.size)
    }
    @Test fun routingDialogsAndHeaderDragPreserveSettings() {
        val vm=model()
        compose.waitUntil(10000) { vm.library.value.busy==null }
        val previousId=vm.library.value.selected
        compose.runOnUiThread { vm.import(uris=listOf(wav("Drag Piano.wav",20),wav("Drag Bass.wav",20))) }
        compose.waitUntil(20000) { vm.library.value.busy==null && vm.library.value.selected!=previousId && vm.library.value.current?.name=="Drag Piano" && vm.playback.value.projectId==vm.library.value.selected }
        compose.runOnUiThread { vm.dismissError() }
        val original=vm.library.value.current!!
        compose.onNodeWithText("Mixer").performClick()
        compose.onNodeWithText("+ DCA").performScrollTo().performClick()
        compose.onNodeWithText("DCA 1").performTextReplacement("Band")
        compose.onNode(hasText("Drag Piano") and hasAnyAncestor(isDialog())).performClick()
        compose.onNodeWithText("SALVAR").performClick()
        compose.waitUntil { vm.library.value.current!!.dcas.size==1 }
        assertTrue(original.stems[0].id in vm.library.value.current!!.dcas.single().members)
        compose.onNodeWithText("+ BUS").performScrollTo().performClick()
        compose.onNodeWithText("BUS 1").performTextReplacement("Keys")
        compose.onNode(hasText("Drag Piano") and hasAnyAncestor(isDialog())).performClick()
        compose.onNodeWithText("SALVAR").performClick()
        compose.waitUntil { vm.library.value.current!!.buses.size==1 }
        assertEquals(vm.library.value.current!!.buses[0].id,vm.library.value.current!!.stems[0].bus)
        compose.onNodeWithText("Playback").performClick()
        val header=compose.onNodeWithContentDescription("Arrastar Drag Piano")
        header.performScrollTo()
        header.performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(700)
        header.performTouchInput { moveBy(Offset(160f*context.resources.displayMetrics.density,0f),300) }
        compose.waitUntil(5000) { vm.library.value.current!!.stems[1].id==original.stems[0].id }
        compose.onNodeWithContentDescription("Arrastar Drag Piano").performTouchInput { up() }
        assertEquals(original.stems[0].volume,vm.library.value.current!!.stems[1].volume,0f)
        compose.waitUntil(5000) { ProjectStore(context.filesDir).load().find { it.id==original.id }?.stems?.get(1)?.id==original.stems[0].id }
    }
    @Test fun mixedSampleRatesPrepareOnCommonTimeline() = runBlocking {
        val p=AudioImporter(context).import(listOf(wav("a.wav",1),wav("b.wav",1,44100)),"Mixed rates") {}
        assertEquals(2,p.stems.size)
        assertEquals(p.stems[0].frames,p.stems[1].frames)
        val block=FloatArray(1024)
        p.stems.forEach { s -> com.atmytrack.app.audio.PlaybackCache(context).open(s,p.sampleRate).use { it.read(0,512,block) } }
    }
    @Test fun corruptFileDoesNotCreatePartialProject() = runBlocking {
        val source = File(context.cacheDir, "invalid.wav").apply { writeText("not an audio file") }
        var rejected = false
        try { AudioImporter(context).import(listOf(Uri.fromFile(source)), "Invalid") {} }
        catch (_: Exception) { rejected = true }
        assertTrue(rejected)
    }
}
