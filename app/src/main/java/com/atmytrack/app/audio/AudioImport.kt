package com.atmytrack.app.audio

import android.content.Context
import android.media.*
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import com.atmytrack.app.data.*
import java.io.*
import java.nio.ByteBuffer
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import android.content.Intent
import android.media.MediaMetadataRetriever
import kotlin.coroutines.coroutineContext
import kotlin.math.roundToLong

data class ImportItem(val index:Int,val name:String,val status:String,val detail:String="")

class AudioImporter(private val context: Context) {
    private val resolver = context.contentResolver
    fun name(uri: Uri): String = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
        if (it.moveToFirst()) it.getString(0) else null
    } ?: uri.lastPathSegment ?: "Track"
    fun folder(uri: Uri): List<Uri> {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(uri, DocumentsContract.getTreeDocumentId(uri))
        val result = mutableListOf<Pair<String, Uri>>()
        resolver.query(children, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE), null, null, null)?.use { cursor ->
            while (cursor.moveToNext()) {
                val label = cursor.getString(1); val mime = cursor.getString(2)
                if (mime.startsWith("audio/") || label.substringAfterLast('.', "").lowercase() in listOf("wav", "mp3", "aac", "m4a", "flac", "ogg", "opus"))
                    result += label to DocumentsContract.buildDocumentUriUsingTree(uri, cursor.getString(0))
            }
        }
        return result.sortedBy { it.first.lowercase() }.map { it.second }
    }
    suspend fun import(uris: List<Uri>, title: String, tree: Uri? = null, allowPartial:Boolean=false, onItem:(ImportItem)->Unit={}, progress: (String) -> Unit): Project = coroutineScope {
        require(uris.isNotEmpty()) { "A pasta não contém arquivos de áudio." }
        require(uris.size <= 64) { "Limite de 64 stems por projeto." }
        val id=UUID.randomUUID().toString()
        val dir=File(context.filesDir,"audio/$id").apply { mkdirs() }
        val slots=Semaphore(3)
        val treePermission=tree?.let { persist(it) } ?: false
        uris.forEachIndexed { index,uri -> onItem(ImportItem(index,uri.lastPathSegment ?: "Track ${index+1}","DISCOVERED","Na fila")) }
        try {
            val stems=coroutineScope { uris.mapIndexed { index, uri -> async(Dispatchers.IO) { slots.withPermit {
                ensureActive()
                var label=uri.lastPathSegment ?: "Track ${index+1}"
                try {
                label=name(uri)
                onItem(ImportItem(index,label,"DISCOVERED"))
                progress("Lendo ${index+1}/${uris.size}: $label")
                val durable=uri.scheme=="file" || treePermission || persist(uri)
                var direct=durable && runCatching { SeekInput.uri(context,uri).use { it.seek(0) } }.isSuccess
                var local=""
                if(!direct) {
                    val target=File(dir,"$index.audio")
                    copy(uri,target) { n,total -> progress("Copiando origem sem acesso persistente: $label • "+if(total>0)"${n*100/total}%" else "${n/(1024*1024)} MB") }
                    local="audio/$id/$index.audio"
                }
                val input=if(direct)SeekInput.uri(context,uri) else SeekInput.file(File(context.filesDir,local))
                val (wav,adts)=input.use { val wav=WavHeader.read(it);wav to if(wav==null)AdtsTiming.read(it) else null }
                val metadata=if(wav!=null) wav else {
                    val extractor=MediaExtractor()
                    try {
                        if(direct)extractor.setDataSource(context,uri,null) else extractor.setDataSource(File(context.filesDir,local).path)
                        val f=(0 until extractor.trackCount).map { extractor.getTrackFormat(it) }.firstOrNull { it.getString(MediaFormat.KEY_MIME)?.startsWith("audio/")==true } ?: error("Áudio não reconhecido: $label")
                        require(MediaCodecList(MediaCodecList.REGULAR_CODECS).findDecoderForFormat(f)!=null) { "Decoder indisponível neste aparelho: $label" }
                        val channels=f.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        require(channels in 1..2) { "Use stems mono ou estéreo: $label" }
                        val rate=f.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        var duration=if(f.containsKey(MediaFormat.KEY_DURATION)) f.getLong(MediaFormat.KEY_DURATION) else 0L
                        if(duration<=0) {
                            val retriever=MediaMetadataRetriever()
                            try {
                                if(direct)retriever.setDataSource(context,uri) else retriever.setDataSource(File(context.filesDir,local).path)
                                duration=(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0)*1000
                            } finally { retriever.release() }
                        }
                        require(duration>0) { "Não foi possível ler a duração de $label." }
                        WavInfo(rate,adts?.frames(duration,rate) ?: (duration*rate/1000000.0).roundToLong(),PcmFormat(channels,PcmEncoding.S16))
                    } finally { extractor.release() }
                }
                Stem(name=label.substringBeforeLast('.'),pcm=local,frames=metadata.frames,source=uri.toString(),format=metadata.format,
                    external=direct,compressed=wav==null,sourceRate=metadata.rate,sourceFrames=metadata.frames,fingerprint=SourceFingerprint.get(context,if(direct)uri else Uri.fromFile(File(context.filesDir,local))))
                    .let { onItem(ImportItem(index,label,"DISCOVERED")); metadata.rate to it }
                } catch(e:Exception) {
                    if(e is CancellationException)throw e
                    File(dir,"$index.audio").delete()
                    onItem(ImportItem(index,label,"ERROR",e.message ?: "Origem incompatível"))
                    if(!allowPartial)throw e
                    null
                }
            } } }.awaitAll().filterNotNull() }
            require(stems.isNotEmpty()) { "Nenhuma track pôde ser importada. Consulte os erros por arquivo." }
            val rate=stems.first().first
            Project(id=id,name=title.ifBlank { "Novo projeto" },sampleRate=rate,stems=stems.map { (sourceRate,s) ->
                s.copy(frames=(s.frames.toDouble()*rate/sourceRate).roundToLong())
            })
        } catch(e:Exception) { dir.deleteRecursively(); throw e }
    }
    private fun persist(uri:Uri):Boolean {
        return try { resolver.takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION); true }
        catch(_:Exception) { resolver.persistedUriPermissions.any { it.isReadPermission && it.uri==uri } }
    }
    private suspend fun copy(uri: Uri, target: File, progress: (Long, Long) -> Unit) {
        val total = if (uri.scheme == "file") File(uri.path!!).length() else
            resolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { if (it.moveToFirst() && !it.isNull(0)) it.getLong(0) else -1L } ?: -1L
        val disk = SpaceGuard(target.parentFile!!); disk.check(if (total > 0) total else 0)
        var copied = 0L; var lastUpdate = 0L
        progress(0, total)
        val input = resolver.openInputStream(uri) ?: error("Não foi possível abrir o arquivo selecionado.")
        input.use { source -> target.outputStream().buffered(1024 * 1024).use { out ->
            val bytes = ByteArray(1024 * 1024)
            while (true) {
                coroutineContext.ensureActive()
                val n = source.read(bytes)
                if (n < 0) break
                if (n == 0) continue
                out.write(bytes, 0, n); copied += n; disk.wrote(n)
                val now = System.nanoTime()
                if (now - lastUpdate >= 200_000_000) { progress(copied, total); lastUpdate = now }
            }
        } }
        require(total < 0 || copied == total) { "Arquivo incompleto: a origem não forneceu todos os bytes." }
        progress(copied, total)
    }
    private class SpaceGuard(private val dir: File) {
        private var sinceCheck = 0L
        fun check(required: Long = 0) { check(dir.usableSpace - required >= 32L * 1024 * 1024) { "Espaço insuficiente. Libere armazenamento e tente novamente." } }
        fun wrote(bytes: Int) { sinceCheck += bytes; if (sinceCheck >= 8L * 1024 * 1024) { check(); sinceCheck = 0 } }
    }
}
