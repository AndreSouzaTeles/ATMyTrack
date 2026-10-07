import AVFoundation
import SwiftUI

struct PitchReading {var frequency=0.0;var rms=0.0;var confidence=0.0}
@MainActor final class Tuner: ObservableObject {
    @Published var reading=PitchReading()
    @Published var active=false
    @Published var denied=false
    @Published var error:String?
    var visible=false
    var foreground=true
    private var engine:AVAudioEngine?
    private var generation=UUID()
    func request() {
        AVAudioSession.sharedInstance().requestRecordPermission { allowed in DispatchQueue.main.async {
            self.denied = !allowed
            if allowed && self.visible && self.foreground {self.start()}
        }}
    }
    func start() {
        guard engine==nil,visible,foreground else{return}
        guard AVAudioSession.sharedInstance().recordPermission == .granted else {denied=true;return}
        do {
            let session=AVAudioSession.sharedInstance()
            try session.setCategory(.record,mode:.measurement,options:[]);try session.setActive(true)
            let engine=AVAudioEngine(),input=engine.inputNode
            let format=input.outputFormat(forBus:0)
            guard format.sampleRate>0,format.channelCount>0 else {throw AppError.message("Nenhuma entrada de áudio disponível.")}
            let token=UUID();generation=token
            let windowSize=Int(format.sampleRate*0.18)
            let worker=PitchWorker(rate:format.sampleRate,size:windowSize)
            input.installTap(onBus:0,bufferSize:AVAudioFrameCount(windowSize),format:format) { [weak self] buffer,_ in
                worker.accept(buffer) { result in DispatchQueue.main.async {
                    guard let self=self,self.generation==token else{return};self.reading=result
                }}
            }
            self.engine=engine;try engine.start();active=true;denied=false
        } catch {stop();self.error="Não foi possível acessar o microfone. Confira a entrada e tente novamente.";NSLog("Tuner: %@",String(describing:error))}
    }
    func stop() {
        generation=UUID();engine?.inputNode.removeTap(onBus:0);engine?.stop();engine=nil
        active=false;reading=PitchReading();try? AVAudioSession.sharedInstance().setActive(false,options:.notifyOthersOnDeactivation)
    }
}
private final class PitchWorker {
    let rate:Double
    let size:Int
    private let queue=DispatchQueue(label:"ATMyTrack.YIN",qos:.userInitiated)
    private let gate=NSLock()
    private var busy=false
    private var samples=[Float](),history=[Double](),smoothed=0.0
    init(rate:Double,size:Int){self.rate=rate;self.size=size}
    func accept(_ buffer:AVAudioPCMBuffer,completion:@escaping(PitchReading)->Void) {
        guard let pointer=buffer.floatChannelData?[0],gate.try() else{return}
        if busy {gate.unlock();return};busy=true;gate.unlock()
        let copy=Array(UnsafeBufferPointer(start:pointer,count:Int(buffer.frameLength)))
        queue.async { [self] in
            defer{gate.lock();busy=false;gate.unlock()}
            samples.append(contentsOf:copy)
            guard samples.count>=size else{return};samples=Array(samples.suffix(size))
            var rms=0.0,confidence=0.0
            let hz=samples.withUnsafeBufferPointer{atm_yin($0.baseAddress,Int32($0.count),rate,&rms,&confidence)}
            var result=PitchReading(frequency:hz,rms:rms,confidence:confidence)
            if hz>0 {
                let value=log(hz)
                if smoothed>0 && abs(value-smoothed)>0.08 {history=[];smoothed=0}
                history.append(value);history=Array(history.suffix(3))
                let median=history.sorted()[history.count/2]
                smoothed=smoothed==0 ? median : smoothed+0.55*(median-smoothed)
                result.frequency=history.count<2 ? 0 : exp(smoothed)
            }else{history=[];smoothed=0}
            completion(result)
        }
    }
}
