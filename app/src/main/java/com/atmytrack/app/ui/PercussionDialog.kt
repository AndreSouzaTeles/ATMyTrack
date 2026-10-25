package com.atmytrack.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.atmytrack.app.PlayerViewModel
import com.atmytrack.app.audio.PercussionSynth
import com.atmytrack.app.data.Project

@Composable internal fun PercussionDialog(vm:PlayerViewModel,project:Project?,close:()->Unit) {
    val engine=vm.rhythm;val state by engine.state.collectAsState();val p=state.percussion
    var bpm by remember(p.bpm) { mutableStateOf("%.1f".format(java.util.Locale.US,p.bpm)) }
    val parsed=bpm.replace(',','.').toDoubleOrNull();val valid=parsed!=null && parsed in 30.0..300.0
    val import=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(engine::importLoop) }
    val canPlay=!state.loading && !state.preparing && state.soundReady && state.error==null && (p.follow || valid) && (!p.follow || project!=null)
    AlertDialog(onDismissRequest=close,title={Text("Percussão")},text={
        Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text("Bateria acústica e percussão gravadas · variações em quatro compassos.",color=Muted,fontSize=12.sp)
            Row(verticalAlignment=Alignment.CenterVertically) {
                Checkbox(p.follow,{ follow -> engine.configure { it.copy(follow=follow) } },modifier=Modifier.semantics { contentDescription="Acompanhar música da percussão" })
                Text("Acompanhar música")
            }
            Text(if(p.follow)project?.let { "Segue a música · %.1f BPM · %d/%d".format(it.effectiveBpm,it.beats,it.denominator) } ?: "Selecione um projeto para acompanhar."
                else "Ritmo independente: toca mesmo sem multitrack, com a música pausada ou parada.",color=Muted,fontSize=12.sp)
            Choice("Batida",p.source,PercussionSynth.patterns.map { it to it }+state.imports.map { it.id to "Importado · ${it.name}" }) { source -> engine.configure { it.copy(source=source) } }
            if(!p.follow) {
                OutlinedTextField(bpm,{ bpm=it },label={Text("BPM • 30 a 300")},singleLine=true,isError=!valid,modifier=Modifier.semantics { contentDescription="BPM da percussão" })
                TextButton(enabled=valid,onClick={engine.configure { it.copy(bpm=parsed!!) }}) { Text("APLICAR BPM") }
                Text("Compasso",color=Muted)
                Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                    listOf(2 to 4,3 to 4,4 to 4,6 to 8,7 to 8).forEach { (beats,denominator) ->
                        SmallButton("$beats/$denominator",p.beats==beats && p.denominator==denominator,{engine.configure { it.copy(beats=beats,denominator=denominator) }})
                    }
                }
            }
            if(p.source !in PercussionSynth.patterns) {
                Choice("Compassos no áudio",p.bars.toString(),listOf(1,2,4,8,16,32,64).map { it.toString() to "$it ${if(it==1)"compasso" else "compassos"}" }) { bars -> engine.configure { it.copy(bars=bars.toInt()) } }
                Text("Informe quantos compassos o áudio contém. O loop se ajusta ao BPM e ao compasso, preservando a afinação.",color=Muted,fontSize=12.sp)
            }
            Text("Volume da percussão")
            Slider(p.volume,{v -> engine.configure { it.copy(volume=v) } },modifier=Modifier.semantics { contentDescription="Volume da percussão" })
            OutlinedButton(onClick={import.launch(arrayOf("audio/*","application/ogg"))},enabled=!state.loading) { Text("IMPORTAR ÁUDIO") }
            Text("Importe loops de 0,05 a 60 segundos. Eles ficam guardados na biblioteca de percussão, sem criar ou alterar seus projetos.",color=Muted,fontSize=12.sp)
            if(state.loading || state.preparing) { LinearProgressIndicator(Modifier.fillMaxWidth());Text(if(state.loading)"Importando áudio…" else "Preparando batida…",color=SecondaryText) }
            state.error?.let { Text(it,color=Red,fontSize=12.sp) }
            Text(if(p.enabled) { if(p.follow)"ATIVADA · acompanha o transporte" else "TOCANDO · independente" } else "PARADA",color=SecondaryText,fontSize=12.sp)
        }
    },confirmButton={
        Button(onClick={ engine.configure { it.copy(enabled=!it.enabled,bpm=if(!it.follow && valid)parsed!! else it.bpm) } },enabled=p.enabled || canPlay,
            modifier=Modifier.semantics { contentDescription="Reprodução da percussão" }) { Text(if(p.enabled)"■ PARAR" else "▶ PLAY") }
    },dismissButton={TextButton(onClick=close){Text("FECHAR")}})
}
