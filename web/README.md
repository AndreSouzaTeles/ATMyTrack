# ATMyTrack Web 0.10.1

Player local no navegador, sem conta e sem upload de áudios. Projetos/arquivos ficam no IndexedDB da origem. Exportação/importação `.atmytrack` transfere projetos entre navegadores. Não compartilha o formato interno Android.

## Executar

Sirva `dist/` por HTTPS (ou localhost para desenvolvimento). Exemplo: `python -m http.server 4173 --bind 127.0.0.1 --directory dist`.

## Recursos

- Projetos, capa, busca por nome/tom, importação multiarquivo e backup.
- Um AudioContext para transporte, gain/pan/mute/solo/master, DCA, BUS e rotas estéreo/esquerda/direita.
- Signalsmith Stretch WASM em Worker (preparo offline), 50–200%, pitch ±12, seleção independente, bypass de DSP em 100%/0 semitons. Configuração 120 ms/10 ms; início comum agendado 100 ms à frente. Mudanças fazem pequena preparação e retomam posição musical.
- Markers na timeline original, seek, loop de seção; click sintetizado em AudioWorklet acompanha clock e velocidade global. Smart Click estima o BPM original em Worker.
- Afinador YIN em Worker, microfone real, cromático/instrumentos, A4 e alvo independente da nota medida. Captura encerrada ao ocultar/sair.
- Menu com logo, DEIXE SUA MARCA, QR/Pix existente e Central de dúvidas. Nomes demonstrativos identificados como fictícios.

## Limites reais

- Reprodução por blocos: um Worker lê PCM do Blob/armazenamento e envia quatro blocos de um segundo por MessageChannel diretamente a um AudioWorklet. Todas as tracks têm início e cursor comuns. Ganhos/pan/DCA/BUS permanecem no grafo Web Audio. Não existe limite fixo de tamanho agregado de 384 MiB no caminho de arquivos importados. O pré-buffer de 30 tracks estéreo em 48 kHz ocupa cerca de 44 MiB, além dos blocos em produção e estruturas do navegador. Isso não é uma medição da RAM total do navegador.
- WAV PCM mono/estéreo 8/16/24/32-bit e float32/64, incluindo WAV extensível/RF64: leitura em fatias. Taxas diferentes usam sinc de 32 taps em Worker, com tabela de fases e filtro para reduzir aliasing; a implementação web é independente do resampler Android.
- Áudio comprimido: decoder nativo por arquivo, descartado após gravar PCM no OPFS. Não retém todos os arquivos decodificados em RAM, mas uma única track comprimida muito longa ainda depende da memória/limites do decoder do navegador. Não foi implementado demux/decodificação incremental universal.
- TOM/VELOCIDADE: Signalsmith em Worker, saída PCM gravada no OPFS em blocos; uma variante em disco por track, sem buffers gigantes de resultado. 100%/tom original ignora DSP. Espaço de disco necessário depende da duração e velocidade. Excluir projeto também remove caches associados.
- Falta de blocos é detectada: pausa conjunta em vez de permitir drift entre tracks; há contador diagnóstico de underruns do streaming. Isso não mede todos os dropouts do dispositivo físico.
- Formatos dependem do decoder do navegador. Saída apenas estéreo; não promete USB multicanal, reprodução em tela bloqueada ou equivalência de desempenho com Android.
- Projetos de cada navegador/origem são separados; limpar dados pode apagar arquivos. Novos backups usam contêiner binário com Blobs em vez de materializar todo o áudio em base64. Leitura de backups antigos JSON/base64 continua disponível e ainda requer memória proporcional ao arquivo legado.
- Sem autenticação/sincronização em nuvem, conforme alternativa autorizada para evitar armazenamento remoto com custo. Hospedagem depende da plataforma e de seus limites.
- UI responsiva testada em Chrome Windows; Safari/iPad e hardware real não certificados.
- Não houve pagamento real nem envio automático de nomes. Inclusão de apoiadores é manual no asset.

## Testes

`npm ci`, servidor local acima, `npm test`. `node tests/stress.cjs` executa cenários sintéticos curtos, não certifica estabilidade em palco. Tests usam Chrome instalado e Playwright como dependência de desenvolvimento. A exportação adicional SignalsmithWasm expõe a factory original para Worker; o algoritmo não foi modificado. Dependência DSP vendorizada a partir do submódulo Android (Signalsmith 1.3.2), licença MIT em dist/vendor.


## Validação 0.10.0

`node tests/stream.cjs`: caminho real Worker/AudioWorklet, DSP +2 com 80/100/120%, seek e loop. `node tests/large-project.cjs`: requer fixtures WAV em verification/fixtures-web-large; importa 30 arquivos reais e verifica reprodução/reabertura. Resultados e duração dos cenários no relatório de entrega; não equivalem a certificação em palco.
