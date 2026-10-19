package com.atmytrack.app.separation

import java.io.File
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** Only pinned data files are accepted. A partial download never replaces an installed model. */
class ModelStore(private val root: File,private val availableBytes:()->Long={root.usableSpace}) {
    data class Weight(val name:String,val bytes:Long,val sha256:String)
    companion object {
        const val VERSION="htdemucs-6s-fp16-49df9b6"
        const val REVISION="49df9b6989cf2150840ea65b0bef77a2e471b678"
        val weights=listOf(Weight("htdemucs_6s_fp16weights.onnx",136428532,"7ce55792e2231c93fbf92de95f5fd5b3a5e6c89f7db690dfd693e8f1dce56869"))
        val downloadBytes=weights.sumOf { it.bytes }
        fun sha256(file:File,check:()->Unit={}):String = hash(file,check) { _,_-> }
        private fun hash(file:File,check:()->Unit,progress:(Long,Long)->Unit):String {
            val digest=MessageDigest.getInstance("SHA-256")
            val total=file.length();var read=0L;progress(0,total)
            file.inputStream().buffered().use { input -> val bytes=ByteArray(131072);while(true) { check();val n=input.read(bytes);if(n<0)break;digest.update(bytes,0,n);read+=n;progress(read,total) } }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }
        fun valid(file:File,w:Weight,check:()->Unit={}):Boolean=verify(file,w,check) {}
        fun verify(file:File,w:Weight,check:()->Unit={},progress:(Int)->Unit):Boolean {
            if(!file.isFile || file.length()!=w.bytes)return false
            var last=-1
            return hash(file,check) { read,total ->
                val percent=if(total>0)(read*100/total).coerceIn(0,100).toInt() else 100
                if(percent!=last) { last=percent;progress(percent) }
            }==w.sha256
        }
    }
    fun file(w:Weight)=File(root,w.name)
    fun installed(check:()->Unit={}):Boolean=weights.all { valid(file(it),it,check) }
    fun install(check:()->Unit,progress:(Long,Long)->Unit)=installWithProgress(check,progress) { _,_-> }
    fun installWithProgress(check:()->Unit,progress:(Long,Long)->Unit,phase:(String,Int)->Unit) {
        kotlin.check(root.isDirectory || root.mkdirs()) { "Não foi possível criar a pasta do modelo." }
        kotlin.check(availableBytes()>downloadBytes+64L*1048576) { "Libere pelo menos 195 MiB para instalar o modelo." }
        var completed=0L
        var ready=0
        for((index,w) in weights.withIndex()) {
            check()
            fun verified(candidate:File)=verify(candidate,w,check) { percent -> phase("Verificando integridade • arquivo ${index+1}/${weights.size}",percent) }
            fun publish(part:File) {
                check()
                kotlin.check(part.renameTo(file(w))) { "Não foi possível instalar o modelo verificado." }
                ready++;phase("Instalando arquivos do modelo",ready*100/weights.size)
            }
            if(verified(file(w))) { ready++;completed+=w.bytes;progress(completed,downloadBytes);continue }
            val part=File(root,w.name+".part")
            if(part.length()>w.bytes)part.delete()
            if(part.length()==w.bytes) {
                if(verified(part)) { publish(part);completed+=w.bytes;progress(completed,downloadBytes);continue }
                part.delete()
            }
            val offset=part.length()
            val connection=URL("https://huggingface.co/StemSplitio/htdemucs-6s-onnx/resolve/$REVISION/${w.name}").openConnection() as HttpURLConnection
            connection.connectTimeout=20000;connection.readTimeout=20000
            connection.setRequestProperty("Accept-Encoding","identity")
            if(offset>0)connection.setRequestProperty("Range","bytes=$offset-")
            try {
                val status=connection.responseCode
                require(status==200 || status==206) { "Download falhou (HTTP $status). Verifique a conexão e tente novamente." }
                val resume=status==206 && offset>0
                if(status==206)require(connection.getHeaderField("Content-Range")?.startsWith("bytes $offset-")==true) { "Resposta de retomada inválida." }
                var written=if(resume)offset else 0L
                progress(completed+written,downloadBytes)
                RandomAccessFile(part,"rw").use { out ->
                    if(!resume)out.setLength(0)
                    out.seek(written)
                    connection.inputStream.use { input ->
                        val bytes=ByteArray(131072)
                        while(true) {
                            check();val n=input.read(bytes);if(n<0)break
                            require(written+n<=w.bytes) { "Tamanho do modelo diferente do esperado." }
                            kotlin.check(availableBytes()>n+32L*1048576) { "Armazenamento insuficiente. Libere espaço e tente novamente." }
                            out.write(bytes,0,n);written+=n;progress(completed+written,downloadBytes)
                        }
                    }
                    out.fd.sync()
                }
                if(!verified(part)) { part.delete();error("Modelo incompleto ou SHA-256 inválido. Tente baixar novamente.") }
                publish(part)
                completed+=w.bytes
            } finally { connection.disconnect() }
        }
        File(root,"version.txt").writeText(VERSION)
    }
}
