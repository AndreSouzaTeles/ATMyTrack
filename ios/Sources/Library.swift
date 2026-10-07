import Foundation
import AVFoundation

enum AppError: LocalizedError {
    case message(String)
    var errorDescription: String? { if case .message(let text) = self { return text }; return nil }
}
enum Files {
    static let root = FileManager.default.urls(for:.documentDirectory,in:.userDomainMask)[0].appendingPathComponent("ATMyTrack",isDirectory:true)
    static let cache = FileManager.default.urls(for:.cachesDirectory,in:.userDomainMask)[0].appendingPathComponent("DSP",isDirectory:true)
    static func ensure() throws { for url in [root,cache] { try FileManager.default.createDirectory(at:url,withIntermediateDirectories:true) } }
    static func url(_ name: String) -> URL { root.appendingPathComponent(name) }
    static func dsp(_ song: Song, _ stem: Stem) -> URL {
        cache.appendingPathComponent("v1-\(stem.id)-\(song.speedSelection.contains(stem.id) ? song.speed : 100)-\(song.pitch(for:stem)).pcm")
    }
    static func source(_ song: Song,_ stem: Stem) -> URL {
        song.rate(for:stem)==1 && song.pitch(for:stem)==0 ? url(stem.file) : dsp(song,stem)
    }
    static func prune(protect: Set<URL>) {
        let urls = (try? FileManager.default.contentsOfDirectory(at:cache,includingPropertiesForKeys:[.contentModificationDateKey,.fileSizeKey])) ?? []
        let ordered = urls.sorted { ((try? $0.resourceValues(forKeys:[.contentModificationDateKey]).contentModificationDate) ?? .distantPast) < ((try? $1.resourceValues(forKeys:[.contentModificationDateKey]).contentModificationDate) ?? .distantPast) }
        var total = ordered.reduce(Int64(0)) { $0 + Int64((try? $1.resourceValues(forKeys:[.fileSizeKey]).fileSize) ?? 0) }
        for url in ordered where total > 2*1024*1024*1024 && !protect.contains(url) {
            let size = Int64((try? url.resourceValues(forKeys:[.fileSizeKey]).fileSize) ?? 0)
            if (try? FileManager.default.removeItem(at:url)) != nil { total -= size }
        }
    }
}
struct LibraryDocument: Codable { var version = 1; var projects: [Song] }
@MainActor final class Library: ObservableObject {
    @Published var projects: [Song] = []
    @Published var error: String?
    init() {
        do {
            try Files.ensure()
            let file = Files.url("library.json")
            if FileManager.default.fileExists(atPath:file.path) {
                projects = try JSONDecoder().decode(LibraryDocument.self,from:Data(contentsOf:file)).projects
            }
        } catch { self.error = "Não foi possível abrir a biblioteca. Os arquivos existentes foram preservados."; NSLog("Library: %@",String(describing:error)) }
    }
    func save() {
        do { try JSONEncoder().encode(LibraryDocument(projects:projects)).write(to:Files.url("library.json"),options:.atomic) }
        catch { self.error = "Não foi possível salvar as alterações. Confira o espaço disponível.";NSLog("Save: %@",String(describing:error)) }
    }
    func put(_ song: Song) { if let index=projects.firstIndex(where:{$0.id==song.id}) { projects[index]=song } else { projects.append(song) };save() }
    func delete(_ song: Song) { projects.removeAll{$0.id==song.id};save() /* originals/copies retained for recoverability */ }
}
enum AudioPreparation {
    static func importFile(_ url: URL) throws -> Stem {
        let access = url.startAccessingSecurityScopedResource()
        defer { if access { url.stopAccessingSecurityScopedResource() } }
        let source = try AVAudioFile(forReading:url)
        guard source.length>0,source.processingFormat.channelCount<=2 else { throw AppError.message("Este arquivo precisa ter áudio mono ou estéreo.") }
        let target = AVAudioFormat(commonFormat:.pcmFormatFloat32,sampleRate:48000,channels:2,interleaved:true)!
        guard let converter=AVAudioConverter(from:source.processingFormat,to:target),
              let input=AVAudioPCMBuffer(pcmFormat:source.processingFormat,frameCapacity:4096),
              let output=AVAudioPCMBuffer(pcmFormat:target,frameCapacity:4096) else { throw AppError.message("Não foi possível preparar este formato de áudio.") }
        let id=UUID().uuidString, name="\(id).pcm"
        let temporary=Files.url(name+".part"),destination=Files.url(name)
        FileManager.default.createFile(atPath:temporary.path,contents:nil)
        let handle=try FileHandle(forWritingTo:temporary)
        var complete=false
        defer { try? handle.close(); if !complete { try? FileManager.default.removeItem(at:temporary) } }
        var frames:Int64=0, readError:Error?, emptyPasses=0
        while true {
            var conversionError:NSError?
            let status=converter.convert(to:output,error:&conversionError) { requested,state in
                if source.framePosition>=source.length { state.pointee = .endOfStream;return nil }
                do { try source.read(into:input,frameCount:min(requested,input.frameCapacity));state.pointee = .haveData;return input }
                catch { readError=error;state.pointee = .endOfStream;return nil }
            }
            if let error=readError ?? conversionError { throw error }
            let n=Int(output.frameLength)
            if n>0,let bytes=output.audioBufferList.pointee.mBuffers.mData {
                try handle.write(contentsOf:Data(bytes:bytes,count:n*8));frames+=Int64(n);emptyPasses=0
            } else { emptyPasses+=1 }
            if status == .endOfStream { break }
            if status == .error || emptyPasses>20 { throw AppError.message("O arquivo não pôde ser decodificado.") }
        }
        guard frames>0 else {throw AppError.message("Este arquivo não contém áudio utilizável.")}
        try handle.synchronize();try handle.close();try FileManager.default.moveItem(at:temporary,to:destination);complete=true
        return Stem(id:id,name:url.deletingPathExtension().lastPathComponent,file:name,frames:frames,peaks:try waveform(destination,frames:frames))
    }
    static func waveform(_ url:URL,frames:Int64) throws -> [Float] {
        let handle=try FileHandle(forReadingFrom:url);defer{try? handle.close()}
        var peaks=[Float](repeating:0,count:512),position:Int64=0
        while let data=try handle.read(upToCount:32768),!data.isEmpty {
            data.withUnsafeBytes { raw in
                for i in stride(from:0,to:raw.count-7,by:8) {
                    let l=raw.loadUnaligned(fromByteOffset:i,as:Float.self),r=raw.loadUnaligned(fromByteOffset:i+4,as:Float.self)
                    let bin=min(511,Int(position*512/max(1,frames)));peaks[bin]=max(peaks[bin],abs(l),abs(r));position+=1
                }
            }
        };return peaks
    }
    static func prepare(_ song:Song, progress: @escaping (String)->Void) throws {
        try Files.ensure()
        for (index,stem) in song.stems.enumerated() {
            let rate=song.rate(for:stem),pitch=song.pitch(for:stem)
            if rate==1 && pitch==0 { continue }
            let out=Files.dsp(song,stem),expected=Int64((Double(stem.frames)/rate).rounded())*8
            if let size=try? out.resourceValues(forKeys:[.fileSizeKey]).fileSize,Int64(size)==expected {continue}
            progress("Preparando \(song.speed)% • \(index+1)/\(song.stems.count) • \(stem.name)")
            let temporary=out.appendingPathExtension("part")
            let result=Files.url(stem.file).path.withCString { source in temporary.path.withCString { target in atm_stretch(source,target,stem.frames,rate,Int32(pitch)) } }
            guard result==0 else {throw AppError.message("Não foi possível preparar a track \(stem.name). Confira o espaço livre e tente novamente.")}
            if FileManager.default.fileExists(atPath:out.path) {try FileManager.default.removeItem(at:out)}
            try FileManager.default.moveItem(at:temporary,to:out)
        }
        Files.prune(protect:Set(song.stems.map{Files.source(song,$0)}))
    }
    static func tempo(_ stem:Stem) throws -> (Double,Double) {
        let handle=try FileHandle(forReadingFrom:Files.url(stem.file));defer{try? handle.close()}
        var envelope=[Double](),previous=0.0
        for _ in 0..<12000 {
            guard let data=try handle.read(upToCount:480*8),data.count==480*8 else {break}
            let power=data.withUnsafeBytes { raw -> Double in
                var sum=0.0
                for i in stride(from:0,to:raw.count,by:4) {let v=Double(raw.loadUnaligned(fromByteOffset:i,as:Float.self));sum+=v*v}
                return sqrt(sum/960)
            }
            envelope.append(max(0,power-previous*0.75));previous=power
        }
        guard envelope.count>=400 else {throw AppError.message("Áudio muito curto para detectar BPM.")}
        var scores=[Double](repeating:0,count:174)
        for lag in 24..0.172 {
            var dot=0.0,a=0.0,b=0.0
            for i in lag..<envelope.count {dot+=envelope[i]*envelope[i-lag];a+=envelope[i]*envelope[i];b+=envelope[i-lag]*envelope[i-lag]}
            scores[lag]=dot/max(1e-12,sqrt(a*b))
        }
        let best=scores.max() ?? 0
        guard best>0.18, let lag=(25..0.171).first(where:{scores[$0]>=best*0.85 && scores[$0]>=scores[$0-1] && scores[$0]>=scores[$0+1]}) else {throw AppError.message("Não foi encontrado um pulso estável. Use TAP ou ajuste o BPM.")}
        let offset=Double(envelope.firstIndex(where:{$0>(envelope.max() ?? 0)*0.2}) ?? 0)/100
        return (6000/Double(lag),offset)
    }
}
