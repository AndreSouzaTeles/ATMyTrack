# Validação 0.21.0 (34)

## Alteração

Ícone e imagens de marca substituídos a partir do print enviado em 20/10/2026. Disco azul-marinho, aro azul/ciano, play contornado e barras de áudio brilhantes. Fundo externo transparente. A mesma arte é usada na logo do cabeçalho/menu/estado vazio, splash e camadas adaptativas. PNGs de launcher normal/round em todas as densidades e logo da web local sincronizados. Paleta dos controles, dados dos projetos, CrossMusic e DSP não foram alterados. Site público não republicado.

[Arte, referência e prompt da ferramenta integrada](../branding/BRAND-0.21.0.md). O launcher Android decide a máscara, efeitos e tamanho final na grade.

## Verificação

- Build `testDebugUnitTest lintDebug :app:exportDebugApk assembleDebugAndroidTest`: aprovado em 3 min 32 s, incluindo lint.
- 58 testes JVM, zero falhas.
- BrandAssetsTest no Android: aprovado, 1 teste em 2,353 s. Verifica transparência dos cantos e da diagonal externa, preenchimento nas quatro bordas e renderização em 48/72/96/144/192/512 px; exporta logo, foreground, adaptive e round.
- [Exportações do renderer Android](../branding/0.21.0/renderer/), [player com a nova logo](player-0.21.0.png), [ícone no launcher](launcher-0.21.0.png), inspecionados visualmente.
- APK instalado sobre 0.20.0 com `install -r`, sem desinstalação. Biblioteca existente abriu normalmente. Nenhum aparelho físico acessado.
- `apksigner verify --print-certs`: certificado original SHA-256 `1f9c8116100c1b13aded62b0c2711e3aa045b60c77bb40f57f5f1a71f77e9c39`.
- `aapt dump badging`: `com.atmytrack.app`, versão 0.21.0 (34).
- `git diff --check`: aprovado. Chave local ignorada, não incluída no repositório/release.

Ambiente: Windows, JDK 17.0.20.1, SDK 36; Android 15 Google APIs x86_64, AVD ATMyTrack_OriginalKey_API35, 4 GiB / 4 cores, SwiftShader, 1080×1920 / 420 dpi. Sem benchmark ou promessa de desempenho em celular.

## APK

`dist/ATMyTrack-debug.apk` e `dist/ATMyTrack-0.21.0-debug.apk`, idênticos, 13.232.151 bytes.

SHA-256: `de73b9788943efb978f3219a79bcfb93a71d7dd1b2f73f2c17086ffcbe738da8`.

[Release v0.21.0](https://github.com/AndreSouzaTeles/ATMyTrack/releases/tag/v0.21.0).