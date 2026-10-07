package com.atmytrack.app

import com.atmytrack.app.data.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.nio.file.Files

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
class ProjectStoreTest {
    @Test fun speedMigrationPreservesOldDataAndEmptySelection() {
        val dir=Files.createTempDirectory("speed-migration").toFile()
        try {
            val old=Project(name="Old",sampleRate=48000,stems=listOf(Stem(name="Guitar",pcm="old.wav",frames=96000,volume=.4f,pan=-.3f)),markers=listOf(Marker(name="Verse",start=0,end=48000)),semitones=2,pitchTracks=listOf("guitar"))
            ProjectStore(dir).save(listOf(old))
            val file=java.io.File(dir,"library.json")
            val doc=org.json.JSONObject(file.readText());val j=doc.getJSONArray("projects").getJSONObject(0)
            j.remove("speed");j.remove("speedTracks");j.remove("speedPreset");j.getJSONArray("stems").getJSONObject(0).remove("dspSpeed")
            file.writeText(doc.toString())
            val migrated=ProjectStore(dir).load().single()
            assertEquals(old,migrated);assertEquals(100,migrated.speed);assertEquals(old.stems.map { it.id },migrated.selectedSpeedTracks)
            val edited=migrated.copy(speed=80,speedTracks=emptyList(),speedPreset=80)
            ProjectStore(dir).save(listOf(edited));assertEquals(edited,ProjectStore(dir).load().single())
            val projects=listOf(
                migrated.copy(id="A",speed=81,speedPreset=80,speedTracks=migrated.stems.map { it.id },stems=migrated.stems.map { it.copy(dspSpeed=81,pitchFile="pitch/A/combined.pcm",pitchApplied=2) }),
                migrated.copy(id="B",semitones=0,speed=100,pitchTracks=emptyList()),
                migrated.copy(id="C",semitones=-1,speed=120,speedPreset=120,speedTracks=migrated.stems.map { it.id },stems=migrated.stems.map { it.copy(dspSpeed=120,pitchFile="pitch/C/combined.pcm",pitchApplied=-1) }))
            ProjectStore(dir).save(projects)
            val reopened=ProjectStore(dir).load()
            for(id in listOf("A","B","C","A"))assertEquals(projects.first { it.id==id },reopened.first { it.id==id })
        } finally { dir.deleteRecursively() }
    }

    @Test fun loopSelectionSmartClickAndPhysicalBusSurviveReopen() {
        val dir=Files.createTempDirectory("atmytrack-section").toFile()
        try {
            val section=Marker(name="Verse",start=1200,end=2400)
            val p=Project(name="Saved",sampleRate=48000,stems=listOf(Stem(name="Stem",pcm="s",frames=5000,volume=.4f)),markers=listOf(section),selectedMarker=section.id,loop=true,smartClick=true,buses=listOf(Bus(name="R",destination="MONO:1")))
            ProjectStore(dir).save(listOf(p));val loaded=ProjectStore(dir).load().single()
            assertEquals(p,loaded);assertEquals(1200L,loaded.playbackFrame(2400));assertEquals(.4f,loaded.stems.single().volume,0f)
        } finally { dir.deleteRecursively() }
    }

    @Test fun mixedRateOriginsSurviveReopenAndOldMarkerPaletteMigrates() {
        val dir=Files.createTempDirectory("atmytrack-rates").toFile()
        try {
            val p=Project(name="Mixed",sampleRate=48000,stems=listOf(
                Stem(name="44k",pcm="a.wav",frames=48000,sourceRate=44100,sourceFrames=44100),
                Stem(name="96k",pcm="b.flac",frames=48000,sourceRate=96000,sourceFrames=96000,compressed=true)),
                markers=listOf(Marker(name="Old",start=0,end=48000,color=0xFF39E0B8)))
            ProjectStore(dir).save(listOf(p))
            val loaded=ProjectStore(dir).load().single()
            assertEquals(p.stems,loaded.stems)
            assertEquals(0xFF3B82F6L,loaded.markers.single().color)
        } finally { dir.deleteRecursively() }
    }
    @Test fun separateInstancesSerializeReadersAndAtomicWriters() {
        val dir=Files.createTempDirectory("atmytrack-concurrent").toFile()
        val workers=java.util.concurrent.Executors.newFixedThreadPool(2)
        try {
            val writer=ProjectStore(dir);val reader=ProjectStore(dir)
            val p=Project(name="0",sampleRate=48000,stems=List(40){Stem(name="Track $it",pcm="audio/$it",frames=96000)})
            writer.save(listOf(p))
            val start=java.util.concurrent.CountDownLatch(1)
            val save=workers.submit { start.await();repeat(40) { writer.save(listOf(p.copy(name="${it+1}"))) } }
            val read=workers.submit { start.await();repeat(80) { assertEquals(p.stems,reader.load().single().stems) } }
            start.countDown();save.get();read.get()
            assertEquals(p.copy(name="40"),reader.load().single())
        } finally { workers.shutdownNow();dir.deleteRecursively() }
    }
    @Test fun nativePcmMetadataRoundTripAndOldProjectsRemainReadable() {
        val dir = Files.createTempDirectory("atmytrack-upgrade").toFile()
        try {
            val p = Project(name = "PCM24", sampleRate = 48000, stems = listOf(Stem(name = "Guitar", pcm = "audio/1.audio", frames = 500, format = PcmFormat(1, PcmEncoding.S24, 80))))
            val store = ProjectStore(dir); store.save(listOf(p))
            assertEquals(p, store.load().single())
            val file = java.io.File(dir, "library.json")
            val json = org.json.JSONObject(file.readText())
            val stem = json.getJSONArray("projects").getJSONObject(0).getJSONArray("stems").getJSONObject(0)
            stem.remove("channels"); stem.remove("encoding"); stem.remove("dataOffset")
            file.writeText(json.toString())
            assertEquals(PcmFormat(), store.load().single().stems.single().format)
        } finally { dir.deleteRecursively() }
    }
    @Test fun roundTripPreservesEveryProjectAndMixerSetting() {
        val dir = Files.createTempDirectory("atmytrack-store").toFile()
        try {
            val store = ProjectStore(dir)
            assertEquals(emptyList<Project>(), store.load())
            val project = Project(name = "Canção çã", sampleRate = 44100,
                stems = listOf(Stem(id="a",name = "Bass", pcm = "audio/x/1.pcm", frames = 987654L, volume = .42f, pan = -.35f, mute = true, solo = true,source="content://fixture/a",external=true,compressed=true,fingerprint="sha256-fixture",bus="bus",route="LEFT",pitchFile="pitch/a.pcm",pitchApplied=3)),
                bpm = 72.1, beats = 6, denominator = 8, multiplier = .5, click = true, clickVolume = .23f,
                master = .74f, masterMute = true, loop = true, key = "Gb", artwork = "artwork/x.jpg",
                markers = listOf(Marker(name = "REFRÃO", start = 123, end = 12345, color = 0xFF3B82F6)),
                dcas=listOf(Dca(name="Band",members=listOf("a","b"),volume=.4f,mute=true)),buses=listOf(Bus(name="Guide",volume=.6f,mute=true,destination="OUT:2")),
                semitones=3,targetKey="A",pitchTracks=listOf("a"),clickSound="Wood",accent=false,clickRoute="LEFT",detectedBpm=71.96,confidence=.94,beatOffset=1234,analysisKey="abc")
            store.save(listOf(project))
            assertEquals(listOf(project), ProjectStore(dir).load())
            store.save(emptyList())
            assertEquals(emptyList<Project>(), ProjectStore(dir).load())
        } finally { dir.deleteRecursively() }
    }
    @Test fun damagedLibraryRaisesErrorInsteadOfSilentlyLosingProjects() {
        val dir = Files.createTempDirectory("atmytrack-corrupt").toFile()
        try {
            java.io.File(dir, "library.json").writeText("not json")
            assertThrows(Exception::class.java) { ProjectStore(dir).load() }
            assertEquals("not json", java.io.File(dir, "library.json").readText())
        } finally { dir.deleteRecursively() }
    }
}
