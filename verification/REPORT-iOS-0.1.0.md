# ATMyTrack iOS 0.1.0 — portabilidade inicial

## Entrega e instalação

Código iOS nativo em `ios/`, integrado ao repositório existente. Interface SwiftUI, saída AVAudioEngine/AVAudioSourceNode, mixer C++ com um clock comum e DSP Signalsmith compartilhado com Android. Requer iOS/iPadOS 16 ou posterior. O app Android continua em 0.7.0, sem alteração de engine, biblioteca ou assinatura.

O pacote ARM64 é **sem assinatura**. Ele precisa ser assinado com uma conta Apple para o aparelho antes de instalar. Não houve criação de conta Apple, envio à App Store/TestFlight ou instalação no iPhone físico do usuário. Guia: [instalação pelo Windows](../ios/INSTALL-IPHONE.md).

## Implementado

- Importação de múltiplos arquivos via seletor iOS, conversão em background para PCM float estéreo 48 kHz, waveform e biblioteca local persistente.
- Projetos, capa, pesquisa por nome/tom, exclusão com confirmação e reordenação das tracks com prévia nativa de arraste.
- Play/pause/stop/seek, faders com curva de console e 0dB, pan, mute/solo, medidores, master, DCA, BUS e routing. Saídas adicionais dependem do dispositivo anunciado pelo iOS; alterações/interrupções pausam para revisão.
- Markers/seções na timeline original, seleção por toque longo e loop processado em frames no produtor de áudio.
- Metrônomo no clock de áudio, acento, volume, timbres, subdivisão, TAP e Smart Click sobre áudio original.
- TOM e VELOCIDADE 50–200% com seleções e resets independentes. Velocidade global converte duração/BPM/markers/loop; seleção parcial exige aviso. Bypass 100%/0 semitons. Processamento offline/cache, não automação instantânea.
- Afinador YIN real/local, permissão de microfone, 12 instrumentos + cromático, afinações, corda AUTO/manual, A4 400–480 Hz, Hz/nota/oitava/cents, RMS/confiança, smoothing e liberação por lifecycle.
- Menu Projetos → Afinador → Plano/Pagamento → Central de dúvidas. Pix/QR, globo, formulário local e compartilhamento. Os nomes de demonstração continuam identificados como fictícios.
- Arte original e QR copiados sem alteração; ícone iOS redimensionado para o formato exigido pela Apple. Controles novos em azul.

## Estratégia de áudio

Produtor C++ lê e mistura blocos de 512 frames, com fila SPSC de 16 blocos. O callback de saída não abre arquivos, não decodifica e não adquire mutex. Atualizações de mixer são aplicadas no produtor, com rampa de ganho. Reservas e latência real dependem também do AVAudioEngine/dispositivo; não houve medição por loopback físico.

Pitch e tempo são renderizados juntos usando Signalsmith Stretch com janela de 120 ms e intervalo de 10 ms, compensação de latência e tamanho final exato. A música atual pode continuar durante o preparo; a troca reabre a saída preservando a posição musical e pode produzir breve rebufferização. Cache LRU de 2 GiB, protegendo o projeto ativo. Os arquivos originais não são apagados.

O afinador usa janela aproximada de 180 ms, RMS mínimo 0,002, confiança mínima 0,90 e indicação de afinado dentro de ±3 cents. Seu processamento é separado da Main Thread. Não há gravação permanente, monitoramento ou upload.

## Validação

Execução final: [GitHub Actions 37697676437](https://github.com/AndreSouzaTeles/ATMyTrack/actions/runs/37697676437), código `c5a4729`. **Build ARM64 e 11 testes aprovados: 9 testes de núcleo/áudio + 2 testes de interface.** Ambiente: runner `macos-15-arm64`, Xcode 16.4, simulador iPhone 16 Pro / iOS 18.5. [Resumo dos testes](ios-tests-0.1.0.txt).

Fixtures são sinais sintéticos, não gravações de instrumentos reais. Build Debug utiliza o mesmo nível de otimização C++ do Release para evitar medir DSP sem otimização.

- Importação efetiva de dois WAVs estéreo a 48 kHz, 30 segundos, senóides de 220/110 Hz. Reprodução nativa AVAudioEngine, Pause/Stop e Seek testados.
- 19 leitores/stems, derivados dessas duas fontes, com ganho 0,1 por stem. Reprodução nativa por aproximadamente **3 segundos em cada velocidade**, em 100%, 80% e 120%, sem underruns registrados nessas janelas. A etapa completa, incluindo preparação DSP, levou 54,694 segundos. Não é um teste de longa duração nem 19 gravações musicais distintas.
- Time-stretch em 50/80/100/120/200% com pitch 0 e +2 semitons: tamanho de saída exato e frequência dentro da tolerância de 0,8 Hz do alvo, nas senóides usadas. Seleções independentes, persistência por round-trip, BPM efetivo e resets matemáticos testados.
- Núcleo: soma de 19 tracks, mute/roteamento à esquerda, seek e loop em limites de frames. Escala de fader/unity testada.
- YIN: B0, E1, E2, A2, D3, G3, B3, E4, A4 e desafinações de −18/+14 cents; tolerância inferior a 1 cent nas senóides. Testes de harmônicos fortes, silêncio, sinal fraco e ruído aprovados.
- Interface: menu durante playback, retorno a Projetos, aplicação de 80%, telas de afinador/pagamento/ajuda, retrato e paisagem.
- Microfone: negação, orientação de Ajustes, permissão concedida, engine de captura ativa no simulador, background/retorno e fechar/reabrir. Não houve instrumento físico emitindo som no microfone.
- Revisão visual das capturas: [Playback retrato](ios-playback-portrait-0.1.0.png), [Playback paisagem](ios-playback-landscape-0.1.0.png), [Afinador retrato](ios-tuner-portrait-0.1.0.png), [Afinador paisagem](ios-tuner-landscape-0.1.0.png).
- Android: `:app:testDebugUnitTest :app:exportDebugApk` validado pelo Gradle incremental; testes Kotlin existentes ficaram `UP-TO-DATE`, sem alteração de código Android. APK 0.7.0 preservado em `dist/ATMyTrack-debug.apk`.

CPU/RAM e latência acústica não foram perfiladas quantitativamente nesta entrega iOS. Não se transfere para iOS o benchmark Android anterior.

## Pacote

- Arquivo: `D:/Projetos/ATMyTrack/dist/ATMyTrack-iOS-0.1.0-UNSIGNED.ipa`.
- Bundle: `com.atmytrack.app`, versão `0.1.0`, build `1`, mínimo iOS `16.0`.
- Mach-O ARM64 conferido; sem perfil de provisionamento.
- Tamanho: 3.881.771 bytes.
- SHA-256: `5356322da5477be430a6b296632e4e67c0c23d29bc75e6049adab7e76f812752`.
- [Metadados verificados](ios-package-0.1.0.json).

## Limitações reais

- Não é possível instalar o IPA diretamente tocando no arquivo: assinatura/provisionamento pessoal ainda necessários. Fluxo AltStore não foi executado no iPhone do usuário.
- Não foi certificada equivalência integral de comportamento/visual com Android. É a primeira portabilidade nativa, não uma conversão binária do APK.
- Projetos Android não são migrados automaticamente para iOS; caminhos/URIs Android não existem no sandbox Apple. A biblioteca iOS é independente.
- Formatos aceitos dependem dos decodificadores iOS. Não há garantia de todos os codecs aceitos pelo Android, nem importação recursiva de pastas nesta versão.
- iPhone/iPad físico, USB multicanal/hot plug, Bluetooth, interrupções telefônicas reais, desempenho de palco prolongado e instrumentos acústicos precisam de validação física.
- Testes digitais do detector não equivalem a medir precisão/latência acústica. Não houve calibração do microfone do usuário.
- Ajustes extremos de time-stretch podem gerar artefatos. Não houve avaliação auditiva comparativa completa de voz, piano, guitarra e bateria reais.
- Os ensaios curtos não certificam ausência de todos os pops/dropouts nem desempenho em um show inteiro. O limitador desta portabilidade protege picos por bloco; sua resposta sonora merece avaliação com material real.

## Referências

[AVAudioEngine](https://developer.apple.com/documentation/avfaudio/avaudioengine), [canais da sessão iOS](https://developer.apple.com/documentation/avfaudio/avaudiosession/setpreferredoutputnumberofchannels(_:)), [contas e instalação pessoal Apple](https://developer.apple.com/help/account/basics/about-your-developer-account/), [AltStore Classic para Windows](https://faq.altstore.io/altstore-classic/how-to-install-altstore-windows). Bibliotecas DSP e licenças estão preservadas nos submódulos e recursos do app.
