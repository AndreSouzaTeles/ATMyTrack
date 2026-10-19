package com.atmytrack.app

import com.atmytrack.app.separation.*
import com.atmytrack.app.audio.*
import java.io.File
import org.junit.Test
import org.junit.Assert.*
import kotlin.math.*

class SeparationTest {
    @Test fun demucsOverlapPreservesAllSamplesIncludingEdges() {
        val n=DemucsChunks.SAMPLES;val overlap=DemucsChunks.OVERLAP;val stride=n-overlap
        for(length in listOf(1,overlap,stride,n,n+17,n*2+31)) {
            val sum=FloatArray(length);val weights=FloatArray(length)
            var base=-overlap
            while(base<length) {
                for(i in 0 until n)if(base+i in 0 until length) {
                    val at=base+i;val sample=sin(at*.001).toFloat()+.25f
                    val w=DemucsChunks.weight(i);sum[at]+=sample*w;weights[at]+=w
                }
                base+=stride
            }
            for(i in 0 until length) { assertTrue(weights[i]>0f);assertEquals(sin(i*.001).toFloat()+.25f,sum[i]/weights[i],.000001f) }
        }
    }
    @Test fun sixStemNamesKeepLegacyResultsReadable() {
        val dir=java.nio.file.Files.createTempDirectory("six-stems").toFile()
        try {
            File(dir,"complete").writeText("1\n${ModelStore.VERSION}")
            SeparationOutputs.current.forEach { File(dir,"$it.wav").writeBytes(byteArrayOf()) }
            assertEquals(listOf("Bateria","Baixo","Outros","Voz","Guitarra","Piano"),SeparationOutputs.names(dir))
            File(dir,"complete").writeText("1\nspleeter-2stems-fp16-93ba771")
            File(dir,"Instrumental.wav").writeBytes(byteArrayOf())
            assertEquals(listOf("Voz","Instrumental"),SeparationOutputs.names(dir))
        } finally { dir.deleteRecursively() }
    }
    @Test fun integrityProgressCountsBytesAndDoesNotAcceptWrongHash() {
        val file=File.createTempFile("model-progress",".onnx")
        try {
            file.writeBytes(ByteArray(1048576+17) { (it*31).toByte() })
            val weight=ModelStore.Weight("test",file.length(),ModelStore.sha256(file))
            val progress=mutableListOf<Int>()
            assertTrue(ModelStore.verify(file,weight,progress={progress+=it}))
            assertEquals(0,progress.first());assertEquals(100,progress.last())
            assertTrue(progress.zipWithNext().all { (a,b)->b>a });assertTrue(progress.size>2)
            java.io.RandomAccessFile(file,"rw").use { it.seek(500000);val value=it.readUnsignedByte();it.seek(500000);it.writeByte(value xor 255) }
            progress.clear()
            assertFalse(ModelStore.verify(file,weight,progress={progress+=it}))
            assertEquals(100,progress.last()) // all bytes read is not successful installation
        } finally { file.delete() }
    }
    @Test fun fftRoundTripAndWindowOverlapHaveNoTimeShift() {
        val re=FloatArray(4096) { (sin(it*.13)+.2*cos(it*.34)).toFloat() };val expected=re.copyOf();val im=FloatArray(4096)
        SeparationDsp.fft(re,im);SeparationDsp.fft(re,im,true)
        assertTrue(re.indices.maxOf { abs(re[it]-expected[it]) }<.00015)
        for(i in 0 until 1024) {
            val sum=(0..3).sumOf { val w=SeparationDsp.window[i+it*1024];(w*w).toDouble() }
            assertEquals(1.5,sum,1e-6)
        }
    }
    @Test fun masksAreFiniteComplementaryAndRejectCorruptInference() {
        for(v in listOf(0f,1f,1000f,Float.MAX_VALUE))for(a in listOf(0f,1f,1000f,Float.MAX_VALUE)) {
            val mask=SeparationDsp.vocalMask(v,a);assertTrue(mask.isFinite());assertTrue(mask in 0f..1f)
            assertEquals(1f,mask+(1f-mask),0f)
        }
        assertThrows(IllegalArgumentException::class.java) { SeparationDsp.vocalMask(Float.NaN,1f) }
    }
    @Test fun floatWavPreservesExactFramesStereoHeadroomAndRejectsIncomplete() {
        val file=File.createTempFile("separated",".wav")
        try {
            FloatWav(file,3).use { it.write(floatArrayOf(1.2f,-1.2f,.3f,.4f,0f,0f),0,3);it.finish() }
            val header=SeekInput.file(file).use { WavHeader.read(it)!! }
            assertEquals(3L,header.frames);assertEquals(44100,header.rate)
            PcmReader(file,3,3,header.format).use { reader -> val data=FloatArray(6);reader.read(0,3,data);assertEquals(1.2f,data[0],0f);assertEquals(-1.2f,data[1],0f) }
            FloatWav(file,3).use { assertThrows(IllegalStateException::class.java) { it.finish() } }
        } finally { file.delete() }
    }
    @Test fun corruptedModelWithCorrectSizeIsRejected() {
        val file=File.createTempFile("weights",".onnx")
        try {
            file.writeText("valid")
            val weight=ModelStore.Weight("test",file.length(),ModelStore.sha256(file))
            assertTrue(ModelStore.valid(file,weight));file.writeText("wrong");assertFalse(ModelStore.valid(file,weight))
            file.writeText("truncated");assertFalse(ModelStore.valid(file,weight))
        } finally { file.delete() }
    }
    @Test fun lowStorageAndCancelledVerificationPreserveInstalledBytes() {
        val root=java.nio.file.Files.createTempDirectory("models").toFile()
        try {
            val existing=File(root,ModelStore.weights[0].name).apply { writeText("preserve existing bytes") }
            val original=existing.readBytes()
            val store=ModelStore(root) { 1024L }
            assertThrows(IllegalStateException::class.java) { store.install({}, { _,_-> }) }
            assertArrayEquals(original,existing.readBytes())
            assertFalse(File(root,ModelStore.weights[0].name+".part").exists())
            assertThrows(java.util.concurrent.CancellationException::class.java) { ModelStore.sha256(existing) { throw java.util.concurrent.CancellationException() } }
            assertArrayEquals(original,existing.readBytes())
        } finally { root.deleteRecursively() }
    }
}
