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

internal val Bg = Color(0xFF0B0D12)
internal val Panel = Color(0xFF121620)
internal val Control = Color(0xFF191E2A)
internal val Blue = Color(0xFF3B82F6)
internal val LightBlue = Color(0xFF60A5FA)
internal val Muted = Color(0xFF94A3B8)
internal val White = Color(0xFFF8FAFC)
internal val Red = Color(0xFFFF777C)

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
    var page by rememberSaveable { mutableIntStateOf(0) }
    var pitch by remember { mutableStateOf(false) }
    var speed by remember { mutableStateOf(false) }
    var tuner by remember { mutableStateOf(false) }
    var group by remember { mutableStateOf<String?>(null) }
    var routing by remember { mutableStateOf<String?>(null) }
    var metronome by remember { mutableStateOf(false) }
    val channelList = rememberLazyListState()
    val projectList = rememberLazyListState()
    val mainScroll=rememberScrollState()
    val uiScope=rememberCoroutineScope()
    LaunchedEffect(library.selected,library.projects.size) {
        val index=library.projects.indexOfFirst { it.id==library.selected }
        if(index>=0)projectList.scrollToItem(index)
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
    MaterialTheme(colorScheme = darkColorScheme(primary=Blue,onPrimary=White,primaryContainer=Color(0xFF1D4ED8),onPrimaryContainer=White,
        secondary=LightBlue,onSecondary=Bg,secondaryContainer=Control,onSecondaryContainer=White,
        tertiary=Color(0xFF60A5FA),onTertiary=Bg,tertiaryContainer=Control,onTertiaryContainer=White,
        background=Bg,onBackground=White,surface=Panel,onSurface=White,surfaceVariant=Control,onSurfaceVariant=Muted,
        surfaceContainer=Panel,surfaceContainerLow=Panel,surfaceContainerLowest=Bg,
        surfaceContainerHigh=Control,surfaceContainerHighest=Control,surfaceDim=Bg,surfaceBright=Control,
        surfaceTint=Blue,inversePrimary=Blue,error=Red,onError=Bg,errorContainer=Color(0xFF531F2A),onErrorContainer=White,
        outline=Muted,outlineVariant=Control,inverseSurface=White,inverseOnSurface=Bg)) {
        Surface(Modifier.fillMaxSize(), color = Bg) {
            BoxWithConstraints(Modifier.safeDrawingPadding()) {
                val screenWidth=maxWidth
                val compact = maxHeight < 500.dp
                val wide = maxWidth >= 840.dp
                val narrow = maxWidth < 400.dp
                val mixerHeight = when { compact->410.dp;wide && page==1->(maxHeight-302.dp).coerceAtLeast(450.dp);else->470.dp }
                Column(Modifier.fillMaxSize()) {
                    if(p==null || (!wide && !compact)) Row(Modifier.fillMaxWidth().padding(horizontal = if (narrow) 8.dp else 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        MenuButton { navigation=true }
                        Image(painterResource(R.drawable.brand_art), "Logo ATMyTrack", Modifier.size(if (narrow) 28.dp else 36.dp))
                        Text("ATMyTrack", fontWeight = FontWeight.Bold, fontSize = if (narrow) 16.sp else 20.sp)
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = { importDialog = true }, enabled = library.busy == null) { Text(if (narrow) "IMPORTAR" else "+ IMPORTAR") }
                        SearchButton { search=true }
                    }
                    if (library.busy != null) {
                        Column(Modifier.fillMaxWidth().padding(horizontal=12.dp,vertical=4.dp)) {
                            LinearProgressIndicator(Modifier.fillMaxWidth(), color = Blue)
                            Text(library.busy!!, Modifier.padding(top = 4.dp), color = Muted,fontSize=11.sp)
                        }
                    }
                    if(library.imports.isNotEmpty() && library.busy!=null) {
                        Column(Modifier.fillMaxWidth().heightIn(max=140.dp).verticalScroll(rememberScrollState()).padding(horizontal=12.dp)) {
                            Row(verticalAlignment=Alignment.CenterVertically) {
                                Text("${library.imports.count { it.status!="DISCOVERED" }} / ${library.imports.size} tracks",Modifier.weight(1f),fontSize=11.sp,color=Muted)
                                if(library.busy==null)TextButton(onClick=vm::dismissImportReport){Text("FECHAR RELATÓRIO",fontSize=10.sp)}
                            }
                            library.imports.forEach { item -> Text("${item.status} · ${item.name}${if(item.detail.isEmpty())"" else " · ${item.detail}"}",color=if(item.status=="ERROR")Red else if(item.status=="READY")Blue else Muted,fontSize=11.sp) }
                        }
                    }
                    if(library.imports.isNotEmpty() && library.busy==null)Row(Modifier.padding(horizontal=12.dp),verticalAlignment=Alignment.CenterVertically) {
                        Text("${library.imports.count { it.status=="DISCOVERED" }} localizadas · ${library.imports.count { it.status=="ERROR" }} erros",Modifier.weight(1f),fontSize=11.sp,color=Muted)
                        TextButton(onClick={importDetails=true}){Text("DETALHES",fontSize=10.sp)}
                        TextButton(onClick=vm::dismissImportReport){Text("×")}
                    }
                    if(playback.preparing) {
                        val done=playback.preparation.count { it.status=="READY" }
                        Column(Modifier.fillMaxWidth().padding(horizontal=12.dp)) {
                            LinearProgressIndicator(progress={done.toFloat()/playback.preparation.size.coerceAtLeast(1)},modifier=Modifier.fillMaxWidth())
                            Row(verticalAlignment=Alignment.CenterVertically) {
                                Text("PREPARANDO ÁUDIO · $done / ${playback.preparation.size}",Modifier.weight(1f).clickable { importDetails=true },fontSize=11.sp,color=Blue)
                                TextButton(onClick=vm::cancelPreparation) { Text("CANCELAR",fontSize=10.sp) }
                            }
                            playback.preparation.firstOrNull { it.status=="PREPARING" }?.let { Text("${it.name} · ${it.detail}",fontSize=11.sp,color=Muted,maxLines=1) }
                        }
                    } else if(playback.ready && playback.projectId==p?.id) {
                        Text("MULTITRACK PRONTA · DETALHES",Modifier.padding(horizontal=12.dp).clickable { importDetails=true },fontSize=10.sp,maxLines=1,color=Blue)
                    }
                    if(!playback.preparing && playback.preparation.any { it.status=="ERROR" }) {
                        Row(Modifier.fillMaxWidth().padding(horizontal=12.dp),verticalAlignment=Alignment.CenterVertically) {
                            val failed=playback.preparation.first { it.status=="ERROR" }
                            Text("${failed.name}: ${failed.detail}",Modifier.weight(1f),fontSize=11.sp,color=Red,maxLines=2)
                            TextButton(onClick=vm::retryPreparation) { Text("TENTAR NOVAMENTE",fontSize=10.sp) }
                            TextButton(onClick={vm.removeTrack(failed.id)}) { Text("REMOVER TRACK",fontSize=10.sp) }
                        }
                    }
                    if (p == null) {
                        Column(Modifier.weight(1f).fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                            Image(painterResource(R.drawable.brand_art), null, Modifier.size(120.dp))
                            Text("Seu multitrack.\nSeu palco. Seu controle.", fontSize = 28.sp, fontWeight = FontWeight.Bold, lineHeight = 36.sp)
                            Text("Suas stems, juntas do primeiro ao último compasso.", Modifier.padding(vertical = 20.dp), color = Muted)
                            Button(onClick = { importDialog = true }, enabled = library.busy == null, modifier = Modifier.heightIn(min = 56.dp)) { Text("CRIAR PRIMEIRO PROJETO") }
                        }
                    } else {
                            Row(Modifier.fillMaxWidth().padding(horizontal=12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                if(wide || compact) {
                                    MenuButton { navigation=true }
                                    Text("ATMyTrack",fontWeight=FontWeight.Bold,fontSize=15.sp)
                                }
                                SmallButton("|◀", onClick = { vm.seek(0) })
                                Button(onClick = { if(playback.playing)vm.pause() else vm.play() }, enabled = library.busy == null && playback.ready && !playback.preparing && playback.projectId==p.id,
                                    modifier = Modifier.weight(1f).height(52.dp), contentPadding = PaddingValues(4.dp)) {
                                    Text(if(playback.playing) "Ⅱ PAUSAR" else "▶ PLAY", maxLines = 1)
                                }
                                SmallButton("■", onClick = vm::stop)
                                SmallButton("▶|", onClick = { if(playback.playing)vm.message("Pause antes de trocar de projeto.") else vm.adjacent(1) })
                                if(wide || compact) Column(Modifier.padding(horizontal=8.dp)) {
                                    Text("${MusicTime.time(playback.frame.toDouble()/p.sampleRate/p.timelineScale)} / ${MusicTime.time(p.playbackSeconds)}", color=Blue)
                                    Text("${"%.1f".format(p.effectiveBpm)} BPM · ${p.beats}/${p.denominator}", color=Muted, fontSize=12.sp)
                                }
                                if(wide || compact) {
                                    TextButton(onClick={importDialog=true},enabled=library.busy==null) { Text("IMPORTAR",fontSize=11.sp) }
                                    SearchButton { search=true }
                                }
                            }
                            if(!wide && !compact) Text("${MusicTime.time(playback.frame.toDouble()/p.sampleRate/p.timelineScale)} / ${MusicTime.time(p.playbackSeconds)}    •    ${"%.1f".format(p.effectiveBpm)} BPM    •    ${p.beats}/${p.denominator}", color = Blue)

                        Column(Modifier.weight(1f).verticalScroll(mainScroll).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            LazyRow(Modifier.semantics { contentDescription="Projetos" },state=projectList,horizontalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(vertical = 4.dp)) {
                                items(library.projects, key = { it.id }) { project ->
                                    ProjectCard(project, project.id == p.id, compact || wide, { delete = project }) {
                                        if (project.id != p.id && playback.playing) vm.message("Pause a reprodução antes de trocar de projeto.") else vm.select(project)
                                    }
                                }
                                item { Box(Modifier.width(150.dp).height(if(compact || wide)76.dp else 102.dp).clip(RoundedCornerShape(12.dp)).background(Control).clickable { importDialog = true }.semantics { contentDescription="Novo projeto" }, contentAlignment = Alignment.Center) { Text("+\nNOVO PROJETO", color = Blue) } }
                            }
                            if (page==0) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(p.name, fontSize = if (compact) 18.sp else 22.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text("${p.stems.size} STEMS  •  ${p.sampleRate / 1000.0} kHz  •  ESTÉREO", color = Muted, fontSize = 11.sp)
                                }
                                TextButton(onClick = { edit = true }, enabled = !playback.playing) { Text("EDITAR") }
                            }
                            if (page == 0) Timeline(p, playback.frame, wide, library.waveform, vm::seek)
                            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Panel).padding(6.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                                FlowRow(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                                    Row(Modifier.width(if(wide)300.dp else (screenWidth-36.dp).coerceAtMost(340.dp)),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                                        DspButton("TOM",if(p.semitones==0)"ORIGINAL" else "%+d semitons".format(p.semitones),p.semitones!=0,Modifier.weight(1f)) { pitch=true }
                                        DspButton("VELOCIDADE","${p.speed}%",p.speed!=100,Modifier.weight(1f)) { speed=true }
                                    }
                                    if(page==0) {
                                        DspButton("LOOP",if(p.loop)"ON" else "OFF",p.loop,Modifier.width(104.dp)) { vm.update { it.copy(loop=!it.loop) } }
                                        DspButton("+ SEÇÃO","CRIAR",false,Modifier.width(104.dp)) { markers=true }
                                    }
                                    DspButton("METRÔNOMO",if(p.click)"ATIVO" else "CLICK",p.click,Modifier.width(116.dp)) { metronome=true }
                                    if(page==1) {
                                        DspButton("DCA","CRIAR",false,Modifier.width(104.dp)) { group="DCA:" };DspButton("BUS","CRIAR",false,Modifier.width(104.dp)) { group="BUS:" }
                                        DspButton("STEREO SPLIT","L / R",false,Modifier.width(132.dp),vm::split);DspButton("ATUALIZAR SAÍDA","DISPOSITIVO",false,Modifier.width(150.dp),vm::refreshOutput)
                                    }
                                }
                                if(page==0 && p.markers.isNotEmpty()) Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                                    p.markers.sortedBy { it.start }.forEach { m ->
                                        Box(Modifier.clip(RoundedCornerShape(20.dp)).background(Color(m.color).copy(alpha=if(p.selectedMarker==m.id).65f else .22f))
                                            .combinedClickable(onClick={vm.seek(m.start)},onLongClick={vm.update { it.copy(selectedMarker=if(it.selectedMarker==m.id)"" else m.id) }})
                                            .semantics { selected=p.selectedMarker==m.id;contentDescription="Seção ${m.name}" }.padding(horizontal=16.dp,vertical=12.dp)) {
                                            Text(m.name,color=White,fontSize=12.sp)
                                        }
                                    }
                                }
                            }
                            library.background?.let { Text(it, color = LightBlue, fontSize = 12.sp); LinearProgressIndicator(Modifier.fillMaxWidth()) }
                            if(page == 1) Text("${p.name} · ${playback.outputName} • ${playback.outputChannels} canais", color = Muted, fontSize = 12.sp,maxLines=1,overflow=TextOverflow.Ellipsis)
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text(if (wide) "MIXER  •  ${p.stems.size} STEMS  •  ${p.sampleRate / 1000.0} kHz" else "MIXER", color = Muted, fontSize = 11.sp, letterSpacing = 2.sp)
                                Spacer(Modifier.weight(1f))
                                Text(if (playback.playing) "● REPRODUZINDO" else if(playback.ready && playback.projectId==p.id) "● PRONTO" else "● PREPARANDO", color = if (playback.playing) Blue else Muted, fontSize = 11.sp)
                            }
                            Row(Modifier.fillMaxWidth().height(mixerHeight), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                LazyRow(Modifier.weight(1f).semantics { contentDescription="Canais" }.pointerInput(p.id) {
                                    awaitEachGesture {
                                        val down = awaitFirstDown(requireUnconsumed = false)
                                        if(down.position.y < 7.dp.toPx() || down.position.y > 55.dp.toPx()) return@awaitEachGesture
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
                                            draggable = true, dragging = dragged == s.id,
                                            routeLabel = if(page == 1) "${p.buses.find { it.id == s.bus }?.name ?: "MASTER"} · ${s.route}" else null,
                                            routing = { routing = s.id }, modifier=Modifier.zIndex(if(dragged==s.id)1f else 0f).graphicsLayer {
                                                if(dragged==s.id) { translationX=dragX-dragGrab-(channelList.layoutInfo.visibleItemsInfo.firstOrNull { it.key==s.id }?.offset ?: 0);scaleX=1.035f;scaleY=1.015f;shadowElevation=16.dp.toPx() }
                                            })
                                    }
                                    if(page == 1) {
                                        items(p.dcas, key = { "dca"+it.id }) { d ->
                                            ChannelStrip(d.name,d.volume,0f,d.mute,false,0f,master = true,
                                                onVolume = { v -> vm.update { it.copy(dcas=it.dcas.map { x -> if(x.id==d.id)x.copy(volume=v) else x }) } },
                                                onPan = {},onMute = { vm.update { it.copy(dcas=it.dcas.map { x -> if(x.id==d.id)x.copy(mute=!x.mute) else x }) } },onSolo = {},
                                                routeLabel = "DCA · EDITAR",routing = { group = "DCA:"+d.id })
                                        }
                                        items(p.buses, key = { "bus"+it.id }) { bus ->
                                            ChannelStrip(bus.name,bus.volume,0f,bus.mute,false,playback.busPeaks[bus.id] ?: 0f,master = true,
                                                onVolume = { v -> vm.update { it.copy(buses=it.buses.map { x -> if(x.id==bus.id)x.copy(volume=v) else x }) } },
                                                onPan = {},onMute = { vm.update { it.copy(buses=it.buses.map { x -> if(x.id==bus.id)x.copy(mute=!x.mute) else x }) } },onSolo = {},
                                                routeLabel = "BUS · EDITAR",routing = { group = "BUS:"+bus.id },outputLabel=ConsoleMath.outputChoices(playback.outputChannels).find { it.first==bus.destination }?.second ?: bus.destination)
                                        }
                                    }
                                }
                                ChannelStrip("MASTER", p.master, 0f, p.masterMute, false, playback.left, playback.right, true,
                                    { v -> vm.update { it.copy(master = v) } }, {}, { vm.update { it.copy(masterMute = !it.masterMute) } }, {})
                            }
                            Spacer(Modifier.height(8.dp))
                        }
                        Row(Modifier.fillMaxWidth().padding(6.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                            TextButton(onClick = { page = 0 }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("Playback", color = if(page == 0) Blue else Muted, fontSize = 11.sp) }
                            TextButton(onClick = { page = 1 }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("Mixer", color = if(page == 1) Blue else Muted, fontSize = 11.sp) }
                        }
                    }
                }
            }
        }
        if(navigation) MainMenu({navigation=false}) { destination ->
            navigation=false
            when(destination) {
                MainDestination.PROJECTS -> { page=0;uiScope.launch { mainScroll.scrollTo(0) } }
                MainDestination.TUNER -> { vm.pause();tuner=true }
                MainDestination.PAYMENT -> supporters=true
                MainDestination.HELP -> help=true
            }
        }
        if(tuner && !playback.playing) TunerScreen { tuner=false }
        if(search) ProjectLibraryMenu(library.projects,search,{ navigation=false;search=false },{ project ->
            if(playback.playing)vm.pause()
            vm.select(project);navigation=false;search=false
        },{ navigation=false;help=true },{ navigation=false;supporters=true })
        if(supporters) SupportersScreen { supporters=false }
        if(importDetails)AlertDialog(onDismissRequest={importDetails=false},title={Text("Resultado da importação")},text={Column(Modifier.verticalScroll(rememberScrollState())) {
            library.imports.filter { it.status=="ERROR" || playback.preparation.isEmpty() }.forEach { Text("${it.status} · ${it.name}\n${it.detail}",color=if(it.status=="ERROR")Red else Blue,fontSize=12.sp) }
            playback.preparation.forEach { Text("${it.status} · ${it.name}\n${it.detail}",color=if(it.status=="ERROR")Red else Blue,fontSize=12.sp) }
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
        if (p != null && metronome) MetronomeEditor(p, vm) { metronome = false }
        if(p!=null && speed) SpeedDialog(p,vm,library.background!=null) { speed=false }
        if (p != null && pitch) PitchDialog(p, vm, library.background != null) { pitch = false }
        if (p != null && group != null) GroupDialog(p, group!!, playback.outputChannels, vm) { group = null }
        if (p != null && routing != null) p.stems.find { it.id == routing }?.let { RoutingDialog(p,it,vm) { routing = null } }
        delete?.let { target ->
            AlertDialog(onDismissRequest = { delete = null }, title = { Text("Excluir projeto?") }, text = { Text("Tem certeza de que deseja excluir '${target.name}'? Os arquivos originais permanecem intactos.") },
                confirmButton = { TextButton(onClick = { vm.delete(target); delete = null }) { Text("EXCLUIR", color = Red) } },
                dismissButton = { TextButton(onClick = { delete = null }) { Text("CANCELAR") } })
        }
        val error = library.message ?: playback.error
        if (error != null) AlertDialog(onDismissRequest = vm::dismissError, title = { Text("ATMyTrack") }, text = { Text(error) }, confirmButton = { TextButton(onClick = vm::dismissError) { Text("ENTENDI") } })
        if (help) AlertDialog(onDismissRequest = { help = false }, title = { Text("ATMyTrack • 0.7.0") }, text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("1. Importe uma pasta ou selecione as stems.\n2. Ajuste volume, pan, mute e solo.\n3. Defina BPM e compasso; o CLICK acompanha a timeline.\n4. Crie seções para saltar aos trechos da música.")
                Text("A importação depende dos decoders disponíveis no Android. WAV, MP3, AAC/M4A, FLAC e OGG/Opus são tentados; arquivos incompatíveis geram erro.", color = Muted)
                Text("Saída estéreo ou canais USB anunciados pelo Android. BPM manual não muda a velocidade das stems. A unidade do BPM é a figura do denominador do compasso. Click e stems passam pelo master.", color = Muted)
                Text("TOM processa as tracks selecionadas em background, preservando a duração. Smart Click aplica o BPM ao ativar. Mixer reúne DCA, buses e roteamento. Segure o nome superior do canal para reordenar.", color = Muted)
                Text("VELOCIDADE altera o tempo preservando o tom. Use todas as tracks para manter a sincronização. O preparo ocorre antes da troca; extremos podem ter mais artefatos. AFINADOR usa o microfone apenas nesta tela e pausa o player.", color = Muted)
                Text("Bibliotecas: AndroidX/Compose, Kotlin e Coroutines (Apache 2.0). Pitch: Signalsmith Stretch e Linear (MIT). Identidade visual original ATMyTrack.", fontSize = 12.sp)
            }
        }, confirmButton = { TextButton(onClick = { help = false }) { Text("FECHAR") } })
    }
}

@Composable
internal fun SmallButton(label: String, active: Boolean = false, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, if (active) Blue else Control),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = if (active) Blue.copy(alpha = .12f) else Panel),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp), modifier = Modifier.heightIn(min = 48.dp)) {
        Text(label, color = if (active) Blue else White, fontSize = 12.sp, maxLines = 1)
    }
}

@Composable
private fun ProjectCard(p: Project, selected: Boolean, compact: Boolean, remove: () -> Unit, onClick: () -> Unit) {
    val context = LocalContext.current
    val bitmap by produceState<ImageBitmap?>(null, p.artwork) {
        value = if (p.artwork.isBlank()) null else withContext(Dispatchers.IO) { BitmapFactory.decodeFile(File(context.filesDir, p.artwork).path)?.asImageBitmap() }
    }
    Box(Modifier.width(190.dp).height(if (compact) 76.dp else 102.dp).clip(RoundedCornerShape(12.dp))
        .background(Brush.linearGradient(listOf(if (selected) Color(0xFF142C55) else Control, Panel)))
        .border(1.dp, if (selected) Blue else Control, RoundedCornerShape(12.dp)).clickable(onClick = onClick).semantics { this.selected = selected;contentDescription="Projeto ${p.name}" }) {
        TextButton(onClick = remove, modifier = Modifier.align(Alignment.TopEnd).size(48.dp).semantics { contentDescription = "Excluir ${p.name}" }, contentPadding = PaddingValues(0.dp)) { Text("−", color = Red, fontSize = 24.sp) }
        bitmap?.let { Image(it, null, Modifier.matchParentSize(), contentScale = ContentScale.Crop, alpha = .28f) }
        Column(Modifier.padding(start=12.dp,end=40.dp,top=12.dp,bottom=12.dp).fillMaxSize(), verticalArrangement = Arrangement.Bottom) {
            Text(p.name, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${p.key}  •  ${p.effectiveBpm.roundToInt()} BPM  •  ${MusicTime.time(p.playbackSeconds)}", color = LightBlue, fontSize = 11.sp)
        }
    }
}

@Composable
private fun Timeline(p: Project, frame: Long, compact: Boolean, peaks: List<Float>, seek: (Long) -> Unit) {
    val callback by rememberUpdatedState(seek)
    val total = p.timelineFrames.coerceAtLeast(1)
    Column(Modifier.clip(RoundedCornerShape(10.dp)).background(Panel).padding(if (compact) 8.dp else 12.dp)) {
        Row(Modifier.fillMaxWidth()) { Text("TIMELINE", fontSize = 10.sp, color = Muted, letterSpacing = 2.sp) }
        Canvas(Modifier.fillMaxWidth().height(if (compact) 32.dp else 48.dp)
            .semantics { contentDescription = "Posição da música"; progressBarRangeInfo = ProgressBarRangeInfo(frame.toFloat(), 0f..total.toFloat()); setProgress { callback(it.toLong()); true } }
            .pointerInput(total) { detectTapGestures { callback((it.x / size.width * total).toLong().coerceIn(0, total)) } }
            .pointerInput(total) { detectDragGestures { change, _ -> change.consume(); callback((change.position.x / size.width * total).toLong().coerceIn(0, total)) } }) {
            val w = size.width; val h = size.height
            for (i in 0..40) { val x = w * i / 40; drawLine(Control, Offset(x, h * .30f), Offset(x, h * if (i % 5 == 0) .85f else .6f), 1.dp.toPx()) }
            if(peaks.isNotEmpty()) {
                val path=Path(); val width=w*p.frames/total
                path.moveTo(0f,h*.5f)
                peaks.forEachIndexed { i,v -> path.lineTo(width*i/peaks.size,h*(.5f-v.coerceIn(0f,1f)*.45f)); path.lineTo(width*(i+1)/peaks.size,h*(.5f-v.coerceIn(0f,1f)*.45f)) }
                for(i in peaks.indices.reversed()) { val y=h*(.5f+peaks[i].coerceIn(0f,1f)*.45f);path.lineTo(width*(i+1)/peaks.size,y);path.lineTo(width*i/peaks.size,y) }
                path.close();drawPath(path,Muted)
            }
            p.markers.forEach { m -> drawRect(Color(m.color).copy(alpha = .28f), Offset(w * m.start / total, 2f), Size((w * (m.end - m.start) / total).coerceAtLeast(2f), h * .9f)) }
            val x = w * frame / total
            drawLine(Blue.copy(alpha = .4f), Offset(0f, h * .7f), Offset(x, h * .7f), 3.dp.toPx())
            drawLine(Blue, Offset(x, 0f), Offset(x, h), 2.dp.toPx())
            drawCircle(Blue, 4.dp.toPx(), Offset(x, h * .7f))
        }
    }
}

@Composable
private fun ChannelStrip(name: String, volume: Float, pan: Float, mute: Boolean, solo: Boolean, peak: Float,
    right: Float = peak, master: Boolean = false, onVolume: (Float) -> Unit, onPan: (Float) -> Unit, onMute: () -> Unit, onSolo: () -> Unit, draggable: Boolean = false, dragging: Boolean = false, routeLabel: String? = null, routing: () -> Unit = {}, modifier:Modifier=Modifier,outputLabel:String="OUT 1–2") {
    Column(modifier.width(if (master) 100.dp else 124.dp).fillMaxHeight().clip(RoundedCornerShape(10.dp)).background(if (master) Color(0xFF192A45) else Panel).padding(7.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(name, Modifier.fillMaxWidth().height(48.dp).background(if(dragging) LightBlue else Control).semantics { contentDescription = if(draggable) "Arrastar $name" else name }.padding(4.dp), textAlign=TextAlign.Center,maxLines = 2, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if(dragging) Bg else White)
        Text(if (master) "OUTPUT" else when { pan < -.05f -> "L ${(abs(pan) * 100).roundToInt()}"; pan > .05f -> "R ${(pan * 100).roundToInt()}"; else -> "PAN · C" }, color = Muted, fontSize = 10.sp)
        if (master) Box(Modifier.height(48.dp), contentAlignment = Alignment.Center) { Text(if(routeLabel?.startsWith("DCA")==true) "RELATIVO" else outputLabel, color = Blue, fontSize = 11.sp) }
        else Slider(pan, onPan, valueRange = -1f..1f, modifier = Modifier.height(48.dp).semantics { contentDescription = "Pan $name" }, colors = SliderDefaults.colors(thumbColor = LightBlue, activeTrackColor = LightBlue))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            if (!master) ChannelToggle("S", solo, LightBlue, "Solo $name", Modifier.weight(1f), onSolo)
            ChannelToggle("M", mute, Red, "Mute $name", Modifier.weight(1f), onMute)
        }
        var fine by remember { mutableStateOf(false) }
        Text(if (volume <= 0f) "−∞ dB" else "%+.1f dB".format(20 * log10(volume)), color = if (master) Blue else White, fontSize = 11.sp,
            modifier = Modifier.fillMaxWidth().heightIn(min=32.dp).clickable { fine=true }.padding(top = 8.dp).semantics { contentDescription="Ajuste fino $name" })
        if(fine) FineGainDialog(name,volume,onVolume) { fine=false }
        Row(Modifier.weight(1f).padding(vertical = 6.dp), horizontalArrangement = Arrangement.Center) {
            Fader(volume, name, Modifier.weight(1f).fillMaxHeight(), onVolume)
            Meter(peak, Modifier.width(5.dp).fillMaxHeight())
            if (master) { Spacer(Modifier.width(3.dp)); Meter(right, Modifier.width(5.dp).fillMaxHeight()) }
        }
        Column(Modifier.fillMaxWidth().height(80.dp),horizontalAlignment=Alignment.CenterHorizontally) {
            TextButton(onClick={onVolume(1f)},modifier=Modifier.fillMaxWidth().height(40.dp).semantics { contentDescription="0dB $name" },contentPadding=PaddingValues(0.dp)) { Text("0dB",fontSize=12.sp) }
            Box(Modifier.fillMaxWidth().height(40.dp),contentAlignment=Alignment.Center) {
                if(routeLabel!=null)TextButton(onClick=routing,modifier=Modifier.fillMaxSize(),contentPadding=PaddingValues(0.dp)) { Text(routeLabel,fontSize=10.sp,maxLines=2) }
                else if(master)Text("MASTER",color=Muted,fontSize=9.sp)
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
private fun Fader(value: Float, name: String, modifier: Modifier, change: (Float) -> Unit) {
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
            val color=if(db==0f) Blue else Muted
            drawLine(color,Offset(mid-8.dp.toPx(),tick),Offset(mid+8.dp.toPx(),tick),if(db==0f)2.dp.toPx() else 1.dp.toPx())
            paint.color=color.toArgb()
            if(detailed || db in compactLabels)
                drawContext.canvas.nativeCanvas.drawText(if(db == -80f)"−∞" else if(db>0)"+${db.toInt()}" else db.toInt().toString(),0f,tick+3.dp.toPx(),paint)
        }
        drawRoundRect(Color(0xFF334155), Offset(mid-12.dp.toPx(), y-8.dp.toPx()), Size(24.dp.toPx(),16.dp.toPx()), androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()))
        drawLine(Blue,Offset(mid-10.dp.toPx(),y),Offset(mid+10.dp.toPx(),y),2.dp.toPx())
    }
}

@Composable
private fun Meter(peak: Float, modifier: Modifier) {
    Canvas(modifier) {
        drawRect(Control)
        val normalized = if (peak <= .0001f) 0f else ((20 * log10(peak) + 60) / 60).coerceIn(0f, 1f)
        val h = size.height * normalized
        drawRect(if (peak >= 1f) Red else if(peak>.7f)Color(0xFFFFC66D) else Blue, Offset(0f, size.height - h), Size(size.width, h))
    }
}

@Composable
private fun ProjectEditor(p: Project, vm: PlayerViewModel, close: () -> Unit, artwork: () -> Unit, delete: () -> Unit) {
    var name by remember(p.id) { mutableStateOf(p.name) }
    var key by remember(p.id) { mutableStateOf(p.key) }
    var keyEdited by remember(p.id) { mutableStateOf(false) }
    LaunchedEffect(p.key) { if(!keyEdited)key=p.key }
    AlertDialog(onDismissRequest = close, title = { Text("Editar projeto") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(name, { name = it }, label = { Text("Nome") }, singleLine = true)
            OutlinedTextField(key, { key = it;keyEdited=true }, label = { Text("Tonalidade (informação)") }, singleLine = true)
            SmallButton(if(p.artwork.isBlank()) "ADICIONAR IMAGEM" else "ALTERAR IMAGEM", onClick = artwork)
            if(p.artwork.isNotBlank()) { ProjectCard(p,true,true,{},{});TextButton(onClick={vm.update { it.copy(artwork="") }}) { Text("REMOVER IMAGEM") } }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SmallButton("PROJETO ←", onClick = { vm.moveProject(p.id, -1) }); SmallButton("PROJETO →", onClick = { vm.moveProject(p.id, 1) })
            }
            Text("Ordem das tracks", color = Muted)
            p.stems.forEach { stem -> Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stem.name, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                TextButton(onClick = { vm.moveStem(stem.id, -1) }) { Text("↑") }
                TextButton(onClick = { vm.moveStem(stem.id, 1) }) { Text("↓") }
            } }
        }
    }, confirmButton = { TextButton(onClick = { vm.update { it.copy(name = name.trim(), key = if(keyEdited)key.trim() else it.key,keyAnalyzed=keyEdited || it.keyAnalyzed) }; close() }, enabled = name.isNotBlank()) { Text("SALVAR") } }, dismissButton = { TextButton(onClick = close) { Text("FECHAR") } })
}

@Composable
private fun MetronomeEditor(p: Project, vm: PlayerViewModel, close: () -> Unit) {
    var bpm by remember(p.bpm) { mutableStateOf("%.1f".format(java.util.Locale.US, p.bpm)) }
    val library by vm.library.collectAsState()
    val valid = bpm.replace(',', '.').toDoubleOrNull()?.let { it in 30.0..300.0 } == true
    AlertDialog(onDismissRequest = close, title = { Text("Metrônomo") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically) {
                SmallButton("TAP",onClick=vm::tap)
                Checkbox(p.smartClick,{vm.smartClick(it)},enabled=library.background==null || p.smartClick)
                Text("Smart Click",fontSize=12.sp)
            }
            OutlinedTextField(bpm, { bpm = it }, label = { Text("BPM • 30 a 300") }, singleLine = true, isError = !valid)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SmallButton("−", onClick = { bpm = ((bpm.toDoubleOrNull() ?: p.bpm) - 1).coerceAtLeast(30.0).toString() })
                SmallButton("+", onClick = { bpm = ((bpm.toDoubleOrNull() ?: p.bpm) + 1).coerceAtMost(300.0).toString() })
            }
            Text("Original: %.1f BPM · Atual: %.1f BPM".format(p.bpm,p.effectiveBpm),color=LightBlue)
            Text("Compasso", color = Muted)
            Choice("Click sound", p.clickSound, listOf("Classic","Digital","Wood","Cowbell","Soft","High Tick","Low Tick").map { it to it }) { v -> vm.update { it.copy(clickSound = v) } }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(p.accent, { v -> vm.update { it.copy(accent = v) } }); Text("Acentuar primeira batida", fontSize = 12.sp)
            }
            Choice("Saída do click", p.clickRoute, listOf("BOTH","LEFT","RIGHT").map { it to it }) { v -> vm.update { it.copy(clickRoute = v) } }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(2 to 4, 3 to 4, 4 to 4, 6 to 8, 7 to 8).forEach { (beats, denominator) ->
                    SmallButton("$beats/$denominator", p.beats == beats && p.denominator == denominator, { vm.update { it.copy(beats = beats, denominator = denominator) } })
                }
            }
            Text("Subdivisão • não altera a velocidade das stems", color = Muted, fontSize = 12.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(.5, 1.0, 2.0).forEach { m -> SmallButton("${m}x", p.multiplier == m, { vm.update { it.copy(multiplier = m) } }) }
            }
            Text("Volume do click", color = Muted)
            Slider(p.clickVolume, { v -> vm.update { it.copy(clickVolume = v) } }, modifier = Modifier.semantics { contentDescription = "Volume do metrônomo" })
            if(p.detectedBpm>0)Text("Detectado: %.2f BPM · confiança %d%%".format(p.detectedBpm,(p.confidence*100).roundToInt()),color=LightBlue,fontSize=12.sp)
            library.background?.let { Text(it,color=LightBlue,fontSize=12.sp) }
            Text("Smart Click aplica o BPM detectado quando ativado. Play liga o metrônomo e inicia o transporte quando parado. Pausar desliga apenas o click.",fontSize=12.sp,color=Muted)
        }
    }, confirmButton = { Row {
        TextButton(onClick={val enable=!p.click;vm.update { it.copy(bpm=bpm.replace(',','.').toDouble(),click=enable) };if(enable && !vm.playback.value.playing)vm.play()},enabled=valid && library.background==null) { Text(if(p.click) "PAUSAR" else "PLAY") }
        TextButton(onClick={if(valid)vm.update { it.copy(bpm=bpm.replace(',','.').toDouble()) };close()}) { Text("FECHAR") }
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
    var color by remember { mutableLongStateOf(0xFF60A5FA) }
    val first = parseTime(start)?.times(p.sampleRate)?.toLong()
    val last = parseTime(end)?.times(p.sampleRate)?.toLong()
    val valid = name.isNotBlank() && first != null && last != null && MusicTime.validMarker(first, last, p.frames)
    AlertDialog(onDismissRequest = close, title = { Text("Seções da música") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Timeline(p,frame,true,library.waveform,vm::seek)
            Text("Posição: %.3f s / %.3f s".format(frame.toDouble()/p.sampleRate,p.seconds),color=LightBlue)
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
