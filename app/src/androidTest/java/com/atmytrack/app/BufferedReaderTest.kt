package com.atmytrack.app

import com.atmytrack.app.audio.*
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.atomic.AtomicBoolean

class BufferedReaderTest {
    @Test fun concurrentPrefetchRandomSeekBoundaryAndLoopUseOneDecoderOwner() {
        val busy=AtomicBoolean(false);var closed=false
        val source=object:FrameReader {
            override fun read(frame:Long,count:Int,result:FloatArray) {
                check(busy.compareAndSet(false,true)) { "Concurrent decoder access" }
                try {
                    Thread.sleep(2)
                    result.fill(0f)
                    repeat(minOf(count.toLong(),(50000-frame).coerceAtLeast(0)).toInt()) {
                        result[it*2]=((frame+it)%997)/1000f;result[it*2+1]=-result[it*2]
                    }
                } finally { busy.set(false) }
            }
            override fun close() { check(!busy.get());check(!closed);closed=true }
        }
        BufferedFrameReader(source,50000).use { reader ->
            val result=FloatArray(2048)
            for(frame in listOf(0L,16000,16384,32000,49000,50000,0,25000,10,49990)) {
                reader.read(frame,1024,result)
                repeat(1024) {
                    val expected=if(frame+it<50000)((frame+it)%997)/1000f else 0f
                    assertEquals(expected,result[it*2],0f);assertEquals(-expected,result[it*2+1],0f)
                }
            }
        }
        assertTrue(closed)
    }
    @Test fun failedWorkerStillReleasesSource() {
        var closed=false
        val reader=BufferedFrameReader(object:FrameReader {
            override fun read(frame:Long,count:Int,result:FloatArray) { error("Source disappeared") }
            override fun close() { closed=true }
        },48000)
        var failed=false
        try { reader.read(0,512,FloatArray(1024)) } catch(_:Exception) { failed=true } finally { reader.close() }
        assertTrue(failed);assertTrue(closed)
    }
}
