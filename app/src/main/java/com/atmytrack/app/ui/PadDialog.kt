package com.atmytrack.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.atmytrack.app.audio.PadEngine

@Composable internal fun PadDialog(engine:PadEngine,close:()->Unit) {
    val p by engine.state.collectAsState()
    val notes=listOf("C","C#","D","D#","E","F","F#","G","G#","A","A#","B")
    AlertDialog(onDismissRequest=close,title={Text("PAD")},text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text("Camada ambiente independente da música.",color=Muted)
        Choice("Tom",p.note.toString(),notes.mapIndexed { i,n -> i.toString() to n }) { engine.configure(note=it.toInt()) }
        Choice("Modo",if(p.minor)"minor" else "major",listOf("major" to "MAIOR","minor" to "MENOR")) { engine.configure(minor=it=="minor") }
        Choice("Som",p.sound,com.atmytrack.app.audio.PadSynth.sounds) { engine.configure(sound=it) }
        Text("Volume do PAD")
        Slider(p.volume,{engine.configure(volume=it)},modifier=Modifier.fillMaxWidth())
        Text(if(p.playing)"SOANDO · ${notes[p.note]}${if(p.minor)"m" else ""}" else "PARADO",color=SecondaryText)
        Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            Button(onClick=engine::play,enabled=!p.playing) { Text("▶ PLAY PAD") }
            OutlinedButton(onClick=engine::stop,enabled=p.playing) { Text("■ STOP PAD") }
        }
        p.error?.let { Text(it,color=Red) }
    }},confirmButton={TextButton(onClick=close){Text("FECHAR")}})
}
