# ATMyTrack_v2 0.17.0 (30) — dourado e remoção da separação

## Resultado

Separação de instrumentos removida a pedido do usuário: tela, item de menu, controlador, serviço/processo, inferência, instalador de modelos, bloqueios de reprodução associados, ONNX Runtime, licenças exclusivas, testes e ferramentas específicos. Relatórios/instaladores antigos dessa função foram retirados da cópia atual do projeto. Nenhum modelo ou download adicional é necessário para usar o player.

Identidade refeita em preto/grafite, cinza quente e dourado acetinado discreto. Novo ícone baseado na referência enviada em 19/10/2026, com play e barras de áudio. Logo, menu, splash, foreground adaptativo e PNGs de densidade usam a mesma arte. [Paleta, arquivos e prompt](../branding/BRAND-0.17.0.md). O ícone adaptativo possui margem proporcional para manter o símbolo dentro das máscaras do Android.

## Compatibilidade e limpeza de dados antigos

Pacote `com.atmytrack.app.v2`, versão 0.17.0 (30), certificado SHA-256 `656039fc2edf595be9c8e7042b1f7b197ad319042bc8de73ff52c4bd59a3d01c`, mantidos para atualizar a instalação v2 anterior. Biblioteca JSON versão 1 e formato das tracks preservados. DSP do mixer/tom/velocidade/PAD não foi substituído.

`RetiredStorageCleanup` remove modelos baixados e arquivos temporários/resultados não referenciados. Mantém WAVs e variantes de áudio já referenciados por projetos salvos. A limpeza só é executada após carregar a biblioteca; não percorre destinos de links simbólicos nem apaga arquivos fora das pastas antigas privadas do aplicativo. As menções restantes a essas pastas no código são exclusivamente de limpeza/compatibilidade, sem oferecer separação de áudio.

Verificação de atualização real no emulador: os **18 WAVs referenciados permaneceram idênticos byte a byte**, a biblioteca JSON manteve SHA-256 `bae8796084c5a38333c06cc19e888bcce8889a7e06a4c5ba5fcac7a211374c96` e o diretório de modelos foi removido (171.724 KiB antes). Essa comparação foi feita antes dos testes interativos que alteram dados de teste. Não foi acessado aparelho físico.

## Validação

- Build limpo inicial, build final, lint e **52 testes JVM aprovados**. Inclui teste da limpeza, preservação das fontes originais e idempotência.
- **Sete testes Android aprovados** no emulador Google Android 15/API 35 x86_64, 6 GiB, quatro núcleos, Windows/WHPX: BrandAssetsTest (1,602 s); três testes de seções/paleta, dois testes de cadeados/PAD e um de arraste da timeline (rodada de 27,531 s).
- Instalação incremental `adb install -r` aprovada. Menu inspecionado: projetos e afinador presentes; antiga opção de separação ausente.
- PNGs 48/72/96/144/192/512 e prévias circular, arredondada e adaptativa renderizados pelo próprio Android, em `branding/0.17.0`.
- Inspeção visual de [player](gold-0.17.0.png), [menu](menu-0.17.0.png) e [ícone adaptativo](../branding/0.17.0/adaptive.png).
- Arquivo APK inspecionado: sem `.onnx`, bibliotecas ONNX Runtime ou classes do antigo serviço/inferência. Bibliotecas nativas: DSP Signalsmith e AndroidX Graphics Path, nas três ABIs mantidas.
- Identidade dos arquivos web locais também sincronizada. Sintaxe JavaScript aprovada; testes Node de música (594 casos), waveform e PCM aprovados. Não houve implantação do site público neste trabalho.

## Entrega

APK final: **13.159.141 bytes**, ante 70.546.839 bytes na 0.16.0. SHA-256 `b2174785cd895e01062d75ad23297047395f81582754687debbd5b308d3bc198`.

Arquivos idênticos: `dist/ATMyTrack-debug.apk`, `dist/ATMyTrack_v2.apk` e `dist/ATMyTrack_v2-0.17.0.apk`. Release `v0.17.0` com instalador versionado e SHA-256.

Logs locais ignorados pelo Git: `build-0.17.0.log`, `brand-0.17.0.log`, `android-0.17.0.log`, `upgrade-before-0.17.0.log`. Não houve benchmark de velocidade no celular.
