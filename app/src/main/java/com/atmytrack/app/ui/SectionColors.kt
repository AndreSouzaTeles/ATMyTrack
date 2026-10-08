package com.atmytrack.app.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable internal fun SectionColors(color:Long,change:(Long)->Unit) {
    var custom by remember { mutableStateOf(false) }
    val colors=listOf("Vermelho" to 0xFFFF0000,"Verde" to 0xFF00FF00,"Azul" to 0xFF0000FF,"Amarelo" to 0xFFFFFF00,"Magenta" to 0xFFFF00FF,"Ciano" to 0xFF00FFFF)
    Text("Cor da seção",color=LightBlue)
    FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
        colors.forEach { (name,value) -> Box(Modifier.size(48.dp).clip(CircleShape).background(Color(value)).border(if(color==value)3.dp else 1.dp,if(color==value)White else Muted,CircleShape).clickable { change(value) }.semantics { contentDescription="Cor $name";selected=color==value }) }
        Box(Modifier.size(48.dp).clip(CircleShape).background(Brush.sweepGradient(colors.map { Color(it.second) }+Color.Red)).border(2.dp,White,CircleShape).clickable { custom=true }.semantics { contentDescription="Escolher cor personalizada" })
    }
    if(custom) {
        var draft by remember { mutableLongStateOf(color) }
        AlertDialog(onDismissRequest={custom=false},title={Text("Cor personalizada")},text={
            Column {
                Box(Modifier.fillMaxWidth().height(48.dp).background(Color(draft)))
                Text("#%06X".format(draft and 0xFFFFFF),color=LightBlue)
                listOf("Vermelho" to 16,"Verde" to 8,"Azul" to 0).forEach { (label,shift) ->
                    val value=((draft shr shift) and 255).toInt()
                    Text("$label · $value")
                    Slider(value.toFloat(),{ v -> draft=(draft and (255L shl shift).inv()) or (v.roundToInt().toLong() shl shift) },valueRange=0f..255f,steps=254,modifier=Modifier.semantics { contentDescription="Componente $label" })
                }
            }
        },confirmButton={TextButton(onClick={change(draft);custom=false}) { Text("USAR COR") }},dismissButton={TextButton(onClick={custom=false}){Text("CANCELAR")}})
    }
}
