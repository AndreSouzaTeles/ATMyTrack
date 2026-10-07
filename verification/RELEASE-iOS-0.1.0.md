# ATMyTrack para iPhone/iPad — prévia 0.1.0

Primeira portabilidade nativa SwiftUI + AVAudioEngine + C++, com projetos, importação, mixer/DCA/BUS/routing, metrônomo, seções/loops, TOM e VELOCIDADE, afinador local, menu, pagamento e ajuda. Identidade azul e arte existente preservadas. Android permanece 0.7.0.

**O IPA está SEM ASSINATURA. Não instala diretamente ao tocar no arquivo.** Para instalar no seu iPhone, assine com sua conta Apple usando o fluxo pessoal descrito no [guia de instalação pelo Windows](https://github.com/AndreSouzaTeles/ATMyTrack/blob/main/ios/INSTALL-IPHONE.md). É possível usar AltStore Classic/AltServer; conta gratuita exige renovação periódica. Não envie senhas no GitHub ou chat.

Requer iOS/iPadOS 16 ou posterior. Nenhum certificado pessoal, perfil Apple ou publicação TestFlight/App Store está incluído.

Build ARM64 e **11 testes passaram** no macOS/Xcode/simulador iPhone 16 Pro: DSP, YIN, importação/reprodução nativa, 19 tracks em janelas de 3 segundos a 100/80/120%, interface e permissão/lifecycle do microfone. Zero underruns nessas janelas curtas. Isso não certifica uso de palco, instrumentos acústicos ou interfaces USB físicas.

Veja [relatório completo, capturas e limitações](https://github.com/AndreSouzaTeles/ATMyTrack/blob/main/verification/REPORT-iOS-0.1.0.md). A biblioteca Android não é transferida automaticamente, e a equivalência integral ao Android ainda não foi homologada em dispositivo físico.

SHA-256 do IPA: `5356322da5477be430a6b296632e4e67c0c23d29bc75e6049adab7e76f812752`.
