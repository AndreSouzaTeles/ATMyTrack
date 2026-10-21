# Validação 0.22.0 (37)

## Timeline e tablets

- Tablets com menor dimensão a partir de 600 dp e largura disponível de pelo menos 600 dp: transporte em coluna de 300 dp à esquerda, timeline flexível à direita. Celulares mantêm o transporte acima da timeline.
- Forma de onda em barras finas separadas, azul suave para o áudio restante e verde suave para o trecho reproduzido. Cursor e régua de progresso separados; seções continuam visíveis ao fundo. Área de toque da forma de onda com 48 dp de altura.
- Energia RMS em 2.048 intervalos por faixa, agregada por energia no relógio do projeto. Antes, 512 máximos por faixa eram combinados pelo maior valor, favorecendo uma faixa quase sólida em músicas densas. Barras representam o áudio real, sem oscilações decorativas.
- Cache visual versionado (`wave-rms-v1` por faixa e `wave-rms-v3` por projeto). Projetos existentes recalculam somente a representação visual a partir do PCM local; nenhum arquivo original ou projeto é apagado. Formato de persistência e processamento da reprodução preservados.

## Verificação

- `tools/build-android.ps1`: build, lint e empacotamento aprovados. 60 testes JVM, zero falhas, incluindo dinâmica com transientes de mesmo pico, silêncio, amostras inválidas e leitura PCM fragmentada.
- `Timeline22UiTest`: importa sinal sintético de 24 segundos, verifica silêncio/dinâmica, disposição dos controles, busca por acessibilidade e arraste que aplica a posição somente ao soltar.
- Tablet horizontal 1600×1000 / 160 dpi: aprovado. [Captura](timeline-0.22.0-tablet-landscape.png).
- Tablet vertical 1000×1600 / 160 dpi: aprovado. [Captura](timeline-0.22.0-tablet-portrait.png).
- Celular 1080×1920 / 420 dpi: aprovado; timeline permanece abaixo dos controles. [Captura](timeline-0.22.0-phone.png).
- `Timeline131Test`: aprovado no celular, incluindo 100 movimentos sem busca intermediária e aplicação do seek final durante a reprodução. Total: quatro execuções de testes Android aprovadas, nas três configurações acima.
- Capturas inspecionadas visualmente; `git diff --check` aprovado. Dimensões do emulador restauradas para 1080×1920 / 420 dpi.

Ambiente: Windows, JDK 17.0.20.1, SDK 36, AVD ATMyTrack_OriginalKey_API35, Android 15 Google APIs x86_64. Permissão de notificações concedida no emulador para não interromper o lançamento da Activity de teste. Sem teste em aparelho físico e sem benchmark de importação. As capturas usam áudio sintético, não a música da imagem enviada.

## APK

`dist/ATMyTrack-debug.apk`, 13.290.687 bytes. Identidade `com.atmytrack.app`, versão 0.22.0 (37), verificada com `aapt`. Assinatura original validada por `apksigner`, certificado SHA-256 `1f9c8116100c1b13aded62b0c2711e3aa045b60c77bb40f57f5f1a71f77e9c39`.

SHA-256 do APK: `a77db6f0ea8894e7b3454a4f977699909f90265aa02f65bd22952b42bf9761d7`.

[Release v0.22.0](https://github.com/AndreSouzaTeles/ATMyTrack/releases/tag/v0.22.0).

## Limpeza pendente

O usuário autorizou remover APKs antigos/duplicados, logs e caches de compilação, mantendo o APK atual e os arquivos necessários. A tentativa com caminhos restritos à pasta do projeto e preservação explícita do APK atual foi rejeitada pela revisão automática de segurança, com o único motivo `blocked by policy`. Nenhum arquivo foi excluído. A limpeza permanece pendente; não se deve interpretar esta entrega como aprovação ou execução dessa exclusão. O checksum do APK atual foi atualizado. Código, versão web, assinatura e relatórios históricos continuam preservados.
