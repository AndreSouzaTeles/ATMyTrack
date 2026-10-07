# ATMyTrack — Android 0.5.0

Atualização incremental do player multitrack em Kotlin/Compose para Android 8.0+ (API 26). Preserva a biblioteca e os áudios internos das versões anteriores.

## Instalação
Baixe o [APK 0.5.0](https://github.com/AndreSouzaTeles/ATMyTrack/releases/download/v0.5.0/ATMyTrack-0.5.0-debug.apk) ou veja a [release e checksum](https://github.com/AndreSouzaTeles/ATMyTrack/releases/tag/v0.5.0).

Instale `dist/ATMyTrack-debug.apk` sobre a versão anterior, sem desinstalar. A assinatura de desenvolvimento foi mantida. O APK inclui arm64-v8a, armeabi-v7a e x86_64. Não exige conta ou assinatura. A origem de nuvem pode precisar de conexão/download.

## Importação e armazenamento
- Seleção de pasta ou vários arquivos pelo seletor Android: memória interna, SD, USB e provedores de nuvem disponíveis. A origem precisa estar acessível durante a preparação; downloads dependem do provedor.
- **DISCOVERED → PREPARING → READY / ERROR**. Metadata não significa áudio pronto. PLAY é habilitado somente após preparar todas as tracks; falhas mostram nome/motivo, tentar novamente e remover track. Há cancelamento do preparo.
- Até três leituras de metadata e dois workers de preparo. Comprimidos e taxas diferentes recebem cache PCM float persistente. WAV PCM local compatível não é convertido; PCM de content providers é copiado sem transcodificação para evitar SAF durante playback.
- A primeira preparação depende da duração, codec e dispositivo. O cache validado permite reabertura rápida. Ele usa armazenamento, não RAM integral: ~121 MiB por stem preparada de 5min30/48kHz. Espaço insuficiente produz erro explícito.
- WAV PCM 8/16/24/32-bit, float32/extensível; MP3, AAC/M4A, FLAC e OGG/Opus dependem dos decoders Android. Aceita mono/estéreo e sample rates diferentes, convertidos para a taxa da primeira stem.
- Fonte externa é validada por tamanho, data e amostras de início/fim. Alterar somente o meio sem atualizar metadata pode não invalidar o cache. Mantenha a origem acessível ao reabrir.
- Waveform espera o preparo essencial e cede à reprodução. Pitch e Smart Click continuam separados. Caches sem referência são removidos numa inicialização com engine ociosa; originais nunca são apagados.

## Controles
O transporte fica **fixo no topo** em ambas as páginas: PLAY/PAUSE, STOP, reiniciar, próximo projeto, posição/duração, BPM e compasso. Cards de músicas e **+** aparecem imediatamente abaixo. PAGE 1 contém waveform e seções; PAGE 2 acrescenta DCA, BUS e routing. Conteúdo e canais têm rolagem independente; navegar não reinicia o áudio. Interface escura azul; níveis altos âmbar, clipping vermelho. O limiter sinaliza excesso de ganho sem esconder os picos dos medidores.

O card **+** após o último projeto abre a importação. O **−** de cada card pede confirmação antes de excluir. Os arquivos originais nunca são apagados. Segure o nome no topo do canal e arraste: a ordem é compartilhada pelas páginas, salva e há rolagem nas bordas. Arrastar o próprio fader ajusta volume.

Todos os faders têm curva de console, escala de −∞ até +10 dB, unity destacado e passos de 0,1 dB. Double tap no fader retorna a 0 dB; toque no valor permanente abre entrada numérica, ±0,1, UNITY e SILÊNCIO. A escala simplifica rótulos quando a altura disponível é menor, mantendo o mesmo ganho. Pan é balance estéreo; mute prevalece sobre solo. Stems menores terminam em silêncio. O master permanece à direita.

### Tom / Key
Escolha origem/destino (incluindo equivalentes bemóis) ou −12…+12 semitons. Selecione as tracks, use TODAS/LIMPAR e APLICAR. Signalsmith Stretch processa realmente o áudio, sem mudar a duração/tempo; só as tracks selecionadas recebem pitch. O preparo ocorre em background e utiliza cache float estéreo (aproximadamente 23 MB/min por track a 48 kHz). Compensamos a latência do DSP e preservamos exatamente a quantidade de frames. ORIGINAL / RESET remove o processamento. Os leitores de pitch são preparados fora da thread de mixagem e trocados juntos entre blocos, preservando AudioTrack, posição e áudio já enfileirado.

### DCA, buses e saídas
DCA multiplica o ganho efetivo dos membros sem sobrescrever seus faders; permite nome, membros, mute, edição e exclusão. Uma track pode pertencer a vários DCAs, cujos ganhos se multiplicam.

Cada track envia para MASTER ou um BUS. O bus soma áudio real, tem fader, mute e meter; seu destino pode ser MASTER/BOTH, LEFT, RIGHT ou pares USB adicionais anunciados pelo Android e aceitos pela saída. Buses enviados diretamente a USB adicional não passam pelo master de OUT 1/2. A exclusão do bus devolve suas tracks ao master.

STEREO SPLIT envia Click/Guide à esquerda e as outras tracks à direita; ajuste individualmente em ROUTING. LEFT/RIGHT somam os dois canais da stem em mono. O click sintetizado possui destino próprio no menu.

ATUALIZAR SAÍDA pausa e reabre a saída após conectar uma interface. O app só oferece pares adicionais quando o Android informa canais e consegue criar AudioTrack compatível. Desconexão USB interrompe reprodução e exige revisão. O emulador testa estéreo; saídas USB físicas ainda precisam de validação na interface real.

### Metrônomo e Smart Click
TAP e CLICK ON/OFF estão no menu METRÔNOMO, com BPM, compasso, 0.5x/1x/2x, volume, acento da primeira batida e sete timbres sintetizados distintos.

Smart Click analisa até 120 segundos em background, priorizando Click/Clk/Metro, Drums e Percussion. Mostra BPM decimal, sugestão arredondada e confiança heurística baseada na regularidade; **só aplica após USAR BPM ou APLICAR BPM**. Resultado sem transientes confiáveis é recusado. Conferir metade/dobro do BPM continua necessário em material ambíguo. O metrônomo começa em 00:00; mudar BPM/multiplicador não altera a velocidade das stems.

## Arquitetura e limites
Um AudioTrack e um clock de frames para todas as stems. Workers preparam arquivos; o produtor lê blocos locais e mixa; uma thread de saída com prioridade áudio consome uma fila SPSC prealocada de oito blocos de 512 frames. Ela não faz I/O de arquivos, SAF, codec, UI ou espera por futures/locks. Antes de PLAY e SEEK a fila comum é preenchida com a saída pausada.

Read-ahead por stem: 4.096 frames/32 KiB. Ganhos, pan, mute/solo/DCA, buses e master são interpolados; limiter vinculado protege a saída. Resampling sinc ocorre durante o preparo, não no playback. Pitch continua JNI/C++ Signalsmith. Biblioteca JSON/AtomicFile mantém compatibilidade com projetos anteriores. Detalhes: [arquitetura](verification/ARCHITECTURE-0.5.md).

Loop/seek não têm garantia gapless ou quantização. Formatos com perdas podem trazer priming/padding próprios. A reserva de áudio acrescenta latência aos controles; o foco é playback de palco, não monitoração ao vivo. Emulador não certifica o aparelho do usuário nem interfaces USB físicas. Resultados e limitações: [relatório 0.5](verification/REPORT-0.5.md).

## Build e verificação
Clone com `git clone --recurse-submodules https://github.com/AndreSouzaTeles/ATMyTrack.git`. Se já clonou, execute `git submodule update --init --recursive`. Os dois submódulos DSP são fixados em commits, com licenças preservadas.

JDK 17, SDK 36, Build Tools 36, NDK 28.2.13676358 e CMake 3.22.1. Configure o SDK em local.properties.
```powershell
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
.\gradlew.bat :app:installDebug
adb shell mkdir -p /sdcard/Android/data/com.atmytrack.app/files/fixtures
adb push verification/fixtures-0.4/. /sdcard/Android/data/com.atmytrack.app/files/fixtures/
.\gradlew.bat :app:connectedDebugAndroidTest
.\gradlew.bat :app:exportDebugApk
```
Os testes instrumentados da 0.4 exigem as fixtures acima, incluindo a subpasta `tempo`. Para regenerá-las, execute `tools/generate-fixtures.py` (dependência de desenvolvimento descrita em `DEPENDENCIES.md`) e `tools/generate-tempo-fixtures.py` (biblioteca padrão Python). A suíte inclui reprodução real de 181 segundos; reserve vários minutos. As fixtures e FFmpeg não são incluídos no APK.
Relatório: `verification/REPORT-0.4.md`. Benchmark compara explicitamente o decoder integral 0.1.0 preservado no APK de teste com o importador atual; não é uma comparação direta contra 0.2.0, nem promessa de tempo em hardware real.

Licenças e commits fixados em `DEPENDENCIES.md`; avisos MIT do DSP incluídos nos assets do APK. Identidade atual: A geométrico azul com travessa azul-clara; símbolo, wordmark SVG, PNGs, adaptive/monochrome e splash acompanham a interface. Assets atuais em `branding/0.5` e `branding/*-0.5.svg`. Versões anteriores permanecem como histórico.

### Sessão de regressão 0.5
`python tools/generate-session-05.py` produz 19 stems musicais sintéticas independentes, de 330 segundos, com ritmos/frequências distintos, WAV16/24, MP3/M4A/AAC/FLAC, mono/estéreo e 44,1/48/96 kHz. Não são gravações fornecidas pelo usuário. Copie `verification/session-0.5` para `/sdcard/Android/data/com.atmytrack.app/files/`. O teste `Session05Test` executa dez minutos com scroll, páginas, faders/pan/mute/solo, menu do metrônomo e seeks; requer alguns minutos adicionais de preparo. `Profile05Test` aceita argumentos `fixtures=session-0.5`, `label=...` e `longTwo=true` para perfil progressivo 1/2/4/8/12/19 com duas tracks por dois minutos.

APKs são publicados em [Releases](https://github.com/AndreSouzaTeles/ATMyTrack/releases), fora do histórico Git; o build local exporta `dist/ATMyTrack-debug.apk`. A chave debug desta máquina não é versionada: builds em outra máquina terão outra assinatura, salvo uso de chave de distribuição própria.
