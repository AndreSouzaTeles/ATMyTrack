# Validação 0.22.1 (38)

Ferramentas dos tablets transferidas do menu para uma faixa horizontal no cabeçalho, imediatamente antes do botão “+” de importar. Tom, Velocidade, PAD, Loop, Seção, Metrônomo, DCA, BUS, Routing, Atualizar saída e CrossMusic mantêm as mesmas ações. Funções ativas recebem contorno colorido; manter o toque mostra a identificação. Em telas estreitas, a faixa tem rolagem horizontal, com importar e pesquisar fixos. Celulares mantêm o menu anterior.

- `tools/build-android.ps1`: compilação, lint e empacotamento aprovados; 60 testes JVM, zero falhas. Build final após o acabamento do contorno ativo também aprovado.
- `Timeline22UiTest` ampliado para verificar ausência do menu no tablet, presença das 11 ações, acesso a cada botão por rolagem, alinhamento com o “+” e abertura/fechamento dos diálogos Tom e Velocidade. Também confere waveform e seek por acessibilidade/arraste.
- Tablet horizontal 1600×1000 / 160 dpi: aprovado. [Captura](tools-0.22.1-tablet-landscape.png).
- Tablet vertical no limite de 600 dp, 600×960 / 160 dpi: aprovado; todos os botões acessíveis por rolagem. [Captura](tools-0.22.1-tablet-portrait.png).
- Celular 1080×1920 / 420 dpi: aprovado; menu presente, ações fechadas inicialmente e diálogos Tom/Velocidade acessíveis pelo menu. [Captura](tools-0.22.1-phone.png).
- Total de três execuções Android aprovadas. Capturas inspecionadas; `git diff --check` aprovado.

Ambiente: Windows, JDK 17.0.20.1, SDK 36, AVD ATMyTrack_OriginalKey_API35 com Android 15 Google APIs x86_64. Dimensões do emulador restauradas para 1080×1920 / 420 dpi. Capturas usam sinal sintético de 24 segundos; sem teste em aparelho físico ou benchmark de importação.

Identidade `com.atmytrack.app`, versão 0.22.1 (38), conferida por `aapt`. Assinatura original validada por `apksigner`, certificado SHA-256 `1f9c8116100c1b13aded62b0c2711e3aa045b60c77bb40f57f5f1a71f77e9c39`. Nenhuma alteração no formato dos projetos ou no processamento de áudio.

APK: `dist/ATMyTrack-debug.apk`, 13.768.254 bytes. SHA-256: `add99e1d7649f06b161692b639b5d2b539a34fa50aa2e6e696f8b5e9d69c22c6`.

[Release v0.22.1](https://github.com/AndreSouzaTeles/ATMyTrack/releases/tag/v0.22.1).
