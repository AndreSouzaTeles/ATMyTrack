# Verificação da entrega 0.1.0

Data: 06/10/2026. Ambiente: Windows, JDK 17, SDK/Build Tools 36, emulador Android 15 (API 35, x86_64, WHPX).

## Evidências

- Build `assembleDebug`: passou.
- Assinatura do APK: verificada com `apksigner verify`, esquema v2.
- Instalação com `adb install -r`: passou; Activity iniciou sem crash.
- 8 testes JVM: 6 de áudio/tempo/markers e 2 de persistência, sem falhas.
- 3 testes instrumentados: importação e reprodução reais de WAV, pause/seek/stop e persistência; rollback de sample rates diferentes; arquivo corrompido. Passaram no emulador.
- Inspeção visual: celular 1080×1920/420 dpi e configuração de tablet 1920×1200/240 dpi. Faders/master visíveis juntos no tablet; celular permite rolagem com transporte fixo.
- Lint: nenhum erro. Avisos restantes incluem atualizações de versões e sugestões de APIs; não indicam falhas de compilação.

Relatórios automatizados completos: `app/build/reports/tests/testDebugUnitTest/`, `app/build/reports/androidTests/connected/` e `app/build/reports/lint-results-debug.html`.

A captura `portrait.png` é do emulador com arquivos gerados exclusivamente pelos testes; não há projetos ou áudio mockados no APK distribuído. O layout de tablet também foi inspecionado durante a execução, mas a captura final não foi preservada.

## O que os testes não certificam

Não foi conectado aparelho Android físico. Não houve escuta ou medição acústica, ensaio prolongado com dezenas de stems, teste de latência real, certificação de interface USB/Bluetooth, nem validação de todos os codecs/perfis em diferentes fabricantes. A sincronização por frames e o comportamento de leitura/seek são testados, mas a estabilidade de palco precisa ser ensaiada com o hardware de destino.

Pitch, detecção automática de BPM, DCA, buses e multi-output não fazem parte desta versão. Não são apresentados como recursos concluídos.
