package com.atmytrack.app.separation

/** Model input shape and 25% overlap; left context avoids a zero-weight first sample. */
object DemucsChunks {
    const val SAMPLES=343980
    const val OVERLAP=SAMPLES/4
    fun weight(i:Int):Float = when {
        i<OVERLAP -> i.toFloat()/(OVERLAP-1)
        i>=SAMPLES-OVERLAP -> (SAMPLES-1-i).toFloat()/(OVERLAP-1)
        else -> 1f
    }
}
