package com.atmytrack.app.separation

import android.app.ActivityManager
import android.content.Context
import java.io.File

object SeparationLock {
    @Volatile var requested=false
    fun active(context:Context):Boolean {
        if(requested)return true
        val file=File(context.filesDir,"separation/processing.lock")
        if(!file.exists())return false
        val pid=runCatching { file.readText().trim().toInt() }.getOrNull()
        val running=context.getSystemService(ActivityManager::class.java).runningAppProcesses
        if(pid!=null && running==null)return true
        val active=running?.any { it.pid==pid && it.processName==context.packageName+":separation" }==true
        if(!active)file.delete()
        return active
    }
}
