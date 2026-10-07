import SwiftUI
import AVFoundation

struct TunerScreen:View {
    @StateObject private var tuner=Tuner()
    @Environment(\.scenePhase) private var scene
    @AppStorage("tuner.instrument") private var instrument="Violão"
    @AppStorage("tuner.tuning") private var tuning="Standard"
    @AppStorage("tuner.a4") private var a4=440.0
    @AppStorage("tuner.string") private var selected = -1
    private var choices:[Tuning]{Instrument.all.first{$0.id==instrument}?.tunings ?? []}
    private var strings:[Int]{choices.first{$0.id==tuning}?.notes ?? choices.first?.notes ?? []}
    private var midi:Int {
        if strings.indices.contains(selected){return strings[selected]}
        if !strings.isEmpty{return strings.min{abs(Music.cents(tuner.reading.frequency,midi:$0,a4:a4))<abs(Music.cents(tuner.reading.frequency,midi:$1,a4:a4))} ?? 69}
        return Music.nearest(tuner.reading.frequency,a4:a4)
    }
    private var cents:Double{Music.cents(tuner.reading.frequency,midi:midi,a4:a4)}
    var body:some View{Panel(title:"AFINADOR"){
        Picker("Instrumento",selection:$instrument){ForEach(Instrument.all){Text($0.id).tag($0.id)}}.onChange(of:instrument){_ in selected = -1;tuning=choices.first?.id ?? "Standard"}
        if !choices.isEmpty {
            Picker("Afinação",selection:$tuning){ForEach(choices){Text($0.id).tag($0.id)}}
            ScrollView(.horizontal){HStack{Button("AUTO"){selected = -1}.tint(selected == -1 ? Palette.blue:.gray);ForEach(Array(strings.enumerated()),id:\.offset){i,n in Button("\(strings.count-i)ª \(Music.note(n))"){selected=i}.tint(selected==i ? Palette.blue:.gray)}}.buttonStyle(.bordered)}
        }
        VStack(spacing:12){
            Text(tuner.reading.frequency>0 ? Music.note(midi) : "—").font(.system(size:88,weight:.bold,design:.rounded)).foregroundStyle(Palette.light)
            Text(tuner.reading.frequency>0 ? String(format:"%0.2f Hz",tuner.reading.frequency) : "AGUARDANDO SINAL").font(.title3.monospacedDigit())
            GeometryReader{geo in
                let x=max(-50,min(50,cents))
                VStack{HStack{ForEach([-50,-25,0,25,50],id:\.self){n in Text("\(n)").font(.caption);if n != 50{Spacer()}}};ZStack(alignment:.leading){Capsule().fill(.gray.opacity(0.3)).frame(height:5);Rectangle().fill(Palette.blue).frame(width:4,height:26).offset(x:geo.size.width/2);if tuner.reading.frequency>0{Image(systemName:"triangle.fill").foregroundStyle(Palette.light).offset(x:(x+50)/100*(geo.size.width-14))}}}
            }.frame(height:62)
            Text(tuner.reading.frequency>0 ? String(format:"%+0.1f cents",cents) : "TOQUE UMA CORDA").font(.system(size:28,weight:.semibold,design:.rounded))
            if tuner.reading.frequency>0{Text(abs(cents)<=3 ? "✓ AFINADO" : cents<0 ? "↑ AUMENTE A AFINAÇÃO":"↓ DIMINUA A AFINAÇÃO").font(.headline).foregroundStyle(abs(cents)<=3 ? Palette.light:.white)}
        }.frame(maxWidth:.infinity).padding(20).background(Palette.panel,in:RoundedRectangle(cornerRadius:20))
        HStack{Text("MIC").font(.caption);ProgressView(value:min(1,tuner.reading.rms*8)).tint(Palette.blue);Text(tuner.active ? "ATIVO":"PARADO").font(.caption)}
        Stepper("A4 = \(Int(a4)) Hz",value:$a4,in:400..0.480,step:1)
        Button("RESET 440 Hz"){a4=440}
        if !tuner.active {
            Text("O ATMyTrack precisa acessar o microfone para identificar a nota tocada pelo seu instrumento.")
            if tuner.denied && AVAudioSession.sharedInstance().recordPermission == .denied {
                Button("ABRIR AJUSTES DO MICROFONE"){if let url=URL(string:UIApplication.openSettingsURLString){UIApplication.shared.open(url)}}
            } else {Button("PERMITIR / INICIAR"){tuner.request()}.buttonStyle(.borderedProminent)}
        }
        if let error=tuner.error{Text(error).foregroundStyle(.orange);Button("Tentar novamente"){tuner.start()}}
        Text("Análise local e offline. Nenhum áudio é gravado ou enviado. O player fica pausado enquanto você afina.").font(.caption).foregroundStyle(.secondary)
    }.onAppear{if AVAudioSession.sharedInstance().recordPermission == .granted{tuner.start()}}
        .onDisappear{tuner.stop()}
        .onChange(of:scene){phase in if phase == .active {if AVAudioSession.sharedInstance().recordPermission == .granted{tuner.start()}}else{tuner.stop()}}
        .onReceive(NotificationCenter.default.publisher(for:AVAudioSession.interruptionNotification)){_ in tuner.stop()}
        .onReceive(NotificationCenter.default.publisher(for:AVAudioSession.routeChangeNotification)){event in
            if let reason=event.userInfo?[AVAudioSessionRouteChangeReasonKey] as? UInt,reason != AVAudioSession.RouteChangeReason.categoryChange.rawValue{tuner.stop();tuner.error="A entrada de áudio mudou. Toque em Tentar novamente."}
        }
    }
}
