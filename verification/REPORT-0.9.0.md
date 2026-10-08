# ATMyTrack 0.9.0 — Seções, waveform e controles

## Implementado
- Android e web local: cores RGB/CMY rápidas e seletor circular para cor personalizada (RGB completo no Android; seletor nativo do navegador na web).
- Cores salvas sem a antiga conversão obrigatória para azul. Sem alteração de schema ou remoção de projetos.
- Timeline sem texto de instrução. Envelope preenchido contínuo, calculado por máximos de todos os samples dos dois canais e de todas as tracks; zeros reais permanecem silenciosos. Não é uma soma que poderia cancelar canais em oposição de fase.
- Cache Android de waveform v2 invalida a representação antiga de uma única track. Análise em I/O cede ao playback; só publica o envelope completo, evitando trechos ainda não analisados parecerem silêncio. A primeira análise pode levar mais tempo em projetos grandes; nenhuma promessa de tempo no aparelho.
- Web: worker para envelope, recalcula caches anteriores ao abrir projetos; preenchimento contínuo sem espaçamento entre barras. Envelope é uma visão geral de 512 bins, não edição em resolução de sample.
- TOM / VELOCIDADE primeiro; demais ferramentas com o mesmo cartão azul e ícones. Cores livres restritas às seções.

## Testado nesta entrega
Ambiente: Windows, emulador Android API 35 x86_64, Chrome desktop headless.
- Build/export APK e 41 testes JVM: aprovados. Lint: 0 erros, 29 avisos.
- Instalação `adb install -r` sobre app existente: aprovada; biblioteca existente abriu e foi usada pelo teste UI.
- Sections09Test: 3 testes aprovados: waveform com primeira track silenciosa, segunda track com tom de 220 Hz somente no canal direito, intervalo de silêncio real de um segundo e leitura do cache; gravação/leitura de cores RGB, personalizada, preto e branco; seletor/UI e ordem TOM/VELOCIDADE.
- Feature06Test.selectedSectionLoopsAndCanBeDisabledDuringPlayback: aprovado; loop/seek e desativação durante playback, assertion de zero underruns neste cenário.
- Web: 594 casos matemáticos do afinador, waveform com silêncio/canal direito/segunda track/transiente curto, fluxo de importação, criação de seção personalizada, reload preservando cor, mixer, backup, menu, microfone simulado e layouts 1440x1000 / 390x844: aprovados. Zero erros JavaScript.
- Áudio real de teste no Chrome: +2 semitons em velocidades 100%, 80% e 120%, frequência medida dentro de 1 Hz do alvo; resultados em web-browser-0.9.json.

## Limites
- Não validado em celular físico, iPad/Safari ou interface USB nesta entrega.
- Teste de 19 tracks/performance prolongada não repetido; a engine DSP/mixer não foi alterada. Não se afirma ausência de dropouts em hardware não testado.
- Waveform mostra envelope original agregado; velocidade individual mantém a referência original da timeline, como antes.
- Site atualizado localmente em http://127.0.0.1:4173/ conforme pedido. Site público anterior não foi republicado nesta entrega.

## APK
- Versão 0.9.0, versionCode 11; mesma assinatura debug.
- dist/ATMyTrack-debug.apk e dist/ATMyTrack-0.9.0-debug.apk
- 12.705.684 bytes
- SHA-256: cdfcf87bf9bd327ce2bfb8f13c8cf7af9b5d1c5db100fc556c6da3e375135f85

## Funções esclarecidas
STEREO SPLIT: reconhece click/guide/guia/clk/metro pelo nome e envia essas tracks para LEFT; demais para RIGHT; click interno LEFT. Remove BUS das tracks para aplicar o split. Não separa instrumentos dentro de uma gravação mixada.
ATUALIZAR SAÍDA: pausa, recarrega a saída, consulta a interface USB disponível/canais aceitos pelo Android e preserva posição/configurações. PLAY retoma após preparo.
