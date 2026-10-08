# ATMyTrack 0.10.0 — importação web e streaming

## Alterações
- Entrada de arquivos WAV/PCM por fatias; os arquivos originais continuam no IndexedDB, sem decodeAudioData do projeto inteiro. WAV extensível e RF64 reconhecidos; PCM mono/estéreo.
- Um Worker de disco e um AudioWorklet com saídas por track. MessageChannel direto, quatro blocos de um segundo, um cursor para todas as tracks. Pré-buffer aproximadamente 44 MiB para 30 tracks estéreo/48kHz; não confundir com RAM total do navegador. Há memória adicional para blocos em produção, mensagens, DSP, UI e estruturas internas.
- Falta de bloco interrompe conjuntamente a reprodução e mostra orientação, evitando continuar com tracks desalinhadas. O contador mede falta de blocos nesta camada, não todos os dropouts físicos.
- Sample rates diferentes usam sinc de 32 taps com tabela de fases. Unity/zero pitch não usa Signalsmith.
- Signalsmith gera PCM em OPFS por blocos, com compensação de latência. Uma variante em disco por track; arquivos do usuário não são removidos. Deletar projeto remove caches associados.
- Formatos comprimidos usam decoder nativo de um arquivo por vez, seguido de cache PCM em disco. Uma track comprimida excepcionalmente longa ainda pode exceder memória/limites do decoder. Não há demux/decoder incremental universal nesta entrega.
- Importação mostra círculo animado, contagem zero-padded, arquivo e etapa, inclusive preparação da waveform e salvamento. A contagem usa o índice tentado, independentemente de falhas. Controles do projeto ficam desativados durante importação. Falhas de arquivos são listadas ao final.
- Backup binário v2 evita expansão global para base64. Restauração v1 permanece compatível. Limpar dados do navegador continua apagando a biblioteca.
- Site local atualizado. Publicação pública anterior não alterada. Android apenas incrementado/recompilado, sem alteração da engine nesta entrega.

## Testes realmente executados
### Projeto grande
Chrome headless no Windows, 30 arquivos físicos WAV PCM16 estéreo 48 kHz, cada um com 950 segundos de senoide 220 Hz. Total **5.472.001.320 bytes** (5,47 GB decimal). Gerador: web/tests/make-large-fixtures.py.
- Importação e análise: 34.693 ms neste ambiente/arquivos sintéticos. Não é previsão de tempo no dispositivo do usuário nem para codecs diferentes.
- Preparação inicial do streaming: 207,7 ms.
- 30 canais com sinal audível via analisadores após 12 segundos de playback.
- Seek para 700 s seguido de 2,5 s de playback; loop 701–703 s por 6 s; zero faltas de blocos nesta execução.
- Reabertura do projeto com as 30 tracks aprovada. Sem erro JS.
- Pré-buffer configurado: 46.080.000 bytes. JS heap reportado pelo Chrome: 3.656.656 bytes; esse contador não inclui toda a memória externa/áudio/processos do navegador.
- Não foi ouvido/reproduzido todo o projeto de 950 s; não houve stress prolongado em segundo plano.

### DSP, formatos e interface
- Worker/AudioWorklet real com Signalsmith: +2 semitons em 80/100/120%; frequências dentro de 1 Hz de 246,94165 Hz; seek e loop corretos, zero faltas de blocos. Fonte sintética curta, um canal de origem. web-stream-0.10.json.
- Opus/WebM capturado por MediaRecorder de sinal 220 Hz: decode nativo → cache OPFS → streaming; 220,00675 Hz detectados, zero faltas de blocos. Excluir projeto/cache também executado.
- Resampler: erro máximo 0,0000502 em senoide 440 Hz 44,1→48kHz; diferença entre blocos e leitura inteira zero; RMS residual 0,000788 de tom 30kHz em conversão 96→48kHz.
- npm test: 594 casos matemáticos de afinador, waveform, resampler, UI desktop/mobile, importação, seções, backup v2/exportação/restauração, mixer, menu, microfone simulado e DSP: aprovados.
- Android: 41 testes JVM, lint (0 erros / 29 avisos), build/export aprovados. Instalação `adb install -r` em emulador aprovada.

## Não validado
- Safari/iPad, celular físico, USB multicanal, tela bloqueada/longos períodos em background.
- 5 GB de arquivos comprimidos, um único WAV >4 GB, 30 tracks grandes com DSP ativo, backup/restore de 5 GB e reprodução prolongada. O teste de 5,47 GB usou 30 WAVs e velocidade original; testes de DSP usaram fontes pequenas.
- A quota real de disco do navegador continua sendo um limite e erros de quota são informados. Não se promete tamanho ilimitado.

## APK
Android 0.10.0, versionCode 12, assinatura debug preservada.
- dist/ATMyTrack-debug.apk
- dist/ATMyTrack-0.10.0-debug.apk
- 12.705.680 bytes
- SHA-256: 33e525feab44b90b6622b0f1a6d893917653e6f1e902196bc605125de192d3f4
