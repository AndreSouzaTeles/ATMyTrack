package com.atmytrack.app.audio

/** One 32 KiB disk read serves eight mixer blocks; only the producer calls this. */
class ReadAheadReader(private val source:FrameReader):FrameReader {
    private val buffer=FloatArray(8192)
    private var start=-4096L
    override fun read(frame:Long,count:Int,result:FloatArray) {
        var copied=0
        while(copied<count) {
            val position=frame+copied
            if(position<start || position>=start+4096) { start=position/4096*4096;source.read(start,4096,buffer) }
            val n=minOf(count-copied,(start+4096-position).toInt())
            buffer.copyInto(result,copied*2,((position-start)*2).toInt(),((position-start+n)*2).toInt())
            copied+=n
        }
    }
    override fun close()=source.close()
}
