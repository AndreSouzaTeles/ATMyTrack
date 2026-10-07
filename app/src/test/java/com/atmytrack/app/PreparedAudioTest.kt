package com.atmytrack.app

import com.atmytrack.app.audio.*
import org.junit.Test
import org.junit.Assert.*
import kotlin.math.*

class PreparedAudioTest {
    @Test fun resamplingPreservesTimeFrequencyAndSeeks() {
        for(sourceRate in listOf(44100,96000)) {
            val source=object:FrameReader {
                override fun read(frame:Long,count:Int,result:FloatArray) { repeat(count) { val x=sin(2*PI*1000*(frame+it)/sourceRate).toFloat();result[it*2]=x;result[it*2+1]=x*.5f } }
                override fun close() { }
            }
            ResampledReader(source,sourceRate,48000,48000L*600).use { reader ->
                val buffer=FloatArray(8192)
                for(frame in listOf(48000L,48000L*590,48000L*30)) {
                    reader.read(frame,4096,buffer)
                    var error=0.0
                    repeat(4096) { i -> val expected=sin(2*PI*1000*(frame+i)/48000);error+=abs(expected-buffer[i*2]);assertEquals(buffer[i*2]*.5f,buffer[i*2+1],.0001f) }
                    assertTrue("rate=$sourceRate frame=$frame error=${error/4096}",error/4096<.001)
                }
            }
        }
    }
    @Test fun resamplingRejectsAliasedUltrasonicContent() {
        val source=object:FrameReader {
            override fun read(frame:Long,count:Int,result:FloatArray) { repeat(count) { result[it*2]=sin(2*PI*36000*(frame+it)/96000).toFloat();result[it*2+1]=result[it*2] } }
            override fun close() { }
        }
        val result=FloatArray(8192);ResampledReader(source,96000,48000,100000).use { it.read(48000,4096,result) }
        assertTrue(result.maxOf { abs(it) }<.01)
    }
    @Test fun readAheadUsesBoundedReadsAndRandomSeekRemainsExact() {
        var reads=0
        val source=object:FrameReader {
            override fun read(frame:Long,count:Int,result:FloatArray) { reads++;repeat(count) { result[it*2]=(frame+it).toFloat();result[it*2+1]=-(frame+it).toFloat() } }
            override fun close() { }
        }
        val result=FloatArray(1024);val reader=ReadAheadReader(source)
        repeat(8) { reader.read(it*512L,512,result);assertEquals(it*512f,result[0],0f) }
        assertEquals(1,reads)
        reader.read(50000,512,result);assertEquals(50000f,result[0],0f)
        reader.read(0,512,result);assertEquals(0f,result[0],0f)
    }
    @Test fun linkedLimiterBoundsOverloadWithoutFlatClippingAndRecovers() {
        val limiter=PeakLimiter();val buffer=FloatArray(1024) { if(it%2==0)2f else 1f }
        assertTrue(limiter.process(buffer,512,2,48000))
        assertTrue(buffer.all { abs(it)<=.980001f })
        repeat(512) { assertEquals(buffer[it*2]*.5f,buffer[it*2+1],.00001f) }
        repeat(100) { buffer.fill(.1f);limiter.process(buffer,512,2,48000) }
        assertEquals(.1f,buffer[0],.0001f)
    }
}
