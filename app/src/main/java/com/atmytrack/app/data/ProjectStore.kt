package com.atmytrack.app.data

import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** One atomic document keeps project edits and channel settings in the same transaction. */
class ProjectStore(private val root: File) {
    private val file = AtomicFile(File(root, "library.json"))
    fun load(): List<Project> = synchronized(transactionLock) {
        if (!file.baseFile.exists() && !File(root, "library.json.bak").exists()) return@synchronized emptyList()
        val document = JSONObject(file.openRead().bufferedReader().use { it.readText() })
        require(document.getInt("version") == 1) { "Versão da biblioteca não suportada." }
        document.getJSONArray("projects").objects().map(::decode)
    }
    fun save(projects: List<Project>) = synchronized(transactionLock) {
        root.mkdirs()
        val bytes = JSONObject().put("version", 1).put("projects", JSONArray(projects.map(::encode))).toString().toByteArray()
        val stream = file.startWrite()
        try { stream.write(bytes); file.finishWrite(stream) }
        catch (e: Exception) { file.failWrite(stream); throw e }
    }
    private fun encode(p: Project): JSONObject = JSONObject().apply {
        put("id", p.id); put("name", p.name); put("rate", p.sampleRate); put("bpm", p.bpm)
        put("beats", p.beats); put("denominator", p.denominator); put("multiplier", p.multiplier)
        put("click", p.click); put("clickVolume", p.clickVolume); put("master", p.master)
        put("masterMute", p.masterMute); put("loop", p.loop); put("key", p.key); put("artwork", p.artwork)
        put("semitones",p.semitones); put("targetKey",p.targetKey); put("pitchTracks",JSONArray(p.pitchTracks))
        put("clickSound",p.clickSound); put("accent",p.accent); put("clickRoute",p.clickRoute)
        put("detectedBpm",p.detectedBpm); put("confidence",p.confidence); put("beatOffset",p.beatOffset); put("analysisKey",p.analysisKey)
        put("dcas",JSONArray(p.dcas.map { d->JSONObject().put("id",d.id).put("name",d.name).put("members",JSONArray(d.members)).put("volume",d.volume).put("mute",d.mute) }))
        put("buses",JSONArray(p.buses.map { b->JSONObject().put("id",b.id).put("name",b.name).put("volume",b.volume).put("mute",b.mute).put("destination",b.destination) }))
        put("stems", JSONArray(p.stems.map { s -> JSONObject().apply {
            put("id", s.id); put("name", s.name); put("pcm", s.pcm); put("frames", s.frames)
            put("source", s.source); put("volume", s.volume); put("pan", s.pan); put("mute", s.mute); put("solo", s.solo)
            put("channels", s.format.channels); put("encoding", s.format.encoding.name); put("dataOffset", s.format.offset)
            put("external",s.external); put("compressed",s.compressed); put("fingerprint",s.fingerprint); put("bus",s.bus); put("route",s.route); put("pitchFile",s.pitchFile); put("pitchApplied",s.pitchApplied);put("sourceRate",s.sourceRate);put("sourceFrames",s.sourceFrames)
        } }))
        put("markers", JSONArray(p.markers.map { m -> JSONObject().apply {
            put("id", m.id); put("name", m.name); put("start", m.start); put("end", m.end); put("color", m.color)
        } }))
    }
    private fun decode(j: JSONObject): Project = Project(
        id = j.getString("id"), name = j.getString("name"), sampleRate = j.getInt("rate"),
        stems = j.getJSONArray("stems").objects().map { s -> Stem(s.getString("id"), s.getString("name"), s.getString("pcm"), s.getLong("frames"), s.optString("source"), s.getDouble("volume").toFloat(), s.getDouble("pan").toFloat(), s.getBoolean("mute"), s.getBoolean("solo"),
            PcmFormat(s.optInt("channels", 2), PcmEncoding.valueOf(s.optString("encoding", "FLOAT32")), s.optLong("dataOffset", 0)),
            s.optBoolean("external"),s.optBoolean("compressed"),s.optString("fingerprint"),s.optString("bus"),s.optString("route","BOTH"),s.optString("pitchFile"),s.optInt("pitchApplied"),s.optInt("sourceRate"),s.optLong("sourceFrames")) },
        bpm = j.getDouble("bpm"), beats = j.getInt("beats"), denominator = j.optInt("denominator", 4),
        multiplier = j.getDouble("multiplier"), click = j.getBoolean("click"), clickVolume = j.getDouble("clickVolume").toFloat(),
        master = j.getDouble("master").toFloat(), masterMute = j.getBoolean("masterMute"), loop = j.getBoolean("loop"),
        key = j.getString("key"), artwork = j.getString("artwork"),
        markers = j.getJSONArray("markers").objects().map { m -> Marker(m.getString("id"), m.getString("name"), m.getLong("start"), m.getLong("end"), m.getLong("color").let { when(it) { 0xFF93C5FDL, 0xFF60A5FAL, 0xFF3B82F6L, 0xFF1D4ED8L -> it; 0xFF39E0B8L -> 0xFF3B82F6L; else -> 0xFF60A5FAL } }) },
        dcas=(j.optJSONArray("dcas") ?: JSONArray()).objects().map { Dca(it.getString("id"),it.getString("name"),it.getJSONArray("members").strings(),it.getDouble("volume").toFloat(),it.getBoolean("mute")) },
        buses=(j.optJSONArray("buses") ?: JSONArray()).objects().map { Bus(it.getString("id"),it.getString("name"),it.getDouble("volume").toFloat(),it.getBoolean("mute"),it.getString("destination")) },
        semitones=j.optInt("semitones"),targetKey=j.optString("targetKey"),pitchTracks=(j.optJSONArray("pitchTracks") ?: JSONArray()).strings(),
        clickSound=j.optString("clickSound","Classic"),accent=j.optBoolean("accent",true),clickRoute=j.optString("clickRoute","BOTH"),
        detectedBpm=j.optDouble("detectedBpm",0.0),confidence=j.optDouble("confidence",0.0),beatOffset=j.optLong("beatOffset"),analysisKey=j.optString("analysisKey")
    )
    private fun JSONArray.objects() = (0 until length()).map { getJSONObject(it) }
    private fun JSONArray.strings() = (0 until length()).map { getString(it) }
    companion object {
        // AtomicFile is not a lock. Different ViewModels/store instances must not
        // openRead while another instance is publishing its .new transaction.
        private val transactionLock=Any()
    }
}
