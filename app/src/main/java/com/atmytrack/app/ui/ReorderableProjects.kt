package com.atmytrack.app.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.atmytrack.app.PlayerViewModel
import com.atmytrack.app.data.Project
import kotlinx.coroutines.delay

@Composable internal fun ReorderableProjects(projects:List<Project>,selectedId:String,vm:PlayerViewModel,select:(Project)->Unit,edit:(Project)->Unit,remove:(Project)->Unit,create:()->Unit) {
    val state=rememberLazyListState()
    val latest by rememberUpdatedState(projects)
    var dragged by remember { mutableStateOf<String?>(null) }
    var x by remember { mutableFloatStateOf(0f) }
    var grab by remember { mutableFloatStateOf(0f) }
    val edge=with(LocalDensity.current) { 32.dp.toPx() }
    LaunchedEffect(selectedId) { val index=projects.indexOfFirst { it.id==selectedId };if(index>=0)state.scrollToItem(index) }
    LaunchedEffect(dragged) {
        while(dragged!=null) {
            val width=state.layoutInfo.viewportEndOffset
            val scroll=when { x<edge->-edge/3;x>width-edge->edge/3;else->0f }
            if(scroll!=0f)state.scrollBy(scroll)
            val pointer=x.coerceIn(0f,(width-1).coerceAtLeast(0).toFloat())
            val target=state.layoutInfo.visibleItemsInfo.firstOrNull { pointer>=it.offset && pointer<it.offset+it.size }
            val from=latest.indexOfFirst { it.id==dragged }
            if(from>=0 && target!=null && target.index in latest.indices && target.index!=from && (scroll==0f || (scroll<0 && target.index<from) || (scroll>0 && target.index>from))) {
                state.requestScrollToItem(state.firstVisibleItemIndex,state.firstVisibleItemScrollOffset)
                vm.reorderProject(dragged!!,target.index)
            }
            delay(24)
        }
    }
    LazyRow(state=state,modifier=Modifier.fillMaxWidth().semantics { contentDescription="Projetos" }.pointerInput(Unit) {
        awaitEachGesture {
            val down=awaitFirstDown(requireUnconsumed=false)
            val item=state.layoutInfo.visibleItemsInfo.firstOrNull { down.position.x>=it.offset && down.position.x<it.offset+it.size } ?: return@awaitEachGesture
            val project=latest.getOrNull(item.index) ?: return@awaitEachGesture
            if(down.position.x>item.offset+item.size-48.dp.toPx())return@awaitEachGesture
            val held=awaitLongPressOrCancellation(down.id) ?: return@awaitEachGesture
            dragged=project.id;x=held.position.x;grab=x-item.offset;held.consume()
            try {
                while(true) {
                    val event=awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id==held.id } ?: break
                    if(!event.pressed)break
                    x=event.position.x;event.consume()
                }
                // Commit the drop even when the final move and pointer-up arrive
                // before the next auto-scroll animation tick.
                val target=state.layoutInfo.visibleItemsInfo.firstOrNull { x>=it.offset && x<it.offset+it.size }
                val from=latest.indexOfFirst { it.id==dragged }
                if(from>=0 && target!=null && target.index in latest.indices && target.index!=from)vm.reorderProject(dragged!!,target.index)
            } finally { dragged=null }
        }
    },horizontalArrangement=Arrangement.spacedBy(8.dp),contentPadding=PaddingValues(vertical=4.dp)) {
        items(projects,key={it.id}) { project ->
            ProjectCard(project,project.id==selectedId,false,{remove(project)},onEdit={edit(project)},
                modifier=Modifier.zIndex(if(dragged==project.id)1f else 0f).graphicsLayer {
                    if(dragged==project.id) { translationX=x-grab-(state.layoutInfo.visibleItemsInfo.firstOrNull { it.key==project.id }?.offset ?: 0);scaleX=1.025f;scaleY=1.025f;shadowElevation=10.dp.toPx() }
                }.semantics { customActions=listOf(
                    CustomAccessibilityAction("Mover projeto para esquerda") { vm.moveProject(project.id,-1);true },
                    CustomAccessibilityAction("Mover projeto para direita") { vm.moveProject(project.id,1);true }) },onClick={select(project)})
        }
        item { Box(Modifier.width(140.dp).height(104.dp).clip(RoundedCornerShape(12.dp)).background(Control).clickable(onClick=create).semantics { contentDescription="Novo projeto" },contentAlignment=Alignment.Center) { Text("+ NOVO PROJETO",color=SecondaryText) } }
    }
}
