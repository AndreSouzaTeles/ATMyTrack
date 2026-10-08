package com.atmytrack.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.SystemClock
import android.provider.DocumentsContract
import androidx.test.platform.app.InstrumentationRegistry
import com.atmytrack.app.audio.*
import com.atmytrack.app.data.*
import kotlinx.coroutines.*
import org.junit.Test
import org.junit.Assert.*
import java.io.File

class Import11Test {
 @Test fun folderMetadataAndPreparedWaveformWithoutWaitingForStop():Unit=runBlocking {
    val instrumentation=InstrumentationRegistry.getInstrumentation()
    val context=instrumentation.targetContext
    fun fixture(name:String)=DocumentsContract.buildDocumentUri("com.atmytrack.app.test.folder",name)
    val wav=ImportBenchmark().wav(120,2,24)
    context.contentResolver.openOutputStream(fixture("tone.wav"))!!.use { out -> wav.inputStream().use { it.copyTo(out) } }
    val bitmap=Bitmap.createBitmap(32,32,Bitmap.Config.ARGB_8888).apply { eraseColor(0xff3478ff.toInt()) }
    for((name,format) in listOf("cover.png" to Bitmap.CompressFormat.PNG,"cover.webp" to Bitmap.CompressFormat.WEBP))context.contentResolver.openOutputStream(fixture(name))!!.use { bitmap.compress(format,90,it) }
    bitmap.recycle()
    val tree=DocumentsContract.buildTreeDocumentUri("com.atmytrack.app.test.folder","opaque-root")
    val metadata=ImportMetadata(context);val files=AudioImporter(context).folder(tree)
    assertEquals(1,files.size)
    assertEquals("Clamo Jesus - Baruk",metadata.folderName(files,tree))
    assertEquals("Band",metadata.folderName(listOf(Uri.parse("file:///Music/Band/Drums.wav"),Uri.parse("file:///Music/Band/Bass.wav")),null))
    assertEquals(2,metadata.images(tree).size)
    val artwork=metadata.randomArtwork(tree,"test11")
    assertTrue(artwork.isNotBlank())
    assertNotNull(BitmapFactory.decodeFile(File(context.filesDir,artwork).path))
    val s=Stem(name="Tone",pcm="",source=files.single().toString(),external=true,frames=120L*48000,
        fingerprint=java.util.UUID.randomUUID().toString(),format=PcmFormat(2,PcmEncoding.S24,44))
    val cache=PlaybackCache(context);val before=SystemClock.elapsedRealtime()
    cache.open(s,48000).close()
    val preparation=SystemClock.elapsedRealtime()-before
    assertTrue(cache.envelope(s,48000)!!.all { it>.39f })
    val project=Project(name="Waveform11",sampleRate=48000,stems=listOf(s))
    val waveStart=SystemClock.elapsedRealtime()
    val result=withTimeout(5000) { Analysis(context).waveform(project,{false},{}) }
    assertTrue(result.all { it>.39f })
    android.util.Log.i("Import11","120s stereo PCM24 48kHz bytes=${wav.length()} prepareMs=$preparation waveformMs=${SystemClock.elapsedRealtime()-waveStart}")
 }
}
