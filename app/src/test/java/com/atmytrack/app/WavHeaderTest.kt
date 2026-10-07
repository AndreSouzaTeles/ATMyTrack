package com.atmytrack.app

import com.atmytrack.app.audio.*
import com.atmytrack.app.data.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

class WavHeaderTest {
    private fun wav(bits: Int, channels: Int, payload: ByteArray, floating: Boolean = false, extensible: Boolean = false, junk: Boolean = false): File {
        val fmt = if (extensible) 40 else 16
        val size = 12 + 8 + fmt + (if (junk) 12 else 0) + 8 + payload.size + (payload.size and 1)
        val b = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN)
        b.put("RIFF".toByteArray()); b.putInt(size - 8); b.put("WAVEfmt ".toByteArray()); b.putInt(fmt)
        b.putShort((if (extensible) 65534 else if (floating) 3 else 1).toShort())
        b.putShort(channels.toShort()); b.putInt(48000); b.putInt(48000 * channels * bits / 8)
        b.putShort((channels * bits / 8).toShort()); b.putShort(bits.toShort())
        if (extensible) { b.putShort(22); b.putShort(bits.toShort()); b.putInt(if (channels == 2) 3 else 4); b.putInt(if (floating) 3 else 1); b.putShort(0); b.putShort(16); b.put(byteArrayOf(-128,0,0,-86,0,56,-101,113)) }
        if (junk) { b.put("JUNK".toByteArray()); b.putInt(3); b.put(byteArrayOf(7,8,9,0)) }
        b.put("data".toByteArray()); b.putInt(payload.size); b.put(payload)
        return File.createTempFile("wav-test", ".wav").apply { writeBytes(b.array()) }
    }
    @Test fun mono16BitWithOddMetadataReadsAndSeeksExactly() {
        val bytes = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).apply { putShort(-32768); putShort(-16384); putShort(0); putShort(16384) }.array()
        val file = wav(16, 1, bytes, junk = true)
        try {
            val info = WavHeader.read(file)!!
            assertEquals(4L, info.frames); assertEquals(56L, info.format.offset)
            PcmReader(file, info.frames, format = info.format).use { reader ->
                val result = FloatArray(8); reader.read(1, 4, result)
                assertArrayEquals(floatArrayOf(-.5f,-.5f,0f,0f,.5f,.5f,0f,0f), result, 0f)
            }
        } finally { file.delete() }
    }
    @Test fun extensibleStereo24PreservesSignAndChannels() {
        val file = wav(24, 2, byteArrayOf(0,0,-128,-1,-1,127), extensible = true)
        try {
            val info = WavHeader.read(file)!!
            assertEquals(PcmEncoding.S24, info.format.encoding)
            PcmReader(file, info.frames, format = info.format).use { reader ->
                val result = FloatArray(2); reader.read(0,1,result)
                assertEquals(-1f, result[0], 0f); assertEquals(8388607f / 8388608f, result[1], 0f)
            }
        } finally { file.delete() }
    }
    @Test fun floatWavPreservesSamplesAndSanitizesNonFinite() {
        val bytes = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN).apply { putFloat(.25f); putFloat(-.75f); putFloat(Float.NaN); putFloat(Float.POSITIVE_INFINITY) }.array()
        val file = wav(32, 2, bytes, floating = true, extensible = true)
        try {
            val info = WavHeader.read(file)!!
            PcmReader(file, info.frames, format = info.format).use { reader ->
                val result = FloatArray(4); reader.read(0,2,result)
                assertArrayEquals(floatArrayOf(.25f,-.75f,0f,0f), result, 0f)
            }
        } finally { file.delete() }
    }
    @Test fun unsigned8AndSigned32MatchFullScale() {
        for ((bits, bytes) in listOf(8 to byteArrayOf(0,-128), 32 to ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).apply { putInt(Int.MIN_VALUE); putInt(0) }.array())) {
            val file = wav(bits, 1, bytes)
            try {
                val info = WavHeader.read(file)!!
                PcmReader(file, info.frames, format = info.format).use { reader ->
                    val result = FloatArray(4); reader.read(0,2,result)
                    assertArrayEquals(floatArrayOf(-1f,-1f,0f,0f),result,0f)
                }
            } finally { file.delete() }
        }
    }
    @Test fun truncatedWavAndIncompleteFramesAreRejected() {
        val file = wav(16, 2, byteArrayOf(0,0,0))
        try {
            assertThrows(IllegalArgumentException::class.java) { WavHeader.read(file) }
            file.writeBytes(file.readBytes().dropLast(8).toByteArray())
            assertThrows(IllegalArgumentException::class.java) { WavHeader.read(file) }
        } finally { file.delete() }
    }
    @Test fun otherContainersFallBackToPlatformDecoder() {
        val file = File.createTempFile("other", ".mp3")
        try { file.writeText("ID3abcdef123456789"); assertNull(WavHeader.read(file)) } finally { file.delete() }
    }
}
