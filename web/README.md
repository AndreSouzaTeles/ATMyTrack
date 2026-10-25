# ATMyTrack Web 0.24.1

Player multitrack local no navegador. Site público: https://atmytrack-web.andmtu.chatgpt.site . Áudios e projetos ficam no navegador, sem upload de músicas ou conta.

## Paridade com o aplicativo

- Transporte, timeline e mixer na mesma tela. Tablet: timeline ao lado do transporte compacto e ferramentas na barra superior. Celular: ferramentas em menu e timeline abaixo do transporte.
- Identidade grafite, cores por instrumento, logo circular azul/ciano e marca Outfit ao lado do ícone. Waveform RMS de 2048 pontos, barras separadas e progresso colorido.
- Faders de −80 a +10 dB com curva de console, ajuste fino, UNITY, silêncio, PAN rotativo/centralização, cadeados, MUTE/SOLO, Master fixo, canais DCA/BUS e medição logarítmica. Migração preserva o silêncio dos projetos antigos.
- Importação por pasta ou multiarquivo (até 64 faixas), editor após importar, capa, cores manuais, ordem e exclusão de faixas, cartões de projetos reordenáveis e busca sem acentos/por tom maior ou menor.
- Tom original/destino, estimativa automática por FFT/chroma, seleção de faixas, exclusão inicial de click/percussão do pitch e velocidade independente com preservação de afinação.
- Seções coloridas, seek por arraste aplicado ao soltar e loop de seção/projeto.
- Metrônomo com Acompanhar música, modo independente mesmo sem projeto, oito timbres, TAP, Smart Click, BPM, compasso com unidade 4/8, divisão, acento, volume e routing.
- Percussão ao lado do PAD: dez padrões com gravações reais de bateria acústica, congas, shaker e pandeiro, variações de quatro compassos e viradas, modo acompanhado/independente, BPM, compasso, volume e biblioteca de loops importados. Loops de 0,05–60 s, ajuste de 0,125× a 8× preservando pitch, resultado até 120 s. Informe os compassos contidos no loop.
- CrossMusic: sequência persistente/reordenável, volume/mute por faixa, PAD por projeto com tom manual ou do projeto e transição de três segundos. Avança no fim natural. Pausar não avança; encerrar a automação mantém a música/PAD atuais.
- PAD independente com quatro timbres, afinador por microfone, routing estéreo/esquerda/direita e retomada/seleção de saída quando oferecida pelo navegador.
- Deixe sua marca com Pix, nomes atuais, globo com toque, busca e rotação automática. Central de dúvidas atualizada.

## Dados e limites da plataforma

Projetos antigos e backups web JSON/base64 continuam legíveis; novos backups usam contêiner binário de Blobs. O formato Android permanece separado. Limpar dados do site pode apagar a biblioteca; exporte backups regularmente. Os loops de percussão importados são guardados separadamente na biblioteca deste navegador.

WAV PCM é lido em fatias; formatos comprimidos dependem dos decoders do navegador. Streaming PCM usa Worker/AudioWorklet, pré-buffer comum e OPFS; pitch/velocidade usam Signalsmith Stretch em Worker com cache em disco. O DSP preserva a afinação, mas mudanças podem exigir preparação.

Não há garantia de USB multicanal, reprodução com tela bloqueada ou em segundo plano. Mantenha a página aberta e a tela ativa; a perda de blocos pausa o transporte para manter sincronismo. Memória, armazenamento, permissões e formatos variam. Chrome Windows foi testado; hardware Android, Safari/iPad e estabilidade em palco não foram certificados nesta entrega. Resultados do computador não estimam tempos no aparelho do usuário.

## Executar e testar

Sirva dist/ por HTTPS ou localhost: python -m http.server 4173 --bind 127.0.0.1 --directory dist . No Windows, execute Abrir ATMyTrack Web.bat (requer Python).

Dependências: pnpm install --frozen-lockfile . Execute pnpm test com o servidor ativo. Playwright usa Chrome instalado. Testes cobrem DSP, leitura PCM, waveform RMS, migração, faders/cadeados, routing, PAD, metrônomo independente, percussão/importação, CrossMusic e layouts responsivos.

Signalsmith Stretch: código vendorizado, licença MIT em dist/vendor/Signalsmith-LICENSE.txt. Fonte Outfit: licença em dist/assets/Outfit-OFL.txt.

## Biblioteca acústica 0.24.1

24 gravações reais (duas por articulação) de Virtuosity Drums e VCSL, CC0. Frases de quatro compassos, alternância de golpes, dinâmica, ghost notes e viradas. Os padrões são sequenciados a partir dos golpes gravados; não são loops completos executados por um baterista. Créditos, licenças e proveniência em dist/assets/drumkit/.
