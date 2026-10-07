package com.atmytrack.app
import com.atmytrack.app.audio.ConsoleMath
import com.atmytrack.app.data.*
import org.junit.Test
import org.junit.Assert.*
class Session06Test {
    @Test fun sectionWrapsAtExactFramesWithoutDrift() {
        val m=Marker(name="Refrão",start=1000,end=3500)
        val p=Project(name="Song",sampleRate=48000,stems=listOf(Stem(name="A",pcm="",frames=9000)),markers=listOf(m),selectedMarker=m.id,loop=true)
        assertEquals(3499L,p.playbackFrame(3499));assertEquals(1000L,p.playbackFrame(3500))
        assertEquals(1234L,p.playbackFrame(3500+2500L*100000+234))
        assertEquals(3500L,p.copy(loop=false).playbackFrame(3500))
        assertEquals(0L,p.copy(selectedMarker="missing").playbackFrame(9000))
    }
    @Test fun physicalMonoRoutesOnlySelectedChannel() {
        for(channels in listOf(2,4,8,16))for(channel in 0 until channels) {
            val output=FloatArray(2*channels)
            ConsoleMath.routePhysical(floatArrayOf(.2f,.6f,-.2f,-.6f),output,2,channels,"MONO:$channel")
            for(c in 0 until channels) { assertEquals(if(c==channel).4f else 0f,output[c],.00001f);assertEquals(if(c==channel)-.4f else 0f,output[channels+c],.00001f) }
        }
        assertTrue(ConsoleMath.outputChoices(2).any { it.first=="MONO:1" && it.second.contains("2/2") })
    }
    @Test(expected=IllegalArgumentException::class) fun unavailableChannelIsRejected() { ConsoleMath.routePhysical(FloatArray(2),FloatArray(2),1,2,"MONO:2") }
    @Test fun importedDefaultsAreUnity() { assertEquals(1f,Stem(name="A",pcm="",frames=1).volume,0f);assertEquals(1f,Project(name="A",sampleRate=48000,stems=emptyList()).master,0f) }
}
