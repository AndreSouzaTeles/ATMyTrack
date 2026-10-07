package com.atmytrack.app.audio

import android.content.Context
import android.media.*
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import com.atmytrack.app.data.*
import java.io.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext

class LegacyAudioImporter(private val context: Context) {
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
    suspend fun import(uris: List<Uri>, title: String, progress: (String) -> Unit): Project {
        require(uris.isNotEmpty()) { "A pasta não contém arquivos de áudio compatíveis." }
        require(uris.size <= 64) { "Esta versão permite até 64 stems por projeto." }
        val id = UUID.randomUUID().toString()
        val dir = File(context.filesDir, "audio/$id").apply { mkdirs() }
        try {
            var rate = 0
            val stems = uris.mapIndexed { index, uri ->
                coroutineContext.ensureActive()
                val label = name(uri)
                progress("Preparando ${index + 1}/${uris.size}: $label")
                val dest = File(dir, "$index.pcm")
                val decoded = decode(uri, dest)
                require(rate == 0 || rate == decoded.first) { "$label usa ${decoded.first} Hz; as outras stems usam $rate Hz. Exporte todas com a mesma taxa de amostragem." }
                rate = decoded.first
                require(decoded.second > 0) { "$label está vazio ou não pôde ser decodificado." }
                Stem(name = label.substringBeforeLast('.'), pcm = "audio/$id/$index.pcm", frames = decoded.second, source = uri.toString())
            }
            return Project(id = id, name = title.ifBlank { "Novo projeto" }, sampleRate = rate, stems = stems)
        } catch (e: Exception) { dir.deleteRecursively(); throw e }
    }
    private suspend fun decode(uri: Uri, target: File): Pair<Int, Long> {
        val extractor = MediaExtractor()
        var decoder: MediaCodec? = null
        try {
            extractor.setDataSource(context, uri, null)
            val track = (0 until extractor.trackCount).firstOrNull { extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true }
                ?: error("Nenhuma faixa de áudio encontrada em ${name(uri)}.")
            extractor.selectTrack(track)
            val format = extractor.getTrackFormat(track)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: error("Formato desconhecido.")
            var rate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            require(channels in 1..2) { "Importe stems mono ou estéreo; áudio surround não é suportado nesta versão." }
            var encoding = if (format.containsKey(MediaFormat.KEY_PCM_ENCODING)) format.getInteger(MediaFormat.KEY_PCM_ENCODING) else AudioFormat.ENCODING_PCM_16BIT
            var frames = 0L
            BufferedOutputStream(FileOutputStream(target), 256 * 1024).use { out ->
                val converted = ByteBuffer.allocate(1024 * 1024).order(ByteOrder.LITTLE_ENDIAN)
                fun write(buffer: ByteBuffer) {
                    buffer.order(ByteOrder.LITTLE_ENDIAN)
                    val size = when (encoding) {
                        AudioFormat.ENCODING_PCM_FLOAT, AudioFormat.ENCODING_PCM_32BIT -> 4
                        AudioFormat.ENCODING_PCM_24BIT_PACKED -> 3
                        AudioFormat.ENCODING_PCM_8BIT -> 1
                        AudioFormat.ENCODING_PCM_16BIT -> 2
                        else -> error("Formato PCM não suportado pelo decoder.")
                    }
                    require(buffer.remaining() % (channels * size) == 0) { "O decoder retornou áudio incompleto." }
                    fun sample(): Float = when (encoding) {
                        AudioFormat.ENCODING_PCM_FLOAT -> buffer.float.let { if (it.isFinite()) it.coerceIn(-1f, 1f) else 0f }
                        AudioFormat.ENCODING_PCM_32BIT -> buffer.int / 2147483648f
                        AudioFormat.ENCODING_PCM_24BIT_PACKED -> {
                            val value = (buffer.get().toInt() and 255) or ((buffer.get().toInt() and 255) shl 8) or (buffer.get().toInt() shl 16)
                            value / 8388608f
                        }
                        AudioFormat.ENCODING_PCM_8BIT -> ((buffer.get().toInt() and 255) - 128) / 128f
                        else -> buffer.short / 32768f
                    }
                    converted.clear()
                    while (buffer.remaining() >= channels * size) {
                        val left = sample(); val right = if (channels == 2) sample() else left
                        converted.putFloat(left); converted.putFloat(right); frames++
                        if (converted.remaining() < 8) { out.write(converted.array(), 0, converted.position()); converted.clear() }
                    }
                    out.write(converted.array(), 0, converted.position())
                    if (target.parentFile!!.usableSpace < 32L * 1024 * 1024) error("Espaço insuficiente. Libere armazenamento e tente novamente.")
                }
                if (mime == "audio/raw") {
                    val buffer = ByteBuffer.allocate(1024 * 1024)
                    while (true) {
                        coroutineContext.ensureActive()
                        buffer.clear()
                        val size = extractor.readSampleData(buffer, 0)
                        if (size < 0) break
                        buffer.position(0); buffer.limit(size); write(buffer); extractor.advance()
                    }
                } else {
                    val codec = MediaCodec.createDecoderByType(mime)
                    decoder = codec
                    codec.configure(format, null, null, 0); codec.start()
                    var inputDone = false; var outputDone = false
                    var lastProgress = System.nanoTime()
                    val info = MediaCodec.BufferInfo()
                    while (!outputDone) {
                        coroutineContext.ensureActive()
                        if (!inputDone) {
                            val index = codec.dequeueInputBuffer(10000)
                            if (index >= 0) {
                                val buffer = codec.getInputBuffer(index)!!
                                val size = extractor.readSampleData(buffer, 0)
                                if (size < 0) { codec.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM); inputDone = true }
                                else { codec.queueInputBuffer(index, 0, size, extractor.sampleTime, 0); extractor.advance() }
                            }
                        }
                        when (val index = codec.dequeueOutputBuffer(info, 10000)) {
                            MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                                val decoded = codec.outputFormat
                                rate = decoded.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                                channels = decoded.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                                require(channels in 1..2) { "O decoder produziu mais de dois canais." }
                                encoding = if (decoded.containsKey(MediaFormat.KEY_PCM_ENCODING)) decoded.getInteger(MediaFormat.KEY_PCM_ENCODING) else AudioFormat.ENCODING_PCM_16BIT
                            }
                            else -> if (index >= 0) {
                                val buffer = codec.getOutputBuffer(index)!!
                                buffer.position(info.offset); buffer.limit(info.offset + info.size)
                                write(buffer)
                                outputDone = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                                codec.releaseOutputBuffer(index, false)
                                lastProgress = System.nanoTime()
                            }
                        }
                        check(System.nanoTime() - lastProgress < 15_000_000_000L) { "O decoder parou de responder. Verifique o arquivo ${name(uri)}." }
                    }
                }
            }
            return rate to frames
        } finally { runCatching { decoder?.stop() }; decoder?.release(); extractor.release() }
    }
}

