package com.atmytrack.app.audio

import android.content.Context
import com.atmytrack.app.data.*
import java.io.File
import kotlinx.coroutines.*
import kotlin.coroutines.coroutineContext
import kotlin.math.*
import org.json.JSONArray
import org.json.JSONObject

data class TempoResult(val bpm:Double,val confidence:Double,val offsetSeconds:Double)
object TempoDetector {
    fun detect(envelope:FloatArray,hz:Double=100.0):TempoResult {
        if(envelope.size<400)return TempoResult(0.0,0.0,0.0)
        // Smooth quantization of a transient across 10 ms bins before autocorrelation;
        // otherwise a sub-bin period can score below its half-tempo harmonic.
        val smooth=DoubleArray(envelope.size) { i ->
            (-2..2).sumOf { d -> envelope.getOrElse(i+d) { 0f }.toDouble()*(3-abs(d)) }/9
        }
        val mean=smooth.average(); val values=DoubleArray(envelope.size) { max(0.0,smooth[it]-mean) }
        val energy=values.sumOf { it*it }; if(energy<1e-9)return TempoResult(0.0,0.0,0.0)
        fun score(lag:Int):Double {
            var sum=0.0; var a=0.0; var b=0.0
            for(i in lag until values.size) { sum+=values[i]*values[i-lag]; a+=values[i]*values[i]; b+=values[i-lag]*values[i-lag] }
            return sum/sqrt(a*b).coerceAtLeast(1e-12)
        }
        val minLag=(hz*60/240).toInt(); val maxLag=(hz*60/35).toInt()
        val scores=DoubleArray(maxLag+2) { if(it>=minLag-1)score(it) else 0.0 }
        val candidates=(minLag..maxLag).filter { scores[it]>=scores[it-1] && scores[it]>=scores[it+1] }
        val best=candidates.maxOfOrNull { scores[it] } ?: 0.0
        if(best<.18)return TempoResult(0.0,best,0.0)
        val lag=candidates.firstOrNull { scores[it]>=best*.85 } ?: candidates.maxBy { scores[it] }
        val correction=.5*(scores[lag-1]-scores[lag+1])/(scores[lag-1]-2*scores[lag]+scores[lag+1]).takeIf { abs(it)>1e-9 }.let { it ?: 1.0 }
        val period=lag+correction.coerceIn(-.5,.5)
        val threshold=values.maxOrNull()!!*.20
        val offset=values.indexOfFirst { it>threshold }.coerceAtLeast(0)/hz
        return TempoResult(60*hz/period,(scores[lag]*min(1.0,values.size/(period*12))).coerceIn(0.0,1.0),offset)
    }
    fun reference(stems:List<Stem>):Stem = stems.minBy { s ->
        val name=s.name.lowercase()
        when { listOf("click","clk","metro").any { it in name }->0; listOf("drum","bateria").any { it in name }->1; listOf("perc").any { it in name }->2; listOf("pad","synth","string").any { it in name }->4; else->3 }
    }
}

class Analysis(private val context:Context) {
    fun key(p:Project)=java.security.MessageDigest.getInstance("SHA-256").digest(p.stems.joinToString("|") { "${it.id}:${it.fingerprint}:${it.frames}" }.toByteArray()).joinToString("") { "%02x".format(it) }
    suspend fun waveform(p:Project,canWork:()->Boolean,emit:(List<Float>)->Unit):List<Float> {
        val file=File(context.filesDir,"analysis/${p.id}/${key(p)}-wave-v2.json")
        if(file.exists())return JSONArray(file.readText()).let { a->(0 until a.length()).map { a.getDouble(it).toFloat() } }.also(emit)
        val peaks=FloatArray(512); val block=FloatArray(8192)
        for(stem in p.stems) Readers.open(context,stem,p.sampleRate,4096,original=true).use { reader ->
            var pos=0L; var last=0L
            while(pos<stem.frames) {
                coroutineContext.ensureActive(); while(!canWork())delay(150)
                val n=minOf(4096L,stem.frames-pos).toInt(); reader.read(pos,n,block)
                repeat(n) { i -> val index=((pos+i)*512/p.frames).toInt().coerceAtMost(511); peaks[index]=max(peaks[index],max(abs(block[i*2]),abs(block[i*2+1]))) }
                pos+=n
                if(pos-last>p.sampleRate*5L) { last=pos; yield() }
            }
        }
        file.parentFile!!.mkdirs(); val temp=File(file.path+".part"); temp.writeText(JSONArray(peaks.toList()).toString()); check(temp.renameTo(file))
        return peaks.toList().also(emit)
    }
    suspend fun tempo(p:Project):TempoResult {
        val stem=TempoDetector.reference(p.stems); val hop=p.sampleRate/100
        val limit=minOf(stem.frames,p.sampleRate*120L)
        val envelope=FloatArray((limit/hop).toInt()); val block=FloatArray(8192)
        Readers.open(context,stem,p.sampleRate,4096,original=true).use { reader ->
            var previous=0f
            for(i in envelope.indices) {
                coroutineContext.ensureActive(); reader.read(i.toLong()*hop,hop,block)
                var power=0f; repeat(hop) { val l=block[it*2]; val r=block[it*2+1]; power+=(l*l+r*r)*.5f }
                val value=sqrt(power/hop); envelope[i]=max(0f,value-previous*.75f); previous=value
            }
        }
        return TempoDetector.detect(envelope,p.sampleRate.toDouble()/hop)
    }
}
