# ATMyTrack

**[Abrir ATMyTrack Web](https://atmytrack-web.andmtu.chatgpt.site)** · [Baixar APK Android](https://github.com/AndreSouzaTeles/ATMyTrack/releases/download/v0.12.0/ATMyTrack-0.12.0-debug.apk)

## Android 0.12.0

Atualização incremental do player multitrack em Kotlin/Compose para Android 8.0+ (API 26). Preserva a biblioteca e os áudios internos das versões anteriores.

## Atualização 0.12.0 — Routing, Master e mixer web

- Web: barra em linha única com rolagem horizontal em telas estreitas; DCA e BUS em botões separados. Faders com cursores retangulares de mesa, mantendo teclado/toque e reset 0dB.
- Android: Routing centralizado lista tracks, BUS e click interno. Stereo split fica dentro do painel, junto de **BOTH ALL**, também adicionado à web.
- BOTH ALL envia todas as tracks diretamente ao Master em estéreo e o click aos dois canais; preserva faders, pans e grupos cadastrados.
- PAN do Master nas duas versões, com balanço que preserva L/R no centro e atenua o lado oposto nos extremos. Ajuste salvo por projeto; projetos antigos começam centralizados. Saída OUT 1–2 no rodapé do Master, abaixo do botão 0dB.
- Site local atualizado: http://127.0.0.1:4173/?v=0.12.0. [Validação e limites](verification/REPORT-0.12.0.md).

## Atualização 0.11.0 — importação e timeline Android

- LOOP usa **OFF / ON**, sem quebrar o estado em várias linhas.
- Waveform acumulada na preparação do áudio e reutilizada em cache; projetos antigos analisam o PCM local mesmo durante PLAY, sem aguardar STOP. Ambos os canais e silêncios reais são preservados.
- Novos projetos usam o nome visível da pasta. Arquivos avulsos aproveitam a pasta quando o provedor Android a informa; caso contrário, usam o nome do primeiro arquivo.
- Importação de pasta escolhe aleatoriamente uma capa entre as imagens que o Android consegue decodificar. A imagem é copiada para o armazenamento do app.
- Detecção local de tonalidade por chroma espectral e perfis Krumhansl–Kessler. Preenche o campo quando há evidência suficiente, mantendo edição manual e sem sobrescrever um tom informado pelo usuário. Uma estimativa não garante acerto em músicas ambíguas ou com modulações.
- [Testes, ambiente e limitações](verification/REPORT-0.11.0.md). Esta atualização é do Android; a versão web permanece inalterada.

## Atualização 0.10.1 — cards web

Cards de projeto com altura adaptativa, nome completo e quantidade de tracks em linha própria. Corrige o corte de texto em títulos como “Clamo Jesus - Baruk”. Verificação visual/geométrica em 320, 390, 768 e 1440 px; build Android e 41 testes JVM aprovados. APK recompilado; engine preservada. Site local atualizado, sem nova publicação do site público.

## Atualização 0.10.0 — projetos grandes na web

- Importação com círculo giratório, nome do arquivo, etapa e contador 01/19, 02/19…; painel permanece visível até terminar. Falhas são listadas por arquivo.
- Arquivos importados usam leitura em blocos e AudioWorklet com relógio compartilhado, sem o teto agregado de 384 MB. WAV PCM é lido diretamente do armazenamento. Comprimidos são decodificados por arquivo e cacheados em disco; um único arquivo comprimido muito longo ainda depende dos limites do decoder.
- TOM/VELOCIDADE geram o resultado em disco por blocos. Seek, loop, mixer e click continuam no relógio comum. Backups novos usam formato binário para evitar expandir todo o projeto em base64.
- Teste real com 30 WAVs / **5.472.001.320 bytes** no Chrome Windows: importação, reabertura e 30 canais audíveis; seek e loop; zero faltas de blocos detectadas no cenário curto. Não é certificação de reprodução integral nem teste em celular. [Resultados e limites](verification/REPORT-0.10.0.md).
- Site local: http://127.0.0.1:4173/?v=0.10.0. Recarregue uma aba antiga; projetos existentes são preservados. Site público não foi republicado.
- APK Android recompilado como 0.10.0 (12), com a engine Android preservada; as melhorias desta entrega são da web.

## Atualização 0.9.0

- Seções: atalhos RGB/CMY e seletor circular de cor personalizada; cores preservadas ao reabrir projetos. A paleta livre é exclusiva das seções, conforme pedido; o restante da interface continua azul.
- Waveform: envelope contínuo, ambos os canais e todas as tracks; silêncios reais preservados. Cache anterior de waveform é recalculado. A análise Android ocorre em segundo plano e cede ao playback; a web usa worker e revisa envelopes antigos ao abrir o projeto.
- TOM e VELOCIDADE abrem a barra; LOOP, + SEÇÃO, METRÔNOMO e ferramentas do mixer compartilham cartões e ícones. Texto de navegação da timeline removido.
- Site **local** atualizado em `http://127.0.0.1:4173/`; publicação pública anterior permanece na versão 0.8.0. Para iniciar localmente: `python -m http.server 4173 --bind 127.0.0.1 --directory web/dist`.
- **STEREO SPLIT** envia tracks cujo nome contém click/guide/guia/clk/metro para L, e as demais para R; click interno vai para L. Remove a associação das tracks aos BUS para aplicar essa separação. Não extrai instrumentos de um áudio mixado.
- **ATUALIZAR SAÍDA** pausa o player, reabre a saída Android e consulta os canais da interface disponível, preservando posição e ajustes do projeto. Retome pelo PLAY; útil após conectar uma interface.
- Validação e limites: [relatório 0.9.0](verification/REPORT-0.9.0.md).

## Atualização 0.8.0

- Afinador mostra a nota cromática realmente captada (incluindo F#), separada da corda-alvo. Cents da nota e desvio até o alvo são distintos. Refinamento YIN interpola a diferença original para reduzir erro de frequência.
- Menu com logo acima do nome, Projetos/Afinador na parte superior e **DEIXE SUA MARCA / Central de dúvidas** fixos no rodapé. Área superior rola em telas baixas.
- Nova versão web responsiva em [`web/`](web/README.md), com biblioteca local no navegador e exportação/importação de backups, sem login/sincronização paga de áudio. Recursos e limites constam no [relatório 0.8.0](verification/REPORT-0.8.0.md).
- Portabilidade nativa iOS, workflow e release retirados a pedido do usuário. O histórico Git não foi reescrito.

## Atualização 0.7.0

- **TOM + VELOCIDADE**, lado a lado. Velocidade de 50% a 200%, passos de 1%, presets, seleção própria de tracks (todas por padrão) e aviso antes de velocidades diferentes. Reset de tom e velocidade independentes.
- Um único processamento Signalsmith Stretch combina pitch e tempo antes da reprodução. A engine continua lendo PCM; 100% sem pitch usa bypass real. A preparação mostra track/progresso e mantém o áudio anterior tocando. A troca de velocidade preserva a posição musical, com breve rebufferização e entrada suavizada; não é automação instantânea de tempo.
- Tempo total/atual, BPM e click interno acompanham a velocidade global. Markers continuam salvos na timeline original; seek e loop convertem para frames de reprodução. Seleção personalizada mantém a referência original de timeline/click e pode dessincronizar as tracks.
- Menu **☰**: Projetos (biblioteca existente), Afinador, Plano / Pagamento e Central de dúvidas. QR Code e conteúdo de ajuda preservados.
- Afinador offline com AudioRecord, YIN, confiança, rejeição de sinal fraco, estabilização, nota/oitava/Hz/cents, tolerância de ±3 cents e indicação azul. 12 instrumentos + Cromático, corda AUTO/manual, cinco afinações para violão/guitarra, A4 de 400 a 480 Hz (padrão/reset 440 Hz).
- O afinador pausa o player e não o retoma automaticamente. Microfone solicitado apenas nesta ferramenta e liberado ao sair, perder foco de tela ou ir para segundo plano. Nenhum áudio é salvo ou enviado.
- Cache DSP remove percentuais antigos; orçamento de 2 GiB para o conjunto, com proteção do projeto ativo (que pode exceder esse tamanho). Caches de outros projetos podem ser recriados ao reabrir; originais nunca são apagados. Configurações persistidas por projeto e preferências globais do afinador.

Resultados, ambiente, cenários e limites: [relatório 0.7.0](verification/REPORT-0.7.0.md).

## Instalação
Baixe o [APK 0.12.0](https://github.com/AndreSouzaTeles/ATMyTrack/releases/download/v0.12.0/ATMyTrack-0.12.0-debug.apk) ou veja a [release e checksum](https://github.com/AndreSouzaTeles/ATMyTrack/releases/tag/v0.12.0).

Instale `dist/ATMyTrack-debug.apk` sobre a versão anterior, sem desinstalar. A assinatura de desenvolvimento foi mantida. O APK inclui arm64-v8a, armeabi-v7a e x86_64. Não exige conta ou assinatura. A origem de nuvem pode precisar de conexão/download.

## Importação e armazenamento
- Seleção de pasta ou vários arquivos pelo seletor Android: memória interna, SD, USB e provedores de nuvem disponíveis. A origem precisa estar acessível durante a preparação; downloads dependem do provedor.
- **DISCOVERED → PREPARING → READY / ERROR**. Metadata não significa áudio pronto. PLAY é habilitado somente após preparar todas as tracks; falhas mostram nome/motivo, tentar novamente e remover track. Há cancelamento do preparo.
- Até três leituras de metadata e dois workers de preparo. Comprimidos e taxas diferentes recebem cache PCM float persistente. WAV PCM local compatível não é convertido; PCM de content providers é copiado sem transcodificação para evitar SAF durante playback.
- A primeira preparação depende da duração, codec e dispositivo. O cache validado permite reabertura rápida. Ele usa armazenamento, não RAM integral: ~121 MiB por stem preparada de 5min30/48kHz. Espaço insuficiente produz erro explícito.
- WAV PCM 8/16/24/32-bit, float32/extensível; MP3, AAC/M4A, FLAC e OGG/Opus dependem dos decoders Android. Aceita mono/estéreo e sample rates diferentes, convertidos para a taxa da primeira stem.
- Fonte externa é validada por tamanho, data e amostras de início/fim. Alterar somente o meio sem atualizar metadata pode não invalidar o cache. Mantenha a origem acessível ao reabrir.
- Waveform espera o preparo essencial e cede à reprodução. Pitch e Smart Click continuam separados. Caches sem referência são removidos numa inicialização com engine ociosa; originais nunca são apagados.

## Novidades da 0.6
Validação, capturas e limites: [relatório da versão](verification/REPORT-0.6.md).

- Editar projeto permite adicionar/trocar/remover capa; imagem atenuada ao fundo do card e prévia no editor.
- Lupa pesquisa por nome ou tom; filtro de tom exato. Toque na logo abre a biblioteca lateral, Central de ajuda e SeuNomeNoApp.
- Segure uma seção para selecioná-la; LOOP repete esse intervalo. Toque curto navega ao início. Segurar novamente desfaz a seleção. O editor mostra waveform, posição em segundos e botões para marcar início/fim.
- Arrastar o nome move visualmente o canal inteiro; nomes centralizados. Barra de ferramentas agrupada e páginas Playback/Mixer.
- Buses oferecem cada canal físico (OUT 1/2 e OUT 2/2 em uma saída de dois canais), pares estéreo e envio ao master. Canais físicos diretos não passam pelo fader master. Somente canais aceitos pela saída Android são oferecidos; interfaces USB físicas exigem validação no aparelho.
- SeuNomeNoApp: globo giratório com arraste, foco por toque e busca. Os 20 nomes de demonstração são explicitamente fictícios. Nome do formulário fica salvo apenas no aparelho; o usuário pode compartilhar sua identificação e comprovante pelo canal escolhido. Não existe recebimento automático, verificação bancária ou publicação automática. Inclusão real é manual no asset `supporters.json`, com `demo=false`.
- Pix: link Nubank, Copia e Cola e QR da imagem enviada pelo usuário, preservada sem edição na 0.6.1. Texto decodificado conferido com o Pix Copia e Cola; CRC textual UTF-8 validado. Nenhum pagamento foi realizado no teste.

## Controles
O transporte fica **fixo no topo** em ambas as páginas: PLAY/PAUSE, STOP, reiniciar, próximo projeto, posição/duração, BPM e compasso. Cards de músicas e **+** aparecem imediatamente abaixo. Playback contém waveform e seções; Mixer acrescenta DCA, BUS e routing. Conteúdo e canais têm rolagem independente; navegar não reinicia o áudio. Interface escura azul; níveis altos âmbar, clipping vermelho. O limiter protege a saída; clipping aparece somente nos medidores, sem mensagem no topo.

O card **+** após o último projeto abre a importação. O **−** de cada card pede confirmação antes de excluir. Os arquivos originais nunca são apagados. Segure o nome no topo do canal e arraste: a ordem é compartilhada pelas páginas, salva e há rolagem nas bordas. Arrastar o próprio fader ajusta volume.

Todos os faders têm curva de console, escala de −∞ até +10 dB, unity destacado e passos de 0,1 dB. Double tap no fader retorna a 0 dB; toque no valor permanente abre entrada numérica, ±0,1, UNITY e SILÊNCIO. A escala simplifica rótulos quando a altura disponível é menor, mantendo o mesmo ganho. Pan é balance estéreo; mute prevalece sobre solo. Stems menores terminam em silêncio. O master permanece à direita. Novas tracks e master começam em 0 dB; o botão 0dB de cada canal retorna ao unity sem apagar ajustes já salvos.

### Tom / Key
Escolha origem/destino (incluindo equivalentes bemóis) ou −12…+12 semitons. Selecione as tracks, use TODAS/LIMPAR e APLICAR. Signalsmith Stretch processa realmente o áudio, sem mudar a duração/tempo; só as tracks selecionadas recebem pitch. O preparo ocorre em background e utiliza cache float estéreo (aproximadamente 23 MB/min por track a 48 kHz). Compensamos a latência do DSP e preservamos exatamente a quantidade de frames. ORIGINAL / RESET remove o processamento. Os leitores de pitch são preparados fora da thread de mixagem e trocados juntos entre blocos, preservando AudioTrack, posição e áudio já enfileirado.

### DCA, buses e saídas
DCA multiplica o ganho efetivo dos membros sem sobrescrever seus faders; permite nome, membros, mute, edição e exclusão. Uma track pode pertencer a vários DCAs, cujos ganhos se multiplicam.

Cada track envia para MASTER ou um BUS. O bus soma áudio real, tem fader, mute e meter; seu destino pode ser MASTER/BOTH, LEFT, RIGHT ou pares USB adicionais anunciados pelo Android e aceitos pela saída. Buses enviados diretamente a USB adicional não passam pelo master de OUT 1/2. A exclusão do bus devolve suas tracks ao master.

STEREO SPLIT envia Click/Guide à esquerda e as outras tracks à direita; ajuste individualmente em ROUTING. LEFT/RIGHT somam os dois canais da stem em mono. O click sintetizado possui destino próprio no menu.

ATUALIZAR SAÍDA pausa e reabre a saída após conectar uma interface. O app só oferece pares adicionais quando o Android informa canais e consegue criar AudioTrack compatível. Desconexão USB interrompe reprodução e exige revisão. O emulador testa estéreo; saídas USB físicas ainda precisam de validação na interface real.

### Metrônomo e Smart Click
TAP, Smart Click e Play/Pausar estão no menu METRÔNOMO, com BPM, compasso, 0.5x/1x/2x, volume, acento da primeira batida e sete timbres sintetizados distintos.

Smart Click analisa até 120 segundos em background, priorizando Click/Clk/Metro, Drums e Percussion. A flag ao lado do TAP aplica automaticamente o BPM detectado, com confiança heurística baseada na regularidade. Desativar a flag impede a aplicação pendente e conserva o BPM atual. Play/Pausar do metrônomo e Fechar ficam no rodapé fixo; Play também inicia o transporte quando parado. Resultado sem transientes confiáveis é recusado. Conferir metade/dobro do BPM continua necessário em material ambíguo. O metrônomo começa em 00:00; mudar BPM/multiplicador não altera a velocidade das stems.

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

Licenças e commits fixados em `DEPENDENCIES.md`; avisos MIT do DSP incluídos nos assets do APK. Identidade atual: imagem fornecida pelo usuário em 07/10/2026, preservada em `app/src/main/res/drawable-nodpi/brand_art.png`. PNGs, adaptive icon, logo e splash usam essa arte. Assets atuais em `branding/0.6`. Versões anteriores permanecem como histórico.

### Sessão de regressão 0.5
`python tools/generate-session-05.py` produz 19 stems musicais sintéticas independentes, de 330 segundos, com ritmos/frequências distintos, WAV16/24, MP3/M4A/AAC/FLAC, mono/estéreo e 44,1/48/96 kHz. Não são gravações fornecidas pelo usuário. Copie `verification/session-0.5` para `/sdcard/Android/data/com.atmytrack.app/files/`. O teste `Session05Test` executa dez minutos com scroll, páginas, faders/pan/mute/solo, menu do metrônomo e seeks; requer alguns minutos adicionais de preparo. `Profile05Test` aceita argumentos `fixtures=session-0.5`, `label=...` e `longTwo=true` para perfil progressivo 1/2/4/8/12/19 com duas tracks por dois minutos.

APKs são publicados em [Releases](https://github.com/AndreSouzaTeles/ATMyTrack/releases), fora do histórico Git; o build local exporta `dist/ATMyTrack-debug.apk`. A chave debug desta máquina não é versionada: builds em outra máquina terão outra assinatura, salvo uso de chave de distribuição própria.
