package com.atmytrack.app

import android.net.Uri
import android.os.SystemClock
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.atmytrack.app.audio.*
import com.atmytrack.app.data.*
import com.atmytrack.app.separation.*
import java.io.File
import org.junit.*
import org.junit.Assert.*
import kotlin.math.*

/** Uses real, pinned models and upstream music fixtures in /data/local/tmp/atmytrack-separation. */
class SeparationIntegrationTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private val context get()=compose.activity.applicationContext
    private fun shell(command:String):String=android.os.ParcelFileDescriptor.AutoCloseInputStream(InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)).bufferedReader().use { it.readText() }
    private fun waitFor(timeout:Long=300000,condition:()->Boolean) {
        compose.waitUntil(timeoutMillis=timeout,condition=condition)
    }
    @Test fun realOfflineSeparationImportPlaybackCancelAndCorruption() {
        val vm=ViewModelProvider(compose.activity)[PlayerViewModel::class.java]
        compose.waitUntil(45000) { vm.library.value.busy==null }
        compose.onNodeWithContentDescription("Abrir menu principal").performClick()
        compose.onNodeWithText("Separar Instrumentos").performClick()
        waitFor(30000) { vm.separation.state.value.connected && !vm.separation.state.value.busy }
        // Exercise the exact installer used in production, including HTTPS, size and SHA-256.
        if(!vm.separation.state.value.installed) {
            compose.runOnUiThread { vm.separation.command(2) }
            waitFor { !vm.separation.state.value.busy || ModelStore.weights.any { File(context.filesDir,"models/${ModelStore.VERSION}/${it.name}.part").length()>1048576 } }
            if(vm.separation.state.value.busy) {
                compose.runOnUiThread { vm.separation.command(4) }
                waitFor(30000) { !vm.separation.state.value.busy }
                android.util.Log.i("SEPARATION150","download cancellation/resume partialBytes="+ModelStore.weights.sumOf { File(context.filesDir,"models/${ModelStore.VERSION}/${it.name}.part").length() })
            }
        }
        compose.runOnUiThread { vm.separation.command(2) }
        waitFor { !vm.separation.state.value.busy }
        assertEquals("",vm.separation.state.value.error)
        assertTrue(vm.separation.state.value.installed)
        val fixtures=File("/data/local/tmp/atmytrack-separation")
        val original=File(fixtures,"audio_example.wav")
        val hash=ModelStore.sha256(original)
        shell("cmd connectivity airplane-mode enable")
        try {
            assertEquals("1",shell("settings get global airplane_mode_on").trim())
            val examples=listOf("audio_example.wav","qi-feng-le-zh.wav")+if(InstrumentationRegistry.getArguments().getString("skipLong")=="true")emptyList() else listOf("long.wav")
            for((index,name) in examples.withIndex()) {
                val input=File(fixtures,name)
                val started=SystemClock.elapsedRealtime()
                compose.runOnUiThread { vm.startSeparation(Uri.fromFile(input),name) }
                if(index==0) {
                    waitFor(30000) { !vm.separation.state.value.busy || vm.separation.state.value.text.startsWith("Separando") }
                    compose.onNodeWithText("CANCELAR").assertIsDisplayed()
                    InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot().let { bitmap ->
                        File(context.getExternalFilesDir(null),"separation-0.15.0.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) };bitmap.recycle()
                    }
                }
                waitFor { !vm.separation.state.value.busy }
                val state=vm.separation.state.value
                assertEquals(state.toString(),"",state.error);assertTrue(state.result.isNotBlank())
                val dir=File(context.filesDir,"separation/results/${state.result}")
                val sourceHeader=SeekInput.file(input).use { WavHeader.read(it)!! }
                val frames=sourceHeader.frames
                val headers=SeparationOutputs.current.map { SeekInput.file(File(dir,"$it.wav")).use { source -> WavHeader.read(source)!! } }
                headers.forEach { assertEquals(frames,it.frames);assertEquals(44100,it.rate) }
                var maxError=0f;var peakV=0f;var peakA=0f;var energyDiff=0.0
                val separated=SeparationOutputs.current.mapIndexed { i,name -> PcmReader(File(dir,"$name.wav"),frames,4096,headers[i].format) }
                try {
                    PcmReader(input,frames,4096,sourceHeader.format).use { source ->
                        val x=FloatArray(8192);val rows=Array(6) { FloatArray(8192) };var at=0L
                        while(at<frames) {
                            val n=min(4096L,frames-at).toInt();source.read(at,n,x)
                            separated.forEachIndexed { i,r -> r.read(at,n,rows[i]) }
                            for(i in 0 until n*2) {
                                rows.forEach { assertTrue(it[i].isFinite()) }
                                val sum=rows.sumOf { it[i].toDouble() }.toFloat()
                                maxError=max(maxError,abs(x[i]-sum));peakV=max(peakV,abs(rows[3][i]));peakA=max(peakA,abs(sum-rows[3][i]));energyDiff+=(rows[3][i]-(sum-rows[3][i])).toDouble().pow(2)
                            }
                            at+=n
                        }
                    }
                } finally { separated.forEach { it.close() } }
                // Demucs estimates waveforms; unlike complementary masks, their sum is not forced to equal the mix.
                assertTrue(maxError.isFinite());assertTrue(peakV>.001f && peakA>.001f && energyDiff>.01)
                android.util.Log.i("SEPARATION150","$name frames=$frames duration=${frames/44100.0} elapsedMs=${SystemClock.elapsedRealtime()-started} maxSumError=$maxError peakV=$peakV peakA=$peakA ${File(dir,"metrics.txt").readText()}")
                if(index==0) {
                    compose.onNodeWithText("OUVIR Voz").performScrollTo().performClick()
                    waitFor(10000) { context.getSystemService(android.media.AudioManager::class.java).isMusicActive }
                    compose.onNodeWithText("PARAR Voz").performScrollTo().performClick()
                }
                val previous=vm.library.value.current
                val expectedCount=if(index==1)12 else 6
                compose.runOnUiThread { vm.importSeparation(index==1) }
                waitFor(60000) { vm.library.value.busy==null && vm.library.value.current?.stems?.size==expectedCount && vm.playback.value.ready && vm.playback.value.preparation.size==expectedCount && vm.playback.value.projectId==vm.library.value.selected }
                val project=vm.library.value.current!!
                assertEquals(project.stems[expectedCount-2].frames,project.stems[expectedCount-1].frames)
                if(index==1) {
                    assertEquals(previous!!.id,project.id);assertEquals(previous.stems,project.stems.take(6))
                    assertEquals(previous.dcas,project.dcas);assertEquals(previous.buses,project.buses)
                    assertEquals(previous.master,project.master,0f);assertEquals(previous.masterPan,project.masterPan,0f)
                }
                compose.runOnUiThread { vm.play() }
                waitFor(10000) { vm.playback.value.playing && vm.playback.value.frame>44100 }
                assertNull(vm.playback.value.error)
                compose.runOnUiThread { vm.stop() }
                if(index==0)compose.runOnUiThread { vm.update { p -> p.copy(master=.7f,masterPan=.2f,dcas=listOf(Dca(name="DCA preserved",members=listOf(p.stems[0].id))),buses=listOf(Bus(name="BUS preserved")),stems=p.stems.map { it.copy(volume=.43f,pan=.1f) }) } }
            }
            assertEquals(hash,ModelStore.sha256(original))
            // A long input is cancelled before publication, leaving the mixer usable.
            compose.runOnUiThread { vm.startSeparation(Uri.fromFile(File(fixtures,"long.wav")),"Long cancellation") }
            waitFor(60000) { vm.separation.state.value.busy && vm.separation.state.value.text.startsWith("Separando") }
            compose.runOnUiThread { vm.separation.command(4) }
            waitFor(60000) { !vm.separation.state.value.busy }
            assertTrue(vm.separation.state.value.result.isBlank())
            assertFalse(File(context.filesDir,"separation/processing.lock").exists())
            val savedIds=vm.library.value.projects.map { it.id }
            compose.runOnUiThread { vm.startSeparation(Uri.fromFile(File(fixtures,"long.wav")),"Interrupted process") }
            waitFor(60000) { vm.separation.state.value.busy && vm.separation.state.value.text.startsWith("Separando") }
            val pid=shell("pidof ${context.packageName}:separation").trim().toInt()
            shell("run-as ${context.packageName} kill -9 $pid")
            waitFor(15000) { !vm.separation.state.value.connected && !vm.separation.state.value.busy }
            assertTrue(vm.separation.state.value.error.isNotBlank())
            assertEquals(savedIds,vm.library.value.projects.map { it.id })
            assertFalse(SeparationLock.active(context))
            compose.onNodeWithText("FECHAR").performClick()
            compose.onNodeWithContentDescription("Abrir menu principal").performClick()
            compose.onNodeWithText("Separar Instrumentos").performClick()
            waitFor(30000) { vm.separation.state.value.connected && !vm.separation.state.value.busy }
            assertTrue(File(context.filesDir,"separation/results").listFiles()!!.all { File(it,"complete").exists() })
            val model=File(context.filesDir,"models/${ModelStore.VERSION}/${ModelStore.weights[0].name}")
            val copy=File(model.path+".test-backup");model.copyTo(copy,true)
            try {
                java.io.RandomAccessFile(model,"rw").use { it.seek(100);val originalByte=it.readUnsignedByte();it.seek(100);it.writeByte(originalByte xor 255) }
                compose.runOnUiThread { vm.separation.command(1) }
                waitFor(30000) { !vm.separation.state.value.busy }
                assertFalse(vm.separation.state.value.installed)
            } finally { copy.copyTo(model,true);copy.delete() }
            waitFor(10000) { ProjectStore(context.filesDir).load().any { it.id==vm.library.value.selected } }
        } finally { shell("cmd connectivity airplane-mode disable") }
    }
}
