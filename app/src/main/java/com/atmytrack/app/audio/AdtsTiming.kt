package com.atmytrack.app.audio

import kotlin.math.roundToLong

/** Android's raw AAC extractor timestamps packets using ceil(1024e6 / sampleRate).
 * Convert its packet ordinal back to sample time so rounding cannot accumulate.
 * M4A/MP4 timestamps must not pass through this correction. */
data class AdtsTiming(val sampleRate:Int) {
    private val packetUs=(1024_000_000L+sampleRate-1)/sampleRate
    fun timestamp(timeUs:Long):Long=(timeUs.toDouble()/packetUs).roundToLong()*1024_000_000L/sampleRate
    fun frames(durationUs:Long,rate:Int):Long=(durationUs.toDouble()/packetUs).roundToLong()*1024L*rate/sampleRate
    companion object {
        fun read(input:SeekInput):AdtsTiming? {
            if(input.length()<10)return null
            input.seek(0);val header=ByteArray(10);input.readFully(header)
            if(header[0]==73.toByte() && header[1]==68.toByte() && header[2]==51.toByte()) {
                val length=(6..9).fold(0) { n,i -> (n shl 7) or (header[i].toInt() and 127) }
                if(input.length()<length+17)return null
                input.seek(length+10L);input.readFully(header,0,7)
            }
            if(header[0].toInt() and 255 !=255 || header[1].toInt() and 246 !=240)return null
            val rate=intArrayOf(96000,88200,64000,48000,44100,32000,24000,22050,16000,12000,11025,8000,7350).getOrNull((header[2].toInt() shr 2) and 15) ?: return null
            return AdtsTiming(rate)
        }
    }
}
