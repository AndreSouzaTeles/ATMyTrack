package com.atmytrack.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp

internal data class ToolAction(val label:String,val active:Boolean=false,val click:()->Unit)

@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun ToolsBar(actions:List<ToolAction>,modifier:Modifier=Modifier) {
    Row(modifier.horizontalScroll(rememberScrollState()),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.End) {
        actions.forEach { action ->
            TooltipBox(positionProvider=TooltipDefaults.rememberPlainTooltipPositionProvider(),tooltip={PlainTooltip { Text(action.label) }},state=rememberTooltipState()) {
                IconButton(onClick=action.click,modifier=Modifier.size(48.dp)
                    .border(BorderStroke(1.dp,if(action.active)toolColor(action.label) else androidx.compose.ui.graphics.Color.Transparent),RoundedCornerShape(8.dp))
                    .semantics { contentDescription=action.label;selected=action.active }) { ToolIcon(action.label) }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class,ExperimentalMaterial3Api::class)
@Composable internal fun ToolsMenu(actions:List<ToolAction>) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick={open=!open},modifier=Modifier.semantics { contentDescription="Ferramentas" }) { ToolIcon("FERRAMENTAS") }
        DropdownMenu(expanded=open,onDismissRequest={open=false}) {
            FlowRow(Modifier.width(272.dp).padding(8.dp),horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                actions.forEachIndexed { index,action ->
                    if(action.label=="PERCUSSÃO" && actions.getOrNull(index-1)?.label=="METRÔNOMO")return@forEachIndexed
                    if(action.label=="METRÔNOMO" && actions.getOrNull(index+1)?.label=="PERCUSSÃO") {
                        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                            MenuTool(action) {open=false;action.click()}
                            val percussion=actions[index+1];MenuTool(percussion) {open=false;percussion.click()}
                        }
                    } else {
                        MenuTool(action) {open=false;action.click()}
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun MenuTool(action:ToolAction,click:()->Unit) {
    TooltipBox(positionProvider=TooltipDefaults.rememberPlainTooltipPositionProvider(),tooltip={PlainTooltip { Text(action.label) }},state=rememberTooltipState()) {
        IconButton(onClick=click,modifier=Modifier.size(56.dp).border(BorderStroke(1.dp,if(action.active)toolColor(action.label) else Control),RoundedCornerShape(8.dp))
            .semantics { contentDescription=action.label;selected=action.active }) { ToolIcon(action.label) }
    }
}
