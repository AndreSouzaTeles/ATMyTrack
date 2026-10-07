import AVFoundation
import Combine

@MainActor final class Transport: ObservableObject {
    @Published var seconds = 0.0
    @Published var peak: Float = 0
    @Published var underruns: Int64 = 0
    @Published var levels: [String:Float] = [:]
}
@MainActor final class Player: ObservableObject {
    @Published var song: Song?
    @Published var playing = false
    @Published var preparing = false
    @Published var progress = ""
    @Published var error: String?
    @Published var channels = 2
    let transport = Transport()
    let library: Library
    private var engine: AVAudioEngine?
    private var core: UnsafeMutableRawPointer?
    private var coreTracks: [String] = []
    private var timer: AnyCancellable?
    private var events = Set<AnyCancellable>()
    private let preparation = DispatchQueue(label:"ATMyTrack.Preparation",qos:.userInitiated)
    init(library:Library) {
        self.library=library
        timer=Timer.publish(every:0.05,on:.main,in:.common).autoconnect().sink { [weak self] _ in self?.tick() }
        for name in [AVAudioSession.interruptionNotification,AVAudioSession.routeChangeNotification,AVAudioSession.mediaServicesWereResetNotification] {
            NotificationCenter.default.publisher(for:name).receive(on:RunLoop.main).sink { [weak self] event in
                guard let self=self else{return}
                if event.name==AVAudioSession.routeChangeNotification,
                   let reason=event.userInfo?[AVAudioSessionRouteChangeReasonKey] as? UInt,
                   reason==AVAudioSession.RouteChangeReason.categoryChange.rawValue {return}
                self.pause();self.error="A saída de áudio mudou ou foi interrompida. Confira a conexão e pressione Play para continuar."
            }.store(in:&events)
        }
    }
    private func tick() {
        guard let core=core,let song=song else{return}
        if playing {
            transport.seconds=Double(atm_position(core))/48000
            transport.peak=atm_peak(core)
            transport.underruns=atm_underruns(core)
            transport.levels=Dictionary(uniqueKeysWithValues:coreTracks.enumerated().map{($0.element,atm_track_peak(core,Int32($0.offset)))})
            if !song.loop && transport.seconds>=song.seconds {pause()}
        }
    }
    func edit(_ change:(inout Song)->Void) {
        guard var current=song else{return};change(&current);song=current;library.put(current);applyMixer()
    }
    func select(_ current:Song) {
        guard !preparing else{return};pause();transport.seconds=0
        prepare(current,resume:false,position:0)
    }
    func transform(speed:Int?=nil,speedTracks:Set<String>?=nil,pitch:Int?=nil,pitchTracks:Set<String>?=nil,preset:Int?=nil) {
        guard var current=song,!preparing else{return}
        let originalPosition=transport.seconds*current.scale
        if let speed=speed {current.speed=speed;current.speedTracks=speedTracks ?? current.speedSelection;current.preset=preset ?? speed}
        if let pitch=pitch {current.semitones=pitch;current.pitchTracks=pitchTracks ?? current.pitchTracks}
        prepare(current,resume:playing,position:originalPosition/current.scale)
    }
    private func prepare(_ current:Song,resume:Bool,position:Double) {
        preparing=true;progress="Preparando áudio…"
        preparation.async { [weak self] in
            do {
                try AudioPreparation.prepare(current) { message in DispatchQueue.main.async {self?.progress=message} }
                DispatchQueue.main.async {
                    guard let self=self else{return}
                    let wasPlaying=self.playing
                    let oldScale=self.song?.scale ?? 1
                    let newPosition=(resume && wasPlaying) ? self.transport.seconds*oldScale/current.scale : position
                    self.pause()
                    // Keep mixer edits made while DSP was being prepared.
                    var next=current
                    if let latest=self.song,latest.id==current.id {
                        next=latest;next.speed=current.speed;next.speedTracks=current.speedTracks;next.preset=current.preset
                        next.semitones=current.semitones;next.pitchTracks=current.pitchTracks
                    }
                    self.song=next;self.library.put(next);self.transport.seconds=min(next.seconds,newPosition)
                    self.preparing=false;self.progress="Áudio pronto"
                    self.disposeOutput()
                    if resume && wasPlaying {self.play()}
                }
            } catch {
                NSLog("Preparation: %@",String(describing:error))
                DispatchQueue.main.async {self?.preparing=false;self?.error=error.localizedDescription}
            }
        }
    }
    func importFiles(_ urls:[URL],append:Bool=false) {
        guard !urls.isEmpty,!preparing else{return};pause();preparing=true
        let initial=append ? song : nil
        preparation.async { [weak self] in
            var current=initial ?? Song(name:urls[0].deletingPathExtension().lastPathComponent)
            var failures=[String]()
            for (i,url) in urls.enumerated() {
                DispatchQueue.main.async {self?.progress="Importando \(i+1)/\(urls.count) • \(url.lastPathComponent)"}
                do {let stem=try AudioPreparation.importFile(url);current.stems.append(stem)}
                catch {failures.append(url.lastPathComponent);NSLog("Import %@: %@",url.lastPathComponent,String(describing:error))}
            }
            let result=current,failed=failures
            DispatchQueue.main.async {
                guard let self=self else{return};self.preparing=false
                if !result.stems.isEmpty {self.select(result)}
                if !failed.isEmpty {self.error="Não foi possível importar: \(failed.joined(separator:", ")). O formato pode não ser suportado pelo iOS ou o arquivo pode estar indisponível."}
            }
        }
    }
    func smartTempo() {
        guard let current=song,let stem=current.stems.min(by:{priority($0.name)<priority($1.name)}),!preparing else{return}
        preparing=true;progress="Analisando BPM original…"
        preparation.async { [weak self] in
            do {
                let result=try AudioPreparation.tempo(stem)
                DispatchQueue.main.async {
                    self?.preparing=false
                    self?.edit {if $0.smartClick {$0.bpm=result.0;$0.detectedBpm=result.0;$0.beatOffset=result.1}}
                }
            } catch {DispatchQueue.main.async {self?.preparing=false;self?.error=error.localizedDescription}}
        }
    }
    func play() {
        guard let song=song,!preparing,!song.stems.isEmpty else{return}
        do {
            if transport.seconds>=song.seconds {transport.seconds=0}
            try configureOutput()
            guard let engine=engine,let core=core else{return}
            applyMixer()
            atm_start(core,Int64(transport.seconds*48000),Int64((song.seconds*48000).rounded()))
            try engine.start();playing=true
        } catch {pause();self.error="Não foi possível iniciar a saída de áudio. Confira os arquivos e o dispositivo conectado.";NSLog("Playback: %@",String(describing:error))}
    }
    func pause() {
        engine?.pause()
        if let core=core {transport.seconds=Double(atm_position(core))/48000;atm_stop(core)}
        playing=false
    }
    func stop() {pause();transport.seconds=0;disposeOutput()}
    func seek(_ seconds:Double) {
        guard let song=song else{return};let resume=playing;pause();transport.seconds=max(0,min(song.seconds,seconds));if resume {play()}
    }
    func next() {
        guard let id=song?.id,let i=library.projects.firstIndex(where:{$0.id==id}),!library.projects.isEmpty else{return}
        select(library.projects[(i+1)%library.projects.count])
    }
    func remove(_ current:Song) { if song?.id==current.id {stop();song=nil};library.delete(current) }
    private func disposeOutput() {
        engine?.stop();engine=nil
        if let core=core {atm_destroy(core)};core=nil
    }
    private func configureOutput() throws {
        disposeOutput()
        let session=AVAudioSession.sharedInstance()
        try session.setCategory(.playback,mode:.default,options:[])
        try session.setPreferredSampleRate(48000)
        try session.setPreferredIOBufferDuration(512.0/48000)
        try session.setActive(true)
        let engine=AVAudioEngine()
        let available=engine.outputNode.inputFormat(forBus:0).channelCount
        channels=max(2,min(16,Int(available)))
        guard let pointer=atm_create(Int32(channels)),let song=song else {throw AppError.message("Saída indisponível.")}
        core=pointer
        coreTracks=song.stems.map(\.id)
        for stem in song.stems {
            let frames=Int64((Double(stem.frames)/song.rate(for:stem)).rounded())
            let index=Files.source(song,stem).path.withCString {atm_add(pointer,$0,frames)}
            guard index>=0 else {throw AppError.message("A track \(stem.name) precisa ser preparada novamente.")}
        }
        let format=AVAudioFormat(standardFormatWithSampleRate:48000,channels:AVAudioChannelCount(channels))!
        let count=channels
        let scratch=RenderScratch(channels:count)
        let node=AVAudioSourceNode(format:format) { _,_,frames,buffers in
            let list=UnsafeMutableAudioBufferListPointer(buffers)
            // Deinterleave from the preallocated ring into the hardware's planar buffers.
            guard frames<=16384 else {for buffer in list {if let p=buffer.mData{memset(p,0,Int(buffer.mDataByteSize))}};return noErr}
            atm_read(pointer,scratch.data,Int32(frames))
            for channel in 0..<min(count,list.count) {
                guard let data=list[channel].mData?.assumingMemoryBound(to:Float.self) else{continue}
                for frame in 0..<Int(frames){data[frame]=scratch.data[frame*count+channel]}
            }
            return noErr
        }
        engine.attach(node);engine.connect(node,to:engine.mainMixerNode,format:format)
        engine.prepare();self.engine=engine
    }
    func applyMixer() {
        guard let current=song,let core=core else{return}
        let solo=current.stems.contains{$0.solo}
        for stem in current.stems {
            guard let i=coreTracks.firstIndex(of:stem.id) else {continue}
            var gain=stem.mute || (solo && !stem.solo) ? Float(0) : stem.volume
            for dca in current.dcas where dca.members.contains(stem.id) {gain *= dca.mute ? 0 : dca.volume}
            var route=stem.route
            if let bus=current.buses.first(where:{$0.id==stem.bus}) {gain *= bus.mute ? 0 : bus.volume;route=bus.route}
            if route<0 {gain *= current.masterMute ? 0 : current.master}
            // Routes unavailable after hot plug are muted instead of redirected silently.
            if route>=100 && route-100>=channels || route>=0 && route<100 && route+1>=channels {gain=0}
            atm_mix(core,Int32(i),gain,stem.pan,Int32(route))
        }
        let clickMaster:Float=current.clickRoute<0 ? (current.masterMute ? 0 : current.master) : 1
        atm_click(core,current.effectiveBPM*current.multiplier,Int32(current.beats),current.click ? current.clickVolume*clickMaster : 0,current.accent ? 1:0,Int32(current.clickSound),Int32(current.clickRoute),Int64(current.beatOffset/current.scale*48000))
        let marker=current.markers.first{$0.id==current.selectedMarker}
        atm_loop(core,Int64((marker?.start ?? 0)/current.scale*48000),Int64((marker?.end ?? current.originalSeconds)/current.scale*48000),current.loop ? 1:0)
    }
}
private func priority(_ name:String)->Int {let n=name.lowercased();if n.contains("click")||n.contains("metro"){return 0};if n.contains("drum")||n.contains("bateria"){return 1};return 2}
private final class RenderScratch {
    let data:UnsafeMutablePointer<Float>
    init(channels:Int){data = .allocate(capacity:16384*channels);data.initialize(repeating:0,count:16384*channels)}
    deinit{data.deallocate()}
}
