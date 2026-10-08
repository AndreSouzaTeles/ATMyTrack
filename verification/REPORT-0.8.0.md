# ATMyTrack 0.8.0 — implementação e verificação

## Android

- Versão 10 / 0.8.0, mesma assinatura de desenvolvimento. APK em `dist/ATMyTrack-debug.apk`; instalado com `adb install -r` no emulador existente. Nenhuma alteração no schema, armazenamento dos projetos ou engine de playback.
- Causa do afinador: a UI usava a nota da corda-alvo como nota medida. Agora `tunerDisplay` devolve separadamente nota cromática, cents da nota, corda-alvo e desvio até o alvo. Exemplo: E4 manual recebendo F#4 mostra **F#4**, alvo E4 e +200 cents para o alvo; não mostra G. AUTO continua sendo uma sugestão de corda, e MANUAL permite fixar a corda quando estiver muito desafinada.
- YIN mantém confiança, RMS e smoothing; interpolação usa a diferença original, reduzindo viés do denominador acumulado nas notas mais agudas.
- Menu: arte vigente acima do nome, Projetos/Afinador em área superior rolável, DEIXE SUA MARCA e Central de dúvidas fixos no rodapé. Ajuda e pagamento existentes preservados.

### Testes Android

Ambiente: Windows, emulador ATMyTrack_API35 x86_64. Build Gradle: `:app:testDebugUnitTest :app:lintDebug :app:exportDebugApk :app:assembleDebugAndroidTest` passou. **41 testes unitários, zero falhas**. Lint: zero erros, 29 avisos. APK inclui arm64-v8a/armeabi-v7a/x86_64.

Regressão adicionada: 66 notas B0–E6 × offsets −35/0/+35 cents × AUTO/duas cordas manuais. Assert explícito de F#4 independente do alvo E4. Casos anteriores de harmônicos, graves, silêncio e ruído passaram.

Quatro cenários instrumentados passaram em execuções isoladas: negar/negação permanente, conceder microfone/lifecycle/preferências/menu, precisão YIN Android e seleção de velocidade/menu durante playback. A suíte sem reset inicial de permissões falhou por estado anterior do emulador; os cenários foram repetidos com flags apropriadas. Uma execução concorrente com stress web apresentou 22 underruns no emulador; repetida isoladamente passou com zero. Não se extrapola estabilidade para hardware físico. A engine Android não foi modificada nesta entrega.

## Web

Publicado em [https://atmytrack-web.andmtu.chatgpt.site](https://atmytrack-web.andmtu.chatgpt.site), acesso público. Deploy confirmado pela plataforma em 07/10/2026.

HTML/CSS/JavaScript modular em `web/dist`, sem backend de projetos. Usuário autorizou armazenamento no navegador quando não houvesse garantia de nuvem sem custo. IndexedDB mantém projetos/arquivos; backups `.atmytrack` permitem transferir a biblioteca por projeto. Não transfere automaticamente projetos Android.

Implementado: projetos/capas/busca; importação de áudios; transporte comum; faders/0dB/pan/mute/solo/master; drag do canal; DCA/BUS; routing estéreo/L/R; tom e velocidade independentes; metrônomo, TAP/Smart Click; seções/loop/seek; afinador local com instrumentos/A4; globo/Pix/ajuda e menu azul. Sem mock de áudio ou notas.

DSP: Signalsmith WASM em Worker prepara PCM por track, compensando latência e duração. Bypass em 100%/0 semitons. Uma variante por track em cache, descartada ao trocar projeto ou configuração. Playback usa AudioBufferSourceNodes com início comum; click sintetizado em AudioWorklet usa o mesmo clock e limites de loop. Pausa/prepara/retoma ao alterar processamento. Afinador YIN/smoothing em Worker; microfone encerrado ao sair/ocultar.

### Testes web

Chrome headless instalado no Windows. UI em 1440×1000 e 390×844: importação de dois WAVs sintéticos, Play/menu, velocidade80% + pitch2, fader0dB, exportar/importar backup, recarregar persistência, microfone de teste e retorno. Zero erros JavaScript. Não é teste acústico com instrumento real.

594 verificações cromáticas (mesmas notas/offsets/modos); erro máximo em senoide: **0,167 cents**. Teste de áudio real processado: entrada 220 Hz, pitch +2 em 100/80/120%; leituras aproximadamente 247,25/247,31/247,21 Hz (esperado 246,94 Hz), desvio abaixo de 1 Hz. Duração derivada e posição verificadas.

Stress progressivo: 2/4/8/12/19 nós com fonte mono sintética de 8 segundos, em 100/80/120%, cerca de 1,5 segundo de playback medido por caso. Todos os canais emitiram sinal; relógio avançou coerentemente. Preparo de 19 tracks: cerca de 23,7s em 80% e 13,9s em 120% neste ambiente. **Não são benchmarks de músicas completas, celular, iPad ou uso de palco.** Web Audio não expõe contador portátil de underruns; não declaramos zero dropouts físicos. Resultados em `web-stress-0.8.json`.

A tentativa inicial de DSP ao vivo perdeu desempenho com 12/19 tracks; foi substituída pelo preparo offline. O limite de entrada sem conexão do wrapper também foi identificado e deixou de fazer parte da reprodução final.

## Limitações e remoção iOS

- Safari/iPad, interfaces USB físicas, dispositivos reais, qualidade subjetiva em vocal/piano e reprodução longa ainda não testados.
- Web carrega PCM em RAM, com orçamento de 384 MiB para originais/cache. Buffers transitórios podem exigir mais. Não possui streaming de disco equivalente à engine Android. Mantenha a aba/tela ativa; políticas do navegador podem suspender áudio.
- Saída web apenas estéreo/L/R, sem USB multicanal. Backup usa memória adicional. Limpar dados do site pode apagar biblioteca; exporte cópias.
- Sem login/sincronização de projetos em servidor. Hospedagem está sujeita à disponibilidade e limites da plataforma; não foi contratado armazenamento pago.
- Código/workflow, documentos/capturas rastreados e release/tag iOS removidos. Histórico Git preservado. A revisão automática bloqueou a remoção recursiva de artefatos locais temporários: `tools/ios-runtime`, `verification/ios-artifacts-*` e pacote IPA local permanecem fora do versionamento/entrega. Não foi tentado contornar o bloqueio.

## Evidências

- `android-tests-0.8.json`, `android-tuner-accuracy-0.8.txt`
- `web-browser-0.8.json`, `web-stress-0.8.json`
- `android-menu-0.8.png`, `web-desktop-0.8.png`, `web-mobile-0.8.png`, `web-tuner-0.8.png`
- APK SHA-256: `555e5171e0c8e0d7995744b9bdedeadd960d66ea43a9d0960a9c6c92bd2f598a`.
