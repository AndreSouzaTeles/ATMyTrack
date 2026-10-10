# ATMyTrack 0.13.1 (19)

## Correções Android e Web

PAD: Android aumenta o nível antes de uma saturação suave aproximada por função racional, limitada a ±0,95, preservando o controle de volume e o fade. Teste dos 24 acordes, um segundo estéreo 48 kHz, volume máximo: ganho RMS mínimo de 2,8635 vezes (aproximadamente 9,14 dB) em relação à 0.13.0; pico máximo observado de 0,8776. Web dobra o ganho (6,02 dB) e usa um DynamicsCompressor próprio após o ganho, threshold −1 dB, ratio 20:1, ataque 2 ms/release 100 ms. PAD continua independente dos faders da música.

Timeline: durante o arraste há apenas prévia do cursor. O seek acontece ao soltar; cancelar não busca uma nova posição. Android usa um slot atômico que substitui o alvo pendente e não acumula histórico de seeks; aplica a mesma conversão de timeline/velocidade e respeita a seção de loop. Troca de projeto/STOP limpa o alvo pendente. Web captura o ponteiro, pinta no próximo requestAnimationFrame e também substitui seeks pendentes; mantém reprodução após o seek quando estava em PLAY. A música não faz scrub audível a cada movimento do dedo: continua na posição atual até a liberação.

## Testes realizados

- Android: build/lint aprovados e 49 testes JVM sem falhas. Testes incluem os 24 acordes, ganho RMS versus síntese anterior, headroom, silêncio e fades, persistência, e 20.000 atualizações substituídas por dois alvos na fila de comandos.
- API35 x86_64, emulador ATMyTrack_API35 no Windows: teste Compose com 3 tracks existentes de teste, 100 movimentos até 75% com áudio pausado. Não houve mudança da posição do áudio durante o arraste. Aplicação do alvo final medida em 29 ms após soltar (tolerância 1024 frames). Outro arraste durante PLAY manteve a reprodução e chegou ao alvo em 30%.
- Teste inicial de PLAY terminou naturalmente uma música curta antes da verificação; fixture foi corrigida para usar loop e começar do início. Repetição final aprovada; não se tratou de falha do produto.
- Reprodução de 19 leitores independentes do mesmo PCM sintético de 4 segundos em 80%, com e sem PAD: zero underruns/starvation em 4 segundos por cenário. Não equivale a 19 stems musicais diferentes nem a um show completo.
- Chrome headless Windows: 100 movimentos da timeline produziram apenas um seek final em 80%. Teste da fila com preparação simulada e 2.000 seeks produziu duas operações, preservando o último alvo e o estado de reprodução. Testes anteriores de browser/player/DSP, routing, locks, PCM, waveform e afinador passaram.
- PAD Web, C maior, volume máximo: RMS 0,2773, pico 0,7032 após limitador; volume zero retornou silêncio. Frequências/Play/Stop/PAD independente também passaram.
- APK instalado sobre a versão anterior usando `adb install -r`, sem apagar dados. Nenhuma alteração de schema, formato de projetos, identidade de pacote ou assinatura.

## Limites reais

Não houve teste em celular físico, audição crítica do timbre ou medição de seek com as multitracks musicais longas do usuário. O seek final ainda pode levar tempo para preparar a posição dos arquivos, mas não executa todo o histórico do arraste. O desempenho medido no emulador não é promessa para outro aparelho. Publicação pública do site não foi atualizada: código web e página local atualizados.

APK: `dist/ATMyTrack-debug.apk`; release: `ATMyTrack-0.13.1-debug.apk`.
SHA-256: `c5d78afa30e864a438dac89373ad9e511e7c67aee37651e0fd3e272a37f0f99f`.
