package com.atmytrack.app.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.*
import com.atmytrack.app.tuner.*
import kotlin.math.*

@Composable internal fun TunerScreen(close:()->Unit) {
    val context=LocalContext.current
    val activity=context as ComponentActivity
    val prefs=remember { context.getSharedPreferences("tuner",0) }
    var instrumentId by remember { mutableStateOf(prefs.getString("instrument","acoustic") ?: "acoustic") }
    var tuningId by remember { mutableStateOf(prefs.getString("tuning","standard") ?: "standard") }
    var stringNumber by remember { mutableIntStateOf(prefs.getInt("string",0)) }
    var a4 by remember { mutableIntStateOf(prefs.getInt("a4",440).coerceIn(400,480)) }
    var permitted by remember { mutableStateOf(context.checkSelfPermission(Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED) }
    var denied by remember { mutableStateOf(prefs.getBoolean("asked",false) && !permitted) }
    var permanentDenial by remember { mutableStateOf(denied && !activity.shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)) }
    var retry by remember { mutableIntStateOf(0) }
    val capture=remember { TunerCapture(context.applicationContext) }
    val state by capture.state.collectAsState()
    val launcher=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> permitted=granted;denied=!granted;permanentDenial=!granted && !activity.shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO);prefs.edit().putBoolean("asked",true).apply() }
    DisposableEffect(activity) {
        val observer=LifecycleEventObserver { _,event ->
            if(event==Lifecycle.Event.ON_RESUME) { permitted=context.checkSelfPermission(Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED;permanentDenial=!permitted && prefs.getBoolean("asked",false) && !activity.shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO) }
        }
        activity.lifecycle.addObserver(observer)
        onDispose { activity.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(permitted,retry) { if(permitted)activity.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) { capture.capture() } }
    LaunchedEffect(instrumentId,tuningId,stringNumber,a4) { prefs.edit().putString("instrument",instrumentId).putString("tuning",tuningId).putInt("string",stringNumber).putInt("a4",a4).apply() }
    val instrument=Instruments.all.find { it.id==instrumentId } ?: Instruments.all.first()
    val tuning=instrument.tunings.find { it.id==tuningId } ?: instrument.tunings.firstOrNull()
    Dialog(onDismissRequest=close,properties=DialogProperties(usePlatformDefaultWidth=false)) {
        Surface(Modifier.fillMaxSize(),color=Bg) {
            Column(Modifier.safeDrawingPadding().padding(16.dp)) {
                Row(verticalAlignment=Alignment.CenterVertically) { Text("AFINADOR",Modifier.weight(1f),fontSize=24.sp,fontWeight=FontWeight.Bold);TextButton(onClick=close){Text("FECHAR")} }
                Text("O player permanece pausado durante a afinação. Análise local, sem gravar áudio.",color=Muted,fontSize=12.sp)
                if(!permitted) {
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.Center) {
                        Text("O ATMyTrack precisa acessar o microfone para identificar a nota tocada pelo seu instrumento.",fontSize=20.sp)
                        if(denied)Text("O microfone não foi autorizado. Permita o acesso para usar o afinador.",color=Muted)
                        val settings=permanentDenial
                        Button(onClick={if(settings)context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:${context.packageName}"))) else launcher.launch(Manifest.permission.RECORD_AUDIO)}) { Text(if(settings)"ABRIR CONFIGURAÇÕES" else "PERMITIR MICROFONE") }
                    }
                } else BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                    val wide=maxWidth>=700.dp
                    @Composable fun Controls(modifier:Modifier) {
                        Column(modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                            Choice("Instrumento",instrument.id,Instruments.all.map { it.id to it.name }) { instrumentId=it;tuningId="standard";stringNumber=0 }
                            if(tuning!=null) {
                                Choice("Afinação",tuning.id,instrument.tunings.map { it.id to it.name }) { tuningId=it;stringNumber=0 }
                                Choice("Corda",stringNumber.toString(),listOf("0" to "AUTO")+tuning.strings.map { it.number.toString() to "${it.number}ª — ${it.label}" }) { stringNumber=it.toInt() }
                            }
                            Row(verticalAlignment=Alignment.CenterVertically) {
                                TextButton(onClick={a4=(a4-1).coerceAtLeast(400)}){Text("−")}
                                Text("A4 = $a4 Hz",fontWeight=FontWeight.Bold)
                                TextButton(onClick={a4=(a4+1).coerceAtMost(480)}){Text("+")}
                            }
                            TextButton(onClick={a4=440}) { Text("RESET 440 Hz") }
                            Text(state.input,color=Muted,fontSize=12.sp)
                            Row(verticalAlignment=Alignment.CenterVertically) {
                                Text("MIC",Modifier.padding(end=8.dp),color=Muted)
                                LinearProgressIndicator(progress={(state.reading.rms*8).toFloat().coerceIn(0f,1f)},modifier=Modifier.weight(1f).semantics { contentDescription="Nível do microfone" },color=Blue)
                            }
                            if(state.error!=null) { Text(state.error!!,color=Red);Button(onClick={retry++}){Text("TENTAR NOVAMENTE")} }
                        }
                    }
                    if(wide) Row(Modifier.fillMaxSize(),horizontalArrangement=Arrangement.spacedBy(24.dp)) { Controls(Modifier.width(300.dp));TunerMeter(state.reading,tuning,stringNumber,a4,Modifier.weight(1f).fillMaxHeight()) }
                    else Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) { TunerMeter(state.reading,tuning,stringNumber,a4,Modifier.fillMaxWidth().heightIn(min=270.dp));Controls(Modifier.fillMaxWidth().heightIn(max=450.dp)) }
                }
            }
        }
    }
}
@Composable private fun TunerMeter(reading:PitchReading,tuning:Tuning?,manual:Int,a4:Int,modifier:Modifier) {
    val f=reading.frequency
    val display=if(f>0)tunerDisplay(f,tuning,manual,a4.toDouble()) else null
    val target=display?.target
    val midi=display?.midi ?: 69
    val cents=display?.cents ?: 0.0
    val tuningCents=display?.targetCents ?: cents
    val tuned=f>0 && abs(tuningCents)<=3
    Column(modifier,verticalArrangement=Arrangement.Center,horizontalAlignment=Alignment.CenterHorizontally) {
        Text(if(f>0)TuningMath.name(midi) else "—",fontSize=80.sp,fontWeight=FontWeight.Bold,color=if(tuned)LightBlue else White)
        Text(if(f>0)"%.2f Hz".format(f) else "AGUARDANDO SINAL ESTÁVEL",fontSize=18.sp,color=Muted)
        target?.let { Text("Alvo: ${it.number}ª CORDA · ${it.label}",color=LightBlue)
            Text("%+.1f cents até o alvo".format(tuningCents),color=Muted,fontSize=14.sp) }
        Row(Modifier.fillMaxWidth().padding(horizontal=12.dp),horizontalArrangement=Arrangement.SpaceBetween) { listOf("−50","−25","0","+25","+50").forEach { Text(it,color=Muted) } }
        Canvas(Modifier.fillMaxWidth().height(42.dp).padding(horizontal=22.dp).semantics { contentDescription="Indicador de cents" }) {
            drawLine(Muted,Offset(0f,center.y),Offset(size.width,center.y),2.dp.toPx())
            drawRect(Blue.copy(alpha=.22f),Offset(size.width*.47f,0f),androidx.compose.ui.geometry.Size(size.width*.06f,size.height))
            if(f>0) { val x=((cents.coerceIn(-50.0,50.0)+50)/100*size.width).toFloat();drawLine(if(tuned)LightBlue else White,Offset(x,0f),Offset(x,size.height),4.dp.toPx()) }
        }
        Text(if(f>0)"%+.1f cents · nota captada".format(cents) else "TOQUE UMA CORDA",fontSize=22.sp,color=if(tuned)LightBlue else White)
        if(f>0)Text(TuningMath.direction(tuningCents),fontSize=16.sp,fontWeight=FontWeight.Bold,color=if(tuned)LightBlue else Muted)
    }
}
