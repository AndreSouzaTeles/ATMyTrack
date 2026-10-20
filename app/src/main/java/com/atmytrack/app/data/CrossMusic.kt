package com.atmytrack.app.data

import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Locale

data class CrossTrack(val volume:Float=1f,val mute:Boolean=false)
data class CrossPad(val enabled:Boolean=false,val followProjectKey:Boolean=true,val note:Int=0,val minor:Boolean=false,val sound:String="Warm",val volume:Float=.25f)
data class CrossEntry(val projectId:String,val tracks:Map<String,CrossTrack> = emptyMap(),val pad:CrossPad=CrossPad()) {
    fun apply(project:Project)=project.copy(stems=project.stems.map { s -> tracks[s.id]?.let { s.copy(volume=it.volume,mute=it.mute,solo=false) } ?: s })
    companion object {
        fun from(project:Project)=CrossEntry(project.id,project.stems.associate { it.id to CrossTrack(it.volume,it.mute) })
    }
}
data class CrossMusicState(val running:Boolean=false,val index:Int=0,val total:Int=0,val projectId:String="",val preparing:Boolean=false)

/** Resolves enharmonic project labels; transposed projects follow their destination key. */
object PadKey {
    fun parse(text:String):Pair<Int,Boolean>? {
        val cleaned=text.trim().replace('♭','b').replace('♯','#')
        val match=Regex("^([A-Ga-g])([#b]?)(.*)$").matchEntire(cleaned) ?: return null
        val root=mapOf("C" to 0,"D" to 2,"E" to 4,"F" to 5,"G" to 7,"A" to 9,"B" to 11).getValue(match.groupValues[1].uppercase(Locale.ROOT))
        val accidental=when(match.groupValues[2]) { "#"->1;"b"->-1;else->0 }
        val suffix=match.groupValues[3].trim().lowercase(Locale.ROOT)
        if(suffix !in listOf("","m","min","minor","menor","maj","major","maior"))return null
        return Math.floorMod(root+accidental,12) to (suffix in listOf("m","min","minor","menor"))
    }
    fun resolve(project:Project,pad:CrossPad):Pair<Int,Boolean>? {
        if(!pad.followProjectKey)return pad.note.coerceIn(0,11) to pad.minor
        if(project.semitones!=0 && project.targetKey.isNotBlank())return parse(project.targetKey)
        return parse(project.key)?.let { Math.floorMod(it.first+project.semitones,12) to it.second }
    }
}

class CrossMusicStore(root:File) {
    private val file=AtomicFile(File(root,"crossmusic.json"))
    fun save(entries:List<CrossEntry>) {
        val doc=JSONObject().put("version",1).put("entries",JSONArray(entries.map { e ->
            JSONObject().put("project",e.projectId).put("tracks",JSONObject().apply { e.tracks.forEach { (id,t)->put(id,JSONObject().put("volume",t.volume).put("mute",t.mute)) } })
                .put("pad",JSONObject().put("enabled",e.pad.enabled).put("follow",e.pad.followProjectKey).put("note",e.pad.note).put("minor",e.pad.minor).put("sound",e.pad.sound).put("volume",e.pad.volume))
        }))
        val out=file.startWrite()
        try { out.write(doc.toString().toByteArray(Charsets.UTF_8));file.finishWrite(out) } catch(e:Exception) { file.failWrite(out);throw e }
    }
    fun load():List<CrossEntry> {
        if(!file.baseFile.exists() && !File(file.baseFile.path+".bak").exists())return emptyList()
        val doc=JSONObject(file.openRead().bufferedReader().use { it.readText() })
        require(doc.getInt("version")==1)
        val all=doc.getJSONArray("entries")
        return (0 until all.length()).map { i ->
            val e=all.getJSONObject(i);val tracks=e.getJSONObject("tracks");val p=e.getJSONObject("pad")
            CrossEntry(e.getString("project"),tracks.keys().asSequence().associateWith { id ->
                val t=tracks.getJSONObject(id);CrossTrack(t.getDouble("volume").toFloat().coerceIn(0f,3.162278f),t.optBoolean("mute"))
            },CrossPad(p.optBoolean("enabled"),p.optBoolean("follow",true),p.optInt("note").coerceIn(0,11),p.optBoolean("minor"),p.optString("sound","Warm"),p.optDouble("volume",.25).toFloat().coerceIn(0f,1f)))
        }.distinctBy { it.projectId }
    }
}
