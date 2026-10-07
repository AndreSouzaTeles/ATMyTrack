package com.atmytrack.app.audio

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import com.atmytrack.app.data.*

/** Disk-backed blocks. Ended stems supply silence while longer stems keep playing. */
class PcmReader(private val input: SeekInput, private val length: Long, blockFrames: Int = 512, private val format: PcmFormat = PcmFormat()) : FrameReader {
    constructor(file: File, length: Long, blockFrames: Int = 512, format: PcmFormat = PcmFormat()) : this(SeekInput.file(file),length,blockFrames,format)
    private val bytes = ByteArray(blockFrames * format.frameBytes)
    private val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
    override fun read(frame: Long, count: Int, result: FloatArray) {
        result.fill(0f)
        val available = minOf(count.toLong(), (length - frame).coerceAtLeast(0)).toInt()
        if (available == 0) return
        require(frame >= 0 && count * format.frameBytes <= bytes.size && result.size >= count * 2)
        input.seek(format.offset + frame * format.frameBytes)
        input.readFully(bytes, 0, available * format.frameBytes)
        buffer.position(0)
        for (i in 0 until available) {
            val left = sample()
            result[2*i] = left
            result[2*i+1] = if (format.channels == 1) left else sample()
        }
    }
    private fun sample(): Float = when (format.encoding) {
        PcmEncoding.U8 -> ((buffer.get().toInt() and 255) - 128) / 128f
        PcmEncoding.S16 -> buffer.short / 32768f
        PcmEncoding.S24 -> ((buffer.get().toInt() and 255) or ((buffer.get().toInt() and 255) shl 8) or (buffer.get().toInt() shl 16)) / 8388608f
        PcmEncoding.S32 -> buffer.int / 2147483648f
        PcmEncoding.FLOAT32 -> buffer.float.let { if (it.isFinite()) it else 0f }
    }
    override fun close() = input.close()
}
