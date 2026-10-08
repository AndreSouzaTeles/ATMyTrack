package com.atmytrack.app
import android.net.Uri
import kotlinx.coroutines.runBlocking
import android.os.SystemClock
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.atmytrack.app.audio.*
import com.atmytrack.app.data.*
import org.junit.*
import org.junit.Assert.*
import java.io.File
class Feature06Test {
 @get:Rule val compose=createAndroidComposeRule<MainActivity>()
 @Test fun newControlsSearchArtworkAndSupporterDraft() {
    val vm=ViewModelProvider(compose.activity)[PlayerViewModel::class.java]
    val context=InstrumentationRegistry.getInstrumentation().targetContext
    compose.waitUntil(15000) { vm.library.value.busy==null }
    val old=vm.library.value.selected
    compose.runOnUiThread { vm.stop();vm.import(uris=listOf(Uri.fromFile(File(context.getExternalFilesDir(null),"fixtures/Aligned.wav")))) }
    compose.waitUntil(30000) { vm.library.value.selected!=old && vm.playback.value.ready && vm.playback.value.projectId==vm.library.value.selected }
    compose.runOnUiThread { vm.dismissError();vm.dismissImportReport();vm.update { it.copy(name="Projeto Azul 06",key="Dm",bpm=72.0) } }
    assertEquals(1f,vm.library.value.current!!.stems.first().volume,0f)
    compose.onNodeWithContentDescription("Pesquisar projetos").performClick()
    compose.onNodeWithText("Nome do projeto ou tom").performTextInput("azul 06")
    compose.onNodeWithContentDescription("Resultados de projetos").performScrollToNode(hasContentDescription("Abrir projeto ${vm.library.value.selected}"))
    compose.onNodeWithContentDescription("Abrir projeto ${vm.library.value.selected}").performClick()
    compose.runOnUiThread { vm.artwork(Uri.parse("android.resource://${context.packageName}/${R.drawable.brand_art}")) }
    compose.waitUntil(10000) { vm.library.value.current!!.artwork.isNotBlank() }
    compose.onNodeWithText("METRÔNOMO").performScrollTo().performClick()
    compose.onNodeWithText("Smart Click").assertIsDisplayed()
    compose.onAllNodes(isToggleable())[0].performClick()
    compose.waitUntil(30000) { vm.library.value.current!!.detectedBpm>0 && vm.library.value.background==null }
    assertEquals(vm.library.value.current!!.detectedBpm,vm.library.value.current!!.bpm,0.0)
    compose.onNodeWithText("PLAY").performClick();assertTrue(vm.library.value.current!!.click)
    compose.onNodeWithText("FECHAR").performClick()
    compose.onNodeWithText("+ SEÇÃO").performScrollTo().performClick()
    compose.onNode(hasContentDescription("Posição da música") and hasAnyAncestor(isDialog())).assertIsDisplayed()
    compose.onNodeWithText("MARCAR INÍCIO").assertExists()
    compose.onNodeWithText("FECHAR").performClick()
    val m=Marker(name="Trecho 06",start=48000,end=96000)
    compose.runOnUiThread { vm.update { it.copy(markers=listOf(m)) } }
    compose.onNodeWithContentDescription("Seção Trecho 06").performScrollTo().performTouchInput { longClick() }
    compose.onNodeWithContentDescription("Seção Trecho 06").assertIsSelected()
    compose.onNodeWithText("↻ LOOP").performClick()
    compose.waitUntil(10000) { vm.playback.value.frame in 48000 until 96000 }
    compose.runOnUiThread { vm.stop();vm.update { it.copy(loop=false) } }
    compose.onNodeWithContentDescription("Abrir menu principal").performClick()
    compose.onNodeWithText("CENTRAL DE DÚVIDAS").assertIsDisplayed()
    compose.onNodeWithText("DEIXE SUA MARCA").performClick()
    compose.onNodeWithContentDescription("Globo de nomes; arraste para girar").assertIsDisplayed()
    compose.onNodeWithText("⌕ Encontrar um nome no globo").performTextInput("Ana Carolina")
    compose.onNodeWithText("Ana Carolina Silva").assertExists()
    InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot().let { bitmap -> File(context.getExternalFilesDir(null),"supporters-0.6.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) };bitmap.recycle() }
    compose.onNodeWithText("Nome completo para publicação").performScrollTo().performTextInput("Pessoa Teste")
    compose.onNodeWithText("SALVAR MEU NOME").performScrollTo().performClick()
    compose.onNodeWithText("Nome salvo neste aparelho. Ele ainda não foi enviado nem publicado.").assertExists()
    InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot().let { bitmap -> File(context.getExternalFilesDir(null),"supporter-form-0.6.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) };bitmap.recycle() }
    compose.onNodeWithContentDescription("QR Code Pix ATMyTrack").performScrollTo().assertIsDisplayed()
    InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot().let { bitmap -> File(context.getExternalFilesDir(null),"pix-screen-0.6.1.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) };bitmap.recycle() }
    compose.onNodeWithText("FECHAR").performClick()
 }
 @Test fun selectedSectionLoopsAndCanBeDisabledDuringPlayback()=runBlocking {
    val context=InstrumentationRegistry.getInstrumentation().targetContext
    val engine=(context.applicationContext as TrackApplication).engine
    val vm=ViewModelProvider(compose.activity)[PlayerViewModel::class.java]
    compose.waitUntil(30000) { vm.library.value.busy==null && (vm.library.value.current==null || (engine.state.value.projectId==vm.library.value.selected && engine.state.value.ready)) }
    val original=AudioImporter(context).import(listOf(Uri.fromFile(ImportBenchmark().wav(8,1,16))),"Section test") {}
    val marker=Marker(name="Solo",start=original.sampleRate.toLong(),end=original.sampleRate*2L)
    val p=original.copy(markers=listOf(marker),selectedMarker=marker.id,loop=true,buses=listOf(Bus(id="b",name="Right",destination="MONO:1")),stems=original.stems.map { it.copy(bus="b") })
    fun await(test:()->Boolean) { val end=SystemClock.elapsedRealtime()+15000;while(!test()) { check(SystemClock.elapsedRealtime()<end);Thread.sleep(10) } }
    try {
      engine.load(p);await { engine.state.value.projectId==p.id && engine.state.value.ready };engine.play();await { engine.state.value.playing }
      val end=SystemClock.elapsedRealtime()+3500
      while(SystemClock.elapsedRealtime()<end) { assertTrue("Frame ${engine.state.value.frame}, project=${engine.state.value.projectId}, expected=${p.id}, error=${engine.state.value.error}",engine.state.value.frame in marker.start until marker.end);Thread.sleep(30) }
      engine.seek(p.frames-1);Thread.sleep(100);assertTrue(engine.state.value.frame in marker.start until marker.end)
      engine.update(p.copy(loop=false));Thread.sleep(1500);assertTrue(engine.state.value.frame>marker.end);assertNull(engine.state.value.error);assertEquals(0,engine.state.value.underruns)
    } finally { engine.stop();engine.unload();await { engine.state.value.projectId.isEmpty() } }
 }
}
