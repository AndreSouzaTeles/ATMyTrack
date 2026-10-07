package com.atmytrack.app

import com.atmytrack.app.tuner.*
import com.atmytrack.app.data.*
import kotlin.math.*
import org.junit.Test
import org.junit.Assert.*

class SpeedTunerTest {
    private fun signal(f:Double,harmonics:Boolean=false,amplitude:Double=.4)=FloatArray(4096) { i ->
        val phase=2*PI*f*i/24000
        (amplitude*(if(harmonics).3*sin(phase)+.55*sin(2*phase)+.15*sin(3*phase) else sin(phase))).toFloat()
    }
    @Test fun knownNotesBassAndDetuning() {
        val d=YinDetector()
        for(midi in listOf(69,40,45,50,55,59,64,28,23))for(offset in listOf(-18.0,-2.0,0.0,2.0,14.0))for(harmonic in listOf(false,true)) {
            val f=TuningMath.frequency(midi)*2.0.pow(offset/1200)
            val reading=d.detect(signal(f,harmonic))
            assertTrue("No signal $midi $offset $harmonic",reading.frequency>0)
            assertEquals(midi,TuningMath.nearest(reading.frequency))
            assertEquals("cents $midi $offset harmonics=$harmonic",offset,TuningMath.cents(reading.frequency,midi),1.0)
            assertTrue(reading.confidence>.9)
        }
        assertEquals("A4",TuningMath.name(69));assertEquals("B0",TuningMath.name(23))
        assertEquals("AFINADO",TuningMath.direction(2.99));assertTrue(TuningMath.direction(-12.0).contains("AUMENTE"));assertTrue(TuningMath.direction(9.0).contains("DIMINUA"))
    }
    @Test fun noiseSilenceAndWeakSignalAreRejected() {
        val d=YinDetector();val random=java.util.Random(17)
        assertEquals(0.0,d.detect(FloatArray(4096)).frequency,0.0)
        assertEquals(0.0,d.detect(signal(440.0,amplitude=.0001)).frequency,0.0)
        repeat(20) { assertEquals(0.0,d.detect(FloatArray(4096){(random.nextGaussian()*.05).toFloat()}).frequency,0.0) }
    }
    @Test fun calibrationAndInstrumentData() {
        for(a4 in listOf(400.0,432.0,440.0,442.0,480.0))for(m in 23..88)assertEquals(0.0,TuningMath.cents(TuningMath.frequency(m,a4),m,a4),1e-6)
        assertEquals(13,Instruments.all.size)
        assertEquals(23,Instruments.all.first { it.id=="bass5" }.tunings.first().strings.first().midi)
        assertEquals(5,Instruments.all.first { it.id=="guitar" }.tunings.size)
    }
    @Test fun speedTimelineClickMarkersAndIndependentSelections() {
        val stems=listOf(Stem(id="a",name="A",pcm="a",frames=480000),Stem(id="b",name="B",pcm="b",frames=480000))
        val p=Project(name="Time",sampleRate=48000,stems=stems,bpm=72.0,markers=listOf(Marker(name="Chorus",start=96000,end=192000)),pitchTracks=listOf("a"),semitones=2)
        assertEquals(listOf("a","b"),p.selectedSpeedTracks)
        for(speed in listOf(50,75,80,90,100,110,120,125,150,200)) {
            val q=p.copy(speed=speed,stems=p.stems.map { it.copy(dspSpeed=speed) })
            val e=q.engineView()
            assertEquals(72*speed/100.0,e.bpm,1e-9)
            assertEquals((480000/(speed/100.0)).roundToLong(),e.frames)
            assertEquals((96000/(speed/100.0)).roundToLong(),e.markers.first().start)
            assertEquals(listOf("a"),q.pitchTracks)
            assertEquals(p.markers,q.markers)
        }
        val partial=p.copy(speed=80,speedTracks=listOf("a"),stems=listOf(stems[0].copy(dspSpeed=80),stems[1]))
        assertEquals(72.0,partial.effectiveBpm,0.0);assertEquals(12.5,partial.playbackSeconds,0.0)
    }
}
