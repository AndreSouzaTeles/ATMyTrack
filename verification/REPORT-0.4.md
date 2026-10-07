# ATMyTrack 0.4.0 — entrega e verificação

06/10/2026. Atualização sobre o projeto existente, com biblioteca retrocompatível e APK instalável.

## IMPLEMENTADO

- Faders de tracks, master, DCA e bus com curva de console, −∞…+10 dB, destaque de unity, valor permanente, passos de 0,1 dB e double tap para 0 dB. Toque no valor abre entrada numérica, ±0,1, UNITY e SILÊNCIO. Meter representa sinal real separadamente do ganho.
- Escalas completas quando há altura e simplificadas em landscape compacto. PAGE 2 prioriza transporte/mixer/routing; projetos, waveform e seções ficam na PAGE 1. Largura dos canais preservada com rolagem horizontal; telas baixas têm rolagem vertical.
- Identidade anterior substituída por A geométrico turquesa com travessa violeta. Logo/wordmark SVG, símbolo, launcher, adaptive foreground/background, monochrome v33, splash e PNGs 48/72/96/144/192/512 px. Verificados recortes circular/arredondado e zona segura de raio 33 dp no viewport 108 dp.
- Importação com PROCESSING/READY/ERROR por arquivo e relatório persistente na sessão. Falhas individuais e taxas incompatíveis não descartam as tracks válidas. Verificação de disponibilidade do decoder antes de marcar READY.
- Correção da reordenação nas bordas; Click foi arrastado da quinta para a primeira posição e permaneceu assim após reabrir. Drag do fader continua independente.
- Persistência completa por projeto, agora com bloqueio compartilhado entre instâncias do ProjectStore para proteger leituras e gravações AtomicFile concorrentes.
- Pitch, metrônomo, Smart Click, DCA, bus, routing e troca de página mantidos e novamente validados funcionalmente.

## OTIMIZAÇÃO

A causa principal da importação antiga era copiar e decodificar arquivos inteiros antes de liberar o projeto. A arquitetura da 0.3, mantida na 0.4, lê metadata e guarda URI persistente quando a origem permite acesso/seek. Sem essa capacidade, copia somente o arquivo codificado. São até três preparações concorrentes. Waveform, BPM e pitch não bloqueiam a importação inicial.

A 0.4 acrescenta antecipação do decoding para reprodução: dois buffers reutilizáveis de 16.384 frames por stem comprimida, 256 KiB por leitor, com pool compartilhado de 2–4 workers. Uma tarefa por leitor evita acesso concorrente ao codec. Ganhos são pré-calculados; arrays de pico e buffers de bus são reutilizados. Buffers frios são preparados antes de iniciar a saída após play/seek.

### Benchmark do mesmo arquivo contra o importador legado

Emulador Android 15/API 35 x86_64; geração das fixtures excluída. Duas passadas com ordem invertida. O baseline é o decoder integral **0.1.0**, preservado somente no APK de teste, e não uma medição direta da 0.3. Importação por file URI local; não inclui seletor, nuvem, abertura dos decoders ou waveform posterior.

| Fixture | Bytes de origem | Legado 0.1 | Atual 0.4 | Cópia de áudio atual |
|---|---:|---:|---:|---:|
| WAV mono16, 5 min | 28.800.044 | 2.051–2.063 ms | 1 ms | 0 |
| WAV stereo24, 3 min | 51.840.044 | 1.575–1.828 ms | 1 ms | 0 |
| AAC estéreo, 1 min | 1.454.897 | 2.837–2.922 ms | 12–13 ms | 0 |

WAV mono e AAC mantiveram as amostras verificadas. WAV24 conserva sua precisão original; diferença máxima frente à conversão legada de 16 bits: 0,0000304.

### Lotes e preparo da reprodução

Sete fixtures originais de 180 s/48 kHz: WAV, MP3, M4A, AAC ADTS, FLAC, Ogg Vorbis e Opus. Lotes maiores repetem essas fontes como stems independentes; não são 24 gravações musicais distintas.

| Tracks | Importação de metadata, uma execução |
|---:|---:|
| 1 | 179 ms |
| 5 | 298 ms |
| 10 | 106 ms |
| 24 | 203 ms |

Não houve cópia de áudio nesses lotes. A variação reflete inicialização/cache dos extractors; não indica uma relação linear com o número de tracks.

Teste separado até a reprodução com 24 stems: **638 ms de metadata + 1.216 ms para abrir leitores/saída = 1.854 ms**. Após PLAY, o playback head atingiu 0,5 s em mais **613 ms**, totalizando **2.467 ms** desde o início da importação. Esse teste inclui a engine, mas ainda exclui seletor Android e latência de provedores de nuvem. Os tempos não constituem promessa para o aparelho do usuário; o relato de dois minutos por track não foi medido no hardware físico dele.

## ÁUDIO

- Um AudioTrack e um cursor comum em frames continuam governando todas as stems. Ganhos, mute/solo, DCA, bus, ordem e páginas não criam clocks independentes.
- ADTS agora converte timestamps arredondados por pacote em tempo exato de amostras. M4A preserva priming ao voltar ao início. MP3/Vorbis avançam pacotes codificados desde o início até o preroll para contornar seeks aproximados, sem decodificar/copiar o trecho anterior.
- Busca para início, 1 s, 90 s e 178 s foi comparada à leitura sequencial de blocos de 4.096 frames. Erro máximo ADTS: **0,000458**; demais formatos: **0** nos trechos medidos. Tolerância definida: 0,003. A correlação entre janelas de 90 s e 178 s encontrou **0 frames de drift nos sete formatos**. Isso distingue drift acumulado de atraso constante já gravado pelo encoder.
- Maior busca observada por leitor nessa rodada: WAV 0 ms, FLAC 71 ms, M4A 83 ms, Opus 126 ms, Vorbis 151 ms, ADTS 286 ms, MP3 564 ms. Buscas não são prometidas como instantâneas.
- Pitch: Signalsmith Stretch/Linear MIT via JNI, processamento sob demanda e cache float estéreo, latência compensada e contagem exata de frames. Leitores novos são preparados fora do mixer e trocados entre blocos, mantendo AudioTrack e transporte. Leitores aposentados são liberados em executor separado.
- DCA multiplica o ganho efetivo dos membros sem sobrescrever seus faders. Bus soma amostras reais, aplica ganho/mute e envia ao master, LEFT/RIGHT ou pares USB disponíveis. Stereo split foi verificado em amostras: Click/Guide à esquerda e banda à direita.

### Ensaio de 24 stems

181 segundos, 1.787 snapshots; **0 underruns durante reprodução contínua**. Houve **1 underrun no retorno do loop**, aos 179.460 ms do período observado, com frame voltando a zero. O ensaio começou após o primeiro segundo de playback, por isso o evento aparece antes de 180 s no cronômetro de observação. Loop não é gapless.

Antes da antecipação, a mesma carga havia registrado 261 underruns. Após a correção, duas rodadas registraram apenas a ocorrência na fronteira do loop. PSS do processo na última rodada: **135.040 → 143.564 KiB**; esse número não inclui toda a memória dos serviços de codecs do sistema. Buffers da engine são limitados, independentemente da duração total dos arquivos.

Pause, seek, loop e troca de leitores de pitch durante playback passaram. A troca de pitch não aumentou o contador de underruns no intervalo verificado. Mudanças repetidas de página mantiveram reprodução, posição progressiva, ganhos e contador de underruns. São verificações instrumentadas, não uma avaliação perceptual de todos os materiais e transições possíveis.

## TESTES

**25 testes JVM aprovados.** Curva/ganho, DCA BAND com faders −2/−4/−6 dB e retorno a unity, bus/mute/split em amostras, metronome 3/4/6 com multiplicadores 0,5/1/2 e acento, timbres, TAP, detecção de tempo, formatos PCM, precisão ADTS e persistência/retrocompatibilidade. A persistência inclui stress entre duas instâncias concorrentes e todos os campos do projeto.

**24 testes Android distintos aprovados ao final das correções.** A rodada completa de 23 testes passou em 22 e revelou a falha de persistência do fader. Após corrigir a serialização entre instâncias, os oito testes de UI/persistência foram repetidos, junto ao novo teste de prontidão com 24 stems: **9/9 aprovados**, em 30,234 s. Os 15 testes restantes da rodada completa já haviam passado e seus caminhos de áudio/importação não foram alterados nessa última correção. Logs anteriores foram preservados para rastreabilidade.

Cobertura funcional adicional:

- WAV/MP3/M4A/AAC/FLAC/Ogg/Opus, arquivos longos, lotes 1/5/10/24, busca e drift; fonte sem seek/tamanho conhecido, arquivo inválido e importação parcial.
- Pitch +1/+2/+3/−3/±12 e reset em uma, duas e três tracks selecionadas; frequência real medida sobre seno de 220 Hz, tolerância ±2 Hz, mesma quantidade de frames/BPM, cache sem novo processamento e track não selecionada preservada. Os pares C→C#, C→D, E→G e A→F# têm mapeamento validado.
- Smart Click em sinais sintéticos distintos: click **120,004 BPM**, bateria **119,975 BPM**, acordes rítmicos sem track de Click **119,982 BPM**. BPM do projeto permanece inalterado até confirmação. Navegação durante análise passou; não foi usado corpus de gravações reais.
- Double tap nos quatro tipos de fader, ajuste −2,7 + 0,1 = −2,6 dB, drag de ganho separado de reordenação e configuração reaberta do disco.
- Cinco tracks reordenadas, três cards de projeto, seleção, criação pelo +, cancelamento/confirmação da exclusão e navegação repetida durante playback.
- Leitura antecipada com seeks aleatórios, fronteiras de buffer, loop, propriedade exclusiva do decoder e liberação após falha.
- Assets renderizados pelo próprio Android e safe zone conferida pixel a pixel. Revisão visual 1920×1200/240 dpi, 1080×1920/420 dpi e 1920×1080/420 dpi, incluindo rolagem e escala simplificada.

## LIMITAÇÕES

- Emulador não certifica estabilidade/desempenho no aparelho ou interface USB física. USB multicanal depende de canais anunciados/aceitos pelo Android e não foi testado em hardware externo.
- Loop/seek não têm contrato gapless; foi medida uma ocorrência de underrun na fronteira do loop. As tracks mantêm a mesma timeline.
- Stems precisam compartilhar sample rate e início temporal. Não há resampling automático nem remoção presumida de silêncio/priming desconhecido em arquivos com perdas. Busca MP3/Vorbis percorre pacotes e depende da duração/latência da origem.
- Nuvem pode exigir download/cópia; referências externas precisam continuar acessíveis. Quantidade de codecs simultâneos depende do aparelho.
- Pitch usa armazenamento adicional, aproximadamente 23 MB/min por track a 48 kHz, e tempo de preparo sob demanda. Qualidade perceptual de material complexo exige avaliação musical.
- Smart Click fornece sugestão heurística: metade/dobro do tempo e downbeat podem exigir ajuste. Fingerprint parcial não detecta alteração apenas no meio do arquivo se o provedor também conservar tamanho/data.

## BUILD

**BUILD SUCCESSFUL**: testes JVM, lint, assemble AndroidTest e exportação do APK. Lint: **0 erros, 21 avisos** de versões disponíveis, KTX, storage/backup e configuração de recursos. Monochrome está implementado na variante v33; o aviso considera a variante base v26.

JDK 17; SDK/Build Tools 36; NDK 28.2.13676358; CMake 3.22.1. APK com arm64-v8a, armeabi-v7a e x86_64. `adb install -r` aprovado; certificado de desenvolvimento igual ao da 0.3. FFmpeg e fixtures não estão no APK; avisos MIT do DSP estão incluídos.

## APK

- Nome: **ATMyTrack-debug.apk**.
- Caminho: **D:\Projetos\ATMyTrack\dist\ATMyTrack-debug.apk**.
- Cópia versionada: **D:\Projetos\ATMyTrack\dist\ATMyTrack-0.4.0-debug.apk**.
- Application ID `com.atmytrack.app`, versionCode **4**, versionName **0.4.0**, minSdk 26, targetSdk 36.
- Tamanho: **11.888.658 bytes**. Assinatura APK v2 verificada.
- SHA-256: **9E703F1E82A26FA0E68592739084ADCCC38019041C1C1A2B5A20C205E55586BE**.

Instale sobre a versão anterior para conservar os projetos; não é necessário desinstalar. As entregas 0.1/0.2/0.3 foram preservadas em `dist`.

## Evidências

`final-build-0.4.log`, `android-tests-before-persistence-fix-0.4.txt`, `android-tests-final-0.4.txt`, `formats-0.4.txt`, `readiness-0.4.txt`, `soak-0.4.txt`, `soak-history-0.4.txt`, `smart-click-0.4.txt`, `import-benchmark-0.4.txt`, `apk-signature-0.4.txt`, `apk-metadata-0.4.txt`, `ARCHITECTURE-0.4.md`. Capturas finais: `tablet-mixer-0.4.png`, `phone-faders-0.4.png`, `landscape-faders-0.4.png`. Identidade: `branding/BRAND-0.4.md` e `branding/0.4`.
