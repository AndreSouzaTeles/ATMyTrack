package com.atmytrack.app

import android.app.Application
import com.atmytrack.app.audio.AudioEngine

class TrackApplication : Application() {
    val pad by lazy { com.atmytrack.app.audio.PadEngine(this) }
    val engine by lazy { AudioEngine(this) }
}
