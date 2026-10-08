# ATMyTrack 0.11.0 — Android

## Alterações

- LOOP mostra OFF/ON; logo e restante da identidade preservados.
- Acumula 512 picos por stem na passagem já necessária de cópia SAF/decodificação. Aberturas seguintes reutilizam envelopes. Fontes locais/caches antigos sem envelope são analisados localmente em blocos, inclusive durante PLAY, com cessão periódica de CPU. Não há mais espera por STOP nem uma segunda decodificação integral para waveform. Cada stem completo atualiza a timeline; silêncio e os dois canais são considerados.
- Nome visível da pasta via DocumentsContract; arquivos avulsos usam pai exposto pelo ExternalStorageProvider, MediaStore ou file URI. Provedores opacos que não informam o pai usam o primeiro nome de arquivo como alternativa.
- Imagens da pasta são embaralhadas; utiliza a primeira decodificável pelo Android, redimensionada e copiada para artwork interno. PNG/WebP testados; outros codecs dependem da versão Android. Não há decoder universal de SVG/TIFF/RAW.
- Tonalidade: FFT/Hann, chroma espectral e correlação Pearson com perfis Krumhansl–Kessler (https://extras.humdrum.org/man/keycor/). Analisa até 24 janelas de até três stems harmônicas, excluindo click/guia/percussão reconhecidos pelo nome. Análise offline, fora da main thread, com threshold de evidência; resultado editável e persistido. Não sobrescreve escolha manual durante análise nem tom já preenchido.
- Campo JSON opcional keyAnalyzed, default false. Sem migration destrutiva. Cache de envelope é derivado; falha ao gravá-lo não invalida o áudio preparado.

## Validação executada

Ambiente: Windows, emulador ATMyTrack_API35 Android 15/API35 x86_64. APK 0.11.0/code14 com mesma assinatura debug, arm64-v8a/armeabi-v7a/x86_64.

- Clean executado. Build, exportDebugApk e lintDebug aprovados (lint: zero erros, 29 avisos).
- 44 testes JVM aprovados. Novos casos cobrem PCM24 estéreo em blocos desalinhados, cabeçalho/trailer/silêncio, todas as 24 tonalidades maiores/menores em progressões sintéticas, rejeição de silêncio/ruído/nota única. Persistência verifica default de projeto antigo e tom analisado salvo, preservando mixer e demais campos.
- 3 testes instrumentados aprovados: metadados/capa/waveform; waveform de ambas as tracks/canais com silêncio real; loop de seção e desligamento durante playback.
- Fixture de pasta com ID opaco e nome Clamo Jesus - Baruk, um WAV e duas imagens (PNG/WebP). Nome visível recuperado, ambas listadas e capa escolhida/decodificada. Nome de pasta para seleção múltipla file URI também confirmado.
- WAV sintético estéreo 48 kHz/24 bits/120 s, 34.560.044 bytes: preparação com envelope 814 ms e 829 ms em duas execuções; waveform a partir do envelope 9 ms e 10 ms. Callback de disponibilidade falso (equivalente a PLAY ativo) não bloqueou a análise. São tempos do emulador e deste arquivo, não do celular do usuário, nem benchmark de 19 tracks.
- Instalação via adb install -r sobre 0.10.1: 143 projetos existentes presentes, todos os valores anteriores do JSON preservados na comparação após upgrade. Novos campos são aditivos. Não foi feita reinstalação limpa.
- Revisão visual portrait do LOOP ON e OFF e waveform no emulador.

## Limitações reais

- Tonalidade validada com sinais/progressões sintéticos, ainda não com o repertório real do usuário. Ambiguidade harmônica, modulações ou stems sem conteúdo harmônico podem produzir estimativa incorreta ou campo não preenchido; edição manual permanece disponível.
- O primeiro preparo de arquivos grandes ainda envolve I/O e, para comprimidos, decodificação. Nenhuma promessa de tempo em aparelho físico; não houve novo stress de 19 tracks nesta versão.
- Android nem sempre expõe a pasta de arquivos avulsos. Selecionar a pasta diretamente fornece seu nome e permite procurar capas.
- Formatos de imagem dependem dos decoders Android; candidatos incompatíveis são ignorados, tentando os demais.
- Esta entrega modifica o Android. Web/site não alterados.

## APK

- dist/ATMyTrack-debug.apk
- dist/ATMyTrack-0.11.0-debug.apk
- SHA256: b09b75f8da55c30371c91bae7abcc2712778a54c2156cd244424885e1b07743d
