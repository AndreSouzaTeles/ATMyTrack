package com.atmytrack.app

import android.graphics.*
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.junit.Assert.*
import java.io.File

class BrandAssetsTest {
    @Test fun exportDensitiesAndVerifyAdaptiveSafeZone() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val folder=File(context.getExternalFilesDir(null),"brand-0.6").apply { mkdirs() }
        fun save(bitmap:Bitmap,name:String) { File(folder,name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) };bitmap.recycle() }
        for(size in listOf(48,72,96,144,192,512)) {
            val bitmap=Bitmap.createBitmap(size,size,Bitmap.Config.ARGB_8888);val canvas=Canvas(bitmap)
            canvas.drawRoundRect(0f,0f,size.toFloat(),size.toFloat(),size*.22f,size*.22f,Paint(Paint.ANTI_ALIAS_FLAG).apply { color=Color.rgb(11,13,18) })
            context.getDrawable(R.drawable.ic_launcher_foreground)!!.apply { setBounds(0,0,size,size);draw(canvas) }
            save(bitmap,"icon-$size.png")
        }
        for((id,name) in listOf(R.drawable.ic_launcher_foreground to "foreground",R.drawable.logo to "symbol")) {
            val bitmap=Bitmap.createBitmap(512,512,Bitmap.Config.ARGB_8888)
            context.getDrawable(id)!!.apply { setBounds(0,0,512,512);draw(Canvas(bitmap)) }
            assertEquals(512,bitmap.width)
            assertEquals(512,bitmap.height)
            save(bitmap,"$name.png")
        }
        for(round in listOf(true,false)) {
            val bitmap=Bitmap.createBitmap(512,512,Bitmap.Config.ARGB_8888); val canvas=Canvas(bitmap)
            val mask=Path().apply { if(round)addCircle(256f,256f,256f,Path.Direction.CW) else addRoundRect(0f,0f,512f,512f,110f,110f,Path.Direction.CW) }
            canvas.clipPath(mask);canvas.drawColor(Color.rgb(11,13,18))
            context.getDrawable(R.drawable.ic_launcher_foreground)!!.apply { setBounds(0,0,512,512);draw(canvas) }
            save(bitmap,if(round)"circle.png" else "rounded.png")
        }
    }
}
