package com.atmytrack.app

import android.graphics.*
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.junit.Assert.*
import java.io.File

class BrandAssetsTest {
    @Test fun exportDensitiesAndVerifyAdaptiveSafeZone() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val folder=File(context.getExternalFilesDir(null),"brand-0.21.0").apply { mkdirs() }
        fun save(bitmap:Bitmap,name:String) { File(folder,name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) };bitmap.recycle() }
        for(size in listOf(48,72,96,144,192,512)) {
            val bitmap=Bitmap.createBitmap(size,size,Bitmap.Config.ARGB_8888);val canvas=Canvas(bitmap)
            context.getDrawable(R.drawable.logo)!!.apply { setBounds(0,0,size,size);draw(canvas) }
            assertEquals("Transparent corner at $size",0,Color.alpha(bitmap.getPixel(0,0)))
            assertEquals("No square plate at $size",0,Color.alpha(bitmap.getPixel(size/8,size/8)))
            for((x,y) in listOf(size/2 to size*6/100,size/2 to size*94/100,size*6/100 to size/2,size*94/100 to size/2)) {
                assertTrue("Circular rim fills icon at $size",Color.alpha(bitmap.getPixel(x,y))>128)
            }
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
            canvas.clipPath(mask)
            context.getDrawable(R.drawable.logo)!!.apply { setBounds(0,0,512,512);draw(canvas) }
            save(bitmap,if(round)"circle.png" else "rounded.png")
        }
        val adaptive=Bitmap.createBitmap(512,512,Bitmap.Config.ARGB_8888)
        context.getDrawable(R.mipmap.ic_launcher)!!.apply { setBounds(0,0,512,512);draw(Canvas(adaptive)) }
        save(adaptive,"adaptive.png")
        val round=Bitmap.createBitmap(512,512,Bitmap.Config.ARGB_8888)
        context.getDrawable(R.mipmap.ic_launcher_round)!!.apply { setBounds(0,0,512,512);draw(Canvas(round)) }
        assertEquals(0,Color.alpha(round.getPixel(0,0)))
        assertTrue(Color.alpha(round.getPixel(256,32))>128)
        save(round,"adaptive-round.png")

    }
}
