package com.atmytrack.app

import com.atmytrack.app.audio.AdtsTiming
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.roundToLong

class AdtsTimingTest {
    @Test fun rawAacRoundingDoesNotAccumulateAcrossLongTracks() {
        for(rate in listOf(44100,48000,96000)) {
            val timing=AdtsTiming(rate)
            val packetUs=(1024_000_000L+rate-1)/rate
            for(packet in listOf(0L,1L,8440L,100000L)) {
                assertEquals(packet*1024,(timing.timestamp(packet*packetUs)*rate/1_000_000.0).roundToLong())
                assertEquals(packet*1024,timing.frames(packet*packetUs,rate))
            }
        }
    }
}
