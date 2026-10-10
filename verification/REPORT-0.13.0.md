# ATMyTrack 0.13.0 (18)

## Implementado em Android e Web

- Cadeado por fader e global no Master. Bloqueia arraste, edição numérica e reset 0dB; DCA/BUS também são protegidos. Desbloquear o Master preserva cadeados individuais. Não bloqueia PAN/MUTE/SOLO.
- PAD independente do transporte, disponível mesmo sem projeto, com os 12 tons e modos maior/menor (24 acordes), Play/Stop próprios e volume persistente. Síntese de nove vozes detunadas, harmônicos suaves, filtro e transições de ganho/frequência. Android usa tabela de onda interpolada para reduzir CPU; Web usa osciladores nativos Web Audio. Não é gravação/sample de instrumento acústico.
- PAD continua ao fechar seu painel ou parar/trocar a música. Para ao colocar o app/página em segundo plano e ao entrar no afinador. Não inicia automaticamente ao reabrir.
- Android: preparação de até duas tracks em paralelo e cópia de PCM em bloco, fora do callback de áudio. Web: blocos DSP de 2048 frames e gravação agrupada (~256 KiB). Algoritmo Signalsmith, janela/hop e pipeline musical preservados.
- Variantes DSP recentes persistem em cache. Orçamento de 2 GiB por plataforma; versões em uso são protegidas e podem exceder esse orçamento. Originais não são apagados; seleção, velocidade e pitch continuam independentes.
- Denner Mendes incluído no globo. Botão de comprovante abre https://wa.me/5511970317078 com mensagem e nome; usuário anexa e envia. Nenhuma mensagem é enviada automaticamente.

## Medições de preparação

Windows, emulador Android 15 API35 x86_64 ATMyTrack_API35 e Chrome headless. Fontes sintéticas; teste sequencial anterior primeiro, novo depois (novo beneficia-se do cache de leitura já aquecido). Valores de uma execução, não promessa para celular, arquivos longos ou músicas reais. Gradle/outros testes estavam ativos no host; não é benchmark isolado de laboratório.

| Plataforma | Fonte | Ajuste | Anterior | Novo | Cache recente |
|---|---|---|---:|---:|---:|
| Android | 4 WAVs, 10 s cada, estéreo PCM24, 48 kHz; 2.880.044 bytes cada | +2 semitons, 80% | 25.100 ms | 5.819 ms | 7 ms |
| Web | 4 fontes, 10 s cada, estéreo Float32, 48 kHz | +2 semitons, 80% | 63.597,5 ms | 26.780,6 ms | 2,9 ms |

Android compara PCM resultante com implementação anterior (tolerância 1e-5). Web confirma duração 12,5 s para fonte de 10 s em 80%; testes de browser verificam frequência após pitch e velocidade. Web permanece preparando tracks sequencialmente e mantém cancelamento existente. Mudanças inéditas ainda precisam renderizar o áudio completo; não são instantâneas.

## Validação

- Build, lint e 47 testes JVM aprovados, incluindo os 24 acordes, ausência de NaN/clipping, fade para silêncio e locks/persistência/defaults antigos.
- Android instrumentado: cadeados individuais/global, PAD real via AudioTrack, mudança para F# menor e continuidade após STOP da música. DSP em 50–200%, bypass, seleção independente, reset, duração, frequência e BPM/click aprovados.
- Upgrade instalado com `adb install -r`; 143 projetos antigos preservaram tracks, markers e volumes. Sem apagar dados nem mudar identidade do pacote/assinatura.
- Web: testes de notas/YIN, PCM/resampling, waveform, browser/player/DSP, routing/master/layout, locks e PAD. AudioContext real confirma áudio do PAD e silêncio após STOP, frequências das nove vozes de F# menor. Link de WhatsApp interceptado em teste, sem abrir/enviar conversa; JSON contém Denner Mendes.

- Teste final Android com 19 leitores independentes do mesmo cache sintético estéreo de 4 s, velocidade 80%, 4 s por cenário: sem PAD e com PAD, zero underruns e zero starvation. Não equivale a 19 stems musicais diferentes nem certifica um show completo.
- Execuções iniciais desse cenário encontraram 49 e depois 33 underruns. Houve otimização do PAD para tabela interpolada e ajuste de prioridade; a repetição final ocorreu após terminar as builds concorrentes do host. Não se atribui todo o ganho à otimização do PAD nem se esconde a influência do ambiente.

## Limites

Não houve teste em telefone físico, audição crítica com monitores nem benchmark de troca de tom em multitracks musicais de vários GB nesta entrega. A qualidade percebida do timbre deve ser avaliada pelo músico no seu sistema de áudio. O PAD não possui serviço próprio para tocar em background. Web depende de suporte ao armazenamento OPFS e às APIs de áudio do navegador; mantenha a página ativa. Publicação pública do site não foi atualizada: esta entrega atualiza o site local e o repositório.

APK: `dist/ATMyTrack-debug.apk`, também distribuído como `ATMyTrack-0.13.0-debug.apk` no release GitHub.

SHA-256 APK: `1beba8d2aaed74cf2c22bd6a0c6a0509e143e4fe49c9109598f31336766fc311`.
