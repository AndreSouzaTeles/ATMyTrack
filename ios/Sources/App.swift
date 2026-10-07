import SwiftUI
import UniformTypeIdentifiers

enum Palette {
    static let background=Color(red:0.035,green:0.043,blue:0.065)
    static let panel=Color(red:0.075,green:0.09,blue:0.135)
    static let blue=Color(red:0.23,green:0.48,blue:0.96)
    static let light=Color(red:0.58,green:0.76,blue:1)
}
@main struct ATMyTrackApp: App {
    @StateObject private var library:Library
    @StateObject private var player:Player
    init() {
        let lib=Library()
        #if DEBUG
        if ProcessInfo.processInfo.arguments.contains("--ui-fixture") {try? TestFixture.create(in:lib)}
        #endif
        _library=StateObject(wrappedValue:lib);_player=StateObject(wrappedValue:Player(library:lib))
    }
    var body: some Scene {
        WindowGroup {
            PlayerScreen(player:player,library:library).preferredColorScheme(.dark).tint(Palette.blue)
        }
    }
}
enum Destination:String,Identifiable {case menu,search,project,metronome,pitch,speed,marker,tuner,payment,help,groups;var id:String{rawValue}}
struct PlayerScreen: View {
    @ObservedObject var player:Player
    @ObservedObject var library:Library
    @State private var destination:Destination?
    @State private var importing=false
    @State private var append=false
    @State private var mixer=false
    @State private var removing:Song?
    @State private var dragging:String?
    var body:some View {
        VStack(spacing:10) {
            header
            if player.preparing {HStack{ProgressView();Text(player.progress).font(.caption);Spacer()}.padding(.horizontal)}
            TransportBar(player:player,clock:player.transport)
            ScrollView {
                VStack(alignment:.leading,spacing:16) {
                    projects
                    if let song=player.song {
                        HStack{VStack(alignment:.leading){Text(song.name).font(.title2.bold());Text("\(song.stems.count) TRACKS · 48 kHz · ESTÉREO").font(.caption).foregroundStyle(.secondary)};Spacer();Button("EDITAR"){destination = .project}}
                        if !mixer {
                            Timeline(player:player,clock:player.transport)
                            ScrollView(.horizontal,showsIndicators:false){HStack{ForEach(song.markers){marker in
                                Text(marker.name).padding(10).background(song.selectedMarker==marker.id ? Palette.blue : Palette.panel,in:RoundedRectangle(cornerRadius:9))
                                    .onTapGesture{player.seek(marker.start/song.scale)}
                                    .onLongPressGesture{player.edit{$0.selectedMarker = $0.selectedMarker==marker.id ? "" : marker.id}}
                            }}}
                        }
                        toolbar
                        HStack{Text("MIXER").font(.caption.bold()).tracking(2);Spacer();Text(player.playing ? "● REPRODUZINDO" : "● PRONTO").font(.caption).foregroundStyle(Palette.light)}
                        ScrollView(.horizontal,showsIndicators:true) {
                            HStack(alignment:.top,spacing:10) {
                                ForEach(song.stems) {stem in
                                    ChannelStrip(stem:stem,player:player)
                                        .onDrag{dragging=stem.id;return NSItemProvider(object:stem.id as NSString)}
                                        .onDrop(of:[UTType.text],delegate:TrackDrop(target:stem.id,dragging:$dragging,player:player))
                                }
                                MasterStrip(player:player)
                            }.padding(.bottom,8)
                        }
                        if mixer {Button("DCA • BUS • ROUTING"){destination = .groups}.buttonStyle(.borderedProminent)}
                    } else {ContentUnavailable()}
                }.padding(.horizontal,16).padding(.bottom,20)
            }
            Picker("Página",selection:$mixer){Text("Playback").tag(false);Text("Mixer").tag(true)}.pickerStyle(.segmented).padding(.horizontal).padding(.bottom,4)
        }
        .background(Palette.background.ignoresSafeArea())
        .sheet(item:$destination){page in
            switch page {
            case .menu: MainMenu {chosen in
                destination=nil
                if chosen == nil {mixer=false}
                else {DispatchQueue.main.asyncAfter(deadline:.now()+0.3){if chosen == .tuner {player.pause()};destination=chosen}}
            }
            case .search: SearchProjects(library:library){player.select($0);destination=nil;mixer=false}
            case .project: ProjectEditor(player:player,onImport:{destination=nil;append=true;importing=true})
            case .metronome: MetronomePanel(player:player)
            case .pitch: DSPPanel(player:player,speedMode:false)
            case .speed: DSPPanel(player:player,speedMode:true)
            case .marker: MarkerEditor(player:player)
            case .tuner: TunerScreen()
            case .payment: SupportScreen()
            case .help: HelpScreen()
            case .groups: GroupsPanel(player:player)
            }
        }
        .fileImporter(isPresented:$importing,allowedContentTypes:[.audio],allowsMultipleSelection:true){result in
            switch result {case .success(let urls):player.importFiles(urls,append:append);case .failure:player.error="Não foi possível acessar os arquivos selecionados."}
        }
        .alert("ATMyTrack",isPresented:Binding(get:{player.error != nil || library.error != nil},set:{if !$0 {player.error=nil;library.error=nil}})){
            Button("OK"){player.error=nil;library.error=nil}
        }message:{Text(player.error ?? library.error ?? "")}
        .alert("Excluir projeto?",isPresented:Binding(get:{removing != nil},set:{if !$0 {removing=nil}})){
            Button("Cancelar",role:.cancel){removing=nil}
            Button("Excluir",role:.destructive){if let song=removing {player.remove(song)};removing=nil}
        }message:{Text("O projeto será removido da biblioteca. Os arquivos originais serão preservados.")}
    }
    private var header:some View {
        HStack(spacing:12){
            Button{destination = .menu}label:{Image(systemName:"line.3.horizontal").font(.title2).frame(width:44,height:44)}.accessibilityLabel("Menu principal")
            Image("Brand").resizable().scaledToFit().frame(width:34,height:34)
            Text("ATMyTrack").font(.headline);Spacer()
            Button{destination = .search}label:{Image(systemName:"magnifyingglass").frame(width:44,height:44)}.accessibilityLabel("Pesquisar projetos")
            Button("IMPORTAR"){append=false;importing=true}.font(.subheadline.bold()).disabled(player.preparing)
        }.padding(.horizontal,12)
    }
    private var projects:some View {
        ScrollView(.horizontal,showsIndicators:false){HStack(spacing:10){
            ForEach(library.projects){song in
                ZStack(alignment:.bottomLeading){
                    RoundedRectangle(cornerRadius:16).fill(Palette.panel)
                    if !song.artwork.isEmpty,let image=UIImage(contentsOfFile:Files.url(song.artwork).path){Image(uiImage:image).resizable().scaledToFill().frame(width:220,height:112).clipped().opacity(0.25)}
                    VStack(alignment:.leading,spacing:6){HStack{Spacer();Button{removing=song}label:{Image(systemName:"minus.circle").foregroundStyle(.red)}};Spacer();Text(song.name).font(.headline).lineLimit(1);Text("\(song.key) · \(song.effectiveBPM,specifier:"%.1f") BPM · \(Music.time(song.seconds))").font(.caption).foregroundStyle(Palette.light)}.padding(12)
                }.frame(width:220,height:112).clipShape(RoundedRectangle(cornerRadius:16)).overlay(RoundedRectangle(cornerRadius:16).stroke(player.song?.id==song.id ? Palette.blue : .clear,lineWidth:2)).onTapGesture{player.select(song)}
            }
            Button{append=false;importing=true}label:{Label("NOVO PROJETO",systemImage:"plus").frame(width:190,height:110).background(Palette.panel,in:RoundedRectangle(cornerRadius:16))}.disabled(player.preparing)
        }.padding(2)}
    }
    private var toolbar:some View {
        VStack(spacing:8){
            HStack{Button{player.edit{$0.loop.toggle()}}label:{Label("LOOP",systemImage:"repeat").foregroundStyle(player.song?.loop==true ? Palette.light : .white)};Button{destination = .marker}label:{Label("SEÇÃO",systemImage:"flag")};Spacer()}
            ViewThatFits(in:.horizontal){
                HStack{Button("METRÔNOMO"){destination = .metronome};dspButtons}
                VStack(alignment:.leading){Button("METRÔNOMO"){destination = .metronome};dspButtons}
            }
        }.buttonStyle(.bordered).font(.caption.bold())
    }
    private var dspButtons:some View {
        HStack{Button{destination = .pitch}label:{Label("TOM \(player.song?.semitones == 0 ? "ORIGINAL" : String(player.song?.semitones ?? 0))",systemImage:"music.note")};Button{destination = .speed}label:{Label("VELOCIDADE \(player.song?.speed ?? 100)%",systemImage:"speedometer")}}
    }
}
struct ContentUnavailable:View {var body:some View{VStack(spacing:12){Image(systemName:"waveform").font(.system(size:44));Text("Suas músicas, no seu controle").font(.title2);Text("Importe as tracks de uma música para criar seu primeiro projeto.").foregroundStyle(.secondary)}.frame(maxWidth:.infinity).padding(.vertical,60)}}
struct TransportBar:View {
    @ObservedObject var player:Player
    @ObservedObject var clock:Transport
    var body:some View {VStack(spacing:6){
        HStack(spacing:10){Button{player.seek(0)}label:{Image(systemName:"backward.end.fill").frame(width:44,height:44)};Button{player.playing ? player.pause() : player.play()}label:{Label(player.playing ? "PAUSAR" : "PLAY",systemImage:player.playing ? "pause.fill":"play.fill").frame(maxWidth:.infinity,minHeight:48)}.buttonStyle(.borderedProminent);Button{player.stop()}label:{Image(systemName:"stop.fill").frame(width:44,height:44)};Button{player.next()}label:{Image(systemName:"forward.end.fill").frame(width:44,height:44)}}
        HStack{Text("\(Music.time(clock.seconds)) / \(Music.time(player.song?.seconds ?? 0))").monospacedDigit();Spacer();Text("\(player.song?.effectiveBPM ?? 70,specifier:"%.1f") BPM");Text("· \(player.song?.beats ?? 4)/\(player.song?.denominator ?? 4)")}.font(.subheadline).foregroundStyle(Palette.light)
    }.padding(.horizontal,16)}
}
struct Timeline:View {
    @ObservedObject var player:Player
    @ObservedObject var clock:Transport
    var body:some View {
        VStack(alignment:.leading){Text("TIMELINE").font(.caption).tracking(2)
            GeometryReader{geo in
                let total=max(1,player.song?.seconds ?? 1)
                ZStack(alignment:.leading){
                    Canvas{ctx,size in
                        let peaks=player.song?.stems.first?.peaks ?? []
                        var path=Path()
                        for (i,peak) in peaks.enumerated(){let x=Double(i)/Double(max(1,peaks.count))*size.width;let h=Double(peak)*size.height*0.45;path.move(to:CGPoint(x:x,y:size.height/2-h));path.addLine(to:CGPoint(x:x,y:size.height/2+h))}
                        ctx.stroke(path,with:.color(Palette.light),lineWidth:1)
                    }
                    Rectangle().fill(Palette.blue).frame(width:2).offset(x:geo.size.width*min(1,clock.seconds/total))
                }.contentShape(Rectangle()).gesture(DragGesture(minimumDistance:0).onEnded{player.seek(Double(max(0,min(geo.size.width,$0.location.x))/geo.size.width)*total)})
            }.frame(height:65)
        }.padding(14).background(Palette.panel,in:RoundedRectangle(cornerRadius:14))
    }
}
struct ChannelStrip:View {
    let stem:Stem
    @ObservedObject var player:Player
    func edit(_ change:@escaping(inout Stem)->Void){player.edit{song in if let i=song.stems.firstIndex(where:{$0.id==stem.id}){change(&song.stems[i])}}}
    var body:some View {
        VStack(spacing:12){
            Text(stem.name).font(.subheadline.bold()).multilineTextAlignment(.center).frame(maxWidth:.infinity,minHeight:52).background(Palette.background,in:RoundedRectangle(cornerRadius:8))
            Text(stem.pan==0 ? "PAN · C" : String(format:"PAN · %.0f",stem.pan*100)).font(.caption).foregroundStyle(.secondary)
            Slider(value:Binding(get:{Double(stem.pan)},set:{v in edit{$0.pan=Float(v)}}),in:-1...1)
            HStack{Button("S"){edit{$0.solo.toggle()}}.tint(stem.solo ? Palette.blue : .gray);Button("M"){edit{$0.mute.toggle()}}.tint(stem.mute ? Palette.blue : .gray)}.buttonStyle(.borderedProminent)
            Fader(gain:Binding(get:{stem.volume},set:{v in edit{$0.volume=v}}))
            Menu {Button("MASTER"){edit{$0.bus=""}};ForEach(player.song?.buses ?? []){bus in Button(bus.name){edit{$0.bus=bus.id}}};Divider();Button("Estéreo"){edit{$0.route = -1}};Button("Esquerda"){edit{$0.route = -2}};Button("Direita"){edit{$0.route = -3}}}label:{Text(stem.bus.isEmpty ? "MASTER / ROUTING" : "BUS / ROUTING").font(.caption2)}
        }.padding(10).frame(width:146).background(Palette.panel,in:RoundedRectangle(cornerRadius:14))
    }
}
struct MasterStrip:View {
    @ObservedObject var player:Player
    var body:some View{VStack(spacing:12){Text("MASTER").font(.headline).frame(height:52);Text("OUT 1/\(player.channels)").font(.caption).foregroundStyle(Palette.light).frame(height:52);Button("M"){player.edit{$0.masterMute.toggle()}}.buttonStyle(.borderedProminent).tint(player.song?.masterMute==true ? Palette.blue : .gray);Fader(gain:Binding(get:{player.song?.master ?? 1},set:{v in player.edit{$0.master=v}}));Text("MASTER").font(.caption)}.padding(10).frame(width:146).background(Palette.blue.opacity(0.18),in:RoundedRectangle(cornerRadius:14))}
}
struct Fader:View {
    @Binding var gain:Float
    @State private var editing=false
    @State private var entered="0"
    var body:some View{VStack(spacing:10){
        Button(gain==0 ? "−∞ dB" : String(format:"%+.1f dB",Music.db(gain))){entered=String(format:"%.1f",Music.db(gain));editing=true}.font(.subheadline.monospacedDigit())
        GeometryReader{geo in
            let height=geo.size.height,position=(Music.db(gain)+80)/90
            ZStack{
                Capsule().fill(Palette.background).frame(width:8)
                ForEach([-60,-40,-30,-20,-10,-5,0,5,10],id:\.self){db in
                    HStack{Text("\(db)").font(.system(size:10)).foregroundStyle(db==0 ? Palette.light : .secondary);Spacer();Rectangle().fill(db==0 ? Palette.blue : .gray).frame(width:26,height:db==0 ? 2:1);Spacer().frame(width:21)}.position(x:geo.size.width/2,y:height*CGFloat(1.0-Double(db+80)/90.0))
                }
                RoundedRectangle(cornerRadius:4).fill(Palette.light).frame(width:38,height:22).overlay(Rectangle().fill(Palette.blue).frame(width:30,height:3)).position(x:geo.size.width/2,y:height*CGFloat(1.0-position))
            }.contentShape(Rectangle()).gesture(DragGesture(minimumDistance:0).onChanged{value in gain=Music.gain(Double(1.0-max(0,min(height,value.location.y))/height)*90.0-80.0)}).onTapGesture(count:2){gain=1}
        }.frame(height:235).padding(.vertical,12)
        Button("0dB"){gain=1}.buttonStyle(.bordered).frame(minHeight:44)
    }.alert("Nível em dB",isPresented:$editing){TextField("−80 até +10",text:$entered).keyboardType(.numbersAndPunctuation);Button("Cancelar",role:.cancel){};Button("Aplicar"){if let db=Double(entered.replacingOccurrences(of:",",with:".")){gain=Music.gain(min(10,max(-80,db)))}}}}
}
struct TrackDrop:DropDelegate {
    var target:String
    @Binding var dragging:String?
    var player:Player
    func dropEntered(info:DropInfo){guard let id=dragging,id != target else{return};player.edit{song in guard let from=song.stems.firstIndex(where:{$0.id==id}),let to=song.stems.firstIndex(where:{$0.id==target}) else{return};song.stems.move(fromOffsets:IndexSet(integer:from),toOffset:to>from ? to+1:to)}}
    func performDrop(info:DropInfo)->Bool{dragging=nil;return true}
    func dropUpdated(info:DropInfo)->DropProposal?{DropProposal(operation:.move)}
}
