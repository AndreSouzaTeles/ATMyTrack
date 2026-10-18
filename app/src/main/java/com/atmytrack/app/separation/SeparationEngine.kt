package com.atmytrack.app.separation

import android.content.Context
import android.net.Uri
import ai.onnxruntime.*
import com.atmytrack.app.audio.*
import com.atmytrack.app.data.*
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.*
import com.atmytrack.app.separation.SeparationDsp.FFT
import com.atmytrack.app.separation.SeparationDsp.HOP
import com.atmytrack.app.separation.SeparationDsp.BINS
import com.atmytrack.app.separation.SeparationDsp.STEPS
import com.atmytrack.app.separation.SeparationDsp.CONTEXT
import com.atmytrack.app.separation.SeparationDsp.CORE
import com.atmytrack.app.separation.SeparationDsp.window
import com.atmytrack.app.separation.SeparationDsp.fft
import com.atmytrack.app.separation.SeparationDsp.vocalMask

class SeparationEngine(private val context:Context) {
    suspend fun run(uri:Uri,dir:File,check:()->Unit,progress:(String,Int)->Unit) {
        val models=ModelStore(File(context.filesDir,"models/${ModelStore.VERSION}"))
        require(models.installed(check)) { "Modelo ausente ou corrompido. Baixe novamente." }
        dir.mkdirs()
        var imported:Project?=null
        try {
            progress("Lendo e validando a música…",-1)
            val source=AudioImporter(context).import(listOf(uri),"Separação") {}
            imported=source
            val frames=(source.frames.toDouble()*44100/source.sampleRate).roundToLong()
            require(frames in 1..((0xffffffffL-36)/8)) { "Música excede o limite WAV de 4 GB por faixa." }
            require(dir.usableSpace>frames*24+64L*1048576) { "Espaço insuficiente. Necessário: ${frames*24/1048576+64} MiB (preparo e duas faixas)." }
            val stem=source.stems.single().copy(frames=frames)
            val raw=File(dir,"input.pcm")
            Readers.open(context,stem,44100,4096,true).use { reader ->
                raw.outputStream().buffered(262144).use { out ->
                    val samples=FloatArray(8192);val bytes=ByteBuffer.allocate(32768).order(ByteOrder.LITTLE_ENDIAN)
                    var at=0L
                    while(at<frames) {
                        check();disk(dir);val count=min(4096L,frames-at).toInt();reader.read(at,count,samples)
                        bytes.clear();for(i in 0 until count*2)bytes.putFloat(samples[i].let { if(it.isFinite())it else 0f })
                        out.write(bytes.array(),0,count*8);at+=count
                        progress("Preparando áudio • ${at*100/frames}%",-1)
                    }
                }
            }
            check();progress("Carregando IA local na CPU…",-1)
            val env=OrtEnvironment.getEnvironment()
            OrtSession.SessionOptions().use { options ->
                options.setIntraOpNumThreads(2);options.setInterOpNumThreads(1)
                options.setMemoryPatternOptimization(false);options.setCPUArenaAllocator(false)
                env.createSession(models.file(ModelStore.weights[0]).path,options).use { vocals ->
                    env.createSession(models.file(ModelStore.weights[1]).path,options).use { accompaniment ->
                        PcmReader(raw,frames,FFT,PcmFormat()).use { reader ->
                            FloatWav(File(dir,"Voz.wav"),frames).use { voice ->
                                FloatWav(File(dir,"Instrumental.wav"),frames).use { music ->
                                    separate(reader,frames,env,vocals,accompaniment,voice,music,check,progress,dir)
                                    voice.finish();music.finish()
                                }
                            }
                        }
                    }
                }
            }
            raw.delete()
            File(dir,"complete").writeText("$frames\n${ModelStore.VERSION}")
        } finally {
            // Only this private, unregistered importer directory belongs to the job.
            imported?.let { val root=File(context.filesDir,"audio").canonicalFile;val child=File(root,it.id).canonicalFile;if(child.parentFile==root)child.deleteRecursively() }
        }
    }
    private fun disk(dir:File) { check(dir.usableSpace>32L*1048576) { "Armazenamento cheio. Libere espaço e tente novamente." } }
    private fun separate(reader:FrameReader,frames:Long,env:OrtEnvironment,v:OrtSession,a:OrtSession,voice:FloatWav,music:FloatWav,check:()->Unit,progress:(String,Int)->Unit,dir:File) {
        val stride=CORE*HOP;val tail=FFT-HOP;val length=stride+tail
        val carryV=FloatArray(tail*2);val carryA=FloatArray(tail*2);val carryN=FloatArray(tail)
        val re=FloatArray(FFT);val im=FloatArray(FFT);val source=FloatArray(FFT*2)
        fun spectrum(center:Long,ch:Int) {
            source.fill(0f)
            val begin=center-FFT/2
            val first=max(0L,begin);val count=(min(frames,begin+FFT)-first).coerceAtLeast(0).toInt()
            if(count>0) {
                reader.read(first,count,source)
                val shift=(first-begin).toInt()
                for(i in 0 until FFT)re[i]=if(i in shift until shift+count)source[(i-shift)*2+ch]*window[i] else 0f
            } else re.fill(0f)
            im.fill(0f);fft(re,im)
        }
        fun infer(session:OrtSession,tensor:OnnxTensor):FloatArray=session.run(mapOf("x" to tensor)).use { result ->
            val out=result[0] as OnnxTensor
            require(out.info.shape.contentEquals(longArrayOf(2,1,512,1024))) { "Formato de saída do modelo incompatível." }
            FloatArray(2*STEPS*BINS).also { out.floatBuffer.get(it) }
        }
        var core=-1L
        while(core*HOP-FFT/2<frames) {
            check();disk(dir)
            val input=FloatArray(2*STEPS*BINS)
            for(ch in 0..1)for(t in 0 until STEPS) {
                check();spectrum((core-CONTEXT+t)*HOP,ch)
                val offset=(ch*STEPS+t)*BINS
                for(k in 0 until BINS)input[offset+k]=hypot(re[k],im[k])
            }
            val percent=((core*HOP).coerceAtLeast(0)*100/frames).coerceIn(0,99).toInt()
            progress("Separando voz e instrumental • blocos concluídos: $percent%",percent)
            val predictions=OnnxTensor.createTensor(env,FloatBuffer.wrap(input),longArrayOf(2,1,512,1024)).use { tensor ->
                val vv=infer(v,tensor);check();val aa=infer(a,tensor);check();vv to aa
            }
            val sumV=FloatArray(length*2);val sumA=FloatArray(length*2);val norm=FloatArray(length)
            carryV.copyInto(sumV);carryA.copyInto(sumA);carryN.copyInto(norm)
            val vr=FloatArray(FFT);val vi=FloatArray(FFT)
            for(t in 0 until CORE) {
                check()
                for(ch in 0..1) {
                    spectrum((core+t)*HOP,ch)
                    val offset=(ch*STEPS+t+CONTEXT)*BINS
                    for(k in 0..FFT/2) {
                        val mask=if(k<BINS)vocalMask(predictions.first[offset+k],predictions.second[offset+k]) else 0f
                        vr[k]=re[k]*mask;vi[k]=im[k]*mask
                        if(k in 1 until FFT/2) { vr[FFT-k]=vr[k];vi[FFT-k]=-vi[k] }
                    }
                    fft(vr,vi,true)
                    fft(re,im,true)
                    for(i in 0 until FFT) {
                        val at=t*HOP+i;val w=window[i]
                        sumV[at*2+ch]+=vr[i]*w
                        sumA[at*2+ch]+=(re[i]-vr[i])*w
                        if(ch==0)norm[at]+=w*w
                    }
                }
            }
            sumV.copyInto(carryV,0,stride*2);sumA.copyInto(carryA,0,stride*2);norm.copyInto(carryN,0,stride)
            val base=core*HOP-FFT/2
            val start=(-base).coerceIn(0,stride.toLong()).toInt()
            val end=min(stride.toLong(),frames-base).toInt()
            for(i in start until end) {
                require(norm[i]>1e-8f) { "Falha no alinhamento dos blocos." }
                for(ch in 0..1) { sumV[i*2+ch]/=norm[i];sumA[i*2+ch]/=norm[i] }
            }
            if(end>start) { voice.write(sumV,start,end-start);music.write(sumA,start,end-start) }
            core+=CORE
            progress("Separando voz e instrumental",((core*HOP-FFT/2).coerceAtLeast(0)*100/frames).coerceIn(0,100).toInt())
        }
    }
}
