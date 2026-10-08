package com.atmytrack.app.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.*
import com.atmytrack.app.PlayerViewModel
import com.atmytrack.app.audio.ConsoleMath
import com.atmytrack.app.data.*

@Composable
internal fun Choice(label:String, value:String, choices:List<Pair<String,String>>, choose:(String)->Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick={open=true}) { Text("$label: ${choices.find { it.first==value }?.second ?: value}") }
        DropdownMenu(open,{open=false}) { choices.forEach { (id,text) -> DropdownMenuItem(text={Text(text)},onClick={choose(id);open=false}) } }
    }
}
private val notes=listOf("C","C#","Db","D","D#","Eb","E","F","F#","Gb","G","G#","Ab","A","A#","Bb","B").map { it to it }
private val stereo=listOf("BOTH" to "BOTH • estéreo", "LEFT" to "LEFT • mono esquerdo", "RIGHT" to "RIGHT • mono direito")

@Composable
internal fun FineGainDialog(name:String,gain:Float,apply:(Float)->Unit,close:()->Unit) {
    var text by remember { mutableStateOf("%.1f".format(java.util.Locale.US,ConsoleMath.db(gain))) }
    val db=text.replace(',','.').toFloatOrNull()
    AlertDialog(onDismissRequest=close,title={Text("Volume · $name")},text={Column {
        OutlinedTextField(text,{text=it},label={Text("dB · −80 a +10")},singleLine=true)
        Row { TextButton(onClick={text="%.1f".format(java.util.Locale.US,((db ?: 0f)-.1f).coerceAtLeast(-80f))}){Text("−0.1")};TextButton(onClick={text="%.1f".format(java.util.Locale.US,((db ?: 0f)+.1f).coerceAtMost(10f))}){Text("+0.1")} }
        TextButton(onClick={apply(1f);close()}){Text("UNITY · 0.0 dB")}
        TextButton(onClick={apply(0f);close()}){Text("SILÊNCIO · −∞")}
        Text("Double tap no fader também retorna a 0 dB.",color=Muted,fontSize=12.sp)
    }},confirmButton={TextButton(onClick={apply(ConsoleMath.gain(kotlin.math.round(db!!*10)/10));close()},enabled=db!=null && db.isFinite() && db in -80f..10f){Text("APLICAR")}},dismissButton={TextButton(onClick=close){Text("CANCELAR")}})
}

@Composable
internal fun PitchDialog(p:Project,vm:PlayerViewModel,busy:Boolean,close:()->Unit) {
    var origin by remember(p.id) { mutableStateOf(p.key.takeIf { ConsoleMath.note(it)>=0 } ?: "C") }
    var semitones by remember(p.id) { mutableIntStateOf(p.semitones) }
    var destination by remember(p.id) { mutableStateOf(ConsoleMath.target(origin,semitones)) }
    var selected by remember(p.id) { mutableStateOf(if(p.pitchTracks.isNotEmpty())p.pitchTracks else p.stems.filterNot { s -> listOf("drum","bateria","perc","click","clk","guide","guia","metro").any { s.name.contains(it,true) } }.map { it.id }) }
    AlertDialog(onDismissRequest=close,title={Text("Tom / Key")},text={
        Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Choice("Original",origin,notes) { origin=it; semitones=ConsoleMath.transpose(origin,destination) }
            Choice("Destino",destination,notes) { destination=it; semitones=ConsoleMath.transpose(origin,destination) }
            Row(verticalAlignment=Alignment.CenterVertically) {
                TextButton(onClick={semitones=(semitones-1).coerceAtLeast(-12);destination=ConsoleMath.target(origin,semitones)}){Text("−")}
                Text("%+d SEMITONS".format(semitones),Modifier.weight(1f))
                TextButton(onClick={semitones=(semitones+1).coerceAtMost(12);destination=ConsoleMath.target(origin,semitones)}){Text("+")}
            }
            Text("Alterar tom em:")
            Row { TextButton(onClick={selected=p.stems.map { it.id }}){Text("TODAS")}; TextButton(onClick={selected=emptyList()}){Text("LIMPAR")} }
            p.stems.forEach { s -> Row(Modifier.fillMaxWidth().clickable { selected=if(s.id in selected)selected-s.id else selected+s.id },verticalAlignment=Alignment.CenterVertically) {
                Checkbox(s.id in selected,{checked->selected=if(checked)selected+s.id else selected-s.id});Text(s.name,Modifier.weight(1f))
            } }
            Text("Mantém o tempo e a duração. Ao aplicar, prepara e guarda o áudio das tracks selecionadas; o tempo de preparo depende da duração. A reprodução assume o novo tom quando terminar.",color=Muted,fontSize=12.sp)
            TextButton(onClick={vm.applyPitch(origin,origin,0,emptyList());close()},enabled=!busy){Text("ORIGINAL / RESET")}
        }
    },confirmButton={TextButton(onClick={vm.applyPitch(origin,destination,semitones,selected);close()},enabled=!busy && (semitones==0 || selected.isNotEmpty())){Text("APLICAR")}},dismissButton={TextButton(onClick=close){Text("FECHAR")}})
}

@Composable
internal fun AllRoutingDialog(p:Project,outputs:Int,vm:PlayerViewModel,close:()->Unit) {
    val routes=listOf("BOTH" to "MASTER · estéreo","LEFT" to "OUT 1/2 · esquerda","RIGHT" to "OUT 2/2 · direita")
    AlertDialog(onDismissRequest=close,title={Text("Routing")},text={
        Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text("Escolha a saída de cada track, BUS e click interno.",color=Muted)
            p.stems.forEach { s ->
                Text(s.name)
                Choice("Saída",if(s.bus.isNotEmpty())"BUS:${s.bus}" else s.route,routes+p.buses.map { "BUS:${it.id}" to "BUS · ${it.name}" }) { value ->
                    vm.stem(s.id) { if(value.startsWith("BUS:"))it.copy(bus=value.removePrefix("BUS:"),route="BOTH") else it.copy(bus="",route=value) }
                }
            }
            p.buses.forEach { b ->
                Text("BUS · ${b.name}")
                Choice("Saída",b.destination,ConsoleMath.outputChoices(outputs)) { value -> vm.update { it.copy(buses=it.buses.map { bus -> if(bus.id==b.id)bus.copy(destination=value) else bus }) } }
            }
            var clickMenu by remember { mutableStateOf(false) }
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                Box(Modifier.weight(1f)) {
                    RoutingAction("CLICK INTERNO",when(p.clickRoute){"LEFT"->"ESQUERDA";"RIGHT"->"DIREITA";else->"ESTÉREO"},false,Modifier.fillMaxWidth()) { clickMenu=true }
                    DropdownMenu(clickMenu,{clickMenu=false}) { routes.forEach { (id,label) ->
                        DropdownMenuItem(text={Text(label)},onClick={vm.update { it.copy(clickRoute=id) };clickMenu=false})
                    } }
                }
                RoutingAction("STEREO SPLIT","L / R",p.stems.isNotEmpty() && p.clickRoute=="LEFT" && p.stems.all { s -> s.bus.isEmpty() && s.route==if(listOf("click","guide","guia","clk","metro").any { s.name.contains(it,true) })"LEFT" else "RIGHT" },Modifier.weight(1f),vm::split)
                RoutingAction("BOTH ALL","ESTÉREO",p.stems.all { it.bus.isEmpty() && it.route=="BOTH" } && p.clickRoute=="BOTH",Modifier.weight(1f),vm::bothAll)
            }
            Text("BOTH ALL envia as tracks diretamente ao Master e o click para os dois lados. Preserva volumes, pans e grupos cadastrados.",color=Muted,fontSize=12.sp)
        }
    },confirmButton={TextButton(onClick=close){Text("FECHAR")}})
}

@Composable
internal fun RoutingDialog(p:Project,s:Stem,vm:PlayerViewModel,close:()->Unit) {
    AlertDialog(onDismissRequest=close,title={Text("Routing · ${s.name}")},text={Column {
        Choice("Enviar para",s.bus,listOf("" to "MASTER")+p.buses.map { it.id to it.name }) { v->vm.stem(s.id){it.copy(bus=v)} }
        Choice("Canais",s.route,stereo) { v->vm.stem(s.id){it.copy(route=v)} }
        Text("LEFT/RIGHT somam a stem em mono. BOTH preserva o estéreo. O pan e o fader permanecem independentes.",color=Muted,fontSize=12.sp)
    }},confirmButton={TextButton(onClick=close){Text("FECHAR")}})
}

@Composable
internal fun GroupDialog(p:Project,token:String,outputs:Int,vm:PlayerViewModel,close:()->Unit) {
    val isDca=token.startsWith("DCA:"); val id=token.substringAfter(':')
    val dca=p.dcas.find { it.id==id }; val bus=p.buses.find { it.id==id }
    var name by remember(token) { mutableStateOf(dca?.name ?: bus?.name ?: if(isDca)"DCA ${p.dcas.size+1}" else "BUS ${p.buses.size+1}") }
    var members by remember(token) { mutableStateOf(dca?.members ?: p.stems.filter { id.isNotEmpty() && it.bus==id }.map { it.id }) }
    var destination by remember(token) { mutableStateOf(bus?.destination ?: "BOTH") }
    val destinations=ConsoleMath.outputChoices(outputs)+listOf("LEFT" to "MASTER · esquerdo", "RIGHT" to "MASTER · direito")
    AlertDialog(onDismissRequest=close,title={Text(if(isDca)"DCA / Grupo" else "Bus / Routing")},text={
        Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(name,{name=it},label={Text("Nome")},singleLine=true)
            if(!isDca) Choice("Destino",destination,destinations) { destination=it }
            Text(if(isDca)"O ganho é relativo e não altera os faders individuais." else "Cada track envia para um bus ou para o master. Uma nova seleção transfere a track do bus anterior.",color=Muted,fontSize=12.sp)
            p.stems.forEach { s -> Row(Modifier.fillMaxWidth().clickable { members=if(s.id in members)members-s.id else members+s.id },verticalAlignment=Alignment.CenterVertically) {
                Checkbox(s.id in members,{checked->members=if(checked)members+s.id else members-s.id});Text(s.name,Modifier.weight(1f))
            } }
            if(id.isNotEmpty())TextButton(onClick={vm.update { q-> if(isDca)q.copy(dcas=q.dcas.filterNot { it.id==id }) else q.copy(buses=q.buses.filterNot { it.id==id },stems=q.stems.map { if(it.bus==id)it.copy(bus="") else it }) };close()}) { Text("EXCLUIR ${if(isDca)"DCA" else "BUS"}",color=Red) }
        }
    },confirmButton={TextButton(onClick={
        vm.update { q->if(isDca) {
            val next=(dca ?: Dca(name=name)).copy(name=name.trim(),members=members)
            q.copy(dcas=if(dca==null)q.dcas+next else q.dcas.map { if(it.id==id)next else it })
        } else {
            val next=(bus ?: Bus(name=name)).copy(name=name.trim(),destination=destination)
            q.copy(buses=if(bus==null)q.buses+next else q.buses.map { if(it.id==id)next else it },stems=q.stems.map { s->when { s.id in members->s.copy(bus=next.id); id.isNotEmpty() && s.bus==id->s.copy(bus=""); else->s } })
        } };close()
    },enabled=name.isNotBlank() && (isDca || destinations.any { it.first==destination })){Text("SALVAR")}},dismissButton={TextButton(onClick=close){Text("CANCELAR")}})
}
