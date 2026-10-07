package com.atmytrack.app.audio

import java.util.concurrent.Executors
import java.util.concurrent.Future
import android.os.Process

/** Bounded decoder lookahead. Two reusable 128 KiB blocks per compressed stem.
 * Only the mixer consumes it; a single in-flight task owns the underlying decoder.
 * A shared pool caps decoder concurrency instead of creating one thread per stem. */
class BufferedFrameReader(private val source:FrameReader,private val frames:Long):FrameReader {
    private data class Block(val start:Long,val samples:FloatArray)
    private val spare=ArrayDeque<FloatArray>().apply { add(FloatArray(BLOCK*2));add(FloatArray(BLOCK*2)) }
    private var current:Block?=null
    private var queuedStart=0L
    private var queued:Future<Block>?=submit(0)
    private var closed=false
    private fun submit(start:Long):Future<Block> {
        val data=spare.removeFirst()
        return workers.submit<Block> { source.read(start,BLOCK,data);Block(start,data) }
    }
    override fun read(frame:Long,count:Int,result:FloatArray) {
        check(!closed);result.fill(0f)
        var position=frame;var offset=0
        val needed=minOf(count.toLong(),(frames-frame).coerceAtLeast(0)).toInt()
        while(offset<needed) {
            var block=current
            if(block==null || position<block.start || position>=block.start+BLOCK) {
                val start=position/BLOCK*BLOCK
                current?.let { spare.addLast(it.samples) };current=null
                if(queuedStart!=start) {
                    queued?.get()?.let { spare.addLast(it.samples) }
                    queuedStart=start;queued=submit(start)
                }
                block=queued!!.get();current=block;queued=null
                queuedStart=start+BLOCK
                if(queuedStart>=frames)queuedStart=0
                queued=submit(queuedStart)
            }
            val n=minOf(needed-offset,(block.start+BLOCK-position).toInt())
            block.samples.copyInto(result,offset*2,((position-block.start)*2).toInt(),((position-block.start+n)*2).toInt())
            offset+=n;position+=n
        }
    }
    override fun close() {
        if(closed)return;closed=true
        try { runCatching { queued?.get() } } finally { source.close();queued=null;current=null;spare.clear() }
    }
    companion object {
        private const val BLOCK=16384
        private val workers=Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors().coerceIn(2,4)) { task ->
            Thread({ Process.setThreadPriority(Process.THREAD_PRIORITY_MORE_FAVORABLE);task.run() },"ATMyTrack-Decode").apply { isDaemon=true }
        }
    }
}
