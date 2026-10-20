package com.atmytrack.app.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.atmytrack.app.PlayerViewModel
import com.atmytrack.app.audio.ConsoleMath
import com.atmytrack.app.audio.PadSynth
import com.atmytrack.app.data.*

@Composable internal fun CrossMusicDialog(vm:PlayerViewModel,close:()->Unit) {
    val library by vm.library.collectAsState()
    val saved by vm.crossPlan.collectAsState()
    val runtime by vm.crossMusic.collectAsState()
    val entries=saved.filter { e->library.projects.any { it.id==e.projectId } }
    var expanded by remember { mutableStateOf(emptySet<String>()) }
    val editable=!runtime.running && !runtime.preparing
    fun set(entry:CrossEntry) { vm.saveCrossMusic(entries.map { if(it.projectId==entry.projectId)entry else it }) }
    val invalid=entries.any { e->e.pad.enabled && library.projects.find { it.id==e.projectId }?.let { PadKey.resolve(it,e.pad)==null }==true }
    Dialog(onDismissRequest=close,properties=DialogProperties(usePlatformDefaultWidth=false)) {
        Surface(Modifier.safeDrawingPadding().padding(12.dp).widthIn(max=680.dp).fillMaxWidth().fillMaxHeight(.95f),shape=RoundedCornerShape(20.dp),color=Panel) {
            Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment=Alignment.CenterVertically) {
                    Text("CrossMusic",Modifier.weight(1f),fontSize=24.sp,fontWeight=FontWeight.Bold,color=Violet)
                    TextButton(onClick=close) { Text("FECHAR") }
                }
                Text(if(runtime.running)"Em execução · ${runtime.index+1}/${runtime.total}. Use os controles do player para ajustar o projeto atual."
                    else "Marque os projetos na ordem em que devem tocar. Abra a seta para ajustar faixas e PAD.",fontSize=12.sp,color=SecondaryText)
                LazyColumn(Modifier.weight(1f).semantics { contentDescription="Projetos CrossMusic" },verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    items(library.projects,key={it.id}) { project ->
                        val index=entries.indexOfFirst { it.projectId==project.id }
                        val selected=index>=0
                        val entry=entries.getOrNull(index) ?: CrossEntry.from(project)
                        Column(Modifier.fillMaxWidth().border(1.dp,if(selected)Violet.copy(alpha=.5f) else Control,RoundedCornerShape(10.dp)).padding(8.dp)) {
                            Row(verticalAlignment=Alignment.CenterVertically) {
                                Checkbox(selected,{ checked -> vm.saveCrossMusic(if(checked)entries+CrossEntry.from(project) else entries.filterNot { it.projectId==project.id }) },enabled=editable && project.stems.isNotEmpty(),
                                    modifier=Modifier.semantics { contentDescription="Incluir ${project.name} no CrossMusic" })
                                Column(Modifier.weight(1f)) {
                                    Text(project.name,maxLines=2,overflow=TextOverflow.Ellipsis,fontWeight=FontWeight.SemiBold)
                                    Text(if(selected)"${index+1} na sequência · ${project.key}" else "${project.stems.size} faixas · ${project.key}",fontSize=11.sp,color=Muted)
                                }
                                IconButton(onClick={expanded=if(project.id in expanded)expanded-project.id else expanded+project.id},modifier=Modifier.semantics { contentDescription="Faixas de ${project.name}";stateDescription=if(project.id in expanded)"Expandido" else "Recolhido" }) { Text(if(project.id in expanded)"⌄" else "›",fontSize=24.sp) }
                            }
                            if(selected && editable) Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End) {
                                TextButton(onClick={val list=entries.toMutableList();java.util.Collections.swap(list,index,index-1);vm.saveCrossMusic(list)},enabled=index>0,modifier=Modifier.semantics { contentDescription="Antecipar ${project.name}" }) { Text("↑") }
                                TextButton(onClick={val list=entries.toMutableList();java.util.Collections.swap(list,index,index+1);vm.saveCrossMusic(list)},enabled=index<entries.lastIndex,modifier=Modifier.semantics { contentDescription="Adiar ${project.name}" }) { Text("↓") }
                            }
                            if(project.id in expanded) {
                                project.stems.forEach { stem ->
                                    val mix=entry.tracks[stem.id] ?: CrossTrack(stem.volume,stem.mute)
                                    Row(verticalAlignment=Alignment.CenterVertically) {
                                        Text(stem.name,Modifier.weight(1f),fontSize=12.sp,maxLines=1,overflow=TextOverflow.Ellipsis)
                                        Text(if(mix.volume==0f)"−∞ dB" else "%+.1f dB".format(ConsoleMath.db(mix.volume)),fontSize=11.sp,color=SecondaryText)
                                        IconButton(onClick={set(entry.copy(tracks=entry.tracks+(stem.id to mix.copy(mute=!mix.mute))))},enabled=selected && editable,
                                            modifier=Modifier.semantics { contentDescription="Mute CrossMusic ${project.name} / ${stem.name}";this.selected=mix.mute }) {
                                            Text(if(mix.mute)"⊘" else "♪",color=if(mix.mute)Amber else SecondaryText,fontSize=20.sp)
                                        }
                                    }
                                    Slider(ConsoleMath.position(mix.volume),{ set(entry.copy(tracks=entry.tracks+(stem.id to mix.copy(volume=ConsoleMath.fromPosition(it))))) },enabled=selected && editable,
                                        modifier=Modifier.fillMaxWidth().semantics { contentDescription="Volume CrossMusic ${project.name} / ${stem.name}" })
                                }
                                HorizontalDivider(Modifier.padding(vertical=8.dp))
                                Row(verticalAlignment=Alignment.CenterVertically) {
                                    Text("PAD",Modifier.weight(1f),fontWeight=FontWeight.Bold,color=Violet)
                                    Switch(entry.pad.enabled,{set(entry.copy(pad=entry.pad.copy(enabled=it)))},enabled=selected && editable,modifier=Modifier.semantics { contentDescription="PAD CrossMusic ${project.name}" })
                                }
                                if(entry.pad.enabled) {
                                    val notes=listOf("C","C#","D","D#","E","F","F#","G","G#","A","A#","B")
                                    val choices=listOf("PROJECT" to "Tom do projeto")+notes.flatMapIndexed { note,name-> listOf("$note:major" to name,"$note:minor" to "${name}m") }
                                    if(editable && selected) {
                                        Choice("Tom do PAD",if(entry.pad.followProjectKey)"PROJECT" else "${entry.pad.note}:${if(entry.pad.minor)"minor" else "major"}",choices) { value ->
                                            val key=value.split(':');set(entry.copy(pad=entry.pad.copy(followProjectKey=value=="PROJECT",note=key[0].toIntOrNull() ?: entry.pad.note,minor=key.getOrNull(1)=="minor")))
                                        }
                                        Choice("Som do PAD",entry.pad.sound,PadSynth.sounds) { set(entry.copy(pad=entry.pad.copy(sound=it))) }
                                    } else Text("${if(entry.pad.followProjectKey)"Tom do projeto" else notes[entry.pad.note]+if(entry.pad.minor)"m" else ""} · ${entry.pad.sound}",color=SecondaryText,fontSize=12.sp)
                                    Text("Volume do PAD · ${(entry.pad.volume*100).toInt()}%",color=SecondaryText,fontSize=12.sp)
                                    Slider(entry.pad.volume,{set(entry.copy(pad=entry.pad.copy(volume=it)))},enabled=editable && selected,modifier=Modifier.semantics { contentDescription="Volume PAD CrossMusic ${project.name}" })
                                    if(PadKey.resolve(project,entry.pad)==null)Text("Defina a tonalidade do projeto ou escolha um tom para este PAD.",color=Amber,fontSize=12.sp)
                                    Text("Troca de tom e som com transição de 3 segundos.",color=Muted,fontSize=11.sp)
                                }
                            }
                        }
                    }
                }
                Button(onClick={vm.startCrossMusic(entries);if(vm.crossMusic.value.running)close()},enabled=editable && entries.isNotEmpty() && !invalid && library.busy==null && library.background==null,
                    colors=ButtonDefaults.buttonColors(containerColor=Violet.copy(alpha=.18f),contentColor=Violet),modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)) { Text("▶ INICIAR CROSSMUSIC · ${entries.size}") }
            }
        }
    }
}
