# ATMyTrack 0.25.0 (42) — vínculo entre ritmos

## Comportamento

- Metrônomo: **Vincular à percussão**. Percussão: **Vincular ao metrônomo**. Representam uma única preferência global, ativada por padrão quando ausente e preservada entre sessões.
- Vinculados, BPM e compasso propagam em ambos os sentidos. Acompanhar música também é compartilhado para que ambos usem o mesmo relógio. No modo acompanhado, seguem BPM efetivo/velocidade e transporte do projeto; no independente, continuam sem multitrack ou com música pausada/parada.
- Play/Pausar ou Parar em qualquer painel controla ambos os ritmos. Desmarcar libera os controles independentes. Volumes, timbres, acento e subdivisão do click permanecem próprios.
- Android: sincronização centralizada em RhythmLink/RhythmEngine, preferência persistente, BPM do projeto atualizado pelo editor de percussão quando necessário. Os dois contadores independentes são reiniciados juntos após preparo/alterações de tempo; ambos aguardam a batida estar preparada. O transporte iniciado pela UI espera o preparo do par acompanhado.
- Web: mesmo ajuste nos dois painéis, armazenamento local, preparação anterior ao Play e época comum no AudioContext. No modo acompanhado, o mesmo início do mixer é usado pela batida e click.
- Áudios importados, biblioteca acústica e projetos preservados. Sem início automático de reprodução ao restaurar a aplicação.

## Validação

- Android: build, lint, APK e compilação de testes instrumentados concluídos. **70 testes unitários aprovados**, zero falhas/erros. Novos testes cobrem padrão ativo, sincronização bidirecional, Play/Stop, modos acompanhado/independente, desvínculo, preservação de volumes, persistência da preferência e atualização de projeto.
- Web: suíte completa com testes musicais/DSP, PCM, waveform, routing, backups, PAD, locks, timeline, ritmos acústicos, CrossMusic e layouts. Teste específico do vínculo verifica BPM 126 → 93 a partir dos dois painéis, início/parada de ambos, desvínculo, persistência após recarregar e par acompanhado com projeto real WAV sintético. Dados em `rhythm-link-0.25.0.json`; screenshot em `rhythm-link-0.25.0.png`.
- Ambiente: Windows, Node 24.19, pnpm 11.25, Chrome instalado/Playwright, servidor Python localhost:4173. Sem certificação em hardware físico nesta entrega; testes instrumentados compilados, não executados em celular/tablet. Não há promessa de tempos no aparelho do usuário.
- APK: `dist/ATMyTrack-debug.apk`, **23921955 bytes**, `com.atmytrack.app`, **0.25.0 (42)**. SHA-256 `3843d7dd01a65a434a0e50ed383f97a7e12ad5e2339f40310fa01daae9515853`.
- Assinatura original verificada: certificado SHA-256 `1f9c8116100c1b13aded62b0c2711e3aa045b60c77bb40f57f5f1a71f77e9c39`.

Site: https://atmytrack-web.andmtu.chatgpt.site . Limitações de USB multicanal/segundo plano e armazenamento do navegador permanecem; projetos Android e web são separados.
