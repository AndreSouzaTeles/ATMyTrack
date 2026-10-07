package com.atmytrack.app.audio

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.provider.DocumentsContract
import java.io.File
import java.security.MessageDigest

object SourceFingerprint {
    fun get(context:Context,uri:Uri):String {
        var size=-1L; var modified=-1L
        if(uri.scheme=="file") { val f=File(uri.path!!); size=f.length(); modified=f.lastModified() }
        else runCatching { context.contentResolver.query(uri,arrayOf(OpenableColumns.SIZE,DocumentsContract.Document.COLUMN_LAST_MODIFIED),null,null,null)?.use {
            if(it.moveToFirst()) { if(!it.isNull(0))size=it.getLong(0); if(!it.isNull(1))modified=it.getLong(1) }
        } }
        val digest=MessageDigest.getInstance("SHA-256")
        digest.update("$uri:$size:$modified".toByteArray())
        // Small head/tail samples detect common replacements even when the provider omits timestamps.
        SeekInput.uri(context,uri).use { input ->
            val n=minOf(4096L,input.length()).toInt(); val bytes=ByteArray(n)
            input.readFully(bytes); digest.update(bytes)
            input.seek((input.length()-n).coerceAtLeast(0)); input.readFully(bytes); digest.update(bytes)
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
