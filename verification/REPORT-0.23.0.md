# Validação 0.23.0 (39)

## Metrônomo

- Nova opção **Acompanhar música**, ativada por padrão na leitura de projetos anteriores. O modo acompanhado conserva o comportamento anterior, incluindo Play que inicia o transporte.
- Desativada, o click usa relógio e AudioTrack próprios: continua com a música pausada ou parada e não inicia o multitrack. A ferramenta também está disponível sem projeto.
- BPM, compasso, timbre, subdivisão, acento, volume e saída esquerda/direita existentes continuam disponíveis. Modo independente não é duplicado pela mixagem principal.

## Percussão

- Botão imediatamente depois do PAD na barra dos tablets e ao lado do PAD no menu do celular (quatro colunas).
- Dez padrões sintetizados originais: Pop/Rock, Rock 16, Balada, Disco, Funk, Samba, Bossa nova, Reggae, Shuffle e Afro/6-8.
- Modo independente com BPM 30–300, compasso e volume; não depende de projeto. Modo acompanhado usa BPM efetivo, compasso e relógio exato do mixer do projeto, antes do Master/limiter. Seek e loop usam a posição musical, sem um segundo relógio tentando acompanhar o transporte.
- Pausar/parar a música mantém os sons independentes. Percussão acompanhada permanece ativada após Stop para retomar no próximo Play. Pausar/Parar pela notificação e perda de foco de áudio interrompem os ritmos. Serviço de mídia e wake lock incluem a reprodução independente.
- Importação usa os decoders/resampling já existentes e guarda PCM próprio em `files/rhythm`, fora da limpeza de projetos. Biblioteca e configurações persistentes; reprodução nunca começa automaticamente ao restaurá-las. Arquivos originais e projetos não são modificados pela importação.
- Para um áudio importado, o usuário informa a quantidade de compassos (1, 2, 4, 8, 16, 32 ou 64). Signalsmith Stretch prepara a duração correspondente ao BPM, preservando a afinação. Preparação e leitura de arquivos ocorrem fora dos callbacks de reprodução; playback usa buffers em memória.

## Limites explícitos

Importação de loops entre 0,05 e 60 segundos; resultado ajustado até 120 segundos e fator de velocidade entre 0,125× e 8×. Combinações fora dos limites mostram orientação para ajustar BPM/compassos. O compasso original do arquivo precisa ser informado corretamente; não há promessa de detecção automática ou alinhamento de um arquivo cortado fora da batida. Formatos dependem dos decoders disponíveis no Android. Saída independente estéreo padrão do Android; percursão acompanhada passa pelo Master, sem criar canal/BUS próprio. Não houve certificação de interfaces USB nem teste em celular físico.

## Testes

- `tools/build-android.ps1`: build, lint e empacotamento aprovados. **65 testes JVM**, zero falhas. Incluem migração do novo flag com default compatível, persistência, dez sinais distintos/limitados, duração por BPM/compasso, continuidade entre blocos/seek e ausência de click duplicado.
- `Rhythm23Test`, celular: três testes aprovados. Saída AudioTrack do emulador continua escrevendo após Pause/Stop da música; alternância para modo acompanhado; equivalência entre blocos e ausência de mixagem em outro projeto; Stop/Play preserva a percussão acompanhada.
- Metrônomo em instância sem projeto: AudioTrack recebe amostras; restauração das configurações não inicia reprodução.
- Importação de WAV mono sintético, dois segundos a 44.100 Hz: normalizada para 96.000 frames a 48 kHz; biblioteca restaurada e projetos comparados antes/depois sem alterações. Loop ajustado de dois para três segundos mantém o tom de 220 Hz, conferido tanto no DSP quanto nos samples importados/restaurados.
- Cenário principal de `Rhythm23Test` também aprovado no tablet; PAD e Percussão acessíveis diretamente na mesma barra.
- `Timeline131Test`: aprovado com a nova integração de áudio, incluindo arraste durante reprodução sem seeks intermediários.
- `git diff --check` e inspeção visual das capturas: aprovados. Um alerta do app Mensagens do emulador interferiu nas capturas iniciais; esse app de sistema foi encerrado e as capturas refeitas sem o alerta.

Ambiente: Windows, JDK 17.0.20.1, SDK 36; AVD ATMyTrack_OriginalKey_API35, Android 15 Google APIs x86_64. Celular simulado 1080×1920 / 420 dpi; tablet simulado 1600×1000 / 160 dpi. Dimensões restauradas para celular. Testes de áudio usam sinais sintéticos e saídas do emulador, sem benchmark de importação nem medição de tempo em aparelho real.

## Capturas

- [Metrônomo no celular](metronome-0.23.0-phone.png) e [no tablet](metronome-0.23.0-tablet.png).
- [Percussão independente no celular](percussion-independent-0.23.0-phone.png) e [no tablet](percussion-independent-0.23.0-tablet.png).
- [Percussão acompanhada no celular](percussion-0.23.0-phone.png) e [no tablet](percussion-0.23.0-tablet.png).
- [PAD/Percussão no menu do celular](tools-rhythm-0.23.0-phone.png) e [na barra do tablet](tools-rhythm-0.23.0-tablet.png).

## APK

`com.atmytrack.app`, versão **0.23.0 (39)**, verificada com `aapt`. Assinatura original validada por `apksigner`: certificado SHA-256 `1f9c8116100c1b13aded62b0c2711e3aa045b60c77bb40f57f5f1a71f77e9c39`.

Arquivo `dist/ATMyTrack-debug.apk`, **13.518.307 bytes**. SHA-256: `768a55eb8d1c25e0782a1f6b0bc0b6b16862f0a28483a9e6f7050f8bc2e6e477`.

[Release v0.23.0](https://github.com/AndreSouzaTeles/ATMyTrack/releases/tag/v0.23.0).
