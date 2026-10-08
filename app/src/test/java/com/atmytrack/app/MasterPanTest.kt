package com.atmytrack.app

import com.atmytrack.app.audio.MixMath
import com.atmytrack.app.data.Project
import org.junit.Test
import org.junit.Assert.*

class MasterPanTest {
 @Test fun balancePreservesStereoAndNeverCrossfeedsSplitChannels() {
    val p=Project(name="Master",sampleRate=48000,stems=emptyList())
    for(pan in listOf(-1f,0f,1f)) {
        val samples=floatArrayOf(.2f,.4f,.1f,.3f)
        MixMath.finish(samples,2,0,p.copy(masterPan=pan))
        assertEquals(if(pan==1f)0f else .2f,samples[0],.000001f)
        assertEquals(if(pan== -1f)0f else .4f,samples[1],.000001f)
    }
    val samples=FloatArray(8) { .2f }
    MixMath.finish(samples,4,0,p.copy(masterPan=1f),fromPan=-1f)
    assertTrue(samples[0]>samples[6]);assertEquals(0f,samples[6],0f)
    val mute=FloatArray(8) { .2f };MixMath.finish(mute,4,0,p.copy(masterMute=true,masterPan=.5f))
    assertTrue(mute.all { it==0f })
 }
}
