package com.atmytrack.app

import com.atmytrack.app.audio.*
import com.atmytrack.app.data.*
import org.junit.Test
import org.junit.Assert.*
import kotlin.math.*

class ImportAnalysisTest {
 @Test fun pcmCopyEnvelopeRetainsPartialFramesAndIgnoresHeaders() {
    val frames=2048;val format=PcmFormat(2,PcmEncoding.S24,45);val bytes=ByteArray(45+frames*6+17)
    val floats=FloatArray(frames*2)
    for(i in 0 until frames)for(c in 0..1){val v=if(i in 512 until 1024)0 else ((if(c==0)-1 else 1)*(.3+.2*sin(i*.7))*8388607).toInt();floats[i*2+c]=v/8388608f;val p=45+i*6+c*3;bytes[p]=v.toByte();bytes[p+1]=(v shr 8).toByte();bytes[p+2]=(v shr 16).toByte()}
    val expected=WaveformAccumulator(frames.toLong());expected.add(floats,frames,0)
    for(chunk in listOf(1,7,67,256,4096)){val actual=WaveformAccumulator(frames.toLong());var offset=0;while(offset<bytes.size){val n=minOf(chunk,bytes.size-offset);actual.addRaw(bytes.copyOfRange(offset,offset+n),n,offset.toLong(),format);offset+=n};assertArrayEquals(expected.peaks,actual.peaks,1e-6f)}
 }
 @Test fun identifiesMajorAndMinorProgressionsInAllTwelveKeys() {
    val names=listOf("C","C#","D","Eb","E","F","F#","G","Ab","A","Bb","B")
    for(root in 0..11)for(minor in listOf(false,true)) {
        val detector=KeyDetector();val triad=if(minor)listOf(0,3,7) else listOf(0,4,7)
        val chords=listOf(triad,triad,triad,if(minor)listOf(5,8,12) else listOf(5,9,12),listOf(7,11,14),triad)
        for(notes in chords)repeat(3){val samples=FloatArray(8192){i->notes.sumOf { note->val hz=440*2.0.pow((48+root+note-69)/12.0);sin(2*PI*hz*i/12000)*.15 }.toFloat()};detector.add(samples,12000)}
        assertEquals("root=$root minor=$minor",names[root]+if(minor)"m" else "",detector.result()?.name)
    }
 }
 @Test fun silenceNoiseAndSinglePitchDoNotInventAKey() {
    val silence=KeyDetector();repeat(5){silence.add(FloatArray(8192),12000)};assertNull(silence.result())
    val noise=KeyDetector();val random=java.util.Random(42);repeat(24){noise.add(FloatArray(8192){(random.nextDouble()*.2-.1).toFloat()},12000)};assertNull(noise.result())
    val single=KeyDetector();repeat(5){single.add(FloatArray(8192){i->(.2*sin(2*PI*440*i/12000)).toFloat()},12000)};assertNull(single.result())
 }
}
