package com.atmytrack.app

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import com.atmytrack.app.data.*

class PlayerViewModel(app:Application):AndroidViewModel(app) {
    private val session=(app as TrackApplication).session
    val library=session.library
    val playback=session.playback
    val pad=session.pad
    val crossMusic=session.crossMusic
    val crossPlan=session.crossPlan
    fun message(value:String?)=session.message(value)
    fun dismissError()=session.dismissError()
    fun dismissImportReport()=session.dismissImportReport()
    fun consumeEditRequest()=session.consumeEditRequest()
    fun saveCrossMusic(entries:List<CrossEntry>)=session.saveCrossMusic(entries)
    fun startCrossMusic(entries:List<CrossEntry>)=session.startCrossMusic(entries)
    fun endCrossMusic()=session.endCrossMusic()
    fun update(transform:(Project)->Project)=session.update(transform)
    fun lockFader(id:String)=session.lockFader(id)
    fun stem(id:String,transform:(Stem)->Stem)=session.stem(id,transform)
    fun select(p:Project)=session.select(p)
    fun moveProject(id:String,delta:Int)=session.moveProject(id,delta)
    fun reorderProject(id:String,to:Int)=session.reorderProject(id,to)
    fun moveStem(id:String,delta:Int)=session.moveStem(id,delta)
    fun reorderStem(id:String,to:Int)=session.reorderStem(id,to)
    fun delete(p:Project)=session.delete(p)
    fun import(uris:List<Uri>?=null,tree:Uri?=null)=session.import(uris,tree)
    fun artwork(uri:Uri)=session.artwork(uri)
    fun retryPreparation()=session.retryPreparation()
    fun cancelPreparation()=session.cancelPreparation()
    fun removeTrack(id:String)=session.removeTrack(id)
    fun play()=session.play()
    fun pause()=session.pause()
    fun refreshOutput()=session.refreshOutput()
    fun stop()=session.stop()
    fun seek(frame:Long)=session.seek(frame)
    fun tap()=session.tap()
    fun adjacent(delta:Int)=session.adjacent(delta)
    fun bothAll()=session.bothAll()
    fun split()=session.split()
    fun smartClick(enabled:Boolean)=session.smartClick(enabled)
    fun analyzeTempo(force:Boolean=false)=session.analyzeTempo(force)
    fun applySpeed(percent:Int,ids:List<String>,preset:Int=percent)=session.applySpeed(percent,ids,preset)
    fun applyPitch(origin:String,destination:String,semitones:Int,ids:List<String>)=session.applyPitch(origin,destination,semitones,ids)
}
