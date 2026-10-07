import SwiftUI
import PhotosUI

struct Panel<Content:View>:View {
    let title:String
    @ViewBuilder var content:Content
    @Environment(\.dismiss) private var dismiss
    var body:some View{NavigationStack{ScrollView{VStack(alignment:.leading,spacing:18){content}.padding(20).frame(maxWidth:720)}.frame(maxWidth:.infinity).background(Palette.background).navigationTitle(title).navigationBarTitleDisplayMode(.inline).toolbar{ToolbarItem(placement:.confirmationAction){Button("Fechar"){dismiss()}}}}.tint(Palette.blue).preferredColorScheme(.dark)}
}
struct MainMenu:View {
    let choose:(Destination?)->Void
    var body:some View{Panel(title:"ATMyTrack"){
        Image("Brand").resizable().scaledToFit().frame(height:100).frame(maxWidth:.infinity)
        item("PROJETOS","square.grid.2x2",nil)
        item("AFINADOR","tuningfork",.tuner)
        item("PLANO / PAGAMENTO","creditcard",.payment)
        item("CENTRAL DE DÚVIDAS","questionmark.circle",.help)
    }}
    func item(_ name:String,_ icon:String,_ target:Destination?)->some View{Button{choose(target)}label:{Label(name,systemImage:icon).font(.headline).frame(maxWidth:.infinity,alignment:.leading).padding(18).background(Palette.panel,in:RoundedRectangle(cornerRadius:12))}}
}
struct SearchProjects:View {
    @ObservedObject var library:Library
    let choose:(Song)->Void
    @State private var query=""
    @State private var key="Todos"
    var body:some View{Panel(title:"Pesquisar projetos"){
        TextField("Nome do projeto ou tom",text:$query).textFieldStyle(.roundedBorder)
        Picker("Tom",selection:$key){Text("Todos").tag("Todos");ForEach(Array(Set(library.projects.map(\.key))).sorted(),id:\.self){Text($0).tag($0)}}
        ForEach(library.projects.filter{(query.isEmpty || $0.name.localizedStandardContains(query) || $0.key.localizedStandardContains(query)) && (key=="Todos" || key==$0.key)}){song in
            Button{choose(song)}label:{VStack(alignment:.leading){Text(song.name).font(.headline);Text("\(song.key) · \(song.effectiveBPM,specifier:"%0.1f") BPM").font(.caption)}.frame(maxWidth:.infinity,alignment:.leading).padding().background(Palette.panel,in:RoundedRectangle(cornerRadius:12))}
        }
    }}
}
struct ProjectEditor:View {
    @ObservedObject var player:Player
    let onImport:()->Void
    @State private var image:PhotosPickerItem?
    var body:some View{Panel(title:"Editar projeto"){
        TextField("Nome",text:Binding(get:{player.song?.name ?? ""},set:{v in player.edit{$0.name=v}})).textFieldStyle(.roundedBorder)
        TextField("Tom original",text:Binding(get:{player.song?.key ?? "—"},set:{v in player.edit{$0.key=v}})).textFieldStyle(.roundedBorder)
        PhotosPicker(selection:$image,matching:.images){Label("Adicionar / trocar imagem",systemImage:"photo")}
        if player.song?.artwork.isEmpty==false {Button("Remover imagem"){player.edit{$0.artwork=""}}}
        Button("Adicionar tracks",action:onImport).disabled(player.preparing)
        Text("Ordem das tracks").font(.headline)
        ForEach(player.song?.stems ?? []){stem in HStack{Text(stem.name);Spacer();Button{move(stem,-1)}label:{Image(systemName:"arrow.up")};Button{move(stem,1)}label:{Image(systemName:"arrow.down")}}.padding(.vertical,8)}
    }.onChange(of:image){item in Task{
        guard let data=try? await item?.loadTransferable(type:Data.self),let image=UIImage(data:data),let jpeg=image.jpegData(compressionQuality:0.8),let id=player.song?.id else{return}
        do{let name="\(id)-art.jpg";try jpeg.write(to:Files.url(name),options:.atomic);player.edit{$0.artwork=name}}catch{player.error="Não foi possível salvar a imagem."}
    }}}
    func move(_ stem:Stem,_ delta:Int){player.edit{song in if let i=song.stems.firstIndex(where:{$0.id==stem.id}),song.stems.indices.contains(i+delta){song.stems.swapAt(i,i+delta)}}}
}
struct DSPPanel:View {
    @ObservedObject var player:Player
    let speedMode:Bool
    @State private var value=100.0
    @State private var selection=Set<String>()
    @State private var warning=false
    @State private var preset:Int?
    var body:some View{Panel(title:speedMode ? "VELOCIDADE":"TOM"){
        Text(speedMode ? "\(Int(value))%" : "\(Int(value)>0 ? "+":"")\(Int(value)) semitons").font(.system(size:42,weight:.bold,design:.rounded)).foregroundStyle(Palette.light).frame(maxWidth:.infinity)
        HStack{Button("−"){value=max(speedMode ? 50:-12,value-1)};Slider(value:$value,in:speedMode ? 50..0.200 : -12..0.12,step:1);Button("+"){value=min(speedMode ? 200:12,value+1)}}.font(.title2)
        if speedMode {
            Text("Todas as tracks: modo recomendado para preservar sincronização.").font(.caption).foregroundStyle(.secondary)
            LazyVGrid(columns:[GridItem(.adaptive(minimum:85))]){ForEach([50,75,80,90,100,110,125,150,200],id:\.self){n in Button(n==100 ? "ORIGINAL":"\(n)%"){value=Double(n);preset=n;apply()}.buttonStyle(.bordered)}}
        }
        HStack{Button("SELECIONAR TODAS"){selection=Set(player.song?.stems.map(\.id) ?? [])};Button("LIMPAR SELEÇÃO"){selection=[]}}.font(.caption.bold())
        ForEach(player.song?.stems ?? []){stem in Toggle(stem.name,isOn:Binding(get:{selection.contains(stem.id)},set:{if $0 {selection.insert(stem.id)}else{selection.remove(stem.id)}}))}
        Button("APLICAR"){preset=nil;apply()}.buttonStyle(.borderedProminent).disabled(player.preparing)
        Button(speedMode ? "RESET 100%":"TOM ORIGINAL"){value=speedMode ? 100:0;preset=nil;apply()}.buttonStyle(.bordered).disabled(player.preparing)
        if player.preparing {ProgressView();Text(player.progress).font(.caption)}
        Text("O preparo mantém o áudio atual até a troca. Pode haver breve rebufferização. Configurações de mixer são preservadas.").font(.caption).foregroundStyle(.secondary)
    }.onAppear{value=Double(speedMode ? player.song?.speed ?? 100 : player.song?.semitones ?? 0);selection=speedMode ? player.song?.speedSelection ?? [] : player.song?.pitchTracks ?? []}
        .alert("Velocidades diferentes",isPresented:$warning){Button("Cancelar",role:.cancel){};Button("Continuar"){commit()}}message:{Text("Tracks com velocidades diferentes podem perder sincronização entre si.")}}
    func apply(){if speedMode && value != 100 && !selection.isEmpty && selection.count != player.song?.stems.count {warning=true}else{commit()}}
    func commit(){if speedMode{player.transform(speed:Int(value),speedTracks:selection,preset:preset)}else{player.transform(pitch:Int(value),pitchTracks:selection)}}
}
struct MetronomePanel:View {
    @ObservedObject var player:Player
    @State private var taps=[Date]()
    var body:some View{Panel(title:"METRÔNOMO"){
        HStack{Button("TAP"){let now=Date();if let last=taps.last,now.timeIntervalSince(last)>2.2 {taps=[]};taps.append(now);taps=Array(taps.suffix(7));if taps.count>1{let bpm=Double(taps.count-1)*60/now.timeIntervalSince(taps[0]);player.edit{$0.bpm=min(300,max(30,bpm))}}}.buttonStyle(.borderedProminent);Toggle("Smart Click",isOn:Binding(get:{player.song?.smartClick ?? false},set:{v in player.edit{$0.smartClick=v};if v {player.smartTempo()}}))}
        Text("Original: \(player.song?.bpm ?? 70,specifier:"%0.1f") BPM • Atual: \(player.song?.effectiveBPM ?? 70,specifier:"%0.1f") BPM").foregroundStyle(Palette.light)
        Stepper("BPM \(player.song?.bpm ?? 70,specifier:"%0.1f")",value:Binding(get:{player.song?.bpm ?? 70},set:{v in player.edit{$0.bpm=v}}),in:30..0.300,step:1)
        Stepper("Batidas: \(player.song?.beats ?? 4)",value:Binding(get:{player.song?.beats ?? 4},set:{v in player.edit{$0.beats=v}}),in:1..0.16)
        Picker("Divisão",selection:Binding(get:{player.song?.multiplier ?? 1},set:{v in player.edit{$0.multiplier=v}})){Text("0.5x").tag(0.5);Text("1x").tag(1.0);Text("2x").tag(2.0)}.pickerStyle(.segmented)
        Text("A divisão altera somente o click.").font(.caption).foregroundStyle(.secondary)
        Toggle("Acentuar primeira batida",isOn:Binding(get:{player.song?.accent ?? true},set:{v in player.edit{$0.accent=v}}))
        Slider(value:Binding(get:{Double(player.song?.clickVolume ?? 0.35)},set:{v in player.edit{$0.clickVolume=Float(v)}}),in:0..0.1){Text("Volume")}
        Picker("Timbre",selection:Binding(get:{player.song?.clickSound ?? 0},set:{v in player.edit{$0.clickSound=v}})){ForEach(Array(["Clássico","Agudo","Madeira","Digital","Suave","Estúdio","Palco"].enumerated()),id:\.offset){i,name in Text(name).tag(i)}}
        RoutePicker(route:Binding(get:{player.song?.clickRoute ?? -1},set:{v in player.edit{$0.clickRoute=v}}),channels:player.channels)
        Button(player.song?.click==true ? "PAUSAR CLICK":"PLAY"){let enabled=player.song?.click != true;player.edit{$0.click=enabled};if enabled && !player.playing {player.play()}}.buttonStyle(.borderedProminent)
    }}
}
struct MarkerEditor:View {
    @ObservedObject var player:Player
    @State private var name=""
    @State private var start=0.0
    @State private var end=0.0
    var body:some View{Panel(title:"SEÇÕES"){
        Timeline(player:player,clock:player.transport)
        Text("Posições na música original; a velocidade é convertida automaticamente.").font(.caption).foregroundStyle(.secondary)
        TextField("Nome da seção",text:$name).textFieldStyle(.roundedBorder)
        HStack{Text("Início (s)");TextField("Início",value:$start,format:.number).keyboardType(.decimalPad);Button("Marcar"){start=player.transport.seconds*(player.song?.scale ?? 1)}}
        HStack{Text("Fim (s)");TextField("Fim",value:$end,format:.number).keyboardType(.decimalPad);Button("Marcar"){end=player.transport.seconds*(player.song?.scale ?? 1)}}
        Button("CRIAR SEÇÃO"){player.edit{$0.markers.append(Marker(name:name,start:start,end:end))};name=""}.buttonStyle(.borderedProminent).disabled(name.isEmpty || start<0 || end<=start || end>(player.song?.originalSeconds ?? 0))
        ForEach(player.song?.markers ?? []){marker in HStack{VStack(alignment:.leading){Text(marker.name);Text("\(Music.time(marker.start)) — \(Music.time(marker.end))").font(.caption)};Spacer();Button("Selecionar"){player.edit{$0.selectedMarker=marker.id}};Button(role:.destructive){player.edit{$0.markers.removeAll{$0.id==marker.id};if $0.selectedMarker==marker.id{$0.selectedMarker=""}}}label:{Image(systemName:"trash")}}}
    }.onAppear{start=player.transport.seconds*(player.song?.scale ?? 1);end=player.song?.originalSeconds ?? 0}}
}
struct RoutePicker:View {
    @Binding var route:Int
    let channels:Int
    var body:some View{Picker("Saída",selection:$route){Text("MASTER · estéreo").tag(-1);Text("MASTER · esquerda").tag(-2);Text("MASTER · direita").tag(-3);ForEach(0..<channels,id:\.self){Text("OUT \($0+1)/\(channels) · mono").tag(100+$0)};ForEach(Array(stride(from:0,to:channels-1,by:2)),id:\.self){Text("OUT \($0+1)–\($0+2) · estéreo").tag($0)}}}
}
struct GroupsPanel:View {
    @ObservedObject var player:Player
    var body:some View{Panel(title:"DCA • BUS • ROUTING"){
        Button("STEREO SPLIT: Click/Guide à esquerda"){player.edit{song in for i in song.stems.indices {let name=song.stems[i].name.lowercased();song.stems[i].route=(name.contains("click")||name.contains("guide")) ? -2:-3}}}
        Text("Saídas anunciadas pelo iOS: \(player.channels). Reconecte e inicie Play para atualizar.").font(.caption)
        Button("+ DCA"){player.edit{$0.dcas.append(DCA(name:"DCA \($0.dcas.count+1)"))}}
        ForEach(player.song?.dcas ?? []){dca in VStack(alignment:.leading){
            TextField("Nome",text:Binding(get:{dca.name},set:{v in updateDCA(dca.id){$0.name=v}})).textFieldStyle(.roundedBorder)
            Slider(value:Binding(get:{Music.db(dca.volume)},set:{v in updateDCA(dca.id){$0.volume=Music.gain(v)}}),in:-80..0.10)
            Toggle("Mute",isOn:Binding(get:{dca.mute},set:{v in updateDCA(dca.id){$0.mute=v}}))
            ForEach(player.song?.stems ?? []){stem in Toggle(stem.name,isOn:Binding(get:{dca.members.contains(stem.id)},set:{v in updateDCA(dca.id){if v{$0.members.insert(stem.id)}else{$0.members.remove(stem.id)}}}))}
            Button("Excluir DCA",role:.destructive){player.edit{$0.dcas.removeAll{$0.id==dca.id}}}
        }.padding().background(Palette.panel,in:RoundedRectangle(cornerRadius:12))}
        Button("+ BUS"){player.edit{$0.buses.append(Bus(name:"BUS \($0.buses.count+1)"))}}
        ForEach(player.song?.buses ?? []){bus in VStack(alignment:.leading){
            TextField("Nome",text:Binding(get:{bus.name},set:{v in updateBus(bus.id){$0.name=v}})).textFieldStyle(.roundedBorder)
            Slider(value:Binding(get:{Music.db(bus.volume)},set:{v in updateBus(bus.id){$0.volume=Music.gain(v)}}),in:-80..0.10)
            Toggle("Mute",isOn:Binding(get:{bus.mute},set:{v in updateBus(bus.id){$0.mute=v}}))
            RoutePicker(route:Binding(get:{bus.route},set:{v in updateBus(bus.id){$0.route=v}}),channels:player.channels)
            Button("Excluir BUS",role:.destructive){player.edit{song in song.buses.removeAll{$0.id==bus.id};for i in song.stems.indices where song.stems[i].bus==bus.id{song.stems[i].bus=""}}}
        }.padding().background(Palette.panel,in:RoundedRectangle(cornerRadius:12))}
    }}
    func updateDCA(_ id:String,_ change:(inout DCA)->Void){player.edit{song in if let i=song.dcas.firstIndex(where:{$0.id==id}){change(&song.dcas[i])}}}
    func updateBus(_ id:String,_ change:(inout Bus)->Void){player.edit{song in if let i=song.buses.firstIndex(where:{$0.id==id}){change(&song.buses[i])}}}
}
