# Validação 0.21.1 (35)

Cadeados fechados dos faders alterados de violeta para vermelho `#E05B60`. O componente compartilhado atende tracks, Master, DCA e BUS. Cadeados abertos continuam neutros. Sem alterações no funcionamento dos bloqueios, projetos, áudio ou ícone.

- Build `testDebugUnitTest lintDebug :app:exportDebugApk assembleDebugAndroidTest`: aprovado em 2 min 24 s.
- 58 testes JVM, zero falhas.
- PadLocks13Test: 2 testes Android aprovados em 34,432 s; bloqueio de volume/PAN, exceções individuais, Master, mute/solo e PAD preservados.
- [Captura com cadeado fechado vermelho e abertos neutros](locks-0.21.1.png), inspecionada.
- APK instalado com `install -r` sobre 0.21.0, biblioteca existente aberta sem reimportação.
- `apksigner verify --print-certs`: certificado original SHA-256 `1f9c8116100c1b13aded62b0c2711e3aa045b60c77bb40f57f5f1a71f77e9c39`.
- `aapt dump badging`: `com.atmytrack.app`, versão 0.21.1 (35).
- `git diff --check`: aprovado; chave local ignorada e não publicada.

Ambiente: Windows, JDK 17.0.20.1, SDK 36, Android 15 Google APIs x86_64; AVD ATMyTrack_OriginalKey_API35, 4 GiB / 4 cores, SwiftShader, 1080×1920 / 420 dpi. Durante a captura apareceu um aviso de lentidão do System UI do emulador; após aguardar, a tela foi capturada normalmente. Não houve teste em aparelho físico nem benchmark de desempenho.

`dist/ATMyTrack-debug.apk` e `dist/ATMyTrack-0.21.1-debug.apk`, idênticos, 13.232.151 bytes.

SHA-256: `f09fc69bf59f1e73f55ef103cdb87c9833a77b7eddda20efedb8837113e04533`.

[Release v0.21.1](https://github.com/AndreSouzaTeles/ATMyTrack/releases/tag/v0.21.1).