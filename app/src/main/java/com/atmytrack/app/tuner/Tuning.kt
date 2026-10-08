package com.atmytrack.app.tuner

import kotlin.math.*

data class PitchReading(val frequency:Double=0.0,val confidence:Double=0.0,val rms:Double=0.0)
/** YIN cumulative mean normalized difference; local interpolation, no FFT peak guessing. */
class YinDetector(private val rate:Int=24000,private val size:Int=4096) {
    private val difference=DoubleArray(size/2)
    private val rawDifference=DoubleArray(size/2)
    fun detect(samples:FloatArray):PitchReading {
        require(samples.size==size)
        val mean=samples.sumOf { it.toDouble() }/size
        val rms=sqrt(samples.sumOf { (it-mean).pow(2) }/size)
        if(rms<.002)return PitchReading(rms=rms)
        val window=size/2
        val minLag=(rate/1600).coerceAtLeast(2)
        val maxLag=min(window-2,rate/25)
        difference[0]=1.0
        var running=0.0
        for(tau in 1..maxLag) {
            var sum=0.0
            for(i in 0 until window) { val d=(samples[i]-samples[i+tau]).toDouble();sum+=d*d }
            running+=sum
            rawDifference[tau]=sum
            difference[tau]=if(running>0)sum*tau/running else 1.0
        }
        var tau=minLag
        while(tau<maxLag) {
            if(difference[tau]<.12) {
                while(tau+1<=maxLag && difference[tau+1]<difference[tau])tau++
                val confidence=1-difference[tau]
                if(confidence<.90)return PitchReading(rms=rms)
                // Interpolate the actual difference minimum, avoiding the slope introduced by CMND.
                val a=rawDifference[tau-1];val b=rawDifference[tau];val c=rawDifference[(tau+1).coerceAtMost(maxLag)]
                val delta=if(abs(a-2*b+c)>1e-12)(.5*(a-c)/(a-2*b+c)).coerceIn(-1.0,1.0) else 0.0
                return PitchReading(rate/(tau+delta),confidence,rms)
            }
            tau++
        }
        return PitchReading(rms=rms)
    }
}
class PitchStabilizer {
    private val history=ArrayDeque<Double>()
    private var value=0.0
    fun update(reading:PitchReading):PitchReading {
        if(reading.frequency<=0) { history.clear();value=0.0;return reading }
        val log=ln(reading.frequency)
        if(value>0 && abs(log-value)>.08) { history.clear();value=0.0 }
        history.addLast(log);while(history.size>3)history.removeFirst()
        if(history.size<2)return reading.copy(frequency=0.0)
        val median=history.sorted()[history.size/2]
        value=if(value==0.0)median else value+.55*(median-value)
        return reading.copy(frequency=exp(value))
    }
}
data class StringNote(val number:Int,val midi:Int) { val label get()=TuningMath.name(midi) }
data class Tuning(val id:String,val name:String,val strings:List<StringNote>)
data class Instrument(val id:String,val name:String,val tunings:List<Tuning>)
object Instruments {
    private fun tuning(id:String,name:String,vararg notes:Int)=Tuning(id,name,notes.mapIndexed { i,n -> StringNote(notes.size-i,n) })
    private val guitar=listOf(tuning("standard","Standard",40,45,50,55,59,64),tuning("drop-d","Drop D",38,45,50,55,59,64),tuning("d-standard","D Standard",38,43,48,53,57,62),tuning("drop-c","Drop C",36,43,48,53,57,62),tuning("half-down","Half Step Down",39,44,49,54,58,63))
    val all=listOf(
        Instrument("chromatic","Cromático",emptyList()),Instrument("acoustic","Violão",guitar),Instrument("guitar","Guitarra",guitar),
        Instrument("bass4","Baixo 4 cordas",listOf(tuning("standard","Standard",28,33,38,43))),
        Instrument("bass5","Baixo 5 cordas",listOf(tuning("standard","Standard",23,28,33,38,43))),
        Instrument("bass6","Baixo 6 cordas",listOf(tuning("standard","Standard",23,28,33,38,43,48))),
        Instrument("ukulele","Ukulele",listOf(tuning("standard","GCEA • High G",67,60,64,69))),
        Instrument("violin","Violino",listOf(tuning("standard","Standard",55,62,69,76))),
        Instrument("viola","Viola",listOf(tuning("standard","Viola de arco • CGDA",48,55,62,69))),
        Instrument("cello","Violoncelo",listOf(tuning("standard","Standard",36,43,50,57))),
        Instrument("doublebass","Contrabaixo acústico",listOf(tuning("standard","Standard",28,33,38,43))),
        Instrument("cavaquinho","Cavaquinho",listOf(tuning("standard","DGBD",62,67,71,74))),
        Instrument("mandolin","Bandolim",listOf(tuning("standard","GDAE • pares",55,62,69,76)))
    )
}
object TuningMath {
    private val notes=listOf("C","C#","D","D#","E","F","F#","G","G#","A","A#","B")
    fun frequency(midi:Int,a4:Double=440.0)=a4*2.0.pow((midi-69)/12.0)
    fun nearest(f:Double,a4:Double=440.0)=(69+12*log2(f/a4)).roundToInt()
    fun cents(f:Double,midi:Int,a4:Double=440.0)=1200*log2(f/frequency(midi,a4))
    fun name(midi:Int)=notes[Math.floorMod(midi,12)]+(Math.floorDiv(midi,12)-1)
    fun direction(cents:Double)=when { abs(cents)<=3->"AFINADO";cents<0->"↑ AUMENTE A AFINAÇÃO";else->"↓ DIMINUA A AFINAÇÃO" }
}

/** Measured chromatic note and intended string are distinct, even far from tuning. */
data class TunerDisplay(val midi:Int,val cents:Double,val target:StringNote?,val targetCents:Double?)
fun tunerDisplay(frequency:Double,tuning:Tuning?,manual:Int,a4:Double=440.0):TunerDisplay {
    val midi=TuningMath.nearest(frequency,a4)
    val target=tuning?.strings?.find { it.number==manual }
        ?: tuning?.strings?.minByOrNull { abs(TuningMath.cents(frequency,it.midi,a4)) }
    return TunerDisplay(midi,TuningMath.cents(frequency,midi,a4),target,
        target?.let { TuningMath.cents(frequency,it.midi,a4) })
}
