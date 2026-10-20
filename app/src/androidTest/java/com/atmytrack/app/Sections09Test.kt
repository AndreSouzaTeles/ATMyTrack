package com.atmytrack.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.atmytrack.app.audio.Analysis
import com.atmytrack.app.data.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.*

class Sections09Test {
 @get:Rule val compose=createAndroidComposeRule<MainActivity>()
 @Test fun waveformIncludesAllTracksAndPreservesSilence()=runBlocking {
    val context=InstrumentationRegistry.getInstrumentation().targetContext
    val rate=48000;val frames=rate*4
    fun stem(name:String,sound:Boolean):Stem {
        val file=File(context.filesDir,"section09-$name.pcm")
        val bytes=ByteBuffer.allocate(frames*8).order(ByteOrder.LITTLE_ENDIAN)
        repeat(frames) { i ->
            val sample=if(sound && (i<rate || i>=rate*2))(.4*sin(2*PI*220*i/rate)).toFloat() else 0f
            bytes.putFloat(0f);bytes.putFloat(sample) // Right-only source catches mono-only analysis.
        }
        file.writeBytes(bytes.array())
        return Stem(name=name,pcm=file.name,frames=frames.toLong())
    }
    val project=Project(name="Waveform regression",sampleRate=rate,stems=listOf(stem("silent",false),stem("tone",true)))
    val analysis=Analysis(context)
    val result=analysis.waveform(project,{true},{})
    assertTrue(result.take(128).all { it>.39f })
    assertTrue(result.subList(128,256).all { it==0f })
    assertTrue(result.drop(256).all { it>.39f })
    assertEquals(result,analysis.waveform(project,{true},{}))
 }
 @Test fun savedCustomColorsSurviveReload() {
    val context=InstrumentationRegistry.getInstrumentation().targetContext
    val store=ProjectStore(File(context.cacheDir,"section-color-test"))
    val colors=listOf(0xFFFF0000,0xFF00FF00,0xFF0000FF,0xFFA137CF,0xFF000000,0xFFFFFFFF)
    val project=Project(name="Colors",sampleRate=48000,stems=emptyList(),markers=colors.map { Marker(name="Color",start=0,end=48000,color=it) })
    store.save(listOf(project))
    assertEquals(colors,store.load().single().markers.map { it.color })
 }
 @Test fun sectionPalettePersistsAndToolbarIsOrdered() {
    val vm=ViewModelProvider(compose.activity)[PlayerViewModel::class.java]
    compose.waitUntil(30000) { vm.library.value.busy==null && vm.playback.value.ready }
    compose.runOnUiThread { vm.stop();vm.dismissError();vm.dismissImportReport() }
    compose.openTool("+ SEÇÃO")
    compose.onNodeWithContentDescription("Cor Azul").performScrollTo().performClick()
    compose.onNodeWithContentDescription("Cor Azul").assertIsSelected()
    compose.onNodeWithContentDescription("Escolher cor personalizada").performClick()
    compose.onNodeWithContentDescription("Componente Azul").assertExists()
    compose.onNodeWithText("USAR COR").performClick()
    compose.onNodeWithText("Nome • INTRO, VERSO, REFRÃO…").performTextInput("Seção azul")
    compose.onNodeWithText("ADICIONAR").performClick()
    compose.waitUntil(5000) { vm.library.value.current!!.markers.any { it.name=="Seção azul" && it.color==0xFF89ACF2 } }
    compose.onNodeWithContentDescription("Ferramentas").performClick()
    compose.onNodeWithText("Toque ou arraste para navegar").assertDoesNotExist()
    assertTrue(compose.onNodeWithContentDescription("TOM").fetchSemanticsNode().boundsInRoot.left < compose.onNodeWithContentDescription("VELOCIDADE").fetchSemanticsNode().boundsInRoot.left)
 }
}
