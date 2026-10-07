package com.atmytrack.app.audio

import com.atmytrack.app.data.*
import java.io.File
import java.io.RandomAccessFile

data class WavInfo(val rate: Int, val frames: Long, val format: PcmFormat)

/** Parses RIFF chunks, including padding/metadata and WAVE_FORMAT_EXTENSIBLE.
 * Unsupported containers/encodings return null for the platform decoder fallback.
 * Invalid PCM files are rejected, never silently truncated. */
object WavHeader {
    fun read(file: File): WavInfo? = SeekInput.file(file).use { read(it) }
    fun read(input: SeekInput): WavInfo? {
        fun u16(): Int = java.lang.Short.reverseBytes(input.readShort()).toInt() and 65535
        fun u32(): Long = Integer.reverseBytes(input.readInt()).toLong() and 0xffffffffL
        fun tag(): String = ByteArray(4).also(input::readFully).toString(Charsets.US_ASCII)
        if (input.length() < 12 || tag() != "RIFF") return null
        val riffEnd = u32() + 8
        if (tag() != "WAVE") return null
        require(riffEnd in 12..input.length()) { "WAV incompleto: tamanho RIFF inválido." }
        var format: PcmFormat? = null
        var rate = 0
        var dataOffset = -1L
        var dataBytes = 0L
        while (input.filePointer + 8 <= riffEnd) {
            val chunk = tag(); val bytes = u32(); val start = input.filePointer
            require(bytes <= riffEnd - start) { "WAV incompleto: chunk $chunk truncado." }
            when (chunk) {
                "fmt " -> {
                    require(bytes >= 16) { "Cabeçalho WAV inválido." }
                    var kind = u16(); val channels = u16(); val sampleRate = u32()
                    u32() // Byte rate can contain inaccurate metadata; block alignment is authoritative.
                    val align = u16(); val bits = u16()
                    if (kind == 0xfffe) {
                        require(bytes >= 40 && u16() >= 22) { "WAV extensível incompleto." }
                        val valid = u16(); u32() // channel mask
                        val guid = ByteArray(16).also(input::readFully)
                        val tail = byteArrayOf(0, 0, 0, 0, 16, 0, -128, 0, 0, -86, 0, 56, -101, 113)
                        if (!guid.copyOfRange(2, 16).contentEquals(tail)) return null
                        kind = (guid[0].toInt() and 255) or ((guid[1].toInt() and 255) shl 8)
                        require(valid == 0 || valid in 1..bits) { "Precisão PCM inválida." }
                        // Valid integer bits are left-aligned in the container (Microsoft WAVEFORMATEXTENSIBLE).
                    }
                    if (kind != 1 && kind != 3) return null
                    require(channels in 1..2) { "Importe stems mono ou estéreo; áudio surround não é suportado." }
                    val encoding = when {
                        kind == 3 && bits == 32 -> PcmEncoding.FLOAT32
                        kind == 1 && bits == 8 -> PcmEncoding.U8
                        kind == 1 && bits == 16 -> PcmEncoding.S16
                        kind == 1 && bits == 24 -> PcmEncoding.S24
                        kind == 1 && bits == 32 -> PcmEncoding.S32
                        else -> return null
                    }
                    require(sampleRate in 8000..384000) { "Taxa de amostragem WAV inválida." }
                    require(align == channels * encoding.bytes) { "Alinhamento PCM inválido." }
                    require(format == null) { "WAV com múltiplos formatos não suportado." }
                    rate = sampleRate.toInt(); format = PcmFormat(channels, encoding)
                }
                "data" -> {
                    require(dataOffset == -1L) { "WAV com múltiplos blocos de áudio não suportado." }
                    dataOffset = start; dataBytes = bytes
                }
            }
            input.seek(start + bytes + (bytes and 1))
        }
        val pcm = format ?: error("WAV sem formato de áudio.")
        require(dataOffset >= 0 && dataBytes > 0 && dataBytes % pcm.frameBytes == 0L) { "WAV vazio ou com frames incompletos." }
        return WavInfo(rate, dataBytes / pcm.frameBytes, pcm.copy(offset = dataOffset))
    }
}
