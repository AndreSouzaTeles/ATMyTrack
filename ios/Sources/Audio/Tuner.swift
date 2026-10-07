import AVFoundation
import SwiftUI

struct PitchReading {var frequency=0.0;var rms=0.0;var confidence=0.0}
@MainActor final class Tuner: ObservableObject {
    @Published var reading=PitchReading()
    @Published var active=false
    @Published var denied=false
    @Published var error:String?
    private var engine:AVAudioEngine?
    private let worker=DispatchQueue(label:"ATMyTrack.YIN",qos:.userInitiated)
    private var generation=UUID()
    private let gate=NSLock()
    private var busy=false
    func request() {
        AVAudioSession.sharedInstance().requestRecordPermission { allowed in DispatchQueue.main.async {
            self.denied = !allowed
            if allowed {self.start()}
        }}
    }
    func start() {
        guard engine==nil else{return}
        guard AVAudioSession.sharedInstance().recordPermission == .granted else {denied=true;return}
        do {
            let session=AVAudioSession.sharedInstance()
            try session.setCategory(.record,mode:.measurement,options:[]);try session.setActive(true)
            let engine=AVAudioEngine(),input=engine.inputNode
            let format=input.outputFormat(forBus:0)
            guard format.sampleRate>0,format.channelCount>0 else {throw AppError.message("Nenhuma entrada de áudio disponível.")}
            let token=UUID();generation=token
            let windowSize=Int(format.sampleRate*0.18)
            var samples=[Float](),history=[Double](),smoothed=0.0
            input.installTap(onBus:0,bufferSize:AVAudioFrameCount(windowSize),format:format) { [weak self] buffer,_ in
                guard let self=self,let pointer=buffer.floatChannelData?[0] else{return}
                // Bound analysis work to one queued buffer. Pitch analysis never runs on main/audio render threads.
                guard self.gate.try() else{return}
                if self.busy {self.gate.unlock();return};self.busy=true;self.gate.unlock()
                let copy=Array(UnsafeBufferPointer(start:pointer,count:Int(buffer.frameLength)))
                self.worker.async {
                    defer{self.gate.lock();self.busy=false;self.gate.unlock()}
                    samples.append(contentsOf:copy)
                    guard samples.count>=windowSize else{return}
                    samples=Array(samples.suffix(windowSize))
                    var rms=0.0,confidence=0.0
                    let hz=samples.withUnsafeBufferPointer {atm_yin($0.baseAddress,Int32($0.count),format.sampleRate,&rms,&confidence)}
                    var result=PitchReading(frequency:hz,rms:rms,confidence:confidence)
                    if hz>0 {
                        let value=log(hz)
                        if smoothed>0 && abs(value-smoothed)>0.08 {history=[];smoothed=0}
                        history.append(value);history=Array(history.suffix(3))
                        let median=history.sorted()[history.count/2]
                        smoothed=smoothed==0 ? median : smoothed+0.55*(median-smoothed)
                        result.frequency=history.count<2 ? 0 : exp(smoothed)
                    } else {history=[];smoothed=0}
                    let published=result
                    DispatchQueue.main.async {if self.generation==token {self.reading=published}}
                }
            }
            try engine.start();self.engine=engine;active=true;denied=false
        } catch {stop();self.error="Não foi possível acessar o microfone. Confira a entrada e tente novamente.";NSLog("Tuner: %@",String(describing:error))}
    }
    func stop() {
        generation=UUID();engine?.inputNode.removeTap(onBus:0);engine?.stop();engine=nil
        active=false;reading=PitchReading();try? AVAudioSession.sharedInstance().setActive(false,options:.notifyOthersOnDeactivation)
    }
}
