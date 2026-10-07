# ATMyTrack 0.5.0 — resultado técnico

Atualização incremental sobre a 0.4.0. Testes em emulador Android 15/API 35, x86_64, `sdk_gphone64_x86_64`. O aparelho e as gravações originais do usuário não estavam disponíveis. Resultados de emulador não são promessa de desempenho físico.

## Playback e diagnóstico

A 0.4 já possuía um AudioTrack e um clock comum, mas seu mixer fazia leitura PCM diretamente e podia bloquear esperando o decoder de cada stem comprimida. READY significava metadata lida. A soma também era truncada em ±1, sem suavização dos controles. Esses problemas foram identificados no código; o limiar de falha em duas tracks relatado no aparelho **não se reproduziu no perfil curto do emulador**.

A 0.5 prepara os leitores antes de liberar PLAY, limita a preparação a dois workers, persiste cache seletivo e separa o produtor da thread de saída. WAV local compatível não é transcodificado. Comprimidos/taxas diferentes são preparados em PCM; origens PCM por content provider são copiadas sem conversão para retirar o provedor do playback. A saída consome somente blocos já mixados. PLAY/SEEK preenchem a reserva do AudioTrack e o ring comum com o relógio pausado.

Ganhos/pan/mute/solo/DCA, buses e master têm rampas; limiter vinculado evita recorte direto sob sobrecarga e conserva indicação dos picos pré-limiter. O limiter pode reduzir dinâmica; o operador deve ajustar o headroom. Detalhes em [ARCHITECTURE-0.5.md](ARCHITECTURE-0.5.md).

## Perfil comparável antes/depois

Mesmas sete fontes sintéticas de 180 s, repetidas para formar os lotes. Cada ponto reproduz 12 s, sem UI. CPU é tempo do processo dividido pelo tempo de reprodução: 100% equivale a **um núcleo**, não ao aparelho inteiro; serviços de codecs externos ao processo não estão incluídos. São medições de uma execução, não estimativa estatística.

| Tracks | CPU 0.4 | CPU 0.5 | Underruns 0.4 / 0.5 | PSS 0.5, KiB | Maior leitura 0.5 |
|---:|---:|---:|---:|---:|---:|
| 1 | 6,0% | 3,6% | 0 / 0 | 86.494 | 1,71 ms |
| 2 | 6,2% | 5,1% | 0 / 0 | 84.826 | 2,91 ms |
| 4 | 13,4% | 7,4% | 0 / 0 | 89.198 | 2,21 ms |
| 8 | 26,8% | 11,3% | 0 / 0 | 90.042 | 3,43 ms |
| 12 | 38,1% | 15,8% | 0 / 0 | 87.086 | 1,70 ms |
| 19 | 60,7% | 22,8% | 0 / 0 | 88.842 | 1,49 ms |

Com 19, o tempo de CPU caiu de 7.282 para 2.737 ms por 12 s (~62,4%). Houve uma coleta de GC no ponto de 19 tracks em ambas as versões. Os máximos de leitura são do **produtor**, não da thread de saída. O perfil final registrou zero starvation da reserva em todos os lotes.

As fontes repetidas sobrecarregam a soma nos ganhos padrão: 1.144 blocos com pico acima de 1 no lote de 19. O campo histórico `clippedBlocks` dos logs da 0.5 conta **sobrecarga pré-limiter**, não recorte na saída. O teste unitário verifica saída limitada a 0,98 e proporção entre canais.

Fontes: [baseline](profile-baseline.txt), [perfil final](profile-final-0.5.txt). Um ensaio adicional com duas stems distintas durou 120 s sem underruns; o arquivo [perfil inicial da sessão](profile-session-prepared.txt) ainda usa o contador antigo `starvation` para qualquer fila momentaneamente vazia. Esse contador foi corrigido: espera coberta pela reserva não equivale a falta de áudio.

## Importação, preparo e cache

Sessão de 19 stems **distintas**, 330 s cada, com ritmos/frequências diferentes; WAV16/24, MP3, M4A, AAC e FLAC; mono/estéreo; 44,1/48/96 kHz. Áudio sintético original, não gravação de banda nem material fornecido pelo usuário. Origem `file://` no armazenamento emulado; o benchmark não inclui nuvem, SD físico ou o seletor Android.

| Etapa | Medição |
|---|---:|
| Metadata, 19 arquivos | 525 ms |
| Preparação essencial, cache de playback novo | 118.620 ms |
| Metadata até READY, lote inteiro | 119.172 ms |
| Reabrir os mesmos leitores/cache, sem repetir metadata/UI | 34 ms |
| Fluxo pela UI com arquivos já preparados | 1.355 ms, incluindo 1.245 ms até seleção/metadata |

O teste força novas chaves para medir um cache de playback vazio, sem apagar caches de projetos salvos. O cache do sistema operacional pode estar aquecido. **A primeira preparação desse lote misto de 5min30 não terminou em poucos segundos**: levou aproximadamente dois minutos para o conjunto. É o custo real de preparar os comprimidos/taxas distintas antes da reprodução. A etapa não é escondida por uma importação visual instantânea. Um ensaio intermediário levou 175,7 s; janelas contíguas de resampling e escrita float em lote reduziram esse custo.

Um arquivo preparado em float estéreo/48 kHz ocupa cerca de 121 MiB por 330 s. PCM local compatível permanece no formato original. Todos os 19 caches/leitores recém-preparados reproduziram amostras iniciais idênticas às fontes decodificadas. Reabertura conserva tamanho/mtime dos caches. Cancelamento não publica `.part`; arquivo ausente impede PLAY e a retirada da track inválida permite continuar. Fonte: [preparo frio](cold-preparation-0.5.txt).

## Sincronização e sessão longa

- Um clock de AudioTrack; todas as stems recebem o mesmo índice absoluto de frame. Loop enfileira o início sem parar a saída. Não há players independentes.
- Comparação entre cache e **decodificação contínua de referência**, nas posições 00:30, 01:00, 02:00, 03:00 e 05:00: erro máximo de amostra **0,0** em todas as 19 stems, incluindo resampling. Buscas no cache: 0–2 ms por leitor nesta execução. Fonte: [alinhamento](alignment-0.5.txt).
- A referência contínua é importante: um teste preliminar usando seek do decoder AAC como referência encontrou diferença de 0,00897, causada pelo comportamento de seek/predictor. O playback em cache evita essas buscas de codec.
- Sessão: **600.715 ms**, 19 stems, 575 ciclos de interação. Páginas, scroll, faders, pan, mute/solo e menu do metrônomo; seeks em 30/60/120/180 s e retorno ao início; passagem por loop.
- **0 underruns, 0 starvation da reserva**, sem erro do motor. 117 coletas de GC; CPU do processo 267.991 ms (~44,6% de um núcleo), incluindo automação/UI. PSS amostrado: 121.756–146.602 KiB (~119–143 MiB).
- Esperas do produtor ocorreram e foram cobertas pela reserva. Não foram tratadas como dropouts. O emulador configurou 8.720 frames no AudioTrack mais até 4.096 no ring: aproximadamente 267 ms de antecipação a 48 kHz. Essa reserva acrescenta latência aos controles.
- Um snapshot de `dumpsys gfxinfo` durante o ensaio registrou 4.575 frames, 342 janky (7,48%), mediana 18 ms, P90 32 ms e P99 93 ms. Não é uma medição completa dos dez minutos nem de GPU física. O relatório não afirma UI sem jank.

Fonte principal: [sessão de dez minutos](session-final-0.5.txt) e [resultado instrumentado](alignment-and-soak-final-0.5.txt). Um ensaio intermediário revelou um underrun em busca; o prebuffer do próprio AudioTrack corrigiu o caso nos ensaios posteriores de 65 s e 600 s. Não houve captura elétrica/loopback ou teste no aparelho do usuário.

## Interface e compatibilidade

Transporte fixo, cards abaixo, conteúdo/mixer rolável; PAGE 1/2 preservam a engine. Paleta azul escura em Compose, seleções, faders, waveform, diálogos, ícone, splash e PNGs. Marcadores antigos conhecidos migram de cor; capas importadas mantêm seu conteúdo. Faders/DCA/buses, metronome, pitch e biblioteca continuam disponíveis. Smart Click e aplicação de pitch aguardam o preparo essencial.

O JSON preserva o formato de biblioteca e lê projetos antigos; novos campos guardam sample rate/frame count nativos para resampling correto após reabertura. As assinaturas das versões anteriores são mantidas. USB multicanal físico e provedores reais de nuvem não foram certificados neste emulador.

## Build e entrega

- Build final: `testDebugUnitTest`, `lintDebug`, `exportDebugApk` e `assembleDebugAndroidTest` concluídos. **30 testes JVM aprovados**, lint sem erros (22 avisos revisados, principalmente versões/API/compatibilidade).
- Regressão Android: **24 testes aprovados**, incluindo importação, codecs, cancelamento, arquivos inacessíveis, pitch, Smart Click e controles. [Log](regression-final-0.5.txt). O ensaio adicional com 24 stems (sete fontes repetidas), 181 s, registrou zero underruns, incluindo troca de leitores de pitch: [log](soak-regression-0.5.txt).
- Alinhamento + sessão de dez minutos: dois testes aprovados. Validação final de transições de loop, controles e layout: **11 testes aprovados** ([log](final-boundary-layout-0.5.txt)). Layout também aprovado em celular retrato e paisagem, com transporte na mesma posição após rolagem e fader visível.
- Capturas: [tablet](layout-tablet-faders-0.5.png), [celular](layout-phone-faders-0.5.png), [paisagem](layout-landscape-faders-0.5.png).
- Uma execução automatizada imediatamente após mudar a resolução falhou em `performMeasureAndLayout called during measure layout` (Compose/Espresso). A repetição com configuração estabilizada passou; não foi identificado erro da engine nesse episódio. Em telas baixas, nomes/pan e faders exigem rolagem vertical; somente o transporte permanece fixo.
- APK **0.5.0 / versionCode 5**, 11.385.508 bytes, instalado com sucesso sobre a versão existente no emulador. API mínima 26, alvo 36; arm64-v8a, armeabi-v7a e x86_64. Assinatura v2 verificada, certificado anterior preservado.
- SHA-256: `80c6df891b5ad12977970ffd4d7fb505638d26a97ce89283f5d753f983b697dc`.
- Arquivo local: `D:\Projetos\ATMyTrack\dist\ATMyTrack-debug.apk`; cópia versionada: `dist/ATMyTrack-0.5.0-debug.apk`.
- Código/README: [GitHub](https://github.com/AndreSouzaTeles/ATMyTrack). APK e checksum: [release v0.5.0](https://github.com/AndreSouzaTeles/ATMyTrack/releases/tag/v0.5.0).
