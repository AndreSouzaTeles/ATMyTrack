# ATMyTrack 0.7.0 — relatório de entrega

## Implementação

**Velocidade:** 50–200%, passos de 1%, presets imediatos, seleção independente de TOM e todas as stems inicialmente selecionadas. Seleção parcial exige confirmação de possível dessincronização. Configuração, seleção e último preset são salvos por projeto. Reset de um processamento preserva o outro e não altera fader/pan/mute/solo/DCA/BUS/routing.

Signalsmith Stretch (MIT, biblioteca já incluída) prepara pitch e tempo juntos, usando a origem, sem encadear duas transformações. Configuração de janela 120 ms/intervalo 10 ms. A proporção entre amostras de entrada/saída altera a duração; a transposição tem parâmetro independente. Compensação de latência, contagem acumulada de frames e tamanho final exato. Em 100%/0 semitons não se cria transformação. A saída de áudio continua recebendo PCM pelo ring buffer existente.

Durante a preparação, o áudio anterior pode continuar tocando. Ao terminar, a troca preserva a posição musical e reabastece a saída, com entrada suavizada de 256 frames. Não é automação instantânea: existe preparação e pode haver uma breve interrupção na troca. Falhas preservam o estado anterior e permitem nova tentativa/reset, inclusive com sessão ainda não pronta.

Timeline e markers salvos permanecem em frames originais. A engine converte posição, duração, markers, loop, seek, beat offset e BPM para o tempo de reprodução. Smart Click mantém BPM original. A subdivisão 0.5x/1x/2x continua independente da velocidade. No modo parcial, o click/timeline usam a referência original e a duração cobre a última track; as stems podem perder sincronização.

Cache: publicação por arquivo temporário, descarte de arquivos incompletos/cancelados, remoção de transformações obsoletas e orçamento de 2 GiB por LRU. O projeto ativo é protegido e pode exceder o orçamento. Caches inativos removidos são regenerados ao reabrir, sem apagar originais.

**Afinador:** AudioRecord real, sem monitoramento/efeito de saída, análise local YIN com confiança, rejeição de sinal abaixo de RMS 0,002, mediana e suavização logarítmica. Captura mono a 48 kHz, análise a 24 kHz, janela 4096 (170,7 ms), salto 768 (32 ms). A latência acústica completa não foi medida. A tolerância de afinado é ±3 cents, em azul.

12 instrumentos + Cromático: violão, guitarra, baixos 4/5/6, ukulele, violino, viola de arco, violoncelo, contrabaixo acústico, cavaquinho e bandolim. Standard, Drop D, D Standard, Drop C e Half Step Down para guitarra/violão. Corda AUTO/manual. Frequências calculadas em temperamento igual, A4 ajustável 400–480 Hz, padrão/reset 440 Hz. Preferências persistem globalmente.

Permissão pedida apenas na ferramenta, com explicação e botão de configurações após bloqueio. O player é pausado ao entrar, sem retomada automática. Captura única serializada e cancelada diretamente pelo lifecycle em segundo plano/pausa da tela. Nenhum áudio é salvo ou enviado. A entrada é a roteada realmente pelo Android; erros de remoção/entrada indisponível liberam recursos e oferecem nova tentativa.

**Menu:** hamburger inequívoco, separado da busca. Ordem: Projetos → Afinador → Plano / Pagamento → Central de dúvidas. Projetos retorna à biblioteca existente. Drawer modal e telas com retorno, sem recriar a engine. Logo, QR Code e funcionalidades de pagamento preservados. TOM e VELOCIDADE formam um grupo que permanece junto, inclusive em celular.

## Testado

- Clean + build das três ABIs + **40 testes JVM**, sem falhas. [Resumo](unit-tests-0.7.txt).
- **29 testes Android** de regressão/novos recursos: importação e providers, decoders, cache/falhas, pitch, mixer/DCA/BUS/routing, pan/mute/solo/faders, drag, páginas, biblioteca, seções/loops, seek, Smart Click, artwork, pagamento, menu, velocidade, afinador matemático e performance. [Execução](regressions-0.7.txt).
- Dois casos adicionais de microfone: negação/bloqueio permanente e fluxo de permissão/captura/lifecycle, totalizando **31 casos Android distintos**. Repetição em formato de celular incluiu background/retorno, bloquear/desbloquear a tela, reabertura repetida e persistência de instrumento/corda. [Negação](mic-denial-0.7.txt), [celular](phone-0.7.txt).
- Renderização de áudio 50/75/80/90/100/110/120/125/150/200%, isolada e com +2 semitons. Duração final exata; pitch medido em amostras PCM, não apenas pela UI. Para entrada de 220 Hz, velocidade isolada manteve aproximadamente 220,01 Hz; +2 semitons ficou a menos de 0,65 Hz do alvo nas condições do ensaio. Seleções e resets independentes e bypass verificados. [Dados](speed-dsp-0.7.txt).
- Click WAV realmente importado e processado a 80/120%: desvios do pico em relação à grade temporal de até 0,5 ms no sinal sintético usado; duas stems idênticas permaneceram alinhadas. [Execução](imported-click-0.7.txt), [dados](click-speed-0.7.txt).
- Click interno 72 BPM convertido para 36/54/72/90/108 BPM, sem alterar a subdivisão; posições de pulso verificadas. Loops 100/80/120%, seek 25/50/75% e transições durante play 100→90→80→110→100 aprovados.
- Afinador: A4, E2, A2, D3, G3, B3, E4, E1 e B0, desafinações positivas/negativas, harmônicos, silêncio, sinal fraco e ruído. Erro inferior a 1 cent nos sinais testados; no ensaio Android com 27 sinais senoidais, erro máximo aproximado de 0,29 cent. As 27 janelas consumiram cerca de 187 ms de parede/188 ms de CPU naquela rodada. [Dados](tuner-accuracy-0.7.txt). O AudioRecord foi aberto e leu a entrada virtual real do emulador, sem notas mockadas; não houve instrumento acústico físico neste teste.
- Layouts de tablet e celular, portrait/landscape. O indicador verde de privacidade eventualmente presente nas capturas pertence ao Android; os novos controles do aplicativo usam azul.
- Upgrade real: 0.7.0 instalada com `adb install -r` sobre 0.6.1; os **128 projetos** do ambiente mantiveram seu JSON, incluindo stems/faders/markers/configurações. Defaults antigos 100%/todas e round-trip de projetos com pitch/velocidades diferentes verificados nos testes. Persistência continua aditiva em JSON/AtomicFile, sem banco destrutivo. [Registro](upgrade-0.7.0.txt).

## Performance medida

Windows, Intel Core i5-14400F (10 núcleos/16 threads), emulador Android 15/API 35 `sdk_gphone64_x86_64`. Revisão visual em 1920×1200 e perfil de celular 1080×2400/densidade 420. Resultados não são uma promessa para aparelhos físicos.

25 combinações: 2/4/8/12/19 tracks em 80/90/100/110/120%. Cada janela de playback durou 6 segundos, com loop, usando WAV mono PCM16 a 48 kHz, 8 segundos, sinal de 220 Hz, leitores/cache independentes. **Zero underruns e zero starvation nas 25 combinações.** CPU abaixo é consumo médio do processo (100% = um núcleo), não CPU total da máquina. PSS inclui o processo aquecido pela suíte.

| Velocidade, 19 tracks | CPU média | PSS | Underruns |
|---|---:|---:|---:|
| 100% | 14.1% | 121.7 MiB | 0 |
| 80% | 24.1% | 122.2 MiB | 0 |
| 120% | 23.2% | 122.4 MiB | 0 |
| 90% | 23.8% | 122.4 MiB | 0 |
| 110% | 23.4% | 122.4 MiB | 0 |

[Perfil completo, incluindo 2/4/8/12 tracks](speed-profile-0.7.txt).

Ensaio adicional: 19 stems musicais sintéticas **distintas**, primeiros 20 segundos da sessão de fixtures 0.5, WAV16/24, MP3/M4A/AAC/FLAC, mono/stereo, taxas 44,1/48/96 kHz. Preparação e aplicação de 80%, depois 120%, **com o player já tocando** e +2 semitons em 18 stems. Cada etapa incluiu 15 segundos adicionais com fader, pan, mute/solo e seeks. Preparação observada: 22,038 s e 15,555 s (cache de decodificação já aquecido); playback: cerca de 25,3% de um núcleo, PSS aproximadamente 128–129 MiB. Zero underruns/starvation. [Registro](musical-0.7.txt).

O ring de saída permanece em 8×512 frames (85,3 ms a 48 kHz), além da reserva de AudioTrack. Não foi medida latência de loopback físico. Contadores de underrun/starvation e continuidade/posições foram examinados; não se certifica ausência de todo artefato audível apenas por esses contadores.

## Limites reais

- Validação de runtime feita em emulador x86_64. ABIs ARM foram compiladas; celulares/tablets físicos, headset e hot plug USB não foram ensaiados.
- Pitch detection foi aferida com sinais digitais conhecidos. Precisão acústica depende do microfone, instrumento e ruído; instrumentos reais e calibração do relógio de entrada ainda precisam de validação física.
- Time-stretch usa preparação/cache, requer espaço e não muda instantaneamente. Os extremos podem ter mais artefatos; não houve avaliação subjetiva de gravações reais de voz/piano/guitarra nesta entrega.
- Seleção parcial deliberadamente pode perder alinhamento. A timeline/click nesse modo seguem referência original, conforme aviso do painel.
- Os ensaios têm as durações descritas; não equivalem a uma apresentação inteira nem determinam o limite máximo de hardware além de 19 tracks com velocidade.

## Build e APK

Versão **0.7.0**, código **9**, Android 8.0/API 26+, target 36. Mesmo certificado de desenvolvimento; APK v2 verificado. [Metadados](apk-metadata-0.7.0.txt), [assinatura](apk-signature-0.7.0.txt).

- Local: `D:/Projetos/ATMyTrack/dist/ATMyTrack-debug.apk`.
- Distribuição: `ATMyTrack-0.7.0-debug.apk`.
- SHA-256: `967a82ef13040aa3b7b79c75d9edccef99b668647da5ab6602e9c8bdebfa9711`.
- Instalar sobre a versão anterior, sem desinstalar.

## Referências técnicas

[Signalsmith Stretch, documentação incluída](../app/src/main/cpp/vendor/stretch/README.md) descreve buffers de entrada/saída de comprimentos diferentes, latência e transposição independente. [YIN, artigo original](https://pubmed.ncbi.nlm.nih.gov/12002874/) fundamenta o detector. [AudioRecord, documentação Android](https://developer.android.com/reference/android/media/AudioRecord) fundamenta captura/leitura/liberação de recursos.
