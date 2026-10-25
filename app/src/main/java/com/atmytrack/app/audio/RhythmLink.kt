package com.atmytrack.app.audio

import com.atmytrack.app.data.Project

/** One persisted switch, two editors, one tempo and transport mode. */
object RhythmLink {
    fun fromMetronome(state:RhythmState,m:Project,armed:Boolean=state.metronomeArmed):RhythmState {
        val next=state.copy(metronome=m,metronomeArmed=armed)
        return if(!state.linked)next else next.copy(percussion=state.percussion.copy(
            bpm=m.bpm,beats=m.beats,denominator=m.denominator,follow=m.clickFollow,
            enabled=m.click && (m.clickFollow || armed)))
    }
    fun fromPercussion(state:RhythmState,p:PercussionSettings):RhythmState =
        if(!state.linked)state.copy(percussion=p) else state.copy(percussion=p,metronomeArmed=p.enabled,
            metronome=state.metronome.copy(bpm=p.bpm,beats=p.beats,denominator=p.denominator,clickFollow=p.follow,click=p.enabled))
}
