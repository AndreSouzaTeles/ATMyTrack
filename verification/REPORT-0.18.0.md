# Validação 0.18.0 (31)

## Entrega

Pacote `com.atmytrack.app.v2`, nome ATMyTrack_v2, mesma assinatura debug da versão anterior. Instalação `adb install -r` sobre 0.17.0 concluída; projetos importados mantidos. Sem alteração de DSP ou schema. A separação de instrumentos permanece removida.

APK: `dist/ATMyTrack-debug.apk`, também `dist/ATMyTrack_v2.apk` e `dist/ATMyTrack_v2-0.18.0.apk`. Os três arquivos são idênticos.

- Tamanho: 13131049 bytes.
- SHA-256: `4f82d1cfdd853637dd851d35db0372e2673aaaf0e3de58e09595a56ce878b543`.
- Certificado SHA-256: `656039fc2edf595be9c8e7042b1f7b197ad319042bc8de73ff52c4bd59a3d01c`.

## Verificação

- `tools/build-android.ps1`: build, `testDebugUnitTest`, `lintDebug`, exportação do APK e montagem do APK de testes: sucesso.
- JVM: 54 testes, zero falhas. Inclui nomes em português/inglês, acentos e estabilidade das cores após reordenação.
- Android: BrandAssetsTest, 1 teste aprovado; exportação de ícones por densidade e prévias adaptativas.
- Android: Sections09Test, PadLocks13Test e Timeline131Test, 6 testes aprovados em 27,64 s. Paleta de seções, persistência das cores personalizadas, waveform, cadeados, PAD e busca na timeline.
- Conferência visual no emulador: player e mixer em retrato e paisagem, canais diferenciados, ferramentas, novo logo e máscara adaptativa. Reprodução manual confirmada por estado PAUSAR / REPRODUZINDO; pausada ao terminar.
- Web local: `node --check`, testes music (594 casos), waveform e PCM aprovados. A suíte de navegador não foi executada nesta entrega; o site público não foi republicado.
- `apksigner verify --print-certs`: assinatura válida e inalterada.
- `git diff --check`: aprovado.

Ambiente: Windows, JDK 17.0.20.1, SDK 36, AVD ATMyTrack_API35 Android 15 x86_64, 6 GiB / 4 cores, renderização SwiftShader. Capturas em 1080×1920/420 dpi e 1920×1200/240 dpi. Não foi feito benchmark de importação ou inferência de desempenho em celular.

## Capturas

- [Player no celular](player-portrait-0.18.0.png)
- [Mixer no celular](mixer-portrait-0.18.0.png)
- [Player em paisagem](player-landscape-0.18.0.png)
- [Mixer em paisagem](mixer-landscape-0.18.0.png)
- [Ícone Android adaptativo](../branding/0.18.0/adaptive.png)

Paleta e prompt da arte: [BRAND-0.18.0](../branding/BRAND-0.18.0.md).
