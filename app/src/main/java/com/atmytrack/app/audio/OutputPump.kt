package com.atmytrack.app.audio

import android.media.AudioTrack
import android.os.Process
import java.util.concurrent.locks.LockSupport

/** Single producer/single consumer ring. The output thread only copies prepared
 * blocks to AudioTrack: no file/decoder/SAF work, mutex, future or allocation.
 * Eight 512-frame blocks = 85 ms at 48 kHz, independent of the stem count. */
internal class OutputPump(private val track:AudioTrack, private val channels:Int,private val producer:Thread) : AutoCloseable {
    private val blocks=Array(8) { FloatArray(512*channels) }
    private val sizes=IntArray(8)
    @Volatile private var read=0L
    @Volatile private var write=0L
    @Volatile private var running=true
    @Volatile private var active=false
    @Volatile private var writing=false
    @Volatile var error:String?=null; private set
    @Volatile var starvation=0L; private set
    @Volatile var producerWaits=0L; private set
    @Volatile private var submittedFrames=0L
    private var headBase=0L
    val full get()=write-read>=blocks.size
    val queued get()=(write-read).toInt()
    private val worker=Thread({
        Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
        var emptySince=0L
        var reported=false
        var starved=false
        while(running) {
            if(!active) { LockSupport.parkNanos(1_000_000);continue }
            writing=true
            if(!active) { writing=false;continue }
            if(read==write) {
                val now=System.nanoTime()
                if(emptySince==0L)emptySince=now
                // Brief queue-empty races are covered by AudioTrack's reserve.
                // Count a producer deadline miss only after one complete block.
                if(now-emptySince>512_000_000_000L/track.sampleRate) {
                    if(!reported) { producerWaits++;reported=true }
                    val consumed=((track.playbackHeadPosition.toLong() and 0xffffffffL)-headBase) and 0xffffffffL
                    if(!starved && submittedFrames-consumed<=512) { starvation++;starved=true }
                }
                writing=false;LockSupport.parkNanos(500_000);continue
            }
            emptySince=0L;reported=false;starved=false
            val slot=(read%blocks.size).toInt()
            var offset=0
            while(active && offset<sizes[slot]) {
                val n=try { track.write(blocks[slot],offset,sizes[slot]-offset,AudioTrack.WRITE_BLOCKING) }
                    catch(e:Exception) { error=e.message ?: "Saída indisponível";active=false;break }
                if(n<=0) { if(active)error="Falha na saída de áudio ($n)";active=false;break }
                offset+=n;submittedFrames+=n/channels
            }
            if(active) { read++;LockSupport.unpark(producer) }
            writing=false
        }
    },"ATMyTrack-Output").apply { isDaemon=true;start() }
    fun offer(samples:FloatArray,count:Int):Boolean {
        if(full)return false
        val slot=(write%blocks.size).toInt()
        samples.copyInto(blocks[slot],endIndex=count*channels)
        sizes[slot]=count*channels
        write++
        LockSupport.unpark(worker)
        return true
    }
    /** Producer-only, while paused. Fill the actual hardware reserve before
     * starting its clock; partial nonblocking writes retain the exact remainder. */
    fun prefill():Int {
        check(!active)
        var total=0
        while(read!=write) {
            val slot=(read%blocks.size).toInt()
            val n=track.write(blocks[slot],0,sizes[slot],AudioTrack.WRITE_NON_BLOCKING)
            check(n>=0) { "Não foi possível preparar a saída ($n)" }
            if(n==0)break
            total+=n;submittedFrames+=n/channels
            if(n==sizes[slot])read++ else {
                blocks[slot].copyInto(blocks[slot],0,n,sizes[slot]);sizes[slot]-=n
            }
        }
        return total
    }
    fun start() { track.play();active=true;LockSupport.unpark(worker) }
    fun reset() {
        active=false
        track.pause() // Also releases a blocking write when the device is paused.
        while(writing)LockSupport.parkNanos(100_000)
        track.flush();read=0;write=0;submittedFrames=0;headBase=track.playbackHeadPosition.toLong() and 0xffffffffL;error=null
    }
    override fun close() { reset();running=false;LockSupport.unpark(worker);worker.join(2000);track.release() }
}
