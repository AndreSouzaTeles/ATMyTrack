package com.atmytrack.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import kotlin.math.*

@Composable internal fun PanKnob(value:Float,name:String,accent:Color,locked:Boolean,change:(Float)->Unit) {
    val current by rememberUpdatedState(value)
    val callback by rememberUpdatedState(change)
    Canvas(Modifier.size(52.dp).semantics {
        contentDescription="Pan $name"
        stateDescription=when { value<-.005f->"Esquerda ${(abs(value)*100).roundToInt()}%";value>.005f->"Direita ${(value*100).roundToInt()}%";else->"Centro" }
        progressBarRangeInfo=ProgressBarRangeInfo(value,-1f..1f)
        if(locked)disabled()
        setProgress { if(!locked) { callback(it.coerceIn(-1f,1f));true } else false }
        customActions=listOf(CustomAccessibilityAction("Centralizar PAN") { if(!locked) { callback(0f);true } else false })
    }.pointerInput(locked) {
        if(locked)return@pointerInput
        detectTapGestures(onDoubleTap={callback(0f)},onTap={ p ->
            val x=p.x-size.width/2f;val y=p.y-size.height/2f
            if(hypot(x,y)<size.width*.18f)callback(0f)
            else { var a=atan2(y,x)+PI.toFloat()/2; if(a>PI)a-=2*PI.toFloat();callback((a/(PI*.75)).toFloat().coerceIn(-1f,1f)) }
        })
    }.pointerInput(locked) {
        if(locked)return@pointerInput
        var draggedValue=0f
        fun angle(p:Offset)=atan2(p.y-size.height/2f,p.x-size.width/2f)
        detectDragGestures(onDragStart={draggedValue=current}) { event,_ ->
            event.consume();val a=angle(event.position);val previous=angle(event.previousPosition);val delta=atan2(sin(a-previous),cos(a-previous))
            draggedValue=(draggedValue+delta/(PI*.75)).toFloat().coerceIn(-1f,1f);callback(draggedValue)
        }
    }) {
        val c=center;val r=size.minDimension*.35f
        val tint=if(locked)Muted else accent
        drawArc(Control,135f,270f,false,Offset(c.x-r*1.13f,c.y-r*1.13f),androidx.compose.ui.geometry.Size(r*2.26f,r*2.26f),style=Stroke(2.dp.toPx()))
        drawCircle(Brush.radialGradient(listOf(Color(0xFF39424C),Control,Bg),center=c,radius=r),r,c)
        drawCircle(Muted.copy(alpha=.3f),r,c,style=Stroke(1.dp.toPx()))
        drawLine(SecondaryText,Offset(c.x,c.y-r*1.04f),Offset(c.x,c.y-r*1.32f),2.dp.toPx())
        val angle=(-PI/2+value*PI*.75)
        fun point(radius:Float)=Offset(c.x+cos(angle).toFloat()*radius,c.y+sin(angle).toFloat()*radius)
        drawLine(tint,point(r*.42f),point(r*.87f),2.5.dp.toPx(),cap=androidx.compose.ui.graphics.StrokeCap.Round)
        drawCircle(tint,1.5.dp.toPx(),c)
    }
}
