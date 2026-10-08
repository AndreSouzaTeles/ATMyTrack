# ATMyTrack Web 0.8.0

Player local no navegador, sem conta e sem upload de áudios. Projetos/arquivos ficam no IndexedDB da origem. Exportação/importação `.atmytrack` transfere projetos entre navegadores. Não compartilha o formato interno Android.

## Executar

Sirva `dist/` por HTTPS (ou localhost para desenvolvimento). Exemplo: `python -m http.server 4173 --bind 127.0.0.1 --directory dist`.

## Recursos

- Projetos, capa, busca por nome/tom, importação multiarquivo e backup.
- Um AudioContext para transporte, gain/pan/mute/solo/master, DCA, BUS e rotas estéreo/esquerda/direita.
- Signalsmith Stretch WASM em Worker (preparo offline), 50–200%, pitch ±12, seleção independente, bypass nativo em 100%/0 semitons. Configuração 120 ms/10 ms; início comum agendado 100 ms à frente. Mudanças fazem pequena preparação e retomam posição musical.
- Markers na timeline original, seek, loop de seção; click sintetizado em AudioWorklet acompanha clock e velocidade global. Smart Click estima o BPM original em Worker.
- Afinador YIN em Worker, microfone real, cromático/instrumentos, A4 e alvo independente da nota medida. Captura encerrada ao ocultar/sair.
- Menu com logo, DEIXE SUA MARCA, QR/Pix existente e Central de dúvidas. Nomes demonstrativos identificados como fictícios.

## Limites reais

- Não é um porte da engine Android de arquivos PCM em streaming. A versão web mantém AudioBuffers em RAM; limite de 384 MiB de áudio decodificado por projeto. Cache PCM de uma variante por track, invalidado ao trocar projeto; orçamento de 384 MiB para originais + resultado DSP. Buffers transitórios podem usar mais RAM. Arquivos grandes podem exceder o limite do dispositivo antes disso.
- Formatos dependem do decoder do navegador. Saída apenas estéreo; não promete USB multicanal, reprodução em tela bloqueada ou equivalência de desempenho com Android.
- Projetos de cada navegador/origem são separados; limpar dados pode apagar arquivos. Backups usam JSON/base64 e consomem memória adicional.
- Sem autenticação/sincronização em nuvem, conforme alternativa autorizada para evitar armazenamento remoto com custo. Hospedagem depende da plataforma e de seus limites.
- UI responsiva testada em Chrome Windows; Safari/iPad e hardware real não certificados.
- Não houve pagamento real nem envio automático de nomes. Inclusão de apoiadores é manual no asset.

## Testes

`npm ci`, servidor local acima, `npm test`. `node tests/stress.cjs` executa cenários sintéticos curtos, não certifica estabilidade em palco. Tests usam Chrome instalado e Playwright como dependência de desenvolvimento. A exportação adicional SignalsmithWasm expõe a factory original para Worker; o algoritmo não foi modificado. Dependência DSP vendorizada a partir do submódulo Android (Signalsmith 1.3.2), licença MIT em dist/vendor.
