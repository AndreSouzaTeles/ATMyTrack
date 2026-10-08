package com.atmytrack.app.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.atmytrack.app.PlayerViewModel
import com.atmytrack.app.data.Project
import com.atmytrack.app.R
import androidx.compose.ui.res.painterResource
import kotlin.math.roundToInt

@Composable internal fun MenuButton(click:()->Unit) {
    IconButton(onClick=click,modifier=Modifier.semantics { contentDescription="Abrir menu principal" }) {
        Canvas(Modifier.size(24.dp)) { for(y in listOf(.2f,.5f,.8f))drawLine(LightBlue,Offset(0f,size.height*y),Offset(size.width,size.height*y),2.dp.toPx()) }
    }
}
internal enum class MainDestination(val title:String,val symbol:String) {
    PROJECTS("PROJETOS","▦"),TUNER("AFINADOR","♫"),PAYMENT("DEIXE SUA MARCA","◇"),HELP("CENTRAL DE DÚVIDAS","?")
}
@Composable internal fun MainMenu(close:()->Unit,navigate:(MainDestination)->Unit) {
    Dialog(onDismissRequest=close,properties=DialogProperties(usePlatformDefaultWidth=false)) {
        Box(Modifier.fillMaxSize().background(Bg.copy(alpha=.65f)).clickable(onClick=close)) {
            Surface(Modifier.fillMaxHeight().widthIn(max=360.dp).fillMaxWidth().clickable(enabled=false){},color=Panel) {
                Column(Modifier.safeDrawingPadding().padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    Image(painterResource(R.drawable.brand_art),"Logo ATMyTrack",Modifier.size(80.dp))
                    Row(verticalAlignment=Alignment.CenterVertically) { Text("ATMyTrack",Modifier.weight(1f),fontSize=24.sp);TextButton(onClick=close){Text("FECHAR")} }
                    HorizontalDivider()
                    listOf(MainDestination.PROJECTS,MainDestination.TUNER).forEach { item ->
                        OutlinedButton(onClick={navigate(item)},modifier=Modifier.fillMaxWidth().heightIn(min=60.dp)) { Text(item.symbol,Modifier.padding(end=16.dp),fontSize=22.sp);Text(item.title,Modifier.weight(1f)) }
                    }
                    }
                    listOf(MainDestination.PAYMENT,MainDestination.HELP).forEach { item ->
                        OutlinedButton(onClick={navigate(item)},modifier=Modifier.fillMaxWidth().heightIn(min=60.dp)) { Text(item.symbol,Modifier.padding(end=16.dp),fontSize=22.sp);Text(item.title,Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}
@Composable internal fun SpeedDialog(p:Project,vm:PlayerViewModel,busy:Boolean,close:()->Unit) {
    var speed by remember(p.id) { mutableIntStateOf(p.speed) }
    var preset by remember(p.id) { mutableIntStateOf(p.speedPreset) }
    var selected by remember(p.id) { mutableStateOf(p.selectedSpeedTracks) }
    var warning by remember { mutableStateOf(false) }
    fun apply() { vm.applySpeed(speed,selected,preset);close() }
    fun requestApply() { if(speed!=100 && selected.isNotEmpty() && p.stems.any { it.id !in selected })warning=true else apply() }
    AlertDialog(onDismissRequest=close,title={Text("VELOCIDADE")},text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text("$speed%",fontSize=40.sp,color=LightBlue)
        Row(verticalAlignment=Alignment.CenterVertically) { TextButton(onClick={speed=(speed-1).coerceAtLeast(50)}){Text("−")};Text("${if(speed==100)"ORIGINAL" else "$speed%"}",Modifier.weight(1f));TextButton(onClick={speed=(speed+1).coerceAtMost(200)}){Text("+")} }
        Slider(speed.toFloat(),{speed=it.roundToInt()},valueRange=50f..200f,steps=149,modifier=Modifier.semantics { contentDescription="Percentual da velocidade" })
        Text("50% — 200% • tom preservado",color=Muted)
        listOf(listOf(50,75,80),listOf(90,100,110),listOf(125,150,200)).forEach { row -> Row { row.forEach { v->TextButton(onClick={speed=v;preset=v;requestApply()},enabled=!busy,modifier=Modifier.weight(1f)){Text(if(v==100)"ORIGINAL" else "$v%") } } } }
        Text(if(p.stems.all { it.id in selected })"VELOCIDADE GLOBAL • recomendado" else "SELEÇÃO PERSONALIZADA",color=LightBlue)
        TextButton(onClick={selected=p.stems.map { it.id }}){Text("SELECIONAR TODAS")}
        TextButton(onClick={selected=emptyList()}){Text("LIMPAR SELEÇÃO")}
        p.stems.forEach { s->Row(Modifier.fillMaxWidth().clickable { selected=if(s.id in selected)selected-s.id else selected+s.id },verticalAlignment=Alignment.CenterVertically) { Checkbox(s.id in selected,{checked->selected=if(checked)selected+s.id else selected-s.id});Text(s.name) } }
        Text("Prepara o áudio antes de aplicar. Durante o preparo, a reprodução atual continua. Extremos podem apresentar mais artefatos. Em seleção personalizada, timeline e click interno seguem a referência original.",color=Muted,fontSize=12.sp)
        TextButton(onClick={speed=100;vm.applySpeed(100,selected,100);close()},enabled=!busy){Text("RESET 100%")}
    }},confirmButton={TextButton(onClick=::requestApply,enabled=!busy){Text("APLICAR")}},dismissButton={TextButton(onClick=close){Text("FECHAR")}})
    if(warning)AlertDialog(onDismissRequest={warning=false},title={Text("Velocidades diferentes")},text={Text("Tracks com velocidades diferentes podem perder sincronização entre si.")},confirmButton={TextButton(onClick={warning=false;apply()}){Text("CONTINUAR")}},dismissButton={TextButton(onClick={warning=false}){Text("CANCELAR")}})
}

@Composable internal fun DspButton(label:String,value:String,active:Boolean,modifier:Modifier,click:()->Unit) {
    OutlinedButton(onClick=click,modifier=modifier.heightIn(min=58.dp).semantics { contentDescription=label },
        shape=androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
        colors=ButtonDefaults.outlinedButtonColors(containerColor=if(active)Blue.copy(alpha=.15f) else Control),
        border=BorderStroke(1.dp,if(active)Blue else Muted.copy(alpha=.3f)),contentPadding=PaddingValues(8.dp)) {
        if(label=="VELOCIDADE")Canvas(Modifier.size(18.dp).padding(end=3.dp)) {
            drawArc(LightBlue,150f,240f,false,style=androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()))
            drawLine(LightBlue,center,Offset(size.width*.8f,size.height*.2f),2.dp.toPx())
        }
        Column(horizontalAlignment=Alignment.CenterHorizontally) { Text(label,fontSize=11.sp,color=White);Text(value,fontSize=13.sp,color=LightBlue) }
    }
}
