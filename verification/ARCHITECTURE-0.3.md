# Auditoria anterior às alterações 0.3

Base analisada: 0.2.0. Kotlin/Compose, AndroidViewModel, persistência JSON AtomicFile; engine única no Application, serviço foreground. Mix por frames de 512 amostras em um único AudioTrack estéreo. Readers atuais abrem caches locais PCM/WAV. Os controles alteram snapshots de Project numa fila da engine.

Importação atual: serial, fora da UI, cópia obrigatória via stream de 1 MiB. WAV PCM preservado; outros formatos decodificados integralmente por MediaCodec para cache PCM. Não há resampling, waveform, BPM, pitch ou DSP na importação. Nenhum WAV é carregado inteiro em RAM. O custo remanescente é cópia obrigatória e decodificação total, multiplicado pela fila serial. Não existe análise/cache de waveform ou beat grid.

Plano incremental: manter engine/transportes/repositório e arquivos antigos; acrescentar fontes por URI e leitores sob demanda, importação com concorrência limitada, análise desacoplada e cache identificado por origem. Reordenação passa a mapear readers por ID, sem reiniciar o áudio. Estender mix com DCA/bus/split, sem simular portas físicas. Pitch explícito processado em background com compensação de latência, nunca no caminho de importação.

O texto do pedido termina na seção 29 após “-40”; adotada curva segmentada de console, com mais curso entre -10 e +10 dB e mute no extremo inferior.
