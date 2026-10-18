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
        const val VERSION="spleeter-2stems-fp16-93ba771"
        const val REVISION="93ba771920ade509f8cbd6825b1a90856c797e08"
        val weights=listOf(
            Weight("vocals.fp16.onnx",19681017,"24cef84aedcd1fe87c0b743ef3370ad34dc1fabf6c9014d6128a75a538c7b668"),
            Weight("accompaniment.fp16.onnx",19681024,"d14cea55793cc531a5875f5f4da08207d1c5ab9292e8e0099a104eecb014fcc0"))
        val downloadBytes=weights.sumOf { it.bytes }
        fun sha256(file:File,check:()->Unit={}):String {
            val digest=MessageDigest.getInstance("SHA-256")
            file.inputStream().buffered().use { input -> val bytes=ByteArray(131072);while(true) { check();val n=input.read(bytes);if(n<0)break;digest.update(bytes,0,n) } }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }
        fun valid(file:File,w:Weight,check:()->Unit={}):Boolean=file.isFile && file.length()==w.bytes && sha256(file,check)==w.sha256
    }
    fun file(w:Weight)=File(root,w.name)
    fun installed(check:()->Unit={}):Boolean=weights.all { valid(file(it),it,check) }
    fun install(check:()->Unit,progress:(Long,Long)->Unit) {
        kotlin.check(root.isDirectory || root.mkdirs()) { "Não foi possível criar a pasta do modelo." }
        kotlin.check(availableBytes()>downloadBytes+64L*1048576) { "Libere pelo menos 102 MiB para instalar o modelo." }
        var completed=0L
        for(w in weights) {
            check()
            if(valid(file(w),w,check)) { completed+=w.bytes;progress(completed,downloadBytes);continue }
            val part=File(root,w.name+".part")
            if(part.length()>w.bytes)part.delete()
            if(part.length()==w.bytes) {
                if(valid(part,w,check)) { kotlin.check(part.renameTo(file(w)));completed+=w.bytes;progress(completed,downloadBytes);continue }
                part.delete()
            }
            val offset=part.length()
            val connection=URL("https://huggingface.co/csukuangfj/sherpa-onnx-spleeter-2stems-fp16/resolve/$REVISION/${w.name}").openConnection() as HttpURLConnection
            connection.connectTimeout=20000;connection.readTimeout=20000
            connection.setRequestProperty("Accept-Encoding","identity")
            if(offset>0)connection.setRequestProperty("Range","bytes=$offset-")
            try {
                val status=connection.responseCode
                require(status==200 || status==206) { "Download falhou (HTTP $status). Verifique a conexão e tente novamente." }
                val resume=status==206 && offset>0
                if(status==206)require(connection.getHeaderField("Content-Range")?.startsWith("bytes $offset-")==true) { "Resposta de retomada inválida." }
                var written=if(resume)offset else 0L
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
                if(!valid(part,w,check)) { part.delete();error("Modelo incompleto ou SHA-256 inválido. Tente baixar novamente.") }
                kotlin.check(part.renameTo(file(w))) { "Não foi possível instalar o modelo verificado." }
                completed+=w.bytes
            } finally { connection.disconnect() }
        }
        File(root,"version.txt").writeText(VERSION)
    }
}
