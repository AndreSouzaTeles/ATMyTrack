package com.atmytrack.app.audio

import kotlin.math.*

data class KeyEstimate(val name:String,val confidence:Double)
/** Spectral chroma + Pearson correlation with Krumhansl-Kessler major/minor profiles.
 * Profiles documented at https://extras.humdrum.org/man/keycor/ . Estimate, not certainty. */
class KeyDetector {
    private val chroma=DoubleArray(12)
    private var windows=0
    fun add(samples:FloatArray,rate:Int) {
        val n=samples.size;require(n>0 && n and (n-1)==0)
        if(samples.sumOf { it.toDouble()*it }/n<1e-7)return
        val real=DoubleArray(n) { samples[it]*(.5-.5*cos(2*PI*it/(n-1))) };val imag=DoubleArray(n)
        var j=0
        for(i in 1 until n){var bit=n shr 1;while(j and bit!=0){j=j xor bit;bit=bit shr 1};j=j xor bit;if(i<j){val tmp=real[i];real[i]=real[j];real[j]=tmp}}
        var size=2
        while(size<=n){val angle=-2*PI/size;val wr=cos(angle);val wi=sin(angle)
            for(start in 0 until n step size){var r=1.0;var im=0.0;for(k in 0 until size/2){val a=start+k;val b=a+size/2;val tr=r*real[b]-im*imag[b];val ti=r*imag[b]+im*real[b];real[b]=real[a]-tr;imag[b]=imag[a]-ti;real[a]+=tr;imag[a]+=ti;val next=r*wr-im*wi;im=r*wi+im*wr;r=next}}
            size=size shl 1
        }
        val mag=DoubleArray(n/2) { hypot(real[it],imag[it]) };val max=mag.maxOrNull() ?: return
        val frame=DoubleArray(12)
        for(i in 2 until mag.size-1){if(mag[i]<max*.025 || mag[i]<mag[i-1] || mag[i]<mag[i+1])continue
            val delta=(.5*(mag[i-1]-mag[i+1])/(mag[i-1]-2*mag[i]+mag[i+1])).coerceIn(-.5,.5)
            val hz=(i+delta)*rate/n;if(hz !in 45.0..2200.0)continue
            val note=69+12*ln(hz/440)/ln(2.0);val midi=note.roundToInt();if(abs(note-midi)>.35)continue
            frame[Math.floorMod(midi,12)]+=mag[i]/sqrt(hz)
        }
        val sum=frame.sum();if(sum>1e-8){for(i in 0..11)chroma[i]+=frame[i]/sum;windows++}
    }
    fun result():KeyEstimate? {
        if(windows<3)return null
        val maximum=chroma.maxOrNull() ?: return null
        if(chroma.count { it>maximum*.12 }<3)return null
        val major=doubleArrayOf(6.35,2.23,3.48,2.33,4.38,4.09,2.52,5.19,2.39,3.66,2.29,2.88)
        val minor=doubleArrayOf(6.33,2.68,3.52,5.38,2.60,3.53,2.54,4.75,3.98,2.69,3.34,3.17)
        val mean=chroma.average();val norm=sqrt(chroma.sumOf { (it-mean).pow(2) });if(norm<1e-7 || norm/chroma.sum()<.05)return null
        val scores=mutableListOf<Pair<Int,Double>>()
        for(mode in 0..1){val profile=if(mode==0)major else minor;val m=profile.average();val d=sqrt(profile.sumOf { (it-m).pow(2) });for(root in 0..11){val score=(0..11).sumOf { (chroma[(it+root)%12]-mean)*(profile[it]-m) }/(norm*d);scores+=root+mode*12 to score}}
        val ranked=scores.sortedByDescending { it.second };val best=ranked[0];val gap=best.second-ranked[1].second
        if(best.second<.6 || gap<.035)return null
        val names=listOf("C","C#","D","Eb","E","F","F#","G","Ab","A","Bb","B")
        return KeyEstimate(names[best.first%12]+if(best.first>=12)"m" else "",(best.second*min(1.0,gap/.15)).coerceIn(0.0,1.0))
    }
}
