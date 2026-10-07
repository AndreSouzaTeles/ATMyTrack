package com.atmytrack.app

import android.app.Application
import com.atmytrack.app.audio.AudioEngine

class TrackApplication : Application() {
    val engine by lazy { AudioEngine(this) }
}
