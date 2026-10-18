package com.atmytrack.app.separation

import android.app.Service
import android.content.Intent
import android.net.Uri
import android.os.*
import kotlinx.coroutines.*
import java.io.File
import java.util.UUID

/** Bound, private second process: native faults cannot take down the live mixer process. */
class SeparationService:Service() {
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    private var job:Job?=null
    private var reply:Messenger?=null
    private val root get()=File(filesDir,"separation").apply { mkdirs() }
    private val lock get()=File(root,"processing.lock")
    private var installed=false
    private var lastSent=0L
    private fun event(text:String="",percent:Int=-1,busy:Boolean=false,dir:String="",error:String="") {
        runCatching { reply?.send(Message.obtain(null,10).apply { data=Bundle().apply {
            putString("text",text);putInt("percent",percent);putBoolean("busy",busy);putBoolean("installed",installed);putString("dir",dir);putString("error",error)
        } }) }
    }
    override fun onCreate() {
        super.onCreate();lock.delete()
        File(root,"results").listFiles()?.filter { it.isDirectory && !File(it,"complete").exists() }?.forEach { it.deleteRecursively() }
    }
    private val messenger=Messenger(Handler(Looper.getMainLooper()) { message ->
        if(message.what==4) { job?.cancel();event("Cancelando; aguardando o bloco atual da IA…",busy=true);return@Handler true }
        if(job?.isActive==true)return@Handler true
        reply=message.replyTo
        val action=message.what
        val uri=message.data.getString("uri");val title=message.data.getString("title") ?: "Música"
        job=scope.launch {
            var output:File?=null
            try {
                val models=ModelStore(File(filesDir,"models/${ModelStore.VERSION}"))
                fun check() { ensureActive() }
                event("Verificando integridade do modelo…",busy=true)
                installed=models.installed(::check)
                when(action) {
                    2 -> {
                        models.install(::check) { bytes,total ->
                            val now=SystemClock.elapsedRealtime()
                            if(now-lastSent>200 || bytes==total) { lastSent=now;event("Baixando modelo • $bytes / $total bytes",(bytes*100/total).toInt(),true) }
                        };installed=true
                    }
                    3 -> {
                        require(installed) { "Baixe o modelo gratuito antes de separar." }
                        require(Process.is64Bit()) { "A separação requer Android de 64 bits. O mixer continua compatível com 32 bits." }
                        val memory=android.app.ActivityManager.MemoryInfo()
                        getSystemService(android.app.ActivityManager::class.java).getMemoryInfo(memory)
                        require(memory.totalMem>=3L*1024*1048576 && memory.availMem>=900L*1048576 && !memory.lowMemory) { "Memória insuficiente para a IA. Feche outros aplicativos; recomendado Android 64 bits com 4 GB de RAM ou mais." }
                        output=File(root,"results/${UUID.randomUUID()}").apply { mkdirs();File(this,"title.txt").writeText(title.take(200)) }
                        val pendingLock=File(root,"processing.lock.part")
                        pendingLock.writeText(Process.myPid().toString())
                        kotlin.check(pendingLock.renameTo(lock)) { "Não foi possível bloquear a reprodução durante a IA." }
                        val start=SystemClock.elapsedRealtime()
                        SeparationEngine(this@SeparationService).run(Uri.parse(requireNotNull(uri)),output,::check) { text,percent ->
                            val now=SystemClock.elapsedRealtime()
                            if(now-lastSent>200 || percent==100) { lastSent=now;event(text,percent,true) }
                        }
                        File(output,"metrics.txt").writeText("elapsedMs=${SystemClock.elapsedRealtime()-start}\nnativeHeapBytes=${Debug.getNativeHeapAllocatedSize()}\nmodel=${ModelStore.VERSION}\ndevice=${Build.MANUFACTURER} ${Build.MODEL}\nabi=${Build.SUPPORTED_ABIS.joinToString()}\nsdk=${Build.VERSION.SDK_INT}\n")
                        event("Separação concluída",100,dir=output.name)
                        output=null
                        return@launch
                    }
                }
                val recovered=File(root,"results").listFiles()?.filter { File(it,"complete").isFile && !File(it,"consumed").exists() }?.maxByOrNull { it.lastModified() }
                event(if(installed)"Modelo verificado • disponível offline" else "Modelo ausente ou inválido. Use BAIXAR MODELO para instalar ou reparar.",dir=recovered?.name ?: "")
            } catch(e:CancellationException) { event("Operação cancelada. Você pode tentar novamente.") }
            catch(e:Throwable) { event(error=if(e is OutOfMemoryError)"A IA excedeu a memória disponível. Feche outros apps e tente novamente." else e.message ?: "Falha na separação.") }
            finally { lock.delete();output?.deleteRecursively() }
        }
        true
    })
    override fun onBind(intent:Intent?)=messenger.binder
    override fun onUnbind(intent:Intent?):Boolean { job?.cancel();return false }
    override fun onDestroy() {
        scope.cancel();lock.delete();super.onDestroy()
        // This service is declared exclusively in :separation. Release native allocations too.
        Process.killProcess(Process.myPid())
    }
}
