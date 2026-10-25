package com.atmytrack.app.audio

import android.app.*
import android.content.*
import android.media.*
import android.os.*
import com.atmytrack.app.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.combine

class PlaybackService : Service() {
    private val engine get() = (application as TrackApplication).engine
    private val rhythm get() = (application as TrackApplication).rhythm
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var audio: AudioManager
    private lateinit var focus: AudioFocusRequest
    private lateinit var wake: PowerManager.WakeLock
    private val noisy = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) { (application as TrackApplication).session.pauseForInterruption("Saída desconectada. Confira o roteamento antes de retomar.") }
    }
    override fun onCreate() {
        super.onCreate()
        audio = getSystemService(AudioManager::class.java)
        focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            .setOnAudioFocusChangeListener { rhythm.audioFocus(it==AudioManager.AUDIOFOCUS_GAIN);if (it != AudioManager.AUDIOFOCUS_GAIN) (application as TrackApplication).session.pauseForInterruption("Reprodução pausada por outro aplicativo ou chamada.") }.build()
        wake = getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ATMyTrack:playback")
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(noisy, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY), RECEIVER_NOT_EXPORTED)
        else registerReceiver(noisy, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY))
        getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel("playback", "Reprodução", NotificationManager.IMPORTANCE_LOW))
        scope.launch { combine(engine.state,rhythm.state) { music,beat -> music.playing || beat.independentWanted }.collect { playing ->
            if (playing && !wake.isHeld) wake.acquire(60 * 60 * 1000L)
            if (!playing && wake.isHeld) wake.release()
        } }
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        fun action(value: String) = PendingIntent.getService(this, value.hashCode(), Intent(this, PlaybackService::class.java).setAction(value), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = Notification.Builder(this, "playback").setSmallIcon(R.drawable.logo)
            .setContentTitle("ATMyTrack").setContentText("Multitrack e ritmos").setContentIntent(open).setOngoing(true)
            .addAction(Notification.Action.Builder(null, "Pausar", action("pause")).build())
            .addAction(Notification.Action.Builder(null, "Parar", action("stop")).build()).build()
        startForeground(1, notification)
        when (intent?.action) {
            "pause" -> { rhythm.stopAll();(application as TrackApplication).session.pause() }
            "stop" -> { rhythm.stopAll();(application as TrackApplication).session.stop(); stopSelf() }
            else -> if (audio.requestAudioFocus(focus) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                rhythm.audioFocus(true);if(intent?.action!="rhythm")engine.play()
            } else (application as TrackApplication).session.pauseForInterruption("A saída está sendo utilizada por outro aplicativo.")
        }
        return START_NOT_STICKY
    }
    override fun onDestroy() {
        engine.pause();rhythm.stopIndependent();rhythm.audioFocus(false); audio.abandonAudioFocusRequest(focus)
        unregisterReceiver(noisy); scope.cancel(); if (wake.isHeld) wake.release()
        super.onDestroy()
    }
    override fun onBind(intent: Intent?) = null
}
