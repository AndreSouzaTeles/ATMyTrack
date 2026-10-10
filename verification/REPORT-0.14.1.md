# ATMyTrack 0.14.1 (21) — mudanças consecutivas de tom

## Correção

A seleção de uma tonalidade ainda não preparada exigia renderização integral. Um tom já preparado pode ser um cache hit, portanto uma primeira troca rápida não garantia que a seguinte seria rápida. Mantida a preparação integral para estabilidade do multitrack, mas reduzido o custo de cada análise DSP, inclusive em trocas sem cache.

Signalsmith Stretch continua com janela de 120 ms. Nas alterações comuns, intervalo de análise de 15 ms (antes 10 ms). Para velocidade abaixo de 70%, acima de 150% ou transposição acima de 7 semitons em magnitude, mantém 10 ms. Bypass, cache anterior, seleção de tracks, volumes, clock e pipeline de playback preservados. A fonte de toda troca é o áudio original, mesmo quando a track já possui um pitchFile de outra tonalidade.

Perfis de 30 e 20 ms foram avaliados e descartados após ultrapassarem a tolerância de frequência dos testes; não fazem parte do APK entregue. Perfil final adaptativo passou na tolerância existente, que não foi ampliada.

## Trocas consecutivas medidas

| Ambiente | Perfil anterior | Novo perfil adaptativo |
|---|---|---|
| Emulador API 35 x86_64, 4 núcleos; 8 leitores independentes do mesmo WAV sintético estéreo PCM24/48kHz/20s; transposição +5, depois +2 | 6.119 ms / 5.738 ms, 4 slots | 2.500 ms / 2.510 ms; terceira troca inédita +3: 2.756 ms |
| Chrome headless Windows, 16 processadores lógicos; 4 fontes independentes dos mesmos sinais estéreo float PCM/48kHz/20s, 2 workers | 4.580 ms / 8.910 ms | 2.227 ms / 2.656 ms; terceira troca +3: 2.259 ms |

Android: aproximadamente 59%/56% menos tempo nas duas trocas comparadas. Novas trocas foram medidas com playback ativo; benchmark antigo Android sem playback. Commit da troca: 40 / 20 / 12 ms incluindo a pequena análise de frequência do teste. Retorno ao primeiro tom cacheado: 4 ms no renderer Android, 61 ms incluindo início do playback na web.

A transposição +5 / +2 corresponde à escolha D → G → E mantendo D como origem. As fontes de teste são sinais sintéticos de 220 Hz no canal esquerdo e 330 Hz no direito; não constituem uma música real em D. Cada alteração foi conferida contra o sinal original, evitando transposição acumulada. Dados brutos: `audio-0.14.1.txt`, `web-repeat-0.14.1.json`.

## Validação

- 51 testes JVM, zero falhas/erros.
- Build Android, lint, APK principal e APK instrumentado: sucesso.
- Quatro testes Android instrumentados: sequência de três tonalidades inéditas com 8 canais e PLAY ativo; combinação de tom/velocidade entre 50% e 200%, seleções/reset/bypass e BPM; alinhamento de click importado; playback de 19 leitores da mesma fonte sintética de 4s com/sem PAD. Nenhum underrun registrado nas sequências de 8 canais nem no teste curto de 19 canais.
- Teste de frequência Android utilizou tolerância original de 0,65 Hz e também verificou duração e offsets temporais. `android-frequency-0.14.1.txt` contém os valores.
- Chrome: suite `npm test` passou; sequência de mudanças de pitch e reutilização do cache passou; frequência/duração em 20 combinações (+2/+5 semitons × 50/75/80/90/100/110/120/125/150/200%) passou. Erro máximo observado no teste Web de frequência: 0.6108 Hz, abaixo de 0,65 Hz. Dados em `web-quality-0.14.1.json`.
- APK atualizado sobre 0.14.0 no emulador com `adb install -r`; package versionCode 21/versionName 0.14.1. Nenhuma alteração de schema ou remoção de dados.
- A primeira rodada Android com o perfil experimental de 30 ms falhou na combinação 50%/+2; o resultado foi preservado em `android-0.14.1.txt`. Resultado final válido: `android-final-0.14.1.txt`, OK (4 tests).

## Entrega

Site publicado: https://atmytrack-web.andmtu.chatgpt.site

Site local: http://127.0.0.1:4173/?v=0.14.1

APK: `dist/ATMyTrack-debug.apk` e `dist/ATMyTrack-0.14.1-debug.apk`, 13.143.607 bytes. SHA256: `56787d154096986d160d7a3fa8ae44b2874fc68daafc41ffd47f94e7a2a9fcb5`.

## Limitações

Não testado no celular físico do usuário, nem com sua música ou arquivos de 5GB. Tempos dependem de duração, quantidade de stems, armazenamento e temperatura. Primeira preparação de cada tom novo ainda processa arquivos completos; não é processamento instantâneo. Cache pode ser removido pelo limite de armazenamento. Não foi feita avaliação auditiva subjetiva de vocals/piano/guitarra reais; testes objetivos de frequência, duração, sincronização e underruns passaram. Qualidade e estabilidade foram priorizadas ao rejeitar os perfis mais agressivos.
