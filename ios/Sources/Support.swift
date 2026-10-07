import SwiftUI

struct Supporter:Codable,Identifiable{var name:String;var demo:Bool;var id:String{name}}
struct SupportScreen:View {
    @AppStorage("support.fullName") private var fullName=""
    @State private var query=""
    @State private var yaw=0.0
    @State private var pitch=0.0
    @State private var rotating=true
    private let names:[Supporter]=(Bundle.main.url(forResource:"supporters",withExtension:"json").flatMap{try? Data(contentsOf:$0)}.flatMap{try? JSONDecoder().decode([Supporter].self,from:$0)}) ?? []
    private let pix="00020126660014BR.GOV.BCB.PIX0111411120718900229Faça parte do mundo ATMyTrack5204000053039865802BR5917Andre Souza Teles6009SAO PAULO62140510qERAMLYJ726304C2A9"
    var body:some View{Panel(title:"PLANO / PAGAMENTO"){
        Text("SeuNomeNoApp").font(.title.bold());Text("Faça parte do mundo ATMyTrack").foregroundStyle(Palette.light)
        TextField("Encontrar nome no globo",text:$query).textFieldStyle(.roundedBorder).onChange(of:query){value in if !value.isEmpty,let i=names.firstIndex(where:{$0.name.localizedStandardContains(value)}){focus(i)}}
        GlobeView(names:names,yaw:$yaw,pitch:$pitch,rotating:$rotating,focus:focus)
        Button(rotating ? "PAUSAR GLOBO":"GIRAR AUTOMATICAMENTE"){rotating.toggle()}
        if names.contains(where:{$0.demo}){Text("Demonstração: os nomes fictícios não representam pagamentos recebidos.").font(.caption).foregroundStyle(.secondary)}
        Text("Contribuições acima de R$ 10 permitem incluir seu nome no globo ATMyTrack após conferência e inclusão manual.")
        TextField("Nome completo",text:$fullName).textFieldStyle(.roundedBorder)
        Text("Seu nome fica salvo apenas neste aparelho. Envie a identificação e o comprovante para conferência; não há validação automática do Pix.").font(.caption).foregroundStyle(.secondary)
        ShareLink(item:"Meu nome é \(fullName). Quero participar do globo ATMyTrack. Encaminho meu comprovante para conferência manual."){Label("Compartilhar identificação",systemImage:"square.and.arrow.up")}.disabled(fullName.isEmpty)
        Link("ABRIR PAGAMENTO NUBANK",destination:URL(string:"https://nubank.com.br/cobrar/53y8j/6ac5cae5-bc5f-4067-ada8-1154e541b67a")!).buttonStyle(.borderedProminent)
        Button("COPIAR PIX COPIA E COLA"){UIPasteboard.general.string=pix}.buttonStyle(.bordered)
        Image("SupportPix").resizable().scaledToFit().frame(maxHeight:300).frame(maxWidth:.infinity)
        Text("Beneficiário: Andre Souza Teles. Confira os dados no banco antes de confirmar.").font(.caption).foregroundStyle(.secondary)
    }}
    private func focus(_ index:Int){rotating=false;yaw = -Double(index)*2.399963;pitch=asin(1-2*(Double(index)+0.5)/Double(max(1,names.count)))}
}
struct HelpScreen:View {
    var body:some View{Panel(title:"CENTRAL DE DÚVIDAS"){
        Text("ATMyTrack para iPhone e iPad").font(.title2.bold())
        section("Importar e projetos","Selecione várias tracks de uma música no app Arquivos, incluindo provedores de nuvem. Aguarde o preparo antes de Play. Cópias PCM ficam no aparelho; os originais não são alterados. Formatos disponíveis dependem dos decodificadores iOS. Use Editar para nome, tom, capa e ordem das tracks.")
        section("Playback e mixer","Play, Pausar, Stop e Seek usam um transporte comum. Arraste o card para reordenar. Ajuste faders, pan, mute e solo; o botão 0dB restaura o ganho. Toque no valor em dB para entrada numérica. Mute prevalece sobre solo. A página Mixer dá acesso aos DCAs e buses.")
        section("Seções e loop","Crie uma seção marcando início e fim na timeline. Segure uma seção para selecioná-la e habilite LOOP. Toque curto navega até o início. As posições salvas são da música original e são convertidas quando a velocidade muda.")
        section("Tom e velocidade","TOM altera semitons preservando duração. VELOCIDADE altera duração preservando aproximadamente o tom. As seleções são independentes. Todas as tracks selecionadas em velocidade preservam a relação temporal; seleções parciais podem perder sincronização. O preparo é feito em cache, com breve rebufferização na troca. Extremos podem apresentar mais artefatos.")
        section("Metrônomo e Smart Click","TAP calcula BPM pelos seus toques. Smart Click procura um pulso no áudio e pode exigir conferir metade/dobro do resultado. A referência original é preservada. Velocidade global ajusta o BPM efetivo. 0.5x/1x/2x altera apenas a divisão do click.")
        section("DCA, BUS e saídas","DCA controla o ganho dos membros sem sobrescrever faders. BUS soma as tracks enviadas ao mesmo destino. Saídas físicas dependem da interface realmente apresentada pelo iOS. Após mudar o dispositivo, a reprodução pausa; confira as rotas e pressione Play. O modo Stereo Split separa Click/Guide à esquerda.")
        section("Afinador","Abra o Afinador e permita o microfone. Escolha instrumento/afinação, AUTO ou corda manual. A4 começa em 440 Hz. Azul indica ±3 cents. O player é pausado e a captura termina ao sair ou colocar o app em segundo plano. O áudio é analisado localmente, sem gravação ou envio.")
        section("Armazenamento e cache","As tracks são preparadas em PCM estéreo 48 kHz, aproximadamente 23 MB/minuto por track. O cache de DSP usa orçamento de 2 GiB com proteção do projeto atual. Não remova arquivos pelo app Arquivos durante playback. Faça backup da pasta ATMyTrack antes de excluir o aplicativo.")
        Text("iOS 0.1.0 • Primeira portabilidade. Testes no simulador não certificam comportamento de interfaces USB ou desempenho no aparelho físico.").font(.caption).foregroundStyle(.secondary)
    }}
    private func section(_ title:String,_ text:String)->some View{VStack(alignment:.leading,spacing:8){Text(title).font(.headline).foregroundStyle(Palette.light);Text(text).foregroundStyle(.secondary)}}
}

private struct GlobeView:View {
    let names:[Supporter]
    @Binding var yaw:Double
    @Binding var pitch:Double
    @Binding var rotating:Bool
    let focus:(Int)->Void
    var body:some View {
        TimelineView(.animation(minimumInterval:1.0/30,paused:!rotating)){time in
            let angle=yaw+(rotating ? time.date.timeIntervalSinceReferenceDate.truncatingRemainder(dividingBy:10000)*0.04:0)
            GeometryReader{geo in
                ZStack{
                    Circle().fill(RadialGradient(colors:[Palette.blue.opacity(0.25),Palette.background],center:.center,startRadius:0,endRadius:140))
                    ForEach(names.indices,id:\.self){i in
                        name(i,angle:angle,width:Double(geo.size.width),height:Double(geo.size.height))
                    }
                }.contentShape(Rectangle()).gesture(DragGesture().onChanged{v in rotating=false;yaw=Double(v.translation.width)*0.01;pitch=max(-1.5,min(1.5,-Double(v.translation.height)*0.01))})
            }.frame(height:280)
        }
    }
    private func name(_ i:Int,angle:Double,width:Double,height:Double)->some View {
        let lat=asin(1.0-2.0*(Double(i)+0.5)/Double(max(1,names.count)))
        let lon=Double(i)*2.399963+angle
        let z=cos(lat)*cos(lon)
        let depth=sin(lat)*sin(pitch)+z*cos(pitch)
        let x=width/2.0+cos(lat)*sin(lon)*width*0.36
        let y=height/2.0-(sin(lat)*cos(pitch)-z*sin(pitch))*110.0
        return Button{focus(i)}label:{Text(names[i].name).font(.system(size:CGFloat(10.0+3.0*(depth+1.0)/2.0))).foregroundStyle(Palette.light.opacity(0.25+0.75*(depth+1.0)/2.0))}.position(x:CGFloat(x),y:CGFloat(y))
    }
}
