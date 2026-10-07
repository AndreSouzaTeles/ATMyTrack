# ATMyTrack para iPhone/iPad — 0.1.0

Portabilidade nativa em SwiftUI + AVAudioEngine + C++17, adicionada ao repositório do aplicativo Android. Android continua em `app/`; seu banco/projetos e código de playback não são substituídos.

## Arquitetura

- Interface SwiftUI adaptativa, paleta azul escura, arte aprovada do Android, projetos/mixer, menu, pagamento e ajuda.
- AVAudioFile/AVAudioConverter importam áudio do seletor Arquivos com acesso ao recurso autorizado pelo sistema. Preparação local PCM Float32 estéreo a 48 kHz. WAV/AAC/MP3/FLAC e outros formatos dependem dos decodificadores iOS. Formatos Android não são automaticamente garantidos no iOS.
- Um AVAudioSourceNode alimentado por ring SPSC C++, 16 blocos de 512 frames. O produtor faz I/O e mixagem; o callback somente consome memória e converte interleaved para planar. Nenhum decoder ou mutex no callback de saída. Transporte em frames comum a todas as tracks; loop ocorre no produtor em limites exatos de amostras.
- Ganho/pan/mute/solo/DCA/BUS/saídas reais, suavização de ganho por bloco e limiter vinculado. Interrupções/alterações de rota pausam o player para revisão. Saídas USB dependem das capacidades anunciadas pela sessão iOS; precisam de validação física.
- Signalsmith Stretch, já fixado nos submódulos Android, prepara velocidade/pitch juntos e compensa latência. Bypass em 100%/0 semitons. Seleções independentes e aviso de dessincronização parcial. Cache LRU de 2 GiB protegendo o projeto ativo. A troca prepara em background e reconstrói a saída, portanto pode interromper brevemente a reprodução.
- Metrônomo sintetizado no mesmo clock, TAP e análise de transientes para Smart Click. BPM original persistido, BPM efetivo derivado da velocidade global.
- Afinador local YIN, janela de aproximadamente 180 ms, RMS/confiança, mediana e suavização. 12 instrumentos + cromático, corda AUTO/manual, A4 400–480 Hz. Captura com AVAudioEngine em sessão measurement, pausando o player. Liberação ao sair/background/interrupção.
- Biblioteca JSON com escrita atômica, arquivos no sandbox do iOS. Dados Android não são migrados automaticamente: URIs `content://` e caminhos Android não são utilizáveis no iPhone. Importe as tracks no iPhone para criar a biblioteca local.

## Build

O workflow `.github/workflows/ios.yml` compila ARM64 no macOS, executa testes no simulador e publica o IPA **sem assinatura** como artifact. Ele não envia áudio nem biblioteca do usuário. As fixtures dos testes são senoidais sintéticas, geradas exclusivamente no build Debug quando o teste passa `--ui-fixture`; não existem projetos fictícios no Release.

```sh
git submodule update --init --recursive
brew install xcodegen
cd ios && xcodegen generate
xcodebuild -project ATMyTrack.xcodeproj -scheme ATMyTrack -sdk iphoneos -configuration Release CODE_SIGNING_ALLOWED=NO build
```

Para regenerar apenas os assets a partir da arte existente, use `python ios/tools/prepare-assets.py` com Pillow 11.3.0. O script copia arte/QR, redimensiona o ícone exigido pelo iOS e mantém as licenças. Nenhuma nova marca é gerada.

## Instalação e limites

Veja [INSTALL-IPHONE.md](INSTALL-IPHONE.md). O IPA precisa de assinatura pessoal antes de instalar. Um pacote sem assinatura não é uma entrega já instalada/homologada em dispositivo físico.

O objetivo é manter os mesmos recursos e linguagem visual; esta primeira portabilidade **não deve ser considerada equivalência integral certificada ao Android** sem validação no iPhone. O relatório de entrega separa compilação, testes sintéticos, testes de interface e casos ainda não validados.
