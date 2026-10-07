# Revisão de arquitetura — 0.4.0

## Importação

A base 0.3 já havia removido a cópia e decodificação integral obrigatórias da importação. Essa arquitetura foi mantida: metadata e referências persistentes, três preparações concorrentes, fallback de cópia codificada para origens sem permissão persistente/seek. A 0.4 verifica também a disponibilidade de decoder e publica o estado individual de cada arquivo. Uma falha não descarta os arquivos válidos; cancelamento continua propagando e remove apenas os temporários da nova importação.

Waveform, detecção de BPM e pitch não integram o caminho crítico. O primeiro é incremental e pausa quando a reprodução está ativa; os dois últimos são sob demanda. O relatório individual permanece disponível após o término. Arquivos de taxas diferentes recebem erro individual porque a engine não faz resampling.

## Relógio e codecs

Continua existindo um AudioTrack, um cursor em frames e um bloco comum de 512 frames. Mute, solo, ganhos, ordem, DCA e bus atuam sobre esse mesmo bloco. Stems comprimidas mantêm o decoder avançando mesmo quando silenciadas, evitando buscas frias ao desmutar.

O novo teste de arquivos de três minutos revelou limitações dos timestamps de busca da plataforma:

- ADTS: o extractor acumula `ceil(1024 × 1e6 / sampleRate)` por pacote. A correção converte o ordinal do pacote em tempo de amostra; metadata de duração recebe a mesma normalização. M4A não passa por essa transformação.
- M4A: voltar ao início reabre extractor/codec para conservar o pacote de priming que uma busca a zero pode omitir.
- MP3/Vorbis: o seek aproximado foi substituído por avanço de pacotes codificados desde o início até o preroll. Não decodifica nem copia o áudio precedente. A busca usa memória constante, mas seu custo cresce com a quantidade de pacotes e a latência da origem.
- Após o primeiro bloco de uma sequência, a contagem real de frames decodificados governa as posições, evitando acumular arredondamento de timestamps.

Isso não remove silêncio ou atraso de encoder já existente na origem. Todas as stems devem ter a mesma origem temporal; raw AAC não fornece metadata universal de priming que permita inferi-la com segurança.

## Recursos da reprodução

O caminho comprimido da engine usa dois buffers reutilizáveis de 16.384 frames estéreo float por stem, 256 KiB ao todo. Há no máximo uma tarefa pendente por leitor; um pool compartilhado de 2–4 threads limita a concorrência. Seek drena a tarefa anterior antes de reutilizar sua memória. Fechar o leitor espera o worker e libera o decoder inclusive após falha. Análises/pitch usam o leitor direto, sem antecipação redundante.

Buffers frios são preparados com a saída pausada antes de play/resume/seek/loop. Ganhos de canal/DCA são pré-calculados a cada alteração; arrays de pico e buffers de buses são reutilizados. Snapshots de nível são publicados a aproximadamente 30 Hz. A memória de áudio não cresce com a duração total da música.

Pitch usa Signalsmith Stretch via JNI, somente sob demanda, com blocos limitados, latência compensada, duração exata e cache em disco. Novos leitores são preparados fora do mixer; a troca ocorre no limite de um bloco comum e mantém AudioTrack, posição e fila de áudio. Leitores aposentados são fechados por executor separado. O teste de troca durante playback verifica continuidade da engine e que o contador de underruns não aumenta naquele intervalo; não equivale a uma avaliação perceptual de toda transição de pitch possível.

Loop/seek continuam compartilhados, sem contrato gapless. A transição do fim ao início pode drenar a saída. Não há relógios independentes capazes de acumular drift entre stems.

## UI e persistência

Fader usa curva de console, dB quantizado em 0,1, ganho linear `10^(dB/20)` e zero para silêncio. Meter só desenha dBFS e não modifica o fader. Double tap usa detector de toque separado do drag; ajuste fino também está disponível por campo numérico. O drag de reordenação começa somente no cabeçalho após long press. Nas bordas, o movimento não se inverte quando a lista reposiciona suas chaves.

ProjectStore conserva JSON retrocompatível e AtomicFile. Um bloqueio compartilhado entre instâncias impede que uma leitura de recuperação do AtomicFile interfira na publicação de outro writer. O writer usa canal conflated para evitar fila de gravações a cada pixel do gesto. A UI e a engine recebem a mesma instância de configuração. O teste de round-trip inclui origem, ordem, ganhos, pan/mute/solo, pitch, metrônomo, análise, markers, loops, DCA, bus e routing; há também stress de leitura/gravação por duas instâncias concorrentes.

## Referências investigadas

Arquivos originais do Android, sob Apache 2.0, consultados para explicar os comportamentos e guardados nesta pasta:

- [AACExtractor.cpp](https://android.googlesource.com/platform/frameworks/av/+/refs/heads/main/media/module/extractors/aac/AACExtractor.cpp)
- [MP3Extractor.cpp](https://android.googlesource.com/platform/frameworks/av/+/refs/heads/main/media/module/extractors/mp3/MP3Extractor.cpp)
- [OggExtractor.cpp](https://android.googlesource.com/platform/frameworks/av/+/refs/heads/main/media/module/extractors/ogg/OggExtractor.cpp)
