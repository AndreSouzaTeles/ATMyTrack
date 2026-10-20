# Validação 0.21.2 (36)

Cabeçalho do menu com ícone circular de 56 dp e nome à direita na mesma linha. Fonte geométrica Outfit incluída localmente, branco suave `#E6EBF0`, ciano discreto `#80D9EC` em “My” e espaçamento de letras de −0,5 sp. O tamanho do nome se ajusta entre 22 e 26 sp conforme a largura disponível. Botão FECHAR mantido. Licença SIL OFL incluída no APK.

- `tools/build-android.ps1`: build, lint, 58 testes JVM (zero falhas) e APK de testes aprovados.
- `aapt dump badging`: `com.atmytrack.app`, versão 0.21.2 (36).
- `apksigner verify --print-certs`: assinatura original SHA-256 `1f9c8116100c1b13aded62b0c2711e3aa045b60c77bb40f57f5f1a71f77e9c39`.
- Sem alterações no formato dos projetos ou no processamento de áudio.
- `git diff --check`: aprovado.
- [Captura do menu](menu-0.21.2.png) no AVD ATMyTrack_OriginalKey_API35 (Android 15 x86_64), inspecionada: ícone e nome alinhados, texto sem cortes, navegação visível.
- Teste legado `Tools07UiTest#menuTunerPermissionLifecycleAndPreferences`: passou pela navegação do menu e afinador, mas falhou posteriormente na linha 81 por procurar o QR Pix antigo, ausente na tela atual. Não foi considerado aprovação integral desse teste; o código legado não foi alterado nesta entrega.
- Execução isolada de `CrossMusic20Test#globeStartsWithAndreAndShowsRequestedContributionAndBeneficiary`: falhou antes do clique no menu, sem hierarquia Compose disponível na Activity. Portanto, a navegação completa desse teste não foi certificada nesta execução. A aprovação visual do cabeçalho se baseia na captura obtida pelo primeiro teste.

APK: `dist/ATMyTrack-debug.apk` e cópia `dist/ATMyTrack-0.21.2-debug.apk`, 13.290.683 bytes.

SHA-256: `cf60ba899fe3b212a8a0a9f5915a321e961eb03a7df656aee0a36de481ced400`.

Ambiente de build: Windows, JDK 17, SDK 36. Sem benchmark ou teste em celular físico nesta entrega.

[Release v0.21.2](https://github.com/AndreSouzaTeles/ATMyTrack/releases/tag/v0.21.2).
