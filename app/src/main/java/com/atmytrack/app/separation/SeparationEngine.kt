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
            require(dir.usableSpace>frames*56+64L*1048576) { "Espaço insuficiente. Necessário: ${frames*56/1048576+64} MiB (preparo e seis faixas)." }
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
                env.createSession(models.file(ModelStore.weights.single()).path,options).use { session ->
                    PcmReader(raw,frames,DemucsChunks.SAMPLES,PcmFormat()).use { reader ->
                        val outputs=SeparationOutputs.current.map { FloatWav(File(dir,"$it.wav"),frames) }
                        try {
                            separate(reader,frames,env,session,outputs,check,progress,dir)
                            outputs.forEach { it.finish() }
                        } finally { outputs.forEach { it.close() } }
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
    private fun separate(reader:FrameReader,frames:Long,env:OrtEnvironment,session:OrtSession,outputs:List<FloatWav>,check:()->Unit,progress:(String,Int)->Unit,dir:File) {
        val n=DemucsChunks.SAMPLES;val overlap=DemucsChunks.OVERLAP;val stride=n-overlap
        val carry=Array(6) { FloatArray(overlap*2) };val carryWeight=FloatArray(overlap)
        val inputInterleaved=FloatArray(n*2)
        var base=-overlap.toLong()
        while(base<frames) {
            check();disk(dir)
            val input=FloatArray(n*2)
            val first=max(0L,base);val count=(min(frames,base+n)-first).coerceAtLeast(0).toInt()
            val shift=(first-base).toInt()
            inputInterleaved.fill(0f)
            reader.read(first,count,inputInterleaved)
            for(i in 0 until count) { input[shift+i]=inputInterleaved[i*2];input[n+shift+i]=inputInterleaved[i*2+1] }
            val percent=(base.coerceAtLeast(0)*100/frames).coerceIn(0,99).toInt()
            progress("Separando 6 faixas • $percent%",percent)
            val sum=Array(6) { FloatArray(n*2) };val weight=FloatArray(n)
            for(s in 0 until 6)carry[s].copyInto(sum[s])
            carryWeight.copyInto(weight)
            OnnxTensor.createTensor(env,FloatBuffer.wrap(input),longArrayOf(1,2,n.toLong())).use { tensor ->
                session.run(mapOf("mix" to tensor)).use { result ->
                    check()
                    val out=result[0] as OnnxTensor
                    require(out.info.shape.contentEquals(longArrayOf(1,6,2,n.toLong()))) { "Saída do modelo incompatível com seis instrumentos." }
                    val prediction=out.floatBuffer
                    for(i in 0 until n) {
                        if(i%4096==0)check()
                        val w=DemucsChunks.weight(i)
                        weight[i]+=w
                        for(s in 0 until 6)for(ch in 0..1) {
                            val sample=prediction.get((s*2+ch)*n+i)
                            require(sample.isFinite()) { "Modelo produziu áudio inválido." }
                            sum[s][i*2+ch]+=sample*w
                        }
                    }
                }
            }
            for(s in 0 until 6)sum[s].copyInto(carry[s],0,stride*2)
            weight.copyInto(carryWeight,0,stride)
            val begin=(-base).coerceIn(0,stride.toLong()).toInt()
            val end=(frames-base).coerceIn(0,stride.toLong()).toInt()
            for(i in begin until end) {
                require(weight[i]>1e-8f) { "Falha no alinhamento dos blocos." }
                for(s in 0 until 6)for(ch in 0..1)sum[s][i*2+ch]/=weight[i]
            }
            for(s in 0 until 6)if(end>begin)outputs[s].write(sum[s],begin,end-begin)
            base+=stride
            progress("Separando 6 faixas",(base.coerceAtLeast(0)*100/frames).coerceIn(0,100).toInt())
        }
    }
}
