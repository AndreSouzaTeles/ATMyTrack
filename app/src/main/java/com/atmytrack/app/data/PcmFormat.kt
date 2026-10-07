package com.atmytrack.app.data

enum class PcmEncoding(val bytes: Int) { U8(1), S16(2), S24(3), S32(4), FLOAT32(4) }

/** Defaults describe the stereo float cache created by version 0.1.0. */
data class PcmFormat(val channels: Int = 2, val encoding: PcmEncoding = PcmEncoding.FLOAT32, val offset: Long = 0) {
    init { require(channels in 1..2); require(offset >= 0) }
    val frameBytes: Int get() = channels * encoding.bytes
}
