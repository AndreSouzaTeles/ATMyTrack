# ATMyTrack 0.14.0 (20)

## Implementado em Android e Web

- Metrônomo Woodblock: envelope percussivo com dois modos ressonantes (880/1874,4 Hz antes do acento); não apenas outro nome para o mesmo som.
- Exceções ao cadeado global: desbloquear uma track/DCA/BUS mantém a Master e demais canais protegidos. Religar o cadeado global limpa exceções. Dados antigos recebem lista vazia e preservam seus cadeados.
- PAD: Warm, Air, Strings e Organ, sintetizados localmente com espectros/filtros diferentes, 12 raízes maiores/menores. Preferência de timbre persistida. São sons sintetizados, não samples licenciados de instrumentos.
- Controle de picos exclusivo do PAD. Android saída própria AudioTrack; Web rota direta com waveshaper e limitador próprios, fora do compressor do Master. Nenhum ducking nem mudança de ganho das tracks foi adicionado.
- O nível do PAD Android foi recalibrado: pico máximo Warm 0,6005 nos 24 acordes (antes 0,8776), ainda 1,96 vezes o RMS da versão 0.13.0. Na web, C maior máximo teve RMS 0,2421 / pico 0,5075, com volume zero silencioso. O ajuste limita o PAD em vez de reduzir o Master.
- Globo retoma rotação ao soltar/cancelar o arraste e ao limpar a busca. Somente busca ativa mantém parado; botão de retomada limpa o filtro.
- Preparação de pitch/time-stretch: até quatro renders independentes no Android (limitados aos núcleos), dois na web. Cancelamento conjunto dos workers web e publicação somente após conclusão. Algoritmo Signalsmith, ordem do DSP, bypass e cache preservados. Menos atualizações de progresso Android para reduzir carga de UI.

## Medições

| Ambiente | Arquivos e transformação | Antes | Agora |
|---|---|---:|---:|
| Emulador Android API 35, x86_64, 4 núcleos | 8 leitores independentes do mesmo WAV estéreo sintético PCM24, 48kHz, 20s, +2 semitons / 80%, cache frio | 13.451 ms (2 renders) | 9.640 ms (4 renders), ~28% menos tempo |
| Chrome headless, Windows, 16 processadores lógicos disponíveis | 8 fontes independentes com os mesmos sinais sintéticos estéreo float PCM, 48kHz, 20s, +2 / 80%, cache frio | 46.704 ms (serial) | 24.368 ms (2 workers), ~48% menos tempo |

Cache quente Android: 3–4 ms. Cache quente web de uma track: 2,4–2,6 ms. Rodadas feitas no mesmo computador durante desenvolvimento; não são promessas para arquivos reais ou outro hardware. Nenhum teste em celular físico.

## Testado

- 51 testes JVM, zero falhas/erros; persistência de exceções, proteção/rebloqueio de ganhos, presets PAD distintos e limitados, Woodblock e testes anteriores.
- Build Android, assembleDebug, exportDebugApk e lintDebug passaram.
- Android instrumentado: comparação de preparação 2/4 tarefas e cache; 19 leitores independentes da mesma fonte sintética estéreo PCM24 de 4s em 80%, 4s sem PAD e 4s com PAD; zero underruns/starvation. Teste curto, não valida 19 stems longas ou show ao vivo.
- Chrome: testes de áudio/DSP e projeto, routing/PAN Master, locks/PAD, coalescência de seek, timeline e volume do PAD. Testes novos confirmam desbloqueio individual com Master bloqueada e persistência, seleção Woodblock, quatro timbres, rotação após gesto e pausa/retomada pela busca.
- Sinal de track no Master web ao variar PAD: RMS 0,035376 antes / 0,035376 durante / 0,035368 após; nenhuma atenuação do ramo da música observada. A soma física da saída do aparelho não foi capturada.
- APK instalado com `adb install -r`, preservando os dados do emulador. Teste de store confirma novos campos persistidos e testes antigos de compatibilidade passaram.
- Primeira instrumentação foi iniciada com APK de testes antigo antes de o build terminar e falhou por incompatibilidade de classe; após instalar ambos os APKs correspondentes, os dois testes passaram. Arquivo `android-final-0.14.0.txt` contém o resultado válido.

## Publicação

Site público atualizado preservando acesso público: https://atmytrack-web.andmtu.chatgpt.site

Site local atualizado: http://127.0.0.1:4173/?v=0.14.0

APK: `dist/ATMyTrack-debug.apk` / `ATMyTrack-0.14.0-debug.apk`, 13.211.367 bytes, SHA256 `320c419dbca9c16626bdcd45b6e8326064aff28be0723fefb0c3f111a685aad2`.

## Limitações reais

A primeira mudança ainda renderiza todo o arquivo; não é realtime e projetos longos continuarão exigindo preparação. Processamento paralelo depende de CPU, armazenamento e temperatura. Não houve teste com multitracks reais de 5GB ou avaliação auditiva no aparelho do usuário. A redução percebida na música ao aumentar PAD não foi reproduzida no ramo do Master da web; o novo controle dos picos reduz a pressão da soma, mas um limitador/efeito do sistema Android ou da interface externa continua fora do controle do app. É necessário confirmar o comportamento auditivo no celular usado pelo usuário.
