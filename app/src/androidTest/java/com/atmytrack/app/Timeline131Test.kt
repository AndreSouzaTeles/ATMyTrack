package com.atmytrack.app
import android.os.SystemClock
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import org.junit.Test
import org.junit.Rule
import org.junit.Assert.*
class Timeline131Test {
 @get:Rule val compose=createAndroidComposeRule<MainActivity>()
 @Test fun dragPreviewsWithoutSeekHistoryAndCommitsFinalPosition() {
  val vm=ViewModelProvider(compose.activity)[PlayerViewModel::class.java]
  compose.waitUntil(45000){vm.library.value.current!=null&&vm.playback.value.ready}
  compose.runOnUiThread{vm.pause();vm.dismissError();vm.dismissImportReport();vm.update{it.copy(loop=false,selectedMarker="")};vm.seek(0)}
  compose.waitUntil(5000){!vm.playback.value.playing&&vm.playback.value.frame==0L}
  val total=vm.library.value.current!!.timelineFrames;val node=compose.onNodeWithContentDescription("Posição da música");node.performScrollTo()
  node.performTouchInput { down(Offset(width*.1f,height*.5f));repeat(100){moveTo(Offset(width*(.1f+.65f*(it+1)/100),height*.5f),delayMillis=4)} }
  compose.waitForIdle();assertEquals("Audio must not chase each pointer event",0L,vm.playback.value.frame)
  val start=SystemClock.elapsedRealtime();node.performTouchInput{up()}
  compose.waitUntil(5000){kotlin.math.abs(vm.playback.value.frame-(total*.75).toLong())<1024}
  android.util.Log.i("SEEK131","100 drag movements committed final 75% in ${SystemClock.elapsedRealtime()-start}ms; stems=${vm.library.value.current!!.stems.size}")
  compose.runOnUiThread{vm.update{it.copy(loop=true)};vm.seek(0);vm.play()};compose.waitUntil(5000){vm.playback.value.playing}
  node.performTouchInput{down(Offset(width*.7f,height*.5f));repeat(50){moveTo(Offset(width*(.7f-.4f*(it+1)/50),height*.5f),delayMillis=4)}}
  compose.waitForIdle();assertTrue(vm.playback.value.playing)
  node.performTouchInput{up()};compose.waitUntil(5000){vm.playback.value.frame in (total*.3).toLong()..(total*.3).toLong()+48000};assertNull(vm.playback.value.error)
  compose.runOnUiThread{vm.stop()}
 }
}
