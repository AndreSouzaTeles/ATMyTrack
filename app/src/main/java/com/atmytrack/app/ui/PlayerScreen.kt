package com.atmytrack.app.ui

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import com.atmytrack.app.audio.ConsoleMath
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.zIndex
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import com.atmytrack.app.*
import com.atmytrack.app.R
import com.atmytrack.app.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.*

internal val Bg = Color(0xFF121518)
internal val Panel = Color(0xFF1C2025)
internal val Control = Color(0xFF272D34)
internal val SecondaryText = Color(0xFFB8C1CC)
internal val Muted = Color(0xFF949FAB)
internal val White = Color(0xFFDCE1E7)
internal val Red = Color(0xFFD86D69)

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun PlayerScreen(vm: PlayerViewModel) {
    val library by vm.library.collectAsState()
    val playback by vm.playback.collectAsState()
    val p = library.current
    var importDialog by remember { mutableStateOf(false) }
    var importDetails by remember { mutableStateOf(false) }
    var edit by remember { mutableStateOf(false) }
    var markers by remember { mutableStateOf(false) }
    var navigation by remember { mutableStateOf(false) }
    var search by remember { mutableStateOf(false) }
    var supporters by remember { mutableStateOf(false) }
    var dragGrab by remember { mutableFloatStateOf(0f) }
    var help by remember { mutableStateOf(false) }
    var delete by remember { mutableStateOf<Project?>(null) }
    var crossOpen by remember { mutableStateOf(false) }
    var endCross by remember { mutableStateOf(false) }
    val cross by vm.crossMusic.collectAsState()
    var pitch by remember { mutableStateOf(false) }
    var padOpen by remember { mutableStateOf(false) }
    var percussionOpen by remember { mutableStateOf(false) }
    val rhythm by vm.rhythm.state.collectAsState()
    val padState by vm.pad.state.collectAsState()
    var speed by remember { mutableStateOf(false) }
    var tuner by remember { mutableStateOf(false) }
    var group by remember { mutableStateOf<String?>(null) }
    var allRouting by remember { mutableStateOf(false) }
    var routing by remember { mutableStateOf<String?>(null) }
    var metronome by remember { mutableStateOf(false) }
    val channelList = rememberLazyListState()
    val mainScroll=rememberScrollState()
    val uiScope=rememberCoroutineScope()
    LaunchedEffect(library.editRequestedId,p?.id) {
        if(library.editRequestedId!=null && library.editRequestedId==p?.id) { edit=true;vm.consumeEditRequest() }
    }
    var dragged by remember { mutableStateOf<String?>(null) }
    var dragX by remember { mutableFloatStateOf(0f) }
    val latestProject by rememberUpdatedState(p)
    val edge = with(LocalDensity.current) { 36.dp.toPx() }
    LaunchedEffect(dragged) {
        while(dragged != null) {
            val width = channelList.layoutInfo.viewportEndOffset
            val scroll = when { dragX < edge -> -edge/3; dragX > width-edge -> edge/3; else -> 0f }
            if(scroll != 0f) channelList.scrollBy(scroll)
            val x = dragX.coerceIn(0f,(width-1).coerceAtLeast(0).toFloat())
            val target = channelList.layoutInfo.visibleItemsInfo.firstOrNull { x >= it.offset && x < it.offset+it.size }
            val stems = latestProject?.stems.orEmpty()
            val current = stems.indexOfFirst { it.id == dragged }
            val followsEdge = target != null && (scroll == 0f || (scroll < 0 && target.index < current) || (scroll > 0 && target.index > current))
            if(current >= 0 && target != null && target.index in stems.indices && target.key != dragged && target.index != current && followsEdge) {
                // Keep the viewport at its numeric position while keys move. Keeping
                // the old first key visible would undo a drop at the left edge.
                channelList.requestScrollToItem(channelList.firstVisibleItemIndex,channelList.firstVisibleItemScrollOffset)
                vm.reorderStem(dragged!!,target.index)
            }
            delay(24)
        }
    }
    val files = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { if (it.isNotEmpty()) vm.import(uris = it) }
    val folder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { if (it != null) vm.import(tree = it) }
    val art = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { if (it != null) vm.artwork(it) }
    MaterialTheme(colorScheme = darkColorScheme(primary=Accent,onPrimary=Bg,primaryContainer=Control,onPrimaryContainer=White,
        secondary=Mint,onSecondary=Bg,secondaryContainer=Control,onSecondaryContainer=White,
        tertiary=Violet,onTertiary=Bg,tertiaryContainer=Control,onTertiaryContainer=White,
        background=Bg,onBackground=White,surface=Panel,onSurface=White,surfaceVariant=Control,onSurfaceVariant=Muted,
        surfaceContainer=Panel,surfaceContainerLow=Panel,surfaceContainerLowest=Bg,
        surfaceContainerHigh=Control,surfaceContainerHighest=Control,surfaceDim=Bg,surfaceBright=Control,
        surfaceTint=Color.Transparent,inversePrimary=Accent,error=Red,onError=Bg,errorContainer=Color(0xFF531F2A),onErrorContainer=White,
        outline=Color(0xFF424B55),outlineVariant=Control,inverseSurface=White,inverseOnSurface=Bg)) {
        Surface(Modifier.fillMaxSize(), color = Bg) {
            BoxWithConstraints(Modifier.safeDrawingPadding()) {
                val compact=maxHeight<500.dp
                val wide=maxWidth>=840.dp
                val tabletTransport=LocalConfiguration.current.smallestScreenWidthDp>=600 && maxWidth>=600.dp
                val mixerHeight=if(compact)450.dp else 490.dp
                Column(Modifier.fillMaxSize()) {
                    Row(Modifier.fillMaxWidth().padding(horizontal=8.dp),verticalAlignment=Alignment.CenterVertically) {
                        MenuButton { navigation=true }
                        Image(painterResource(R.drawable.brand_art),"Logo ATMyTrack",Modifier.size(28.dp))
                        Text(androidx.compose.ui.res.stringResource(R.string.app_name),(if(tabletTransport)Modifier.width(136.dp) else Modifier.weight(1f)).padding(start=4.dp),fontWeight=FontWeight.Bold,fontSize=16.sp,maxLines=1,overflow=TextOverflow.Ellipsis)
                        val toolActions=buildList {
                            if(p!=null) {
                                add(ToolAction("TOM",p.semitones!=0){pitch=true})
                                add(ToolAction("VELOCIDADE",p.speed!=100){speed=true})
                            }
                            add(ToolAction("PAD",padState.playing){padOpen=true})
                            add(ToolAction("METRÔNOMO",p?.click ?: rhythm.metronomeArmed){metronome=true})
                            add(ToolAction("PERCUSSÃO",rhythm.percussion.enabled){percussionOpen=true})
                            if(p!=null) {
                                add(ToolAction("LOOP",p.loop){vm.update { it.copy(loop=!it.loop) }})
                                add(ToolAction("+ SEÇÃO"){markers=true})
                                add(ToolAction("DCA"){group="DCA:"})
                                add(ToolAction("BUS"){group="BUS:"})
                                add(ToolAction("ROUTING"){allRouting=true})
                                add(ToolAction("ATUALIZAR SAÍDA"){vm.refreshOutput()})
                            }
                            add(ToolAction("CROSSMUSIC",cross.running){crossOpen=true})
                        }
                        if(tabletTransport)ToolsBar(toolActions,Modifier.weight(1f)) else ToolsMenu(toolActions)
                        IconButton(onClick={importDialog=true},enabled=library.busy==null,modifier=Modifier.semantics { contentDescription="Importar projeto" }) { Text("+",fontSize=26.sp,color=Accent) }
                        SearchButton { search=true }
                    }
                    if(library.busy!=null)Column(Modifier.padding(horizontal=12.dp)) {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                        Text(library.busy!!,fontSize=11.sp,color=SecondaryText)
                    }
                    if(playback.preparing)Row(Modifier.fillMaxWidth().padding(horizontal=12.dp),verticalAlignment=Alignment.CenterVertically) {
                        val total=playback.preparation.size.coerceAtLeast(1)
                        val done=playback.preparation.count { it.status=="READY" }
                        Text("Preparando áudio · $done/$total",Modifier.weight(1f).clickable { importDetails=true },fontSize=11.sp,color=SecondaryText)
                        TextButton(onClick=vm::cancelPreparation) { Text("CANCELAR",fontSize=10.sp) }
                    }
                    if(library.imports.isNotEmpty() && library.busy==null)Row(Modifier.padding(horizontal=12.dp),verticalAlignment=Alignment.CenterVertically) {
                        Text("Importação · ${library.imports.count { it.status=="ERROR" }} erros",Modifier.weight(1f),fontSize=11.sp,color=Muted)
                        TextButton(onClick={importDetails=true}){Text("DETALHES",fontSize=10.sp)}
                        IconButton(onClick=vm::dismissImportReport){Text("×")}
                    }
                    if(p==null)Column(Modifier.weight(1f).fillMaxWidth().padding(24.dp),verticalArrangement=Arrangement.Center,horizontalAlignment=Alignment.CenterHorizontally) {
                        Image(painterResource(R.drawable.brand_art),null,Modifier.size(120.dp))
                        Text("Seu multitrack.\nSeu palco. Seu controle.",fontSize=28.sp,fontWeight=FontWeight.Bold,lineHeight=36.sp)
                        Text("Importe suas faixas para criar o primeiro projeto.",Modifier.padding(vertical=20.dp),color=Muted)
                        Button(onClick={importDialog=true},enabled=library.busy==null) { Text("CRIAR PRIMEIRO PROJETO") }
                    } else {
                        val transportControls:@Composable ()->Unit = {
                        Row(Modifier.fillMaxWidth().padding(horizontal=8.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                            IconButton(onClick={vm.seek(0)},modifier=Modifier.semantics { contentDescription="Início" }) { Text("|◀",color=White) }
                            Button(onClick={if(playback.playing)vm.pause() else vm.play()},enabled=library.busy==null && playback.ready && !playback.preparing && playback.projectId==p.id,
                                modifier=Modifier.weight(1f).height(52.dp).semantics { contentDescription=if(playback.playing)"Pausar" else "Reproduzir" },
                                colors=ButtonDefaults.buttonColors(containerColor=Mint.copy(alpha=.15f),contentColor=Mint),contentPadding=PaddingValues(4.dp)) {
                                Column(horizontalAlignment=Alignment.CenterHorizontally) {
                                    Text(if(playback.playing)"Ⅱ PAUSAR" else "▶ PLAY",maxLines=1,fontSize=13.sp)
                                    if(cross.running)Text("CROSSMUSIC ${cross.index+1}/${cross.total}",fontSize=9.sp,maxLines=1)
                                }
                            }
                            if(cross.running)Checkbox(true,{endCross=true},colors=CheckboxDefaults.colors(checkedColor=Violet),modifier=Modifier.semantics { contentDescription="Encerrar CrossMusic" })
                            IconButton(onClick=vm::stop,modifier=Modifier.semantics { contentDescription="Parar" }) { Text("■",color=White) }
                            IconButton(onClick={if(playback.playing)vm.message("Pause antes de trocar de projeto.") else vm.adjacent(1)},modifier=Modifier.semantics { contentDescription="Próximo projeto" }) { Text("▶|",color=White) }
                        }
                        Text("${MusicTime.time(playback.frame.toDouble()/p.sampleRate/p.timelineScale)} / ${MusicTime.time(p.playbackSeconds)}  ·  ${"%.1f".format(p.effectiveBpm)} BPM  ·  ${p.beats}/${p.denominator}",Modifier.padding(horizontal=12.dp,vertical=4.dp),fontSize=12.sp,color=SecondaryText,maxLines=1)
                        }
                        if(tabletTransport) {
                            Row(Modifier.fillMaxWidth().padding(end=8.dp,top=4.dp,bottom=4.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                                Column(Modifier.width(300.dp)) { transportControls() }
                                Box(Modifier.weight(1f)) { Timeline(p,playback.frame,true,library.waveform,vm::seek) }
                            }
                        } else {
                            transportControls()
                            Box(Modifier.fillMaxWidth().padding(horizontal=8.dp,vertical=4.dp)) { Timeline(p,playback.frame,true,library.waveform,vm::seek) }
                        }
                        library.background?.let { Text(it,Modifier.padding(horizontal=12.dp),color=SecondaryText,fontSize=11.sp);LinearProgressIndicator(Modifier.fillMaxWidth()) }
                        if(playback.error!=null || (!playback.preparing && playback.preparation.any { it.status=="ERROR" })) Row(Modifier.padding(horizontal=12.dp)) {
                            TextButton(onClick=vm::retryPreparation){Text("TENTAR NOVAMENTE")}
                            TextButton(onClick={importDetails=true}){Text("DETALHES")}
                        }
                        Column(Modifier.weight(1f).verticalScroll(mainScroll).padding(horizontal=8.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                            ReorderableProjects(library.projects,p.id,vm,{ project ->
                                if(project.id!=p.id && playback.playing)vm.message("Pause a reprodução antes de trocar de projeto.") else vm.select(project)
                            },{project->
                                if(project.id!=p.id && playback.playing)vm.message("Pause a reprodução antes de trocar de projeto.") else {vm.select(project);edit=true}
                            },{delete=it},{importDialog=true})
                            if(p.markers.isNotEmpty())Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                                p.markers.sortedBy { it.start }.forEach { m ->
                                    Box(Modifier.clip(RoundedCornerShape(16.dp)).background(Color(m.color).copy(alpha=if(p.selectedMarker==m.id).5f else .16f))
                                        .combinedClickable(onClick={vm.seek(m.start)},onLongClick={vm.update { it.copy(selectedMarker=if(it.selectedMarker==m.id)"" else m.id) }})
                                        .semantics { selected=p.selectedMarker==m.id;contentDescription="Seção ${m.name}" }.padding(horizontal=12.dp,vertical=8.dp)) { Text(m.name,color=White,fontSize=11.sp) }
                                }
                            }
                            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                                Text("${p.stems.size} FAIXAS · ${playback.outputChannels} CANAIS",Modifier.weight(1f),color=Muted,fontSize=10.sp,letterSpacing=1.sp)
                                Text(if(playback.playing)"● REPRODUZINDO" else if(playback.ready && playback.projectId==p.id)"● PRONTO" else "● PREPARANDO",fontSize=10.sp,color=if(playback.playing)Mint else Muted)
                            }
                            Row(Modifier.fillMaxWidth().height(mixerHeight), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                LazyRow(Modifier.weight(1f).semantics { contentDescription="Canais" }.pointerInput(p.id) {
                                    awaitEachGesture {
                                        val down = awaitFirstDown(requireUnconsumed = false)
                                        if(down.position.y < 28.dp.toPx() || down.position.y > 82.dp.toPx()) return@awaitEachGesture
                                        val item = channelList.layoutInfo.visibleItemsInfo.firstOrNull { down.position.x >= it.offset && down.position.x < it.offset+it.size } ?: return@awaitEachGesture
                                        val stem = latestProject?.stems?.getOrNull(item.index) ?: return@awaitEachGesture
                                        val held = awaitLongPressOrCancellation(down.id) ?: return@awaitEachGesture
                                        dragged = stem.id; dragX = held.position.x;dragGrab=held.position.x-item.offset
                                        held.consume()
                                        try {
                                            while(true) {
                                                val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == held.id } ?: break
                                                if(!change.pressed) break
                                                dragX = change.position.x
                                                change.consume()
                                            }
                                        }
                                        finally { dragged = null }
                                    }
                                }, state = channelList, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    items(p.stems, key = { it.id }) { s ->
                                        val index = p.stems.indexOfFirst { it.id == s.id }
                                        ChannelStrip(s.name, s.volume, s.pan, s.mute, s.solo, playback.peaks.getOrElse(index) { 0f },
                                            onVolume = { v -> vm.stem(s.id) { it.copy(volume = v) } },
                                            onPan = { v -> vm.stem(s.id) { it.copy(pan = v) } },
                                            onMute = { vm.stem(s.id) { it.copy(mute = !it.mute) } },
                                            onSolo = { vm.stem(s.id) { it.copy(solo = !it.solo) } },
                                            accent=s.color?.let { Color(it) } ?: channelColor(s.name),locked=p.gainLocked(s.id),onLock={vm.lockFader(s.id)},draggable = true, dragging = dragged == s.id,
                                            routeLabel = "${p.buses.find { it.id == s.bus }?.name ?: "MASTER"} · ${s.route}",
                                            routing = { routing = s.id }, modifier=Modifier.zIndex(if(dragged==s.id)1f else 0f).graphicsLayer {
                                                if(dragged==s.id) { translationX=dragX-dragGrab-(channelList.layoutInfo.visibleItemsInfo.firstOrNull { it.key==s.id }?.offset ?: 0);scaleX=1.035f;scaleY=1.015f;shadowElevation=16.dp.toPx() }
                                            })
                                    }
                                        items(p.dcas, key = { "dca"+it.id }) { d ->
                                            ChannelStrip(d.name,d.volume,0f,d.mute,false,0f,master = true,
                                                onVolume = { v -> vm.update { it.copy(dcas=it.dcas.map { x -> if(x.id==d.id)x.copy(volume=v) else x }) } },
                                                onPan = {},onMute = { vm.update { it.copy(dcas=it.dcas.map { x -> if(x.id==d.id)x.copy(mute=!x.mute) else x }) } },onSolo = {},
                                                locked=p.gainLocked("DCA:"+d.id),onLock={vm.lockFader("DCA:"+d.id)},accent=Amber,routeLabel = "DCA · EDITAR",routing = { group = "DCA:"+d.id })
                                        }
                                        items(p.buses, key = { "bus"+it.id }) { bus ->
                                            ChannelStrip(bus.name,bus.volume,0f,bus.mute,false,playback.busPeaks[bus.id] ?: 0f,master = true,
                                                onVolume = { v -> vm.update { it.copy(buses=it.buses.map { x -> if(x.id==bus.id)x.copy(volume=v) else x }) } },
                                                onPan = {},onMute = { vm.update { it.copy(buses=it.buses.map { x -> if(x.id==bus.id)x.copy(mute=!x.mute) else x }) } },onSolo = {},
                                                locked=p.gainLocked("BUS:"+bus.id),onLock={vm.lockFader("BUS:"+bus.id)},accent=Violet,routeLabel = "BUS · EDITAR",routing = { group = "BUS:"+bus.id },outputLabel=ConsoleMath.outputChoices(playback.outputChannels).find { it.first==bus.destination }?.second ?: bus.destination)
                                        }
                                }
                                ChannelStrip("MASTER", p.master, p.masterPan, p.masterMute, false, playback.left, playback.right, true,
                                    { v -> vm.update { it.copy(master = v) } }, { v -> vm.update { it.copy(masterPan=v) } }, { vm.update { it.copy(masterMute = !it.masterMute) } }, {},locked=p.masterFaderLock,onLock={vm.lockFader("MASTER")})
                            }
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                }
            }
        }
        if(navigation) MainMenu({navigation=false}) { destination ->
            navigation=false
            when(destination) {
                MainDestination.PROJECTS -> { uiScope.launch { mainScroll.scrollTo(0) } }
                MainDestination.TUNER -> { vm.pause();vm.pad.stop();tuner=true }
                MainDestination.PAYMENT -> supporters=true
                MainDestination.HELP -> help=true
            }
        }
        if(crossOpen)CrossMusicDialog(vm) { crossOpen=false }
        if(endCross)AlertDialog(onDismissRequest={endCross=false},title={Text("Encerrar CrossMusic")},text={Text("Tem certeza que deseja encerrar o CrossMusic?")},
            confirmButton={TextButton(onClick={vm.endCrossMusic();endCross=false}){Text("Sim")}},dismissButton={TextButton(onClick={endCross=false}){Text("Não")}})
        if(tuner && !playback.playing) TunerScreen { tuner=false }
        if(search) ProjectLibraryMenu(library.projects,search,{ navigation=false;search=false },{ project ->
            if(playback.playing)vm.pause()
            vm.select(project);navigation=false;search=false
        },{ navigation=false;help=true },{ navigation=false;supporters=true })
        if(supporters) SupportersScreen { supporters=false }
        if(importDetails)AlertDialog(onDismissRequest={importDetails=false},title={Text("Resultado da importação")},text={Column(Modifier.verticalScroll(rememberScrollState())) {
            library.imports.filter { it.status=="ERROR" || playback.preparation.isEmpty() }.forEach { Text("${it.status} · ${it.name}\n${it.detail}",color=if(it.status=="ERROR")Red else SecondaryText,fontSize=12.sp) }
            playback.preparation.forEach { Text("${it.status} · ${it.name}\n${it.detail}",color=if(it.status=="ERROR")Red else SecondaryText,fontSize=12.sp) }
        }},confirmButton={TextButton(onClick={importDetails=false}){Text("FECHAR")}})
        if (importDialog) AlertDialog(onDismissRequest = { importDialog = false }, title = { Text("Adicionar suas tracks") }, text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Cada importação cria um projeto. O áudio é preparado antes de liberar PLAY. O preparo pode usar armazenamento para manter a reprodução estável; nuvem pode exigir download. Mantenha os originais disponíveis.", color = Muted)
                Button(onClick = { importDialog = false; folder.launch(null) }, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text("IMPORTAR PASTA") }
                OutlinedButton(onClick = { importDialog = false; files.launch(arrayOf("audio/*", "application/ogg")) }, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text("IMPORTAR ARQUIVOS") }
                Text("Use stems alinhadas ao mesmo início. Taxas diferentes são ajustadas durante o preparo. WAV PCM local dispensa decodificação.", fontSize = 12.sp, color = Muted)
            }
        }, confirmButton = {}, dismissButton = { TextButton(onClick = { importDialog = false }) { Text("FECHAR") } })
        if (p != null && edit) ProjectEditor(p, vm, { edit = false }, { art.launch(arrayOf("image/*")) }, { edit = false; delete = p })
        if (p != null && markers) MarkerEditor(p, playback.frame, vm, { markers = false })
        if (metronome) MetronomeEditor(p, vm) { metronome = false }
        if(padOpen) PadDialog(vm.pad) { padOpen=false }
        if(percussionOpen) PercussionDialog(vm,p) { percussionOpen=false }
        if(p!=null && speed) SpeedDialog(p,vm,library.background!=null) { speed=false }
        if (p != null && pitch) PitchDialog(p, vm, library.background != null) { pitch = false }
        if (p != null && group != null) GroupDialog(p, group!!, playback.outputChannels, vm) { group = null }
        if(p!=null && allRouting) AllRoutingDialog(p,playback.outputChannels,vm) { allRouting=false }
        if (p != null && routing != null) p.stems.find { it.id == routing }?.let { RoutingDialog(p,it,vm) { routing = null } }
        delete?.let { target ->
            AlertDialog(onDismissRequest = { delete = null }, title = { Text("Excluir projeto?") }, text = { Text("Tem certeza de que deseja excluir '${target.name}'? Os arquivos originais permanecem intactos.") },
                confirmButton = { TextButton(onClick = { vm.delete(target); delete = null }) { Text("EXCLUIR", color = Red) } },
                dismissButton = { TextButton(onClick = { delete = null }) { Text("CANCELAR") } })
        }
        val error = library.message ?: playback.error
        if (error != null) AlertDialog(onDismissRequest = vm::dismissError, title = { Text("ATMyTrack") }, text = { Text(error) }, confirmButton = { TextButton(onClick = vm::dismissError) { Text("ENTENDI") } })
        if (help) AlertDialog(onDismissRequest = { help = false }, title = { Text("ATMyTrack • 0.25.2") }, text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("1. Importe uma pasta ou selecione as stems.\n2. Ajuste volume, pan, mute e solo.\n3. Defina BPM e compasso; o CLICK acompanha a timeline.\n4. Crie seções para saltar aos trechos da música.")
                Text("A importação depende dos decoders disponíveis no Android. WAV, MP3, AAC/M4A, FLAC e OGG/Opus são tentados; arquivos incompatíveis geram erro.", color = Muted)
                Text("Saída estéreo ou canais USB anunciados pelo Android. BPM manual não muda a velocidade das stems. A unidade do BPM é a figura do denominador do compasso. Click e stems passam pelo master.", color = Muted)
                Text("TOM processa as tracks selecionadas em background, preservando a duração. Smart Click aplica o BPM ao ativar. Ferramentas reúne DCA, buses, roteamento e CrossMusic. Segure um quadro de projeto ou o nome superior do canal para reordenar.", color = Muted)
                Text("VELOCIDADE altera o tempo preservando o tom. Use todas as tracks para manter a sincronização. O preparo ocorre antes da troca; extremos podem ter mais artefatos. AFINADOR usa o microfone apenas nesta tela e pausa o player.", color = Muted)
                Text("Bibliotecas: AndroidX/Compose, Kotlin e Coroutines (Apache 2.0). Pitch: Signalsmith Stretch e Linear (MIT). Identidade visual original ATMyTrack.", fontSize = 12.sp)
            }
        }, confirmButton = { TextButton(onClick = { help = false }) { Text("FECHAR") } })
    }
}

@Composable
internal fun SmallButton(label: String, active: Boolean = false, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, if (active) Accent else Control),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = if (active) Accent.copy(alpha = .12f) else Panel),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp), modifier = Modifier.heightIn(min = 48.dp)) {
        Text(label, color = if (active) Accent else White, fontSize = 12.sp, maxLines = 1)
    }
}

@Composable
internal fun ProjectCard(p: Project, selected: Boolean, compact: Boolean, remove: () -> Unit, onEdit:(()->Unit)?=null,modifier:Modifier=Modifier,onClick: () -> Unit) {
    val context = LocalContext.current
    val bitmap by produceState<ImageBitmap?>(null, p.artwork) {
        value = if (p.artwork.isBlank()) null else withContext(Dispatchers.IO) { BitmapFactory.decodeFile(File(context.filesDir, p.artwork).path)?.asImageBitmap() }
    }
    Box(modifier.width(210.dp).height(104.dp).clip(RoundedCornerShape(12.dp))
        .background(Brush.linearGradient(listOf(Control, Panel)))
        .border(1.dp, if (selected) Accent else Control, RoundedCornerShape(12.dp)).clickable(onClick = onClick).semantics { this.selected = selected;contentDescription="Projeto ${p.name}" }) {
        bitmap?.let { Image(it, null, Modifier.matchParentSize(), contentScale = ContentScale.Crop, alpha = .28f) }
        Column(Modifier.align(Alignment.TopEnd)) {
            IconButton(onClick=remove,modifier=Modifier.size(48.dp).semantics { contentDescription="Excluir ${p.name}" }) { Text("−",color=SecondaryText,fontSize=22.sp) }
            if(onEdit!=null)IconButton(onClick=onEdit,modifier=Modifier.size(48.dp).semantics { contentDescription="Editar ${p.name}" }) { Text("✎",color=SecondaryText,fontSize=21.sp) }
        }
        Column(Modifier.padding(start=12.dp,end=40.dp,top=12.dp,bottom=12.dp).fillMaxSize(), verticalArrangement = Arrangement.Bottom) {
            Text(p.name, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text("${p.key}  •  ${p.effectiveBpm.roundToInt()} BPM  •  ${MusicTime.time(p.playbackSeconds)}", color = SecondaryText, fontSize = 11.sp)
        }
    }
}

@Composable
private fun Timeline(p: Project, frame: Long, compact: Boolean, peaks: List<Float>, seek: (Long) -> Unit) {
    val callback by rememberUpdatedState(seek)
    val total = p.timelineFrames.coerceAtLeast(1)
    var preview by remember(p.id) { mutableStateOf<Long?>(null) }
    var dragging by remember(p.id) { mutableStateOf(false) }
    val display=preview ?: frame
    LaunchedEffect(frame,dragging) { if(!dragging && preview!=null && abs(frame-preview!!)<p.sampleRate/2)preview=null }
    LaunchedEffect(preview,dragging) { if(!dragging && preview!=null) { delay(1000);preview=null } }
    Column(Modifier.clip(RoundedCornerShape(10.dp)).background(Panel).padding(if (compact) 8.dp else 12.dp)) {
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
            Text("TIMELINE", fontSize = 10.sp, color = Muted, letterSpacing = 2.sp)
            if(peaks.isEmpty())Text("ANALISANDO ÁUDIO",fontSize=9.sp,color=Muted)
        }
        val maximum=remember(peaks) { peaks.filter { it.isFinite() }.maxOrNull()?.coerceAtLeast(.001f) ?: 1f }
        Canvas(Modifier.fillMaxWidth().height(if (compact) 48.dp else 64.dp)
            .semantics { contentDescription = "Posição da música"; progressBarRangeInfo = ProgressBarRangeInfo(display.toFloat(), 0f..total.toFloat()); setProgress { callback(it.toLong()); true } }
            .pointerInput(total) { detectTapGestures { callback((it.x / size.width * total).toLong().coerceIn(0, total)) } }
            .pointerInput(total) { detectDragGestures(
                onDragStart={ dragging=true;preview=(it.x/size.width*total).toLong().coerceIn(0,total) },
                onDragEnd={ dragging=false;preview?.let(callback) },
                onDragCancel={ dragging=false;preview=null }
            ) { change, _ -> change.consume();preview=(change.position.x/size.width*total).toLong().coerceIn(0,total) } }) {
            val w = size.width; val h = size.height
            val center=h*.47f
            for (i in 0..8) { val x = w * i / 8; drawLine(Control.copy(alpha=.6f), Offset(x, 3.dp.toPx()), Offset(x, h-5.dp.toPx()), 1.dp.toPx()) }
            drawLine(Control,Offset(0f,center),Offset(w,center),1.dp.toPx())
            p.markers.forEach { m -> drawRect(Color(m.color).copy(alpha = .16f), Offset(w * m.start / total, 2f), Size((w * (m.end - m.start) / total).coerceAtLeast(2f), h-4f)) }
            val x = (w * display / total).coerceIn(0f,w)
            if(peaks.isNotEmpty()) {
                val width=w*p.frames/total
                val bars=(width/3.5.dp.toPx()).toInt().coerceIn(1,peaks.size)
                val step=width/bars
                repeat(bars) { i ->
                    val first=i*peaks.size/bars;val end=((i+1)*peaks.size/bars).coerceAtLeast(first+1)
                    var energy=0.0
                    for(j in first until end) { val v=peaks[j].takeIf { it.isFinite() } ?: 0f;energy+=v.toDouble()*v }
                    val amplitude=(sqrt(energy/(end-first)).toFloat()/maximum).coerceIn(0f,1f).pow(1.35f)
                    val halfHeight=(amplitude*h*.40f).coerceAtLeast(.5.dp.toPx())
                    val barX=step*(i+.5f)
                    drawLine(if(barX<=x)Mint.copy(alpha=.85f) else Color(0xFF7895AB),Offset(barX,center-halfHeight),Offset(barX,center+halfHeight),minOf(2.dp.toPx(),step*.62f),StrokeCap.Round)
                }
            }
            drawLine(Control,Offset(0f,h-2.dp.toPx()),Offset(w,h-2.dp.toPx()),2.dp.toPx())
            drawLine(Mint.copy(alpha=.55f),Offset(0f,h-2.dp.toPx()),Offset(x,h-2.dp.toPx()),2.dp.toPx())
            drawLine(Accent, Offset(x, 0f), Offset(x, h), 2.dp.toPx())
            drawCircle(Accent, 3.dp.toPx(), Offset(x, h-2.dp.toPx()))
        }
    }
}

@Composable
private fun ChannelStrip(name: String, volume: Float, pan: Float, mute: Boolean, solo: Boolean, peak: Float,
    right: Float = peak, master: Boolean = false, onVolume: (Float) -> Unit, onPan: (Float) -> Unit, onMute: () -> Unit, onSolo: () -> Unit, draggable: Boolean = false, dragging: Boolean = false, routeLabel: String? = null, routing: () -> Unit = {}, modifier:Modifier=Modifier,outputLabel:String="OUT 1–2",locked:Boolean=false,onLock:()->Unit={},accent:Color=channelColor(name)) {
    Column(modifier.width(if (master) 100.dp else 124.dp).fillMaxHeight().clip(RoundedCornerShape(10.dp)).background(if (master) Control else Panel).padding(7.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        TextButton(onClick=onLock,modifier=Modifier.height(24.dp).semantics { contentDescription="Cadeado $name";selected=locked },contentPadding=PaddingValues(0.dp)) { Canvas(Modifier.size(16.dp)) {
            val c=if(locked)LockRed else Muted
            drawRoundRect(c,Offset(size.width*.15f,size.height*.45f),Size(size.width*.7f,size.height*.5f),androidx.compose.ui.geometry.CornerRadius(2.dp.toPx()),style=androidx.compose.ui.graphics.drawscope.Stroke(1.5.dp.toPx()))
            drawArc(c,if(locked)180f else 220f,if(locked)180f else 140f,false,Offset(size.width*.3f,size.height*.05f),Size(size.width*.4f,size.height*.65f),style=androidx.compose.ui.graphics.drawscope.Stroke(1.5.dp.toPx()))
        } }
        Box(Modifier.fillMaxWidth().height(3.dp).background(accent, RoundedCornerShape(2.dp)))
        Text(name, Modifier.fillMaxWidth().height(40.dp).background(accent.copy(alpha=if(dragging).35f else .12f)).semantics { contentDescription = if(draggable) "Arrastar $name" else name }.padding(4.dp), textAlign=TextAlign.Center,maxLines = 2,lineHeight=14.sp,overflow=TextOverflow.Ellipsis, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = accent)
        Text(if (master && routeLabel!=null) "OUTPUT" else when { pan < -.05f -> "L ${(abs(pan) * 100).roundToInt()}"; pan > .05f -> "R ${(pan * 100).roundToInt()}"; else -> "PAN · C" }, color = Muted, fontSize = 10.sp)
        if (master && routeLabel!=null) Box(Modifier.height(48.dp), contentAlignment = Alignment.Center) { Text(if(routeLabel?.startsWith("DCA")==true) "RELATIVO" else outputLabel, color = SecondaryText, fontSize = 11.sp) }
        else PanKnob(pan,name,accent,locked,onPan)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            if (!master) ChannelToggle("S", solo, Accent, "Solo $name", Modifier.weight(1f), onSolo)
            ChannelToggle("M", mute, Amber, "Mute $name", Modifier.weight(1f), onMute)
        }
        var fine by remember { mutableStateOf(false) }
        Text(if (volume <= 0f) "−∞ dB" else "%+.1f dB".format(20 * log10(volume)), color = White, fontSize = 11.sp,
            modifier = Modifier.fillMaxWidth().heightIn(min=24.dp).clickable(enabled=!locked) { fine=true }.padding(top = 8.dp).semantics { contentDescription="Ajuste fino $name" })
        if(fine) FineGainDialog(name,volume,onVolume) { fine=false }
        Row(Modifier.weight(1f).padding(vertical = 6.dp), horizontalArrangement = Arrangement.Center) {
            Fader(volume, name, Modifier.weight(1f).fillMaxHeight(),accent) { if(!locked)onVolume(it) }
            Meter(peak, Modifier.width(5.dp).fillMaxHeight())
            if (master) { Spacer(Modifier.width(3.dp)); Meter(right, Modifier.width(5.dp).fillMaxHeight()) }
        }
        Column(Modifier.fillMaxWidth().height(48.dp),horizontalAlignment=Alignment.CenterHorizontally) {
            TextButton(enabled=!locked,onClick={onVolume(1f)},modifier=Modifier.fillMaxWidth().height(24.dp).semantics { contentDescription="0dB $name" },contentPadding=PaddingValues(0.dp)) { Text("0dB",fontSize=12.sp) }
            Box(Modifier.fillMaxWidth().height(24.dp),contentAlignment=Alignment.Center) {
                if(routeLabel!=null)TextButton(onClick=routing,modifier=Modifier.fillMaxSize(),contentPadding=PaddingValues(0.dp)) { Text(routeLabel,fontSize=10.sp,maxLines=2) }
                else if(master)Text(outputLabel,color=Muted,fontSize=9.sp)
            }
        }

    }
}

@Composable
private fun ChannelToggle(label: String, active: Boolean, color: Color, description: String, modifier: Modifier, onClick: () -> Unit) {
    Box(modifier.height(48.dp).clip(RoundedCornerShape(7.dp)).background(if (active) color else Control).clickable(onClick = onClick)
        .semantics { contentDescription = description; selected = active }, contentAlignment = Alignment.Center) {
        Text(label, color = if (active) Bg else Muted, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun Fader(value: Float, name: String, modifier: Modifier, accent:Color, change: (Float) -> Unit) {
    val callback by rememberUpdatedState(change)
    val position = ConsoleMath.position(value)
    val paint=remember { android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG) }
    val compactLabels=remember { setOf(10f,0f,-10f,-20f,-40f,-80f) }
    Canvas(modifier.semantics { contentDescription = "Volume $name"; stateDescription=if(value==0f)"Silêncio" else "%.1f dB".format(ConsoleMath.db(value)); progressBarRangeInfo = ProgressBarRangeInfo(position, 0f..1f); setProgress { callback(ConsoleMath.fromPosition(it)); true }; customActions=listOf(CustomAccessibilityAction("Unity gain · 0 dB") { callback(1f); true }) }
        .pointerInput(Unit) { detectTapGestures(onDoubleTap = { callback(1f) }, onTap = { callback(ConsoleMath.fromPosition(1f-(it.y-10.dp.toPx())/(size.height-20.dp.toPx()))) }) }
        .pointerInput(Unit) { detectDragGestures { event, _ -> event.consume(); callback(ConsoleMath.fromPosition(1f-(event.position.y-10.dp.toPx())/(size.height-20.dp.toPx()))) } }) {
        val mid = size.width*.72f
        val usable = size.height-20.dp.toPx()
        val y = (1-position)*usable+10.dp.toPx()
        drawLine(Bg, Offset(mid, 0f), Offset(mid, size.height), 6.dp.toPx())
        paint.textSize=9.sp.toPx()
        val detailed=usable>=150.dp.toPx()
        ConsoleMath.points.forEach { (pos,db) ->
            val tick=(1-pos)*usable+10.dp.toPx()
            val color=if(db==0f) SecondaryText else Muted
            drawLine(color,Offset(mid-8.dp.toPx(),tick),Offset(mid+8.dp.toPx(),tick),if(db==0f)2.dp.toPx() else 1.dp.toPx())
            paint.color=color.toArgb()
            if(detailed || db in compactLabels)
                drawContext.canvas.nativeCanvas.drawText(if(db == -80f)"−∞" else if(db>0)"+${db.toInt()}" else db.toInt().toString(),0f,tick+3.dp.toPx(),paint)
        }
        drawRoundRect(Color(0xFF454E59), Offset(mid-12.dp.toPx(), y-8.dp.toPx()), Size(24.dp.toPx(),16.dp.toPx()), androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()))
        drawLine(accent,Offset(mid-10.dp.toPx(),y),Offset(mid+10.dp.toPx(),y),2.dp.toPx())
    }
}

@Composable
private fun Meter(peak: Float, modifier: Modifier) {
    Canvas(modifier) {
        drawRect(Control)
        val normalized = if (peak <= .0001f) 0f else ((20 * log10(peak) + 60) / 60).coerceIn(0f, 1f)
        val h = size.height * normalized
        drawRect(if (peak >= 1f) Red else if(peak>.7f)Amber else Mint, Offset(0f, size.height - h), Size(size.width, h))
    }
}

@Composable
private fun ProjectEditor(p:Project,vm:PlayerViewModel,close:()->Unit,artwork:()->Unit,delete:()->Unit) {
    var name by remember(p.id) { mutableStateOf(p.name) }
    var key by remember(p.id) { mutableStateOf(p.key) }
    var keyEdited by remember(p.id) { mutableStateOf(false) }
    var tracks by remember(p.id) { mutableStateOf(p.stems) }
    var coloring by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(p.key) { if(!keyEdited)key=p.key }
    AlertDialog(onDismissRequest=close,title={Text("Editar projeto")},text={
        Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(name,{name=it},label={Text("Nome")},singleLine=true)
            OutlinedTextField(key,{key=it;keyEdited=true},label={Text("Tonalidade (informação)")},singleLine=true)
            SmallButton(if(p.artwork.isBlank())"ADICIONAR IMAGEM" else "ALTERAR IMAGEM",onClick=artwork)
            if(p.artwork.isNotBlank()) { ProjectCard(p,true,true,{},onClick={});TextButton(onClick={vm.update { it.copy(artwork="") }}) { Text("REMOVER IMAGEM") } }
            Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                SmallButton("PROJETO ←",onClick={vm.moveProject(p.id,-1)});SmallButton("PROJETO →",onClick={vm.moveProject(p.id,1)})
            }
            Text("Ordem e cores das tracks",color=Muted)
            tracks.forEachIndexed { index,stem -> Row(verticalAlignment=Alignment.CenterVertically) {
                IconButton(onClick={coloring=stem.id},modifier=Modifier.semantics { contentDescription="Cor da faixa ${stem.name}" }) { Canvas(Modifier.size(20.dp)) { drawCircle(stem.color?.let { Color(it) } ?: channelColor(stem.name)) } }
                Text(stem.name,Modifier.weight(1f),maxLines=1,overflow=TextOverflow.Ellipsis,fontSize=12.sp)
                TextButton(onClick={tracks=tracks.toMutableList().apply { java.util.Collections.swap(this,index,index-1) }},enabled=index>0,contentPadding=PaddingValues(4.dp)) { Text("↑") }
                TextButton(onClick={tracks=tracks.toMutableList().apply { java.util.Collections.swap(this,index,index+1) }},enabled=index<tracks.lastIndex,contentPadding=PaddingValues(4.dp)) { Text("↓") }
            } }
        }
    },confirmButton={TextButton(onClick={
        vm.update { current->current.copy(name=name.trim(),key=if(keyEdited)key.trim() else current.key,keyAnalyzed=keyEdited || current.keyAnalyzed,
            stems=tracks.mapNotNull { draft->current.stems.find { it.id==draft.id }?.copy(color=draft.color) }+current.stems.filter { live->tracks.none { it.id==live.id } }) }
        close()
    },enabled=name.isNotBlank()){Text("SALVAR")}},dismissButton={TextButton(onClick=close){Text("FECHAR")}})
    coloring?.let { id -> tracks.find { it.id==id }?.let { stem ->
        AlertDialog(onDismissRequest={coloring=null},title={Text("Cor · ${stem.name}")},text={Column { SectionColors(stem.color ?: (channelColor(stem.name).toArgb().toLong() and 0xFFFFFFFF),"Cor da faixa") { color->tracks=tracks.map { if(it.id==id)it.copy(color=color) else it } } }},
            confirmButton={TextButton(onClick={coloring=null}){Text("PRONTO")}},dismissButton={TextButton(onClick={tracks=tracks.map { if(it.id==id)it.copy(color=null) else it };coloring=null}){Text("AUTOMÁTICA")}})
    } }
}

@Composable
private fun MetronomeEditor(project: Project?, vm: PlayerViewModel, close: () -> Unit) {
    val rhythm by vm.rhythm.state.collectAsState()
    val p=project ?: rhythm.metronome
    fun change(transform:(Project)->Project) { if(project==null)vm.rhythm.configureMetronome(transform) else vm.update(transform) }
    val tap=remember { TapTempo() }
    val active=p.click && (p.clickFollow || rhythm.metronomeArmed)
    var bpm by remember(p.bpm) { mutableStateOf("%.1f".format(java.util.Locale.US, p.bpm)) }
    val library by vm.library.collectAsState()
    val valid = bpm.replace(',', '.').toDoubleOrNull()?.let { it in 30.0..300.0 } == true
    AlertDialog(onDismissRequest = close, title = { Text("Metrônomo") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically) {
                Checkbox(rhythm.linked,{vm.rhythm.setLinked(it)},modifier=Modifier.semantics { contentDescription="Sincronizar com percussão" })
                Text("Sincronizar com percussão")
            }
            Row(verticalAlignment=Alignment.CenterVertically) {
                Checkbox(p.clickFollow && project!=null,{ follow -> vm.rhythm.armMetronome(active);change { it.copy(clickFollow=follow) } },enabled=project!=null,
                    modifier=Modifier.semantics { contentDescription="Acompanhar música do metrônomo" })
                Text("Acompanhar música")
            }
            Text(if(p.clickFollow && project!=null)"O click segue o transporte da música." else "O click toca com a música pausada ou parada, sem iniciar o multitrack.",fontSize=12.sp,color=Muted)
            Row(verticalAlignment=Alignment.CenterVertically) {
                SmallButton("TAP",onClick={ if(project!=null)vm.tap() else tap.tap(android.os.SystemClock.elapsedRealtime())?.let { value -> change { it.copy(bpm=value) } } })
                if(project!=null) {
                Checkbox(p.smartClick,{vm.smartClick(it)},enabled=library.background==null || p.smartClick,modifier=Modifier.semantics { contentDescription="Smart Click" })
                Text("Smart Click",fontSize=12.sp)
                }
            }
            OutlinedTextField(bpm, { bpm = it }, label = { Text("BPM • 30 a 300") }, singleLine = true, isError = !valid,
                modifier=Modifier.semantics { contentDescription="BPM do metrônomo" })
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SmallButton("−", onClick = { bpm = ((bpm.toDoubleOrNull() ?: p.bpm) - 1).coerceAtLeast(30.0).toString() })
                SmallButton("+", onClick = { bpm = ((bpm.toDoubleOrNull() ?: p.bpm) + 1).coerceAtMost(300.0).toString() })
            }
            Text(if(p.clickFollow)"Original: %.1f BPM · Atual: %.1f BPM".format(p.bpm,p.effectiveBpm) else "Independente: %.1f BPM".format(p.bpm),color=SecondaryText)
            Text("Compasso", color = Muted)
            Choice("Click sound", p.clickSound, listOf("Classic","Digital","Wood","Woodblock","Cowbell","Soft","High Tick","Low Tick").map { it to it }) { v -> change { it.copy(clickSound = v) } }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(p.accent, { v -> change { it.copy(accent = v) } }); Text("Acentuar primeira batida", fontSize = 12.sp)
            }
            Choice("Saída do click", p.clickRoute, listOf("BOTH","LEFT","RIGHT").map { it to it }) { v -> change { it.copy(clickRoute = v) } }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(2 to 4, 3 to 4, 4 to 4, 6 to 8, 7 to 8).forEach { (beats, denominator) ->
                    SmallButton("$beats/$denominator", p.beats == beats && p.denominator == denominator, { change { it.copy(beats = beats, denominator = denominator) } })
                }
            }
            Text("Subdivisão • não altera a velocidade das stems", color = Muted, fontSize = 12.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(.5, 1.0, 2.0).forEach { m -> SmallButton("${m}x", p.multiplier == m, { change { it.copy(multiplier = m) } }) }
            }
            Text("Volume do click", color = Muted)
            Slider(p.clickVolume, { v -> change { it.copy(clickVolume = v) } }, modifier = Modifier.semantics { contentDescription = "Volume do metrônomo" })
            if(p.detectedBpm>0)Text("Detectado: %.2f BPM · confiança %d%%".format(p.detectedBpm,(p.confidence*100).roundToInt()),color=SecondaryText,fontSize=12.sp)
            library.background?.let { Text(it,color=SecondaryText,fontSize=12.sp) }
            if(project!=null)Text("Smart Click aplica o BPM detectado quando ativado. Com Acompanhar música ativo, Play também inicia o transporte. Com o vínculo ativo, Play/Pausar controla os dois ritmos.",fontSize=12.sp,color=Muted)
            rhythm.error?.let { Text(it,color=Red,fontSize=12.sp) }
        }
    }, confirmButton = { Row(horizontalArrangement=Arrangement.spacedBy(10.dp),verticalAlignment=Alignment.CenterVertically) {
        Button(shape=RoundedCornerShape(10.dp),colors=ButtonDefaults.buttonColors(containerColor=Control,contentColor=Accent),modifier=Modifier.heightIn(min=48.dp).semantics { contentDescription="Reprodução do metrônomo" },onClick={val enable=!active;vm.rhythm.armMetronome(enable);change { it.copy(bpm=bpm.replace(',','.').toDouble(),click=enable) };if(enable && p.clickFollow && project!=null && !vm.playback.value.playing)vm.play()},enabled=valid && library.background==null) { Text(if(active) "Ⅱ  PAUSAR" else "▶  PLAY",fontWeight=FontWeight.Bold) }
        TextButton(onClick={if(valid)change { it.copy(bpm=bpm.replace(',','.').toDouble()) };close()}) { Text("FECHAR") }
    } })
}

private fun parseTime(value: String): Double? {
    val parts = value.replace(',', '.').split(':')
    return if (parts.size == 1) parts[0].toDoubleOrNull() else if (parts.size == 2) {
        val min = parts[0].toDoubleOrNull(); val sec = parts[1].toDoubleOrNull()
        if (min == null || sec == null || sec !in 0.0..<60.0) null else min * 60 + sec
    } else null
}

@Composable
private fun MarkerEditor(p: Project, frame: Long, vm: PlayerViewModel, close: () -> Unit) {
    val library by vm.library.collectAsState()
    var name by remember { mutableStateOf("") }
    var start by remember { mutableStateOf("%.2f".format(java.util.Locale.US, frame.toDouble() / p.sampleRate)) }
    var end by remember { mutableStateOf("%.2f".format(java.util.Locale.US, minOf(p.seconds, frame.toDouble() / p.sampleRate + 16))) }
    var color by remember { mutableLongStateOf(0xFF89ACF2) }
    val first = parseTime(start)?.times(p.sampleRate)?.toLong()
    val last = parseTime(end)?.times(p.sampleRate)?.toLong()
    val valid = name.isNotBlank() && first != null && last != null && MusicTime.validMarker(first, last, p.frames)
    AlertDialog(onDismissRequest = close, title = { Text("Seções da música") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Timeline(p,frame,true,library.waveform,vm::seek)
            Text("Posição: %.3f s / %.3f s".format(frame.toDouble()/p.sampleRate,p.seconds),color=SecondaryText)
            Row { TextButton(onClick={start="%.3f".format(java.util.Locale.US,frame.toDouble()/p.sampleRate)}) { Text("MARCAR INÍCIO") };TextButton(onClick={end="%.3f".format(java.util.Locale.US,frame.toDouble()/p.sampleRate)}) { Text("MARCAR FIM") } }
            OutlinedTextField(name, { name = it }, label = { Text("Nome • INTRO, VERSO, REFRÃO…") }, singleLine = true)
            OutlinedTextField(start, { start = it }, label = { Text("Início • segundos ou mm:ss") }, singleLine = true)
            OutlinedTextField(end, { end = it }, label = { Text("Fim • segundos ou mm:ss") }, singleLine = true)
            SectionColors(color) { color=it }
            if (!valid) Text("Informe um nome e um intervalo dentro da música.", color = Muted, fontSize = 12.sp)
            p.markers.sortedBy { it.start }.forEach { m -> Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${m.name}\n${MusicTime.time(m.start.toDouble()/p.sampleRate)} → ${MusicTime.time(m.end.toDouble()/p.sampleRate)}", Modifier.weight(1f), fontSize = 12.sp)
                TextButton(onClick = { vm.update { it.copy(markers = it.markers.filterNot { old -> old.id == m.id }) } }) { Text("REMOVER", color = Red, fontSize = 11.sp) }
            } }
        }
    }, confirmButton = { TextButton(onClick = { vm.update { it.copy(markers = it.markers + Marker(name = name.trim(), start = first!!, end = last!!, color = color)) }; close() }, enabled = valid) { Text("ADICIONAR") } }, dismissButton = { TextButton(onClick = close) { Text("FECHAR") } })
}
