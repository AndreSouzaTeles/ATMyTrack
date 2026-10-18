package com.atmytrack.app.separation

import android.content.*
import android.os.*
import kotlinx.coroutines.flow.*
import java.io.File

data class SeparationState(val connected:Boolean=false,val busy:Boolean=false,val installed:Boolean=false,val text:String="Conectando…",val percent:Int=-1,val result:String="",val error:String="")

class SeparationController(private val context:Context) {
    private val mutable=MutableStateFlow(SeparationState())
    val state=mutable.asStateFlow()
    private var remote:Messenger?=null
    private var bound=false
    private val receiver=Messenger(Handler(Looper.getMainLooper()) { msg ->
        val d=msg.data
        if(!d.getBoolean("busy"))SeparationLock.requested=false
        mutable.update { it.copy(connected=true,busy=d.getBoolean("busy"),installed=d.getBoolean("installed"),text=d.getString("text") ?: "",percent=d.getInt("percent",-1),result=d.getString("dir")?.ifBlank { it.result } ?: it.result,error=d.getString("error") ?: "") };true
    })
    private val connection=object:ServiceConnection {
        override fun onServiceConnected(name:ComponentName,binder:IBinder) { remote=Messenger(binder);command(1) }
        override fun onServiceDisconnected(name:ComponentName) { failed() }
        override fun onBindingDied(name:ComponentName) { failed() }
        private fun failed() {
            SeparationLock.requested=false
            remote=null;File(context.filesDir,"separation/processing.lock").delete()
            mutable.update { it.copy(connected=false,busy=false,error="O Android encerrou o processo da IA. Seus projetos foram preservados. Feche esta tela, libere memória e tente novamente.") }
        }
    }
    fun open() {
        if(bound && remote==null) { context.unbindService(connection);bound=false }
        if(!bound)bound=context.bindService(Intent(context,SeparationService::class.java),connection,Context.BIND_AUTO_CREATE)
        else if(!state.value.busy)command(1)
        if(!bound)mutable.value=SeparationState(error="Não foi possível iniciar o separador.")
    }
    fun command(what:Int,uri:String="",title:String="") {
        val service=remote ?: return
        if(what!=4)mutable.update { it.copy(busy=true,error="",percent=-1) }
        try { service.send(Message.obtain(null,what).apply { replyTo=receiver;data=Bundle().apply { putString("uri",uri);putString("title",title) } }) }
        catch(e:RemoteException) { SeparationLock.requested=false;mutable.update { it.copy(busy=false,error="Serviço de IA indisponível. Abra a tela novamente.") } }
    }
    fun consumed() { mutable.update { it.copy(result="") } }
    fun close() { SeparationLock.requested=false;if(bound) { if(state.value.busy)command(4);context.unbindService(connection);bound=false;remote=null } }
}
