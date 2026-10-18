package com.atmytrack.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.atmytrack.app.separation.*
import kotlinx.coroutines.*
import java.io.File
import java.util.concurrent.ConcurrentLinkedQueue
import org.junit.*
import org.junit.Assert.*

class ModelInstallProgressUiTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    @Test fun realDownloadShowsNumericPercentageAndFinishesAfterVerification() {
        val context=compose.activity.applicationContext
        val vm=ViewModelProvider(compose.activity)[PlayerViewModel::class.java]
        compose.waitUntil(45000) { vm.library.value.busy==null }
        val root=File(context.filesDir,"models/${ModelStore.VERSION}")
        val backup=File(context.filesDir,"models/progress-test-backup")
        check(!backup.exists())
        val hadModel=root.exists()
        if(hadModel)check(root.renameTo(backup))
        val seen=ConcurrentLinkedQueue<SeparationState>()
        val observer=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
        observer.launch { vm.separation.state.collect { seen.add(it) } }
        try {
            compose.onNodeWithContentDescription("Abrir menu principal").performClick()
            compose.onNodeWithText("Separar Instrumentos").performClick()
            compose.waitUntil(30000) { vm.separation.state.value.connected && !vm.separation.state.value.busy }
            assertFalse(vm.separation.state.value.installed)
            compose.onNodeWithText("BAIXAR MODELO GRATUITO").performScrollTo().performClick()
            compose.onNodeWithText("CONFIRMAR DOWNLOAD").performClick()
            compose.waitUntil(90000) { vm.separation.state.value.busy && vm.separation.state.value.text=="Baixando modelo" && vm.separation.state.value.percent in 1..99 }
            compose.onNode(hasText("% concluído nesta etapa",substring=true)).assertIsDisplayed()
            compose.onNodeWithText("CANCELAR").assertIsDisplayed()
            InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot().let { bitmap ->
                File(context.getExternalFilesDir(null),"model-progress-0.15.1.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) };bitmap.recycle()
            }
            compose.waitUntil(180000) { !vm.separation.state.value.busy }
            val done=vm.separation.state.value
            assertEquals("",done.error);assertTrue(done.installed);assertEquals(100,done.percent)
            assertEquals("Modelo instalado • 100% • disponível offline",done.text)
            compose.onNodeWithText(done.text).performScrollTo().assertIsDisplayed()
            assertTrue(ModelStore(root).installed())
            assertEquals(ModelStore.VERSION,File(root,"version.txt").readText())
            assertTrue(seen.any { it.text=="Baixando modelo" && it.percent in 1..99 })
            android.util.Log.i("INSTALL151","Numeric progress displayed; final 100% with both hashes verified; events=${seen.size}")
        } finally {
            if(vm.separation.state.value.busy) {
                compose.runOnUiThread { vm.separation.command(4) }
                compose.waitUntil(30000) { !vm.separation.state.value.busy }
            }
            compose.runOnUiThread { vm.separation.close() }
            observer.cancel()
            if(hadModel) {
                check(root.canonicalFile.parentFile==File(context.filesDir,"models").canonicalFile)
                root.deleteRecursively();check(backup.renameTo(root))
            }
        }
    }
}
