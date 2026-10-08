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
        val peaks=FloatArray(512);val cache=PlaybackCache(context);val job=coroutineContext
        for(stem in p.stems) {
            coroutineContext.ensureActive()
            val original=stem.copy(pitchFile="",pitchApplied=0,dspSpeed=100)
            val summary=cache.envelope(original,p.sampleRate) ?: run {
                val accumulator=WaveformAccumulator(stem.frames);val block=FloatArray(8192)
                // Preparation has already created local PCM. Never decode the source again.
                cache.open(original,p.sampleRate,{job.ensureActive()}).use { reader ->
                    var pos=0L;var blocks=0
                    while(pos<stem.frames) {
                        coroutineContext.ensureActive()
                        val n=minOf(4096L,stem.frames-pos).toInt();reader.read(pos,n,block);accumulator.add(block,n,pos);pos+=n
                        if(++blocks%32==0){if(!canWork())delay(1) else yield()}
                    }
                }
                cache.saveEnvelope(original,p.sampleRate,accumulator.peaks)
                accumulator.peaks.toList()
            }
            // Each completed stem covers its entire duration, not just an unfinished prefix.
            summary.forEachIndexed { i,v ->
                val first=(i.toLong()*stem.frames/p.frames).toInt().coerceIn(0,511)
                val end=(((i+1L)*stem.frames+p.frames-1)/p.frames).toInt().coerceIn(first+1,512)
                for(j in first until end)peaks[j]=max(peaks[j],v)
            }
            emit(peaks.toList());yield()
        }
        file.parentFile!!.mkdirs(); val temp=File(file.path+".part"); temp.writeText(JSONArray(peaks.toList()).toString()); check(temp.renameTo(file))
        return peaks.toList().also(emit)
    }
    suspend fun musicalKey(p:Project):KeyEstimate? {
        val detector=KeyDetector();val job=coroutineContext;val hop=maxOf(1,p.sampleRate/11025)
        val candidates=p.stems.filterNot { Regex("click|clk|metro|guide|guia|drum|bateria|perc",RegexOption.IGNORE_CASE).containsMatchIn(it.name) }
            .sortedBy { if(Regex("piano|guitar|viol|keys|tecla|pad",RegexOption.IGNORE_CASE).containsMatchIn(it.name))0 else 1 }.take(3)
        for(stem in candidates) {
            PlaybackCache(context).open(stem.copy(pitchFile="",pitchApplied=0,dspSpeed=100),p.sampleRate,{job.ensureActive()}).use { reader ->
                val block=FloatArray(8192);val samples=FloatArray(8192);val right=FloatArray(8192);val length=samples.size*hop
                for(w in 0 until 24) {
                    job.ensureActive();samples.fill(0f);right.fill(0f)
                    val start=((stem.frames-length).coerceAtLeast(0)*w/23);var pos=0;var index=0;var leftSum=0f;var rightSum=0f;var grouped=0
                    while(pos<length && start+pos<stem.frames) {
                        val n=minOf(4096L,(length-pos).toLong(),stem.frames-start-pos).toInt();reader.read(start+pos,n,block)
                        for(i in 0 until n) {
                            leftSum+=block[i*2];rightSum+=block[i*2+1];grouped++
                            if(grouped==hop && index<samples.size){samples[index]=leftSum/hop;right[index]=rightSum/hop;index++;leftSum=0f;rightSum=0f;grouped=0}
                        }
                        pos+=n
                    }
                    // Choose a complete channel, avoiding phase cancellation or nonlinear per-sample switching.
                    detector.add(if(samples.sumOf { (it*it).toDouble() }>=right.sumOf { (it*it).toDouble() })samples else right,p.sampleRate/hop);yield()
                }
            }
        }
        return detector.result()
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
