package com.atmytrack.app.ui

import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.atmytrack.app.PlayerViewModel
import com.atmytrack.app.audio.AudioImporter
import com.atmytrack.app.separation.ModelStore
import kotlinx.coroutines.*
import java.io.File

@Composable internal fun SeparationDialog(vm:PlayerViewModel,close:()->Unit) {
    val state by vm.separation.state.collectAsState()
    val library by vm.library.collectAsState()
    val context=LocalContext.current
    val view=LocalView.current
    val owner=LocalLifecycleOwner.current
    val scope=rememberCoroutineScope()
    var uriText by rememberSaveable { mutableStateOf("") }
    var title by rememberSaveable { mutableStateOf("") }
    var confirmDownload by remember { mutableStateOf(false) }
    var preview by remember { mutableStateOf<MediaPlayer?>(null) }
    var previewName by remember { mutableStateOf("") }
    var exportName by remember { mutableStateOf("") }
    var localMessage by remember { mutableStateOf("") }
    var exporting by remember { mutableStateOf(false) }
    val busy=state.busy || exporting || library.busy!=null
    val result=state.result.takeIf { it.matches(Regex("[a-f0-9-]{36}")) }?.let { File(context.filesDir,"separation/results/$it") }
    fun stopPreview() { preview?.release();preview=null;previewName="" }
    val choose=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if(uri!=null) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            uriText=uri.toString()
            scope.launch { title=withContext(Dispatchers.IO) { runCatching { AudioImporter(context).name(uri).substringBeforeLast('.') }.getOrDefault("Música") } }
        }
    }
    val export=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("audio/wav")) { uri ->
        if(uri!=null && result!=null)scope.launch {
            exporting=true
            try {
                withContext(Dispatchers.IO) {
                    val out=context.contentResolver.openOutputStream(uri,"wt") ?: error("Destino indisponível")
                    out.use { target -> File(result,"$exportName.wav").inputStream().use { it.copyTo(target,262144) } }
                }
                localMessage="$exportName exportado em WAV."
            } catch(e:Exception) { localMessage="Falha ao exportar: ${e.message}" }
            finally { exporting=false }
        }
    }
    DisposableEffect(Unit) {
        vm.separation.open()
        val observer=LifecycleEventObserver { _,event -> if(event==Lifecycle.Event.ON_STOP) { stopPreview();if(vm.separation.state.value.busy)vm.separation.command(4) } }
        owner.lifecycle.addObserver(observer)
        onDispose { stopPreview();view.keepScreenOn=false;owner.lifecycle.removeObserver(observer);vm.separation.close() }
    }
    LaunchedEffect(state.busy) { view.keepScreenOn=state.busy;if(state.busy)stopPreview() }
    AlertDialog(onDismissRequest={if(!busy)close()},title={Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text("Separar Instrumentos")
        if(state.busy) {
            Text(state.text,color=SecondaryText,style=MaterialTheme.typography.bodyMedium)
            if(state.percent>=0)Text("${state.percent}% concluído nesta etapa",color=SecondaryText,style=MaterialTheme.typography.titleMedium)
            if(state.percent>=0)LinearProgressIndicator(progress={state.percent/100f},modifier=Modifier.fillMaxWidth()) else LinearProgressIndicator(Modifier.fillMaxWidth())
        }
    }},text={
        Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text("Voz + Instrumental",color=SecondaryText)
            Text("IA local gratuita • Spleeter 2 stems. Após instalar o modelo, funciona offline. Sua música não é enviada para servidores.")
            Text("Recurso experimental: pode haver vazamento entre voz e instrumentos. Requer Android 64 bits e memória livre; recomendado 4 GB de RAM ou mais. Não separa bateria e baixo individualmente.",color=Muted)
            Text(if(state.installed)"Modelo instalado e verificado" else "Download inicial: ${ModelStore.downloadBytes} bytes (37,54 MiB).",color=SecondaryText)
            OutlinedButton(onClick={stopPreview();confirmDownload=true},enabled=state.connected && !busy) { Text(if(state.installed)"VERIFICAR / REPARAR MODELO" else "BAIXAR MODELO GRATUITO") }
            OutlinedButton(onClick={stopPreview();choose.launch(arrayOf("audio/*"))},enabled=!busy) { Text("ESCOLHER MÚSICA") }
            if(title.isNotBlank())Text(title,color=SecondaryText)
            Text("Mantenha esta tela aberta. Ao sair do aplicativo, a operação é cancelada após o bloco atual. Reprodução ao vivo fica bloqueada durante a separação. A duração depende da música e do aparelho.",color=Muted)
            Text("Espaço estimado: 60,6 MiB por minuto de música + 64 MiB de reserva; o app verifica a duração e o espaço antes de processar.",color=Muted)
            Button(onClick={stopPreview();localMessage="";vm.startSeparation(Uri.parse(uriText),title)},enabled=state.connected && state.installed && uriText.isNotBlank() && !busy && result==null) { Text("INICIAR SEPARAÇÃO") }
            if(!state.busy)Text(state.text,color=SecondaryText)
            if(state.error.isNotBlank())Text(state.error,color=Red)
            if(localMessage.isNotBlank())Text(localMessage)
            if(result!=null && !state.busy) {
                Text("Concluído • WAV estéreo 44,1 kHz / float32, com duração e alinhamento comuns.",color=SecondaryText)
                for(name in listOf("Voz","Instrumental")) {
                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick={
                            if(previewName==name)stopPreview() else {
                                stopPreview()
                                val player=MediaPlayer()
                                try {
                                    player.setDataSource(File(result,"$name.wav").path);player.setVolume(.5f,.5f)
                                    player.setOnCompletionListener { stopPreview() }
                                    player.setOnErrorListener { _,_,_ -> localMessage="Prévia indisponível neste aparelho. Importe no mixer ou exporte o WAV.";stopPreview();true }
                                    player.setOnPreparedListener { it.start() };preview=player;previewName=name;player.prepareAsync()
                                } catch(e:Exception) { player.release();localMessage="Prévia indisponível: ${e.message}" }
                            }
                        },enabled=!busy,modifier=Modifier.weight(1f)) { Text(if(previewName==name)"PARAR $name" else "OUVIR $name") }
                        TextButton(onClick={exportName=name;export.launch("${title.ifBlank { "ATMyTrack" }}-$name.wav")},enabled=!busy) { Text("WAV") }
                    }
                }
                Button(onClick={stopPreview();vm.importSeparation(false);close()},enabled=!busy) { Text("IMPORTAR EM NOVO PROJETO") }
                OutlinedButton(onClick={stopPreview();vm.importSeparation(true)},enabled=!busy && library.current!=null) { Text("ADICIONAR AO PROJETO ATUAL") }
                Text("No projeto atual, as faixas entram no início da timeline. Use tom original e velocidade 100%. Mute tracks da música original para evitar soma de volume.",color=Muted)
                TextButton(onClick={stopPreview();vm.discardSeparation()},enabled=!busy) { Text("DESCARTAR RESULTADO E SEPARAR OUTRA") }
            }
        }
    },confirmButton={TextButton(onClick={stopPreview();close()},enabled=!busy){Text("FECHAR")}},dismissButton={if(state.busy)TextButton(onClick={vm.separation.command(4)}){Text("CANCELAR")}})
    if(confirmDownload)AlertDialog(onDismissRequest={confirmDownload=false},title={Text("Baixar modelo gratuito?")},text={Text("Serão verificados/baixados ${ModelStore.downloadBytes} bytes de pesos Spleeter (MIT), hospedados pelo mantenedor da conversão ONNX no Hugging Face. São necessários 102 MiB livres. Nenhum áudio será enviado. O download pode usar sua franquia de dados.")},confirmButton={TextButton(onClick={confirmDownload=false;vm.separation.command(2)}){Text("CONFIRMAR DOWNLOAD")}},dismissButton={TextButton(onClick={confirmDownload=false}){Text("CANCELAR")}})
}
