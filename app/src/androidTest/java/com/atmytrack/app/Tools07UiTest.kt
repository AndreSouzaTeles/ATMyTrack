package com.atmytrack.app

import android.Manifest
import android.content.pm.PackageManager
import android.content.pm.ActivityInfo
import android.os.SystemClock
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.*
import androidx.test.platform.app.InstrumentationRegistry
import com.atmytrack.app.tuner.*
import org.junit.*
import org.junit.Assert.*
import java.io.File
import kotlinx.coroutines.*

class Tools07UiTest {
 @get:Rule val compose=createAndroidComposeRule<MainActivity>()
 private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
 private fun screenshot(name:String) { compose.waitForIdle();Thread.sleep(300);InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot().let { bitmap->File(context.getExternalFilesDir(null),name).outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) };bitmap.recycle() } }
 private fun shell(command:String) { android.os.ParcelFileDescriptor.AutoCloseInputStream(InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)).use { it.readBytes() } }
 private fun systemButton(id:String) {
    val automation=InstrumentationRegistry.getInstrumentation().uiAutomation
    val deadline=SystemClock.elapsedRealtime()+5000
    while(SystemClock.elapsedRealtime()<deadline) {
        val node=(listOf(id)+if(id=="permission_deny_button")listOf("permission_deny_and_dont_ask_again_button") else emptyList()).firstNotNullOfOrNull { key -> automation.rootInActiveWindow?.findAccessibilityNodeInfosByViewId("com.android.permissioncontroller:id/$key")?.firstOrNull() }
        if(node!=null) { node.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK);return }
        Thread.sleep(50)
    }
    error("Permission button missing: $id")
 }
 @Test fun microphoneDenialAndPermanentDenial() {
    val vm=ViewModelProvider(compose.activity)[PlayerViewModel::class.java]
    compose.waitUntil(30000){vm.library.value.busy==null}
    context.getSharedPreferences("tuner",0).edit().putBoolean("asked",false).apply()
    menu();compose.onNodeWithText("AFINADOR").performClick()
    compose.onNodeWithText("PERMITIR MICROFONE").performClick();systemButton("permission_deny_button")
    compose.onNodeWithText("O microfone não foi autorizado. Permita o acesso para usar o afinador.").assertIsDisplayed()
    assertEquals(0,TunerCapture.activeInstances.get())
    compose.onNodeWithText("PERMITIR MICROFONE").performClick();systemButton("permission_deny_button")
    compose.waitUntil(5000) { compose.onAllNodesWithText("ABRIR CONFIGURAÇÕES").fetchSemanticsNodes().isNotEmpty() }
    compose.onNodeWithText("ABRIR CONFIGURAÇÕES").performScrollTo().assertIsDisplayed();screenshot("tuner-denied-0.7.png")
    compose.onNodeWithText("FECHAR").performClick()
 }
 private fun menu()=compose.onNodeWithContentDescription("Abrir menu principal").performClick()
 @Test fun menuTunerPermissionLifecycleAndPreferences() {
    val vm=ViewModelProvider(compose.activity)[PlayerViewModel::class.java]
    compose.waitUntil(30000) { vm.library.value.busy==null }
    context.getSharedPreferences("tuner",0).edit().putBoolean("asked",false).putString("instrument","acoustic").apply()
    menu();compose.onNodeWithText("PROJETOS").assertIsDisplayed();compose.onNodeWithText("PLANO / PAGAMENTO").assertIsDisplayed();compose.onNodeWithText("CENTRAL DE DÚVIDAS").assertIsDisplayed();screenshot("menu-0.7.png")
    compose.onNodeWithText("AFINADOR").performClick()
    if(context.checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED) {
        compose.onNodeWithText("O ATMyTrack precisa acessar o microfone para identificar a nota tocada pelo seu instrumento.").assertIsDisplayed()
        screenshot("tuner-permission-0.7.png")
        compose.onNodeWithText("PERMITIR MICROFONE").performClick();systemButton("permission_allow_foreground_only_button")
    }
    compose.waitUntil(10000) { TunerCapture.activeInstances.get()==1 }
    compose.onNodeWithText("Instrumento: Violão").performClick();compose.onNodeWithText("Baixo 5 cordas").performClick()
    compose.onNodeWithText("Corda: AUTO").performClick();compose.onNodeWithText("5ª — B0").performClick()
    compose.runOnUiThread { compose.activity.requestedOrientation=ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
    screenshot("tuner-landscape-0.7.png")
    compose.runOnUiThread { compose.activity.requestedOrientation=ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
    Thread.sleep(800);screenshot("tuner-portrait-0.7.png")
    shell("input keyevent KEYCODE_HOME")
    val until=SystemClock.elapsedRealtime()+5000;while(TunerCapture.activeInstances.get()!=0 && SystemClock.elapsedRealtime()<until)Thread.sleep(20)
    assertEquals(0,TunerCapture.activeInstances.get())
    shell("am start -n ${context.packageName}/.MainActivity --activity-reorder-to-front");compose.waitUntil(10000) { TunerCapture.activeInstances.get()==1 }
    shell("input keyevent KEYCODE_SLEEP")
    val sleepDeadline=SystemClock.elapsedRealtime()+5000
    while(TunerCapture.activeInstances.get()!=0 && SystemClock.elapsedRealtime()<sleepDeadline)Thread.sleep(20)
    assertEquals(0,TunerCapture.activeInstances.get())
    shell("input keyevent KEYCODE_WAKEUP");shell("wm dismiss-keyguard")
    compose.waitUntil(10000) { TunerCapture.activeInstances.get()==1 }
    compose.onNodeWithText("FECHAR").performClick();compose.waitUntil(5000) { TunerCapture.activeInstances.get()==0 }
    repeat(3) { menu();compose.onNodeWithText("AFINADOR").performClick();compose.waitUntil(5000){TunerCapture.activeInstances.get()==1};compose.onNodeWithText("FECHAR").performClick();compose.waitUntil(5000){TunerCapture.activeInstances.get()==0} }
    menu();compose.onNodeWithText("AFINADOR").performClick()
    compose.onNodeWithText("Instrumento: Baixo 5 cordas").assertExists()
    compose.onNodeWithText("Instrumento: Baixo 5 cordas").performClick();compose.onNodeWithText("Cromático").performClick()
    compose.onNodeWithText("Instrumento: Cromático").assertExists();compose.onNodeWithText("FECHAR").performClick()
    menu();compose.onNodeWithText("CENTRAL DE DÚVIDAS").performClick();compose.onNodeWithText("FECHAR").performClick()
    menu();compose.onNodeWithText("PLANO / PAGAMENTO").performClick();compose.onNodeWithContentDescription("QR Code Pix ATMyTrack").performScrollTo().assertIsDisplayed();compose.onNodeWithText("FECHAR").performClick()
    menu();compose.onNodeWithText("PROJETOS").performClick();compose.onNodeWithContentDescription("Pesquisar projetos").assertExists()
 }
 @Test fun speedSelectionWarningAndLiveMenu() {
    val vm=ViewModelProvider(compose.activity)[PlayerViewModel::class.java]
    compose.waitUntil(30000) { vm.library.value.busy==null }
    val source=ImportBenchmark().wav(12,1,16)
    val old=vm.library.value.selected
    compose.runOnUiThread { vm.import(uris=List(3){android.net.Uri.fromFile(source)}) }
    compose.waitUntil(30000) { vm.library.value.selected!=old && vm.playback.value.ready && vm.playback.value.projectId==vm.library.value.selected }
    compose.runOnUiThread { vm.dismissImportReport();vm.dismissError();vm.update { it.copy(loop=true) };vm.play() }
    compose.waitUntil(5000){vm.playback.value.playing}
    val before=vm.playback.value.frame;menu();compose.onNodeWithText("PROJETOS").performClick()
    assertTrue(vm.playback.value.playing);assertTrue(vm.playback.value.frame>=before)
    compose.onNodeWithContentDescription("VELOCIDADE").performScrollTo().performClick()
    assertEquals(3,compose.onAllNodes(isToggleable()).fetchSemanticsNodes().size)
    compose.onAllNodes(isToggleable()).assertAll(isOn())
    compose.onNodeWithContentDescription("Percentual da velocidade").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress) { it(80f) };compose.onAllNodes(isToggleable())[0].performScrollTo().performClick();compose.onNodeWithText("APLICAR").performClick()
    compose.onNodeWithText("Tracks com velocidades diferentes podem perder sincronização entre si.").assertIsDisplayed()
    compose.onNodeWithText("CANCELAR").performClick();compose.onNodeWithText("SELECIONAR TODAS").performScrollTo().performClick();screenshot("speed-0.7.png")
    compose.onNodeWithText("APLICAR").performClick()
    compose.waitUntil(90000) { vm.library.value.current!!.speed==80 && vm.library.value.background==null }
    assertTrue(vm.playback.value.playing);assertTrue(vm.library.value.current!!.stems.all { it.dspSpeed==80 });assertEquals(0,vm.playback.value.underruns)
    compose.runOnUiThread { vm.pause() };compose.waitUntil(5000){!vm.playback.value.playing}
    (context.applicationContext as TrackApplication).engine.unload()
    compose.waitUntil(5000){vm.playback.value.projectId.isBlank()}
    compose.runOnUiThread { vm.applySpeed(100,vm.library.value.current!!.selectedSpeedTracks) }
    compose.waitUntil(30000){vm.library.value.current!!.speed==100 && vm.library.value.background==null && vm.playback.value.ready && vm.playback.value.projectId==vm.library.value.selected}
 }
 @Test fun tunerDetectorCpuAndPrecisionOnAndroid() {
    val detector=YinDetector();val log=StringBuilder()
    val start=SystemClock.elapsedRealtime();val cpu=android.os.Process.getElapsedCpuTime()
    for(midi in listOf(23,28,40,45,50,55,59,64,69))for(cents in listOf(-18.0,0.0,14.0)) {
        val f=TuningMath.frequency(midi)*Math.pow(2.0,cents/1200)
        val input=FloatArray(4096) { (.3*kotlin.math.sin(2*Math.PI*f*it/24000)).toFloat() }
        val value=detector.detect(input);assertEquals(midi,TuningMath.nearest(value.frequency));assertEquals(cents,TuningMath.cents(value.frequency,midi),1.0)
        log.append("midi=$midi expected=$f actual=${value.frequency} cents=${TuningMath.cents(value.frequency,midi)} confidence=${value.confidence}\n")
    }
    log.append("27 windows wall=${SystemClock.elapsedRealtime()-start}ms cpu=${android.os.Process.getElapsedCpuTime()-cpu}ms\n")
    File(context.getExternalFilesDir(null),"tuner-accuracy-0.7.txt").writeText(log.toString())
 }
}
