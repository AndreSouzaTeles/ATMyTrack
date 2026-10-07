package com.atmytrack.app.audio

import android.content.Context
import android.net.Uri
import java.io.*
import java.nio.ByteBuffer
import java.nio.channels.FileChannel

class SeekInput private constructor(private val stream: FileInputStream, private val origin: Long, private val size: Long, private val owner: Closeable? = null) : Closeable {
    private val channel: FileChannel = stream.channel
    private val small = ByteArray(4)
    val filePointer get() = channel.position() - origin
    fun length() = size
    fun seek(position: Long) { require(position >= 0); channel.position(origin + position) }
    fun readFully(bytes: ByteArray, offset: Int = 0, count: Int = bytes.size) {
        require(filePointer + count <= size) { "Arquivo de áudio truncado." }
        val b = ByteBuffer.wrap(bytes,offset,count)
        while(b.hasRemaining()) if(channel.read(b)<0) throw EOFException("Arquivo de áudio incompleto.")
    }
    fun readShort(): Short { readFully(small,0,2); return ((small[0].toInt() shl 8) or (small[1].toInt() and 255)).toShort() }
    fun readInt(): Int { readFully(small); return ByteBuffer.wrap(small).int }
    override fun close() { runCatching { stream.close() }; owner?.close() }
    companion object {
        fun file(file: File) = SeekInput(FileInputStream(file),0,file.length())
        fun uri(context: Context, uri: Uri): SeekInput {
            if(uri.scheme == "file") return file(File(uri.path!!))
            val fd=context.contentResolver.openAssetFileDescriptor(uri,"r") ?: error("Origem inacessível.")
            try {
                val stream=FileInputStream(fd.fileDescriptor)
                val length=if(fd.declaredLength>=0)fd.declaredLength else stream.channel.size()-fd.startOffset
                require(length>0) { "Origem não permite leitura direta." }
                stream.channel.position(fd.startOffset)
                return SeekInput(stream,fd.startOffset,length,fd)
            } catch(e: Exception) { fd.close(); throw e }
        }
    }
}

interface FrameReader : AutoCloseable { fun read(frame: Long, count: Int, result: FloatArray) }
