import XCTest
import AVFoundation
@testable import ATMyTrack

final class CoreTests:XCTestCase {
    func testTunerReferenceNotesAndDetuning() {
        for midi in [23,28,40,45,50,55,59,64,69] {
            for cents in [-18.0,0,14] {
                let hz=Music.frequency(midi)*pow(2,cents/1200)
                let samples=(0..<8192).map{Float(0.5*sin(2*Double.pi*hz*Double($0)/48000))}
                var rms=0.0,confidence=0.0
                let measured=samples.withUnsafeBufferPointer{atm_yin($0.baseAddress,Int32($0.count),48000,&rms,&confidence)}
                XCTAssertEqual(Music.nearest(measured),midi)
                XCTAssertEqual(Music.cents(measured,midi:midi),cents,accuracy:1)
                XCTAssertGreaterThan(confidence,0.9)
            }
        }
    }
    func testTunerSilenceWeakSignalAndNoise() {
        var seed:UInt64=42
        let noise=(0..<8192).map{_ -> Float in seed=seed &* 6364136223846793005 &+ 1;return Float(Int32(truncatingIfNeeded:seed>>32))/Float(Int32.max)*0.1}
        for samples in [[Float](repeating:0,count:8192),(0..<8192).map{Float(0.0001*sin(Double($0)*0.1))},noise] {
            var rms=0.0,confidence=0.0
            let hz=samples.withUnsafeBufferPointer{atm_yin($0.baseAddress,Int32($0.count),48000,&rms,&confidence)}
            XCTAssertEqual(hz,0)
        }
    }
    func testStrongHarmonics() {
        let hz=Music.frequency(28)
        let samples=(0..<8192).map{n -> Float in let phase=2*Double.pi*hz*Double(n)/48000;return Float(0.1*sin(phase)+0.4*sin(phase*2)+0.2*sin(phase*3))}
        var rms=0.0,confidence=0.0
        let measured=samples.withUnsafeBufferPointer{atm_yin($0.baseAddress,Int32($0.count),48000,&rms,&confidence)}
        XCTAssertEqual(measured,hz,accuracy:0.05)
    }
    func testTimelineAndIndependentSelections() throws {
        let a=Stem(name:"A",file:"a.pcm",frames:48000*240),b=Stem(name:"B",file:"b.pcm",frames:48000*240)
        var song=Song(name:"Test",stems:[a,b]);song.bpm=72;song.speed=75
        XCTAssertEqual(song.speedSelection,Set([a.id,b.id]));XCTAssertEqual(song.seconds,320,accuracy:0.01);XCTAssertEqual(song.effectiveBPM,54)
        song.semitones=2;song.pitchTracks=[a.id];song.speedTracks=[a.id]
        XCTAssertEqual(song.rate(for:b),1);XCTAssertEqual(song.pitch(for:b),0);XCTAssertEqual(song.effectiveBPM,72)
        let original=try JSONEncoder().encode(song),restored=try JSONDecoder().decode(Song.self,from:original)
        XCTAssertEqual(restored,song);song.semitones=0;XCTAssertEqual(song.speed,75)
    }
    func testA4AndInstrumentDefinitions(){
        XCTAssertEqual(Bundle.main.object(forInfoDictionaryKey:"CFBundleShortVersionString") as? String,"0.1.0")
        XCTAssertEqual(Music.frequency(69),440);XCTAssertEqual(Music.frequency(57,a4:432),216)
        XCTAssertEqual(Instrument.all.count,13);XCTAssertEqual(Music.note(23),"B0");XCTAssertEqual(Music.note(40),"E2")
        XCTAssertEqual(Music.faderPosition(1),0.78,accuracy:0.0001)
        XCTAssertEqual(Music.faderGain(0.78),1,accuracy:0.0001)
    }
    func testStretchDurationPitchAndCombinedProcessing() throws {
        try Files.ensure()
        let directory=FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        try FileManager.default.createDirectory(at:directory,withIntermediateDirectories:true)
        defer{try? FileManager.default.removeItem(at:directory)}
        let input=directory.appendingPathComponent("input.pcm"),frames=48000*3
        var signal=[Float](repeating:0,count:frames*2)
        for n in 0..<frames {let value=Float(0.4*sin(2*Double.pi*220*Double(n)/48000));signal[n*2]=value;signal[n*2+1]=value}
        try signal.withUnsafeBytes{try Data($0).write(to:input)}
        for speed in [0.5,0.8,1.0,1.2,2.0] {for pitch in [0,2]{
            let output=directory.appendingPathComponent("out.pcm")
            let status=input.path.withCString{s in output.path.withCString{atm_stretch(s,$0,Int64(frames),speed,Int32(pitch))}}
            XCTAssertEqual(status,0)
            let data=try Data(contentsOf:output)
            XCTAssertEqual(data.count,Int((Double(frames)/speed).rounded())*8)
            let samples=data.withUnsafeBytes{raw in (0..<8192).map{raw.loadUnaligned(fromByteOffset:(24000+$0)*8,as:Float.self)}}
            var rms=0.0,confidence=0.0
            let hz=samples.withUnsafeBufferPointer{atm_yin($0.baseAddress,Int32($0.count),48000,&rms,&confidence)}
            XCTAssertEqual(hz,220*pow(2,Double(pitch)/12),accuracy:0.8)
        }}
    }
    func testMixerNineteenTracksSeekLoopMuteAndRouting() throws {
        let url=FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString+".pcm")
        let samples=[Float](repeating:0.01,count:48000*2)
        try samples.withUnsafeBytes{try Data($0).write(to:url)};defer{try? FileManager.default.removeItem(at:url)}
        guard let core=atm_create(2) else {return XCTFail("No core")};defer{atm_destroy(core)}
        for i in 0..<19 {XCTAssertEqual(url.path.withCString{atm_add(core,$0,48000)},Int32(i));atm_mix(core,Int32(i),1,0,-1)}
        atm_start(core,0,48000)
        var result=[Float](repeating:0,count:1024)
        for _ in 0..<10 {result.withUnsafeMutableBufferPointer{atm_read(core,$0.baseAddress,512)};Thread.sleep(forTimeInterval:0.011)}
        XCTAssertEqual(result[0],0.19,accuracy:0.001);XCTAssertEqual(atm_underruns(core),0);atm_stop(core)
        for i in 0..<19 {atm_mix(core,Int32(i),i==0 ? 1:0,0,-2)}
        atm_loop(core,1000,2024,1);atm_start(core,1000,48000)
        for _ in 0..<10{result.withUnsafeMutableBufferPointer{atm_read(core,$0.baseAddress,512)};Thread.sleep(forTimeInterval:0.011)}
        XCTAssertEqual(result[0],0.01,accuracy:0.001);XCTAssertEqual(result[1],0,accuracy:0.001)
        XCTAssertTrue((1000...2024).contains(atm_position(core)));atm_stop(core)
    }
    @MainActor func testNativeOutputAndImportedWave() async throws {
        let library=Library();try TestFixture.create(in:library)
        guard let song=library.projects.first(where:{$0.name=="Sessão de teste iOS"}) else{return XCTFail("Import failed")}
        XCTAssertEqual(song.stems.count,2)
        XCTAssertEqual(song.stems[0].frames,48000*30)
        let player=Player(library:library);player.select(song)
        for _ in 0..<100 {if !player.preparing{break};try await Task.sleep(nanoseconds:100_000_000)}
        XCTAssertFalse(player.preparing)
        player.play();try await Task.sleep(nanoseconds:2_000_000_000)
        XCTAssertNil(player.error);XCTAssertTrue(player.playing);XCTAssertGreaterThan(player.transport.seconds,1)
        player.seek(10);try await Task.sleep(nanoseconds:500_000_000)
        XCTAssertGreaterThanOrEqual(player.transport.seconds,10)
        player.pause();let position=player.transport.seconds
        try await Task.sleep(nanoseconds:300_000_000)
        XCTAssertEqual(player.transport.seconds,position)
        player.stop();XCTAssertEqual(player.transport.seconds,0)
    }
    @MainActor func testNativeNineteenTracksAndSpeed() async throws {
        let library=Library();try TestFixture.create(in:library)
        guard var song=library.projects.first(where:{$0.name=="Sessão de teste iOS"}) else{return XCTFail("Import failed")}
        let originals=song.stems
        song.id=UUID().uuidString;song.name="Perfil 19 tracks iOS"
        song.stems=(0..<19).map{i in var stem=originals[i%originals.count];stem.id=UUID().uuidString;stem.volume=0.1;stem.name="Track \(i+1)";return stem}
        let player=Player(library:library);player.select(song)
        for _ in 0..<100 {if !player.preparing{break};try await Task.sleep(nanoseconds:100_000_000)}
        for speed in [100,80,120] {
            if speed != 100 {
                player.transform(speed:speed,speedTracks:Set(song.stems.map(\.id)))
                for _ in 0..<1200 {if !player.preparing{break};try await Task.sleep(nanoseconds:100_000_000)}
            }
            XCTAssertFalse(player.preparing);XCTAssertNil(player.error)
            player.seek(0);player.play();let start=Date()
            try await Task.sleep(nanoseconds:3_000_000_000)
            print("IOS_PROFILE tracks=19 speed=\(speed) elapsed=\(Date().timeIntervalSince(start)) position=\(player.transport.seconds) underruns=\(player.transport.underruns)")
            XCTAssertTrue(player.playing);XCTAssertGreaterThan(player.transport.seconds,2)
            XCTAssertEqual(player.transport.underruns,0)
            player.stop()
        }
        library.delete(song)
    }
}
