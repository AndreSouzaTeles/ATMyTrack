# ATMyTrack 0.24.0 (40) — app e site

Site: https://atmytrack-web.andmtu.chatgpt.site

## Comparação e implementação web

| Área do app | Resultado no site |
| --- | --- |
| Identidade e navegação | Grafite, ícone circular azul/ciano, marca Outfit na mesma linha e globo com nomes atuais, toque, foco e rotação |
| Tablet/celular | Ferramentas na barra superior do tablet; menu no celular; transporte compacto ao lado da timeline no tablet |
| Timeline | RMS de 2048 pontos recalculado nos projetos antigos, barras separadas e progresso colorido |
| Mixer | Tela unificada, PAN rotativo/centralização, cores manuais, locks vermelhos, MUTE/SOLO, Master fixo, faders de console −80/+10 dB, ajuste fino e medidores logarítmicos |
| DCA e BUS | Canais no mixer, controles de grupo e medição BUS no grafo de áudio |
| Projetos | Importar pasta/múltiplos arquivos, até 64 faixas, editor após importação, cores e ordem de faixas, reordenação persistente de cartões, proteção de troca durante Play |
| Tom/velocidade | Original/destino, FFT/chroma maior/menor, seleção de faixas e exclusão inicial de percussão/click; DSP preserva afinação |
| Busca | Normalização de acentos e filtro exato pelo tom efetivo maior/menor |
| Metrônomo | Acompanhar música ativado para projetos antigos; modo independente sem projeto, oito timbres equivalentes ao app, TAP, Smart Click, compasso 4/8, divisão e volume |
| Percussão | Dez padrões, modo acompanhado/independente, BPM/compasso/volume e loops importados persistentes com ajuste de tempo preservando pitch |
| CrossMusic | Ordem persistente, volume/mute por faixa, PAD por projeto, tom manual/efetivo, transição de 3 s e avanço no fim natural; encerrar mantém música/PAD atuais |
| Recursos existentes | PAD, afinador, routing, backups, leitura PCM por blocos, OPFS e cache DSP preservados |

Migração mantém projetos e áudios existentes; o antigo silêncio em −60 dB passa a −80 dB uma única vez. Backups web legados continuam legíveis. Loops importados da percussão ficam em biblioteca separada no navegador.

## Verificação

Ambiente: Windows, Node 24.19, pnpm 11.25, Chrome instalado em modo headless via Playwright; servidor Python localhost:4173. Viewports 390×844, 768×1000 e 1440×1000. Fixtures WAV sintéticas mono PCM16 48 kHz, sem áudios do usuário. Os resultados não estimam importação ou desempenho no celular/tablet do usuário.

- `pnpm test`: suíte aprovada — 594 casos musicais, FFT maior/menor/silêncio, migração/curva de fader, waveform RMS, PCM/resampling, navegador/DSP, routing/balance, cadeados/PAD, 2000 seeks coalescidos, timeline, globo/Pix e paridade de recursos.
- `tests/parity.cjs` repetido após ajustes finais: aprovado, sem pageerrors. Verifica click sem projeto, percussão independente mesmo após Stop, dez batidas, importação, layouts, cores, volume, locks, busca, DCA, CrossMusic e persistência de ordem.
- Loop importado: 2 s em 220 Hz ajustado para 3 s a 80 BPM (144000 frames/48 kHz), frequência medida 220,0004 Hz. É uma fixture sintética, não avaliação perceptual de todos os materiais musicais.
- Revisão visual por screenshots desktop, celular, menu e globo em `verification/web-parity-*-0.24.0.png`.
- Android: build e exportação concluídos; 65 testes unitários, zero falhas/erros. APK com pacote `com.atmytrack.app`, versionCode 40 e versionName 0.24.0. Nesta entrega, os recursos Android foram preservados; alteração Android de versão/rótulo de ajuda.
- Assinatura verificada com apksigner: certificado SHA-256 `1f9c8116100c1b13aded62b0c2711e3aa045b60c77bb40f57f5f1a71f77e9c39`.
- APK: `dist/ATMyTrack-debug.apk`, 13356223 bytes. SHA-256 `1446883f46394b6573755145f2af82bb140aa39fcfe8e186e12c3a2b527d345a`.

## Diferenças da plataforma

USB multicanal, serviço Android de áudio/notificação e execução com tela bloqueada não têm equivalência garantida no navegador. Use a página aberta com tela ativa. Seleção de dispositivo aparece somente nas plataformas que oferecem a API. Formatos, armazenamento e memória variam por navegador. Safari/iPad e hardware físico não foram certificados nesta entrega.

Projetos Android e web continuam separados e não sincronizam automaticamente. O site conserva a origem pública existente para manter o armazenamento dos usuários. Não há conta ou upload das músicas. Não foi feito pagamento ou envio de mensagens. Detecção automática de tom/BPM é uma estimativa e pode precisar de correção manual.
