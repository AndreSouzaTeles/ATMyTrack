package com.atmytrack.app

import java.io.File
import java.nio.file.Files
import org.junit.Test
import org.junit.Assert.*

class RetiredStorageCleanupTest {
    @Test fun upgradeRemovesModelsAndPendingOutputsButPreservesImportedAudio() {
        val root=Files.createTempDirectory("retired-ai").toFile()
        try {
            fun put(path:String)=File(root,path).apply { parentFile!!.mkdirs();writeText(path) }
            val retained="separation/results/saved/Voz.wav"
            val audio=put(retained)
            put("separation/results/saved/complete")
            put("separation/results/pending/Voz.wav")
            put("separation/processing.lock")
            put("models/old/weights.onnx")
            put("models/old/weights.onnx.part")
            val library=put("library.json")
            val original=put("audio/project/source.wav")
            RetiredStorageCleanup.clean(root,setOf(retained))
            assertEquals(retained,audio.readText())
            assertEquals("library.json",library.readText())
            assertEquals("audio/project/source.wav",original.readText())
            assertFalse(File(root,"models").exists())
            assertFalse(File(root,"separation/results/pending").exists())
            assertFalse(File(root,"separation/processing.lock").exists())
            RetiredStorageCleanup.clean(root,setOf(retained))
            assertTrue(audio.isFile)
        } finally { root.deleteRecursively() }
    }
}
