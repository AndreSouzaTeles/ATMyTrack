package com.atmytrack.app.data

import java.util.UUID

data class Stem(
    val id: String = UUID.randomUUID().toString(), val name: String,
    val pcm: String, val frames: Long, val source: String = "",
    val volume: Float = 1f, val pan: Float = 0f, val mute: Boolean = false,
    val solo: Boolean = false, val format: PcmFormat = PcmFormat(),
    val external: Boolean = false, val compressed: Boolean = false, val fingerprint: String = "",
    val bus: String = "", val route: String = "BOTH", val pitchFile: String = "", val pitchApplied: Int = 0, val sourceRate: Int = 0, val sourceFrames: Long = 0, val dspSpeed: Int = 100
)
data class Marker(val id: String = UUID.randomUUID().toString(), val name: String,
    val start: Long, val end: Long, val color: Long = 0xFF60A5FA)
data class Project(
    val id: String = UUID.randomUUID().toString(), val name: String,
    val sampleRate: Int, val stems: List<Stem>, val bpm: Double = 70.0,
    val beats: Int = 4, val denominator: Int = 4, val multiplier: Double = 1.0,
    val click: Boolean = false, val clickVolume: Float = 0.35f,
    val master: Float = 1f, val masterMute: Boolean = false,
    val loop: Boolean = false, val key: String = "—", val artwork: String = "",
    val markers: List<Marker> = emptyList(),
    val dcas: List<Dca> = emptyList(), val buses: List<Bus> = emptyList(),
    val semitones: Int = 0, val targetKey: String = "", val pitchTracks: List<String> = emptyList(),
    val clickSound: String = "Classic", val accent: Boolean = true, val clickRoute: String = "BOTH",
    val detectedBpm: Double = 0.0, val confidence: Double = 0.0, val beatOffset: Long = 0,
    val analysisKey: String = "", val selectedMarker: String = "", val smartClick: Boolean = false,
    val speed: Int = 100, val speedTracks: List<String>? = null, val speedPreset: Int = 100,
    val keyAnalyzed:Boolean = false
) {
    val selectedSpeedTracks: List<String> get() = speedTracks ?: stems.map { it.id }
    val globalSpeed: Boolean get() = stems.isNotEmpty() && stems.all { it.id in selectedSpeedTracks }
    val timelineScale: Double get() = if(globalSpeed) speed / 100.0 else 1.0
    val effectiveBpm: Double get() = bpm * timelineScale
    val playbackFrames: Long get() = stems.maxOfOrNull { kotlin.math.round(it.frames / (it.dspSpeed / 100.0)).toLong() } ?: 0L
    val timelineFrames: Long get() = kotlin.math.round(playbackFrames*timelineScale).toLong()
    val playbackSeconds: Double get() = playbackFrames.toDouble() / sampleRate
    /** Saved markers always stay on the original musical timeline. */
    fun engineView(): Project = copy(
        stems=stems.map { it.copy(frames=kotlin.math.round(it.frames/(it.dspSpeed/100.0)).toLong(),dspSpeed=100) },
        markers=markers.map { it.copy(start=kotlin.math.round(it.start/timelineScale).toLong(),end=kotlin.math.round(it.end/timelineScale).toLong()) },
        bpm=effectiveBpm,beatOffset=kotlin.math.round(beatOffset/timelineScale).toLong(),speed=100,speedTracks=null)
    val frames: Long get() = stems.maxOfOrNull { it.frames } ?: 0L
    val loopSection: Marker? get() = if(loop) markers.find { it.id==selectedMarker && MusicTime.validMarker(it.start,it.end,frames) } else null
    fun playbackFrame(absolute:Long):Long {
        val section=loopSection
        return if(section!=null && absolute>=section.end) section.start+(absolute-section.end)%(section.end-section.start)
        else if(frames>0) absolute%frames else 0
    }
    val seconds: Double get() = frames.toDouble() / sampleRate
}

data class Dca(val id: String = UUID.randomUUID().toString(), val name: String, val members: List<String> = emptyList(), val volume: Float = 1f, val mute: Boolean = false)
data class Bus(val id: String = UUID.randomUUID().toString(), val name: String, val volume: Float = 1f, val mute: Boolean = false, val destination: String = "BOTH")

class TapTempo {
    private val taps = ArrayDeque<Long>()
    fun tap(nowMs: Long): Double? {
        if (taps.isNotEmpty() && (nowMs - taps.last() > 2200 || nowMs <= taps.last())) taps.clear()
        if (taps.isNotEmpty() && nowMs - taps.last() < 120) return null
        taps.addLast(nowMs)
        while (taps.size > 7) taps.removeFirst()
        if (taps.size < 2) return null
        return (60000.0 * (taps.size - 1) / (taps.last() - taps.first())).coerceIn(30.0, 300.0)
    }
}

object MusicTime {
    fun beatFrames(rate: Int, bpm: Double, multiplier: Double): Double = rate * 60.0 / (bpm * multiplier)
    fun validMarker(start: Long, end: Long, total: Long) = start >= 0 && end > start && end <= total
    fun time(seconds: Double): String {
        val s = seconds.coerceAtLeast(0.0).toLong()
        return "%02d:%02d".format(s / 60, s % 60)
    }
}
