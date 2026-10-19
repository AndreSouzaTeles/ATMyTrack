package com.atmytrack.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.graphics.Paint
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.atmytrack.app.R
import com.atmytrack.app.data.Project
import kotlinx.coroutines.delay
import org.json.JSONArray
import java.text.Normalizer
import kotlin.math.*

private fun normalized(s:String)=Normalizer.normalize(s,Normalizer.Form.NFD).replace(Regex("\\p{M}"),"").lowercase()
@Composable internal fun SearchButton(click:()->Unit) {
    IconButton(onClick=click,modifier=Modifier.semantics { contentDescription="Pesquisar projetos" }) {
        Canvas(Modifier.size(22.dp)) { drawCircle(SecondaryText,size.width*.30f,Offset(size.width*.4f,size.height*.4f),style=androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()));drawLine(SecondaryText,Offset(size.width*.63f,size.height*.63f),Offset(size.width*.92f,size.height*.92f),2.dp.toPx()) }
    }
}
@Composable internal fun ProjectLibraryMenu(projects:List<Project>,searchMode:Boolean,close:()->Unit,choose:(Project)->Unit,help:()->Unit,support:()->Unit) {
    var query by remember { mutableStateOf("") };var key by remember { mutableStateOf("") }
    val filtered=projects.filter { (query.isBlank() || normalized(it.name).contains(normalized(query)) || normalized(it.targetKey.ifBlank { it.key }).contains(normalized(query))) && (key.isBlank() || it.targetKey.ifBlank { it.key }==key) }
    Dialog(onDismissRequest=close,properties=DialogProperties(usePlatformDefaultWidth=false)) {
        Box(Modifier.fillMaxSize().background(Bg.copy(alpha=.5f)).clickable(onClick=close)) {
            Surface(Modifier.fillMaxHeight().widthIn(max=380.dp).fillMaxWidth().align(if(searchMode)Alignment.Center else Alignment.CenterStart).clickable(enabled=false){},color=Panel) {
                Column(Modifier.safeDrawingPadding().padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment=Alignment.CenterVertically) { Text(if(searchMode)"Pesquisar projetos" else "Sua biblioteca",Modifier.weight(1f),fontSize=21.sp,fontWeight=FontWeight.Bold);TextButton(onClick=close){Text("×")} }
                    OutlinedTextField(query,{query=it},modifier=Modifier.fillMaxWidth(),label={Text("Nome do projeto ou tom")},singleLine=true)
                    Choice("Tom",key,listOf("" to "Todos")+projects.map { it.targetKey.ifBlank { it.key } }.filter { it!="—" && it.isNotBlank() }.distinct().sorted().map { it to it }) { key=it }
                    Text("${filtered.size} projetos",color=Muted,fontSize=12.sp)
                    LazyColumn(Modifier.weight(1f).semantics { contentDescription="Resultados de projetos" },verticalArrangement=Arrangement.spacedBy(8.dp)) { items(filtered,key={it.id}) { p ->
                        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Control).clickable { choose(p) }.semantics { contentDescription="Abrir projeto ${p.id}" }.padding(14.dp)) {
                            Text(p.name,fontWeight=FontWeight.SemiBold);Text("${p.targetKey.ifBlank { p.key }} · ${p.bpm} BPM",color=SecondaryText,fontSize=12.sp)
                        }
                    } }
                    if(filtered.isEmpty())Text("Nenhum projeto encontrado.",color=Muted)
                    HorizontalDivider()
                    OutlinedButton(onClick=support,modifier=Modifier.fillMaxWidth()) { Text("SeuNomeNoApp") }
                    TextButton(onClick=help,modifier=Modifier.fillMaxWidth()) { Text("Central de dúvidas") }
                }
            }
        }
    }
}
private data class GlobeName(val name:String,val demo:Boolean)
private const val pix="atelestechnology@jim.com"
@Composable internal fun SupportersScreen(close:()->Unit) {
    val context=LocalContext.current
    val names=remember { val json=JSONArray(context.assets.open("supporters.json").bufferedReader().use { it.readText() });(0 until json.length()).map { val o=json.getJSONObject(it);GlobeName(o.getString("name"),o.optBoolean("demo",false)) } }
    var query by remember { mutableStateOf("") }
    var yaw by remember { mutableFloatStateOf(0f) };var pitch by remember { mutableFloatStateOf(0f) };var rotating by remember { mutableStateOf(true) };var focused by remember { mutableStateOf("") }
    val paint=remember { Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign=Paint.Align.CENTER;typeface=android.graphics.Typeface.create("sans-serif-medium",android.graphics.Typeface.NORMAL) } }
    fun center(i:Int) { yaw=-(i*2.399963f);pitch=asin(1f-2f*(i+.5f)/names.size);rotating=query.isBlank();focused=names[i].name }
    LaunchedEffect(query) { rotating=query.isBlank();if(query.isNotBlank()) names.indexOfFirst { normalized(it.name).contains(normalized(query)) }.takeIf { it>=0 }?.let { center(it) } }
    LaunchedEffect(rotating) { while(rotating) { delay(33);yaw+=.002f } }
    Dialog(onDismissRequest=close,properties=DialogProperties(usePlatformDefaultWidth=false)) {
        Surface(Modifier.fillMaxSize(),color=Bg) {
            Column(Modifier.safeDrawingPadding().padding(16.dp)) {
                Row(verticalAlignment=Alignment.CenterVertically) { Text("SeuNomeNoApp",Modifier.weight(1f),fontSize=22.sp,fontWeight=FontWeight.Bold);TextButton(onClick=close){Text("FECHAR")} }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    Text("Globo exemplo",color=SecondaryText)
                    OutlinedTextField(query,{query=it},modifier=Modifier.fillMaxWidth(),label={Text("⌕ Encontrar um nome no globo")},singleLine=true)
                    if(query.isNotBlank() && names.none { normalized(it.name).contains(normalized(query)) })Text("Nome não encontrado.",color=Muted)
                    Canvas(Modifier.fillMaxWidth().height(300.dp).semantics { contentDescription="Globo de nomes; arraste para girar" }
                        .pointerInput(query) { detectDragGestures(onDragStart={rotating=false},onDragEnd={rotating=query.isBlank()},onDragCancel={rotating=query.isBlank()}) { change,drag -> change.consume();yaw+=drag.x*.009f;pitch=(pitch-drag.y*.009f).coerceIn(-1.5f,1.5f) } }
                        .pointerInput(query) { detectTapGestures { tap ->
                            val radius=min(size.width,size.height)*.37f
                            val candidate=names.indices.map { i -> val lat=asin(1f-2f*(i+.5f)/names.size);val lon=i*2.399963f+yaw;val z=cos(lat)*cos(lon);val y=sin(lat)*cos(pitch)-z*sin(pitch);val depth=sin(lat)*sin(pitch)+z*cos(pitch);val x=cos(lat)*sin(lon);i to if(depth>0)(Offset(size.width/2f+x*radius,size.height/2f-y*radius)-tap).getDistance() else Float.MAX_VALUE }.minByOrNull { it.second }
                            if(candidate!=null && candidate.second<70.dp.toPx())center(candidate.first)
                        } }) {
                        val radius=min(size.width,size.height)*.37f
                        drawCircle(Brush.radialGradient(listOf(Control,Bg)),radius*1.15f)
                        names.indices.map { i -> val lat=asin(1f-2f*(i+.5f)/names.size);val lon=i*2.399963f+yaw;val z=cos(lat)*cos(lon);floatArrayOf(i.toFloat(),cos(lat)*sin(lon),sin(lat)*cos(pitch)-z*sin(pitch),sin(lat)*sin(pitch)+z*cos(pitch)) }.sortedBy { it[3] }.forEach { v ->
                            paint.color=android.graphics.Color.rgb(155,169,182);paint.alpha=(65+190*((v[3]+1)/2)).toInt();paint.textSize=(9f+4f*((v[3]+1)/2)).sp.toPx()
                            drawContext.canvas.nativeCanvas.drawText(names[v[0].toInt()].name,center.x+v[1]*radius,center.y-v[2]*radius,paint)
                        }
                    }
                    if(focused.isNotBlank())Text(focused,color=SecondaryText,fontWeight=FontWeight.Bold)
                    TextButton(onClick={query="";rotating=true}) { Text("GIRAR AUTOMATICAMENTE") }
                    Text("Contribua com o valor que desejar via Pix e deixe sua marca no mundo ATMyTrack.")
                    Text(pix,color=SecondaryText)
                    OutlinedButton(onClick={(context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Pix ATMyTrack",pix))},modifier=Modifier.fillMaxWidth()) { Text("COPIAR CHAVE PIX") }
                    Text("Beneficiário informado: Andre Souza Teles. Confira os dados no aplicativo do seu banco antes de confirmar.",color=Muted,fontSize=12.sp)
                    OutlinedButton(onClick={context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse("https://wa.me/5511970317078?text="+Uri.encode("Olá! Fiz um Pix para ATMyTrack. Vou enviar o comprovante nesta conversa."))))}) { Text("ENVIAR COMPROVANTE NO WHATSAPP") }
                }
            }
        }
    }
}
