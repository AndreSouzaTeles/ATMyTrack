package com.atmytrack.app

import android.net.Uri
import android.os.SystemClock
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.atmytrack.app.audio.*
import com.atmytrack.app.separation.*
import java.io.File
import kotlin.math.*
import org.junit.*
import org.junit.Assert.*

class DemucsSixStemTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    @Test fun realModelSeparatesSixAlignedSourcesAndImportsIntoMixer() {
        val context=compose.activity.applicationContext
        val vm=ViewModelProvider(compose.activity)[PlayerViewModel::class.java]
        compose.waitUntil(45000) { vm.library.value.busy==null }
        val root=File(context.filesDir,"models/${ModelStore.VERSION}").apply { mkdirs() }
        val weight=ModelStore.weights.single()
        File("/data/local/tmp/atmytrack-separation/${weight.name}").copyTo(File(root,weight.name),true)
        assertTrue(ModelStore.valid(File(root,weight.name),weight))
        File(root,"version.txt").writeText(ModelStore.VERSION)
        compose.onNodeWithContentDescription("Abrir menu principal").performClick()
        compose.onNodeWithText("Separar Instrumentos").performScrollTo().performClick()
        compose.waitUntil(45000) { vm.separation.state.value.connected && !vm.separation.state.value.busy }
        assertTrue(vm.separation.state.value.installed)
        val source=File("/data/local/tmp/atmytrack-separation/audio_example.wav")
        val hash=ModelStore.sha256(source)
        val started=SystemClock.elapsedRealtime()
        compose.runOnUiThread { vm.startSeparation(Uri.fromFile(source),"Demucs 6 faixas") }
        compose.waitUntil(600000) { !vm.separation.state.value.busy }
        val state=vm.separation.state.value
        assertEquals(state.toString(),"",state.error)
        assertTrue(state.result.isNotBlank())
        val dir=File(context.filesDir,"separation/results/${state.result}")
        val names=SeparationOutputs.names(dir)
        assertEquals(6,names.size)
        val original=SeekInput.file(source).use { WavHeader.read(it)!! }
        val energy=mutableListOf<Double>()
        for(name in names) {
            val file=File(dir,"$name.wav")
            val h=SeekInput.file(file).use { WavHeader.read(it)!! }
            assertEquals(original.frames,h.frames);assertEquals(44100,h.rate)
            var e=0.0
            PcmReader(file,h.frames,4096,h.format).use { reader ->
                val x=FloatArray(8192);var at=0L
                while(at<h.frames) { val count=min(4096L,h.frames-at).toInt();reader.read(at,count,x);for(i in 0 until count*2) { assertTrue(x[i].isFinite());e+=x[i].toDouble()*x[i] };at+=count }
            }
            energy+=sqrt(e/(h.frames*2))
            file.copyTo(File(context.getExternalFilesDir(null),"demucs-$name.wav"),true)
        }
        assertTrue(energy.count { it>.00001 }>=4)
        assertEquals(6,names.map { ModelStore.sha256(File(dir,"$it.wav")) }.distinct().size)
        assertEquals(hash,ModelStore.sha256(source))
        android.util.Log.i("DEMUCS160","frames=${original.frames} elapsedMs=${SystemClock.elapsedRealtime()-started} rms=$energy ${File(dir,"metrics.txt").readText()}")
        compose.onNodeWithText("OUVIR Voz").performScrollTo().performClick()
        compose.waitUntil(10000) { context.getSystemService(android.media.AudioManager::class.java).isMusicActive }
        compose.onNodeWithText("PARAR Voz").performScrollTo().performClick()
        compose.runOnUiThread { vm.importSeparation(false) }
        compose.waitUntil(60000) { vm.library.value.busy==null && vm.library.value.current?.stems?.size==6 && vm.playback.value.ready }
        assertEquals(names,vm.library.value.current!!.stems.map { it.name })
        compose.onNodeWithText("FECHAR").performClick()
        compose.runOnUiThread { vm.play() }
        compose.waitUntil(10000) { vm.playback.value.playing && vm.playback.value.frame>44100 }
        assertNull(vm.playback.value.error)
        compose.runOnUiThread { vm.stop() }
        InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot().let { b -> File(context.getExternalFilesDir(null),"demucs-0.16.0.png").outputStream().use { b.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) };b.recycle() }
    }
}
