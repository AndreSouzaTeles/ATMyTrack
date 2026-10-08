# ATMyTrack 0.12.0 (15)

## Implementado

Web: toolbar em uma linha rolável, Routing junto dos controles musicais, DCA/BUS independentes visualmente e em seus painéis. Faders verticais com cap retangular e marca azul, interação nativa acessível preservada. LOOP usa OFF/ON.

Android: novo painel Routing com seletor por track, BUS e click; presets Stereo split e BOTH ALL. BOTH ALL também na web, remove encaminhamento de tracks a BUS e envia diretamente ao Master estéreo, incluindo click nos dois lados; mantém definições dos grupos, gains, pans, mute e solo. BUS com destino físico direto no Android continua independente do Master.

Master: PAN real de balanço estéreo em ambas as engines; sem cruzamento L/R para preservar separação click/música. Android interpola entre blocos; web suaviza gains com AudioParams. Valor persistido por projeto, default centralizado em projetos antigos. Indicador OUT movido para rodapé do Master. A web continua usando o par estéreo disponibilizado pelo navegador.

## Testado

- Build Android, exportDebugApk e lint aprovados: zero erros, 29 avisos.
- 45 testes JVM aprovados, incluindo balanço do Master centro/extremos/transição/mute e persistência compatível com projetos antigos.
- Android 15/API35 x86_64: teste Compose de Routing, Stereo split, BOTH ALL, controle de PAN MASTER e persistência aprovado; regressão de loop e desligamento durante playback aprovada. APK instalado por cima da versão anterior via adb install -r.
- npm test: testes music/waveform/PCM e navegador Chrome aprovados. Fluxo de importação, mixer/reset, seção, loop, pitch +2 em 100/80/120%, backup/reabertura e afinador continuaram passando.
- Teste adicional Chrome: Routing split/BOTH ALL/click, DCA e BUS independentes, persistência de PAN após reload; uma linha nas larguras 390/768/1440 sem overflow da página.
- Áudio Web Audio OfflineAudioContext: L=.2/R=.4; no centro relação 1:2 preservada, extremo L zera R e extremo R zera L. O compressor existente aplica seu ganho próprio; comparação usa o centro como referência e confirma ausência de crossfeed. Dados em routing-master-0.12.json.
- Site local respondeu com WEB 0.12.0; capturas da UI desktop/mobile geradas.

## Limites

Não houve teste em aparelho físico, Safari/iPad, Firefox ou novo stress de 19/30 tracks. O servidor público não foi republicado: usuário solicitou página local. Routing de hardware adicional mantém suporte Android existente; web continua estéreo.

## APK

Caminho: dist/ATMyTrack-debug.apk (também ATMyTrack-0.12.0-debug.apk).
SHA256: 30e11fc672f6e22148b5ca01356651b9c782bdd477abcfc0df2f99885f8f774d
