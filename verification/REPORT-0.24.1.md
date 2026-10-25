# ATMyTrack 0.24.1 (41) — bateria acústica e percussão gravada

Os timbres sintetizados da percussão foram substituídos por gravações reais de instrumentos no Android e no site. A entrega também inclui toda a atualização de paridade descrita no [relatório 0.24.0](REPORT-0.24.0.md).

## Sons e execução

- 24 gravações, duas variantes por articulação: bumbo, caixa, ghost note, chimbal fechado/aberto, cross-stick, tom, crash, ride, conga, shaker e pandeiro.
- Os dez estilos anteriores mantêm seus identificadores para preservar configurações. Frases de quatro compassos acrescentam alternância de gravações, dinâmica por golpe, pequenas diferenças de ataque, ghost notes e viradas. São padrões sequenciados a partir de golpes gravados, não performances completas de um baterista.
- Mesma biblioteca PCM16 estéreo/48 kHz no APK e no site. Arquivos originais, versões e hashes registrados em `provenance.json`; licenças e créditos acompanham os assets. Android carrega/decodifica fora da thread de reprodução; web carrega uma vez ao iniciar a biblioteca.
- BPM, compasso, volume, Acompanhar música, uso independente, sincronismo de seek/loop e importações existentes preservados. Loops importados continuam usando o áudio do usuário.
- Site: https://atmytrack-web.andmtu.chatgpt.site
- Prévia de 24 segundos em `dist/ATMyTrack-acoustic-preview.wav`: Pop/Rock, Samba e Bossa Nova, oito segundos de cada a 120 BPM. Mesmos samples e arranjador usados pelo site.

## Origem dos samples

1. [Virtuosity Drums](https://versilian-studios.com/virtuosity-drums/), Versilian Studios / Karoryfer Samples, performances de Austin McMahon. [Fonte fixada](https://github.com/sfzinstruments/virtuosity_drums/tree/9f04cf9a734527edfbb0a4eee1f674e45bbf71bc), CC0-1.0.
2. [VCSL](https://versilian-studios.com/vcsl/), Sam Gossner / Versilian Studios e colaboradores. [Fonte fixada](https://github.com/sgossner/VCSL/tree/c1ea7bcc3c7309650ab0da9d15c9cd1fbc4a4c7e), CC0-1.0.

Adaptação: silêncio aparado, resampling, conversão PCM16, fade curto no final e nivelamento conjunto por instrumento. Não há síntese dos timbres da percussão. O metrônomo conserva seu click próprio. `tools/prepare-acoustic-kit.py` reproduz a preparação (numpy, scipy, soundfile).

## Verificação

- Windows, Node 24.19, pnpm 11.25, Playwright/Chrome instalado e servidor Python localhost:4173. Testes web completos aprovados, incluindo os cenários anteriores de DSP, routing, backup, PAD, locks, timeline, metrônomo independente, loops importados, CrossMusic e layouts responsivos.
- Teste adicional de áudio acústico: dez estilos com 384000 frames (quatro compassos a 120 BPM/4-4), amostras finitas, picos até 0,90, sinal não silencioso e diferenças reais entre compassos. Compassos 6/8 e 7/8 e extremos 30/300 BPM com duração exata. Dados em `acoustic-0.24.1.json`.
- Android: build, lint, exportação APK e compilação dos testes instrumentados concluídos; **66 testes unitários aprovados**, zero falhas/erros. Testes incluem carregamento dos 24 samples, variantes distintas, dez padrões, limites de pico, duração e consistência entre blocos/seeks. Testes instrumentados não foram executados em aparelho físico nesta entrega.
- Assinatura original verificada por apksigner; certificado SHA-256 `1f9c8116100c1b13aded62b0c2711e3aa045b60c77bb40f57f5f1a71f77e9c39`.
- APK: `dist/ATMyTrack-debug.apk`, **24404503 bytes**, pacote `com.atmytrack.app`, versão **0.24.1 (41)**. SHA-256 `25780c42d4f387b2667db672bc3a91320f37d9208b51c85c412ca71e6f3cac94`.

Não se presume preferência sonora do usuário a partir dos testes numéricos: a prévia permite avaliar o resultado. Não foram medidos tempos no celular/tablet do usuário. Permanecem os limites do navegador descritos no relatório anterior (USB multicanal, tela bloqueada, memória e armazenamento; projetos Android/web separados).
