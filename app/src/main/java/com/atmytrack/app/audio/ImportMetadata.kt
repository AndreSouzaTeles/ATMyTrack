package com.atmytrack.app.audio

import android.content.Context
import android.graphics.*
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import java.io.File

class ImportMetadata(private val context:Context) {
    private val resolver=context.contentResolver
    private fun display(uri:Uri):String?=runCatching { resolver.query(uri,arrayOf(OpenableColumns.DISPLAY_NAME),null,null,null)?.use { if(it.moveToFirst())it.getString(0) else null } }.getOrNull()
    fun folderName(files:List<Uri>,tree:Uri?):String {
        if(tree!=null)display(DocumentsContract.buildDocumentUriUsingTree(tree,DocumentsContract.getTreeDocumentId(tree)))?.takeIf { it.isNotBlank() }?.let { return it }
        val parents=files.map(::parentName).distinct()
        return if(parents.size==1 && parents.single()!=null)parents.single()!! else files.firstOrNull()?.let { (display(it) ?: it.lastPathSegment)?.substringBeforeLast('.') } ?: "Novo projeto"
    }
    private fun parentName(uri:Uri):String? {
        if(uri.scheme=="file")return File(uri.path ?: return null).parentFile?.name
        // ExternalStorageProvider exposes a path in the document ID; opaque providers do not.
        if(uri.authority=="com.android.externalstorage.documents") {
            val id=runCatching { DocumentsContract.getDocumentId(uri) }.getOrNull() ?: return null
            val path=id.substringAfter(':',"")
            if('/' in path)return path.substringBeforeLast('/').substringAfterLast('/').takeIf { it.isNotBlank() }
        }
        if(Build.VERSION.SDK_INT>=29)return runCatching { resolver.query(uri,arrayOf(MediaStore.MediaColumns.RELATIVE_PATH),null,null,null)?.use { if(it.moveToFirst())it.getString(0)?.trimEnd('/')?.substringAfterLast('/') else null } }.getOrNull()
        return null
    }
    fun images(tree:Uri):List<Uri> {
        val children=DocumentsContract.buildChildDocumentsUriUsingTree(tree,DocumentsContract.getTreeDocumentId(tree))
        return resolver.query(children,arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID,DocumentsContract.Document.COLUMN_DISPLAY_NAME,DocumentsContract.Document.COLUMN_MIME_TYPE),null,null,null)?.use { cursor ->
            buildList { while(cursor.moveToNext()) { val name=cursor.getString(1);val mime=cursor.getString(2) ?: ""
                if(mime.startsWith("image/") || name.substringAfterLast('.',"").lowercase() in setOf("jpg","jpeg","png","webp","gif","bmp","heic","heif","avif","tif","tiff","svg","ico"))add(DocumentsContract.buildDocumentUriUsingTree(tree,cursor.getString(0)))
            } }
        } ?: emptyList()
    }
    fun randomArtwork(tree:Uri,id:String):String {
        for(uri in images(tree).shuffled()) {
            try { return saveArtwork(uri,id) } catch(e:Exception) { android.util.Log.w("ATMyTrack","Imagem da pasta não suportada pelo dispositivo",e) }
        }
        return ""
    }
    fun saveArtwork(uri:Uri,id:String):String {
        val bitmap=if(Build.VERSION.SDK_INT>=28)ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver,uri)) { decoder,info,_ ->
            val scale=800.0/maxOf(info.size.width,info.size.height).coerceAtLeast(800)
            decoder.setTargetSize((info.size.width*scale).toInt().coerceAtLeast(1),(info.size.height*scale).toInt().coerceAtLeast(1));decoder.allocator=ImageDecoder.ALLOCATOR_SOFTWARE
        } else {
            val bounds=BitmapFactory.Options().apply { inJustDecodeBounds=true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it,null,bounds) }
            val options=BitmapFactory.Options().apply { inSampleSize=maxOf(1,maxOf(bounds.outWidth,bounds.outHeight)/800) }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it,null,options) } ?: error("Imagem não reconhecida pelo Android.")
        }
        val path="artwork/$id-${java.util.UUID.randomUUID()}.jpg";val file=File(context.filesDir,path);file.parentFile!!.mkdirs()
        try { file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.JPEG,85,it)) };return path } finally { bitmap.recycle() }
    }
}
