# ATMyTrack 0.3.0 — entrega e verificação

Data: 06/10/2026. Atualização incremental da base 0.2.0, sem recriar o aplicativo.

## APK

- Entrega versionada: `D:\Projetos\ATMyTrack\dist\ATMyTrack-0.3.0-debug.apk`.
- Atalho da entrega atual: `D:\Projetos\ATMyTrack\dist\ATMyTrack-debug.apk`.
- Application ID `com.atmytrack.app`, versionCode 3, versionName 0.3.0, API mínima 26, target 36.
- ABIs arm64-v8a, armeabi-v7a e x86_64. Assinatura Android Debug preservada; instalação por atualização (`adb install -r`) aprovada.
- SHA-256: `D97D4BCE79C817AFF4E2905BA8D7ECEF33E8D7482E6C3EC06290F466EFBB504B`.
- Verificação de assinatura v2 aprovada em `apk-signature-0.3.txt`; metadata em `apk-metadata-0.3.txt`.

## Diagnóstico e mudança de arquitetura

Auditoria anterior às alterações: `ARCHITECTURE-0.3.md`. A 0.2.0 ainda exigia cópia de todos os arquivos e decodificação integral dos comprimidos, sequencialmente, antes de liberar o projeto. Waveform/BPM/pitch não participavam daquele caminho.

A 0.3.0 usa URI persistente quando a origem permite seek, com até três preparações concorrentes de metadata. Não faz cópia nem decodificação integral nesse caminho. Provedores sem acesso persistente/seek recebem cópia do arquivo codificado. PCM é lido por blocos; comprimidos são decodificados sob demanda com timestamps e preroll no seek, sob um único cursor de mixagem/AudioTrack.

Waveform de uma stem de referência é produzido progressivamente depois, pausando o trabalho enquanto há playback, e salvo em cache. Duração/metadata são persistidas. Fingerprint de tamanho/data/início/fim invalida waveform, análise e pitch quando a origem muda. Smart Click só analisa sob comando. Pitch só processa as tracks selecionadas ao aplicar e publica o cache completo ao terminar.

## Recursos entregues

- Tom original/destino com sustenidos/bemóis; −12…+12 semitons, seleção de tracks, todas/limpar e reset. Signalsmith Stretch/Linear MIT via JNI, latência compensada e mesma quantidade de frames.
- PAGE 1/2 compartilhando engine, timeline e ordem. Transporte acima dos faders. Metrônomo reúne TAP, click, sete timbres, acento, compassos e multiplicadores.
- Smart Click: referência por nome, análise de transientes/autocorrelação, BPM decimal/arredondado, confiança heurística e aplicação explícita.
- DCA relativo sem sobrescrever faders; buses com soma, meter, ganho, mute, membros/destino, edição e exclusão. Stereo split e LEFT/RIGHT/BOTH por track.
- Detecção de capacidades USB anunciadas, abertura por channel index mask e destinos adicionais apenas quando disponíveis. Desconexão interrompe a saída; atualização/revisão é explícita.
- Exclusão confirmada pelo − do card e card + após o último projeto. Long press no cabeçalho para reordenar, com feedback e rolagem nas bordas; fader conserva seu gesto de volume.
- Curva de console e escala de −∞ a +10 dB em tracks, master, buses e DCAs. Ícone T turquesa/onda roxa preservado.

## Testes

Ambiente: Windows, JDK 17, SDK 36, NDK 28.2.13676358, CMake 3.22.1; emulador Android 15/API 35 x86_64, sem interface USB física.

- **21 testes JVM aprovados**: PCM/WAV, compatibilidade/persistência, mixagem, DCA, routing, curva de fader, notas equivalentes, timbres/acento, detecção de BPM e rejeição de silêncio.
- **11 testes Android aprovados** em 28,82 s: importação, origem não navegável/tamanho desconhecido, erros/rollback, reprodução/pause/seek, troca de página durante playback, criação de DCA/bus pela interface, long press/arraste e ordem persistida, DSP nativo, streaming AAC, cache e 12 stems simultâneas.
- Pitch: seno de 220 Hz passou a 261 cruzamentos positivos/s com +3 semitons (esperado 261,63 Hz), mantendo **192.000 frames**; track não selecionada permaneceu igual. Extremos −12 e +12 também passaram nas verificações de frequência e duração. Reset removeu o cache aplicado.
- AAC: seeks para frente/trás/início/final comparados ao decoding sequencial. Benchmark confirmou os mesmos frames e amostras do decoder integral nas posições inicial, intermediária e final. Corrigido arredondamento de microssegundos para frames que removia uma amostra do final.
- 12 AACs de 8 s/48 kHz/estéreo: **43 ms** para metadata, **0 bytes de cópias de áudio**, **0 underruns** nessa execução. Meters iguais entre stems idênticas; pause, seek e reordenação durante playback aprovados.
- Lint: **0 erros, 19 avisos e 1 sugestão** (versões disponíveis, KTX, metadados de backup/ícone, espaço de armazenamento). Build aprovado para as três ABIs.
- Revisão visual em 1920×1200/240 dpi e 1080×1920/420 dpi; navegação, controles, escala dB e master fixo. Faders de tracks/master receberam rodapé de mesma altura para alinhar escalas. Último ajuste foi visual, seguido de rebuild e instalação.

Logs: `final-build-0.3.log`, `export-0.3.log`, `android-tests-0.3.txt`, `pitch-test-0.3.txt`, `twelve-stems-0.3.txt`, `import-benchmark-0.3.txt`.

## Benchmark de importação

Fixtures locais geradas pelo teste; geração excluída do tempo. Duas passadas com ordem invertida. **Baseline é 0.1.0** (decoder integral mantido somente no APK de teste), não 0.2.0. Mede importação de metadata por file URI no emulador, sem latência de nuvem/seletor Android; não inclui abertura da saída nem waveform posterior.

| Fixture | Origem | 0.1.0 | 0.3.0 | Áudio copiado pela 0.3.0 |
|---|---:|---:|---:|---:|
| WAV mono16, 5 min | 28,8 MB | 1.684–1.690 ms | 1 ms | 0 |
| WAV stereo24, 3 min | 51,8 MB | 1.398–1.430 ms | 1 ms | 0 |
| AAC estéreo, 1 min | 1,45 MB | 2.261–2.346 ms | 3–10 ms | 0 |

WAV mono e AAC tiveram amostras idênticas nas posições testadas. WAV 24-bit apresentou diferença máxima 0,0000304 frente à conversão legada, dentro de 2 LSB de 16-bit; o leitor novo conserva a precisão original. Arquivos comprimidos deixaram de ocupar PCM expandido na importação.

## Limitações e validação pendente no hardware

- Tempos de emulador não preveem o desempenho no aparelho do usuário. Nuvem, permissões e provedores podem exigir download/cópia.
- USB multicanal foi implementado contra as capacidades da plataforma, mas não validado em interface física. Decoders/limites de stems dependem do aparelho. WAV e AAC receberam testes instrumentados; outros formatos dependem do suporte MediaCodec/MediaExtractor do dispositivo.
- Todas as stems precisam da mesma taxa. Não há resampling automático ou garantia gapless de loop/seek. Padding/atraso gravado pelo encoder da origem pode afetar alinhamento musical de arquivos com perdas.
- Pitch usa espaço em disco e processamento sob demanda; a troca dos leitores pode provocar breve interrupção quando aplicada durante playback, sem mudar a posição comum.
- Smart Click usa confiança heurística, não probabilidade calibrada; material ambíguo pode exigir metade/dobro ou ajuste manual. O downbeat do metrônomo continua em 00:00.
- Fingerprint não é hash integral: alterações no meio de um arquivo com tamanho/data falsamente inalterados pelo provedor podem escapar da detecção. Fontes externas precisam continuar acessíveis.
