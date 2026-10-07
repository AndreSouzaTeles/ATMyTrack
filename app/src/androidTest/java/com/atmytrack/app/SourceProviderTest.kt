package com.atmytrack.app

import android.content.*
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.atmytrack.app.audio.*
import com.atmytrack.app.data.PcmEncoding
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

@RunWith(AndroidJUnit4::class)
class SourceProviderTest {
    @Test fun acceptsUnknownSizeNonSeekableProviderAndCreatesOfflineCopy() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val progress = mutableListOf<String>()
        val p = AudioImporter(context).import(listOf(Uri.parse("content://com.atmytrack.app.test.stream/audio")), "Provider", progress = progress::add)
        try {
            val stem = p.stems.single()
            assertEquals("Cloud", stem.name); assertEquals(48000L, stem.frames)
            assertEquals(PcmEncoding.S16, stem.format.encoding)
            assertTrue(progress.any { it.contains("MB") })
            PcmReader(File(context.filesDir,stem.pcm),stem.frames,format=stem.format).use { reader ->
                val block = FloatArray(1024); reader.read(12345,512,block)
                assertTrue(block.all { it == .25f })
            }
        } finally { File(context.filesDir,"audio/${p.id}").deleteRecursively() }
    }
}
