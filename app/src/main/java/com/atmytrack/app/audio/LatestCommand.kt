package com.atmytrack.app.audio

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/** One queued/running operation and one replaceable target; never replay pointer history. */
internal class LatestCommand<T:Any>(private val enqueue:(()->Unit)->Unit,private val apply:(T)->Unit) {
    private val pending=AtomicReference<T?>(null)
    private val scheduled=AtomicBoolean(false)
    fun offer(value:T) { pending.set(value);schedule() }
    fun clear() { pending.set(null) }
    private fun schedule() {
        if(!scheduled.compareAndSet(false,true))return
        enqueue {
            try { pending.getAndSet(null)?.let(apply) }
            finally { scheduled.set(false);if(pending.get()!=null)schedule() }
        }
    }
}
