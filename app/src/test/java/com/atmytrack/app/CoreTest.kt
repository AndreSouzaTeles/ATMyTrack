package com.atmytrack.app

import com.atmytrack.app.audio.*
import com.atmytrack.app.data.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

class CoreTest {
    @Test fun tapAveragesIntervalsAndResetsAfterPause() {
        val tap = TapTempo()
        assertNull(tap.tap(1000))
        assertEquals(120.0, tap.tap(1500)!!, .0001)
        assertEquals(120.0, tap.tap(2000)!!, .0001)
        assertNull(tap.tap(5000))
        assertEquals(60.0, tap.tap(6000)!!, .0001)
        assertNull(tap.tap(6010))
    }
    @Test fun beatMultipliersAndLongTimelineHaveNoCumulativeError() {
        assertEquals(48000.0, MusicTime.beatFrames(48000, 120.0, .5), 0.0)
        assertEquals(24000.0, MusicTime.beatFrames(48000, 120.0, 1.0), 0.0)
        assertEquals(12000.0, MusicTime.beatFrames(48000, 120.0, 2.0), 0.0)
        val offset = 48000L * 60 * 60 * 2
        for (i in 0L..500) assertEquals(MixMath.click(i, 48000, 120.0, 1.0, 4), MixMath.click(offset + i, 48000, 120.0, 1.0, 4), .00001f)
    }
    @Test fun markersRequireStrictPositiveRangeInsideSong() {
        assertTrue(MusicTime.validMarker(0, 48000, 96000))
        assertFalse(MusicTime.validMarker(-1, 100, 1000))
        assertFalse(MusicTime.validMarker(100, 100, 1000))
        assertFalse(MusicTime.validMarker(0, 1001, 1000))
    }
    @Test fun stereoBalanceAndMasterMuteWork() {
        val source = floatArrayOf(.5f, .7f)
        val output = FloatArray(2)
        MixMath.add(source, output, 1, .5f, -1f)
        assertArrayEquals(floatArrayOf(.25f, 0f), output, .0001f)
        val project = Project(name = "Test", sampleRate = 48000, stems = emptyList(), masterMute = true, click = true)
        MixMath.finish(output, 1, 100, project)
        assertArrayEquals(floatArrayOf(0f, 0f), output, 0f)
    }
    @Test fun masterClipsSafelyAndReportsOverload() {
        val output = floatArrayOf(2f, -2f)
        val levels = MixMath.finish(output, 1, 0, Project(name = "Test", sampleRate = 48000, stems = emptyList(), master = 1f))
        assertEquals(2f, levels.first, 0f)
        assertArrayEquals(floatArrayOf(1f, -1f), output, 0f)
    }
    @Test fun diskStemsShareExactSamplePositionAcrossSeeksAndDifferentLengths() {
        val a = File.createTempFile("stemA", ".pcm"); val b = File.createTempFile("stemB", ".pcm")
        fun write(file: File, count: Int) {
            val bytes = ByteBuffer.allocate(count * 8).order(ByteOrder.LITTLE_ENDIAN)
            repeat(count) { bytes.putFloat(it.toFloat()); bytes.putFloat(-it.toFloat()) }
            file.writeBytes(bytes.array())
        }
        write(a, 2000); write(b, 1200)
        try {
            PcmReader(a, 2000).use { ra -> PcmReader(b, 1200).use { rb ->
                val left = FloatArray(1024); val right = FloatArray(1024)
                listOf(0L, 512L, 1150L, 100L, 1999L, 2000L).forEach { pos ->
                    ra.read(pos, 512, left); rb.read(pos, 512, right)
                    repeat(512) { i ->
                        assertEquals(if (pos + i < 2000) (pos + i).toFloat() else 0f, left[2*i], 0f)
                        assertEquals(if (pos + i < 1200) (pos + i).toFloat() else 0f, right[2*i], 0f)
                    }
                }
            } }
        } finally { a.delete(); b.delete() }
    }
}
