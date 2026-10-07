#if DEBUG
import AVFoundation
enum TestFixture {
    @MainActor static func create(in library:Library) throws {
        guard !library.projects.contains(where:{$0.name=="Sessão de teste iOS"}) else{return}
        var song=Song(name:"Sessão de teste iOS");song.bpm=120;song.key="A"
        let format=AVAudioFormat(standardFormatWithSampleRate:48000,channels:2)!
        for (index,hz) in [220.0,110.0].enumerated() {
            let url=FileManager.default.temporaryDirectory.appendingPathComponent("\(index==0 ? "Guitarra":"Baixo").wav")
            do {
                let file=try AVAudioFile(forWriting:url,settings:format.settings)
                let buffer=AVAudioPCMBuffer(pcmFormat:format,frameCapacity:48000)!
                for second in 0..<30 {
                    buffer.frameLength=48000
                    for frame in 0..<48000 {
                        let phase=Double(second*48000+frame)/48000
                        let value=Float(0.1*sin(2*Double.pi*hz*phase))
                        buffer.floatChannelData![0][frame]=value;buffer.floatChannelData![1][frame]=value
                    };try file.write(from:buffer)
                }
            }
            song.stems.append(try AudioPreparation.importFile(url))
            try? FileManager.default.removeItem(at:url)
        }
        song.markers=[Marker(name:"REFRÃO",start:5,end:10)]
        library.put(song)
    }
}
#endif
