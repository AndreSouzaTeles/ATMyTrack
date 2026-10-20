# Validação 0.19.0 (32)

## Assinatura e atualização do original

A chave `debug.keystore` trazida pelo usuário do outro computador foi aberta com o alias padrão `androiddebugkey`. Seu certificado SHA-256 é `1f9c8116100c1b13aded62b0c2711e3aa045b60c77bb40f57f5f1a71f77e9c39`, exatamente o mesmo do APK público original [0.14.4](https://github.com/AndreSouzaTeles/ATMyTrack/releases/tag/v0.14.4).

O APK entregue tem `applicationId=com.atmytrack.app`, rótulo ATMyTrack, versão 0.19.0 (32), e foi validado por `apksigner verify --print-certs`. A chave é local, ignorada pelo Git; não foi incluída em commits ou assets. O build original falha se ela não estiver presente, em vez de substituir a assinatura. V2 permanece opcional e separado.

Foi usado um AVD novo e isolado, ATMyTrack_OriginalKey_API35, para instalar a versão pública 0.14.4 (24), carregar um projeto de teste e aplicar `adb install -r` da 0.19.0, sem desinstalação. A biblioteca e o PCM permaneceram idênticos byte a byte. Após abrir a nova versão, o projeto ficou pronto e o conteúdo da biblioteca permaneceu igual. [Evidência com hashes](original-upgrade-0.19.0.json).

Fixture: um projeto, uma faixa estéreo sintética float32 de 12 s / 48 kHz / 4.608.000 bytes, ganho 0,65, PAN −0,25, seção personalizada violeta. Os testes interativos posteriores alteram apenas esses dados do emulador. Nenhum aparelho físico foi acessado e não houve transferência automática de dados do v2 para o original.

## Ícone circular

Arte circular ciano/azul/violeta com transparência fora do disco, mantendo play/equalizador. Adicionado roundIcon e exportadas as cinco densidades de ic_launcher e ic_launcher_round. Logo, splash e web local usam a mesma arte. As camadas adaptativas aproveitam o viewport; máscara e tamanho da grade continuam sob controle do launcher Android.

BrandAssetsTest: transparência dos cantos e do ponto diagonal de 1/8 da imagem, preenchimento próximo das quatro bordas em 48/72/96/144/192/512 px, renderização de ícones adaptativo e round. Aprovado, 1 teste, 1,996 s.

## Checks

- Build final: `testDebugUnitTest lintDebug :app:exportDebugApk assembleDebugAndroidTest`, aprovado em 2 min 12 s.
- JVM: 54 testes, zero falhas.
- Android: Sections09Test, PadLocks13Test e Timeline131Test, 6 testes aprovados em 77,908 s. Seções personalizadas, waveform, proteção dos ganhos/PAN, MUTE/SOLO, PAD e busca na timeline.
- Atualização do APK final via `adb install -r`: aprovada.
- `aapt dump badging`: com.atmytrack.app, código 32, versão 0.19.0, nome ATMyTrack.
- `node --check web/dist/app.js` e `git diff --check`: aprovados. Web só recebeu arte e versão; site público não foi republicado.

Ambiente: Windows, JDK 17.0.20.1, SDK 36, Android 15 Google APIs x86_64, AVD isolado com 4 GiB / 4 cores, SwiftShader, 1080×1920 / 420 dpi. Não foi feito benchmark de importação nem promessa de desempenho em celular.

## Instalador

`dist/ATMyTrack-debug.apk` e `dist/ATMyTrack-0.19.0-debug.apk`, idênticos.

- Tamanho: 13284170 bytes.
- SHA-256: `800f7fab4205eccd2aeef5464e09dd3aa1461e4f5ab8b17214fb78f1a2a60b77`.
- Release: [v0.19.0](https://github.com/AndreSouzaTeles/ATMyTrack/releases/tag/v0.19.0).

## Capturas e arte

- [Antes, original 0.14.4](original-before-0.19.0.png)
- [Depois, original 0.19.0](original-after-0.19.0.png)
- [Launcher Android](launcher-circle-0.19.0.png)
- [Ícone adaptativo circular](../branding/0.19.0/adaptive-round.png)
- [Paleta, fonte e prompt](../branding/BRAND-0.19.0.md)
