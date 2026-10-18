# ATMyTrack 0.15.0 (25) — separação Android experimental

Entrega de 18/10/2026. Implementação real de **Voz + Instrumental**, gratuita, local e offline depois da instalação dos pesos. Não oferece quatro stems nem anuncia qualidade equivalente ao Moises. A validação executada é em Android 15 x86_64 emulado; ARM64 físico e avaliação musical com referências continuam pendentes.

## Modelo e licenças

Spleeter 2 stems, pesos fp16 convertidos pelo mantenedor do sherpa-onnx, revisão HF imutável `93ba771920ade509f8cbd6825b1a90856c797e08`. Dois modelos, **39.362.041 bytes** no total, não incluídos no APK. Inferência real com ONNX Runtime Android 1.23.2, CPU, duas threads intra-op e uma inter-op.

Os autores do [Spleeter declaram MIT para código e modelos pré-treinados](https://github.com/deezer/spleeter/blob/master/paper.md); [licença](https://github.com/deezer/spleeter/blob/master/LICENSE). O runtime é [MIT](https://github.com/microsoft/onnxruntime/blob/v1.23.2/LICENSE). O [exportador de referência](https://github.com/k2-fsa/sherpa-onnx/blob/v1.13.8/scripts/spleeter/export_onnx.py) pertence ao sherpa-onnx, Apache-2.0. Licenças e avisos estão nos assets do APK. Uso comercial e redistribuição dos pesos Spleeter são permitidos por MIT, preservando os avisos. Não se estende essa conclusão a MDX ou Demucs.

[Auditoria, comparação dos modelos, hashes e arquitetura completos](SEPARATION-DESIGN.md).

## Implementado

- Menu **Separar Instrumentos**, seleção de uma música, modo identificado como voz/instrumental, confirmação do tamanho do download e das condições de uso de armazenamento/rede.
- Download HTTPS de origem fixada, retomada Range validada, conferência de tamanho e SHA-256, instalação privada e versão registrada. Nenhum áudio é enviado; sem API paga, chave ou atualização automática.
- Validação do áudio e do disco, decodificação/resampling em disco, tensores e STFT em blocos fixos. WAV float32 estéreo 44,1 kHz com frames e alinhamento comuns; headroom mantido, sem clipping de conversão ou normalização independente.
- Processo Android separado, limitação de threads, checagem conservadora de memória/64 bits, cancelamento, tratamento de falhas, limpeza de parciais e recuperação de resultados completos.
- Status e progresso mensurável fixos no diálogo, Cancelar sempre visível. Carga de modelo/preparação sem progresso global artificial. Sair do app pede cancelamento; não há processamento persistente em background.
- Prévia individual, exportação de WAV pelo seletor Android, importação em novo projeto ou adição ao atual. Importação atual preserva stems anteriores, faders, PAN, master, DCA/BUS e demais ajustes. Novas faixas começam no frame zero; exige tom original/100% antes de adicionar.
- Biblioteca mantém schema 1; originais preservados. Mixer e PAD ficam protegidos durante a IA; projeto importado usa o motor multitrack existente.
- APK paralelo para testar sem remover a instalação antiga enquanto a chave de assinatura anterior não está disponível.

## Testes realmente executados

- Build nas três ABIs, **56 testes JVM sem falhas**. Incluem FFT/overlap, WAV float e frames exatos, headroom, rejeição de inferência inválida, SHA-256 corrompido, armazenamento insuficiente por injeção de quota e cancelamento de verificação preservando os bytes existentes, além da suíte anterior de mixer/projetos/DSP.
- Lint debug/preview sem erros: 34/37 avisos, incluindo sugestões de KTX, uso de espaço alocável, atualização de dependências e avisos anteriores de ícone/Compose. As verificações de disco usam espaço livre conservador, sem contar cache potencialmente removível como garantido.
- Teste Android completo passou em **126,237 s**, com dois exemplos e arquivo musical de 5min27s inteiro. Modelo real, modo avião, stems geradas, conferência amostra a amostra, prévia com saída de áudio ativa, importação nova/atual, reprodução no relógio do mixer e persistência/reabertura do projeto.
- Download real interrompido após **1.107.612 bytes** e retomado até obter os dois hashes esperados, em execução anterior da mesma suíte.
- Durante a inferência offline, `cmd connectivity airplane-mode` retornou `enabled`; `dumpsys connectivity` mostrou **Active default network: none**.
- Cancelamento de arquivo longo durante etapa de inferência não publicou stems parciais e liberou a proteção de reprodução.
- Processo `:separation` encerrado deliberadamente durante outro trabalho longo (`run-as ... kill -9`). O processo do mixer permaneceu vivo, os IDs dos projetos ficaram intactos, o erro foi informado e a próxima abertura removeu o parcial e revalidou o modelo.
- Um byte dos pesos foi alterado mantendo o tamanho; o modelo foi rejeitado. O arquivo correto foi restaurado pelo teste.
- Hash do exemplo original antes/depois igual. Arquivos originais são abertos para leitura; temporários usam pastas privadas próprias.
- Um primeiro ensaio do harness expirou por não avançar o relógio de recomposição Compose; foi corrigido para usar `compose.waitUntil`, e os ensaios seguintes passaram. Isso ocorreu antes de carregar o modelo, não foi uma separação simulada.
- Interface final do APK normal verificada novamente: fluxo completo passou em **46,641 s**, inclusive prévia e confirmação de que **CANCELAR** permanece visível durante a inferência. Os dois pacotes foram instalados simultaneamente no emulador; a mesma integração passou também no APK paralelo em **51,241 s**, com download/retomada, inferência offline, prévia, mixer, cancelamento, interrupção e rejeição de pesos corrompidos.

Exportação pela interface SAF, formatos comprimidos dentro do novo separador, perda total do processo principal, sensores térmicos/bateria, pressão real de armazenamento do Android e execução ARM64 **não foram todos exercitados nesta sessão**. A quota insuficiente foi testada de forma determinística, sem encher o disco do dispositivo. Não há teste auditivo/SDR contra stems de referência nem alegação de ausência de regressões em todos os aparelhos.

## Ambiente, arquivos e medições

Host Windows com AMD Ryzen AI 9 465, 32 GB RAM; emulador Google Android 15/API 35 x86_64, **6 GiB configurados, quatro vCPUs**, WHPX e renderização por software. CPU do runtime com duas threads; sem NNAPI/GPU. Arquivos WAV estéreo PCM16 a 44.100 Hz, processados por inteiro. São medições de emulador, **não previsão para o celular**.

| Arquivo | Bytes de origem | Duração | Tempo do serviço* | Tempo incluindo verificação do teste** | Erro máximo `origem − voz − instrumental` |
| --- | ---: | ---: | ---: | ---: | ---: |
| audio_example.wav | 1.926.188 | 10,919 s | 4,262 s | 4,982 s | 0,0000371933 |
| qi-feng-le-zh.wav | 4.604.504 | 26,102 s | 5,898 s | 6,762 s | 0,00003606081 |
| long.wav | 57.784.364 | 327,576 s | 76,543 s | 88,781 s | 0,00003886223 |

\* O cronômetro começa depois da verificação inicial dos pesos/checagem de memória e inclui preparo, carga das sessões, inferência e escrita de WAV; não inclui download. Não é só tempo de `session.run`.

\** Cronômetro do teste inicia antes da solicitação e termina após ler e conferir todas as amostras das duas saídas. Na primeira execução com sessões frias, os exemplos curtos levaram 4,935/6,678 s no serviço; há variação de cache/JIT/host.

`long.wav` foi criado repetindo 30 vezes os bytes PCM do primeiro exemplo: testa duração/memória/limites de bloco, **não representa uma terceira música nem um benchmark de qualidade musical**. Cada stem longa possui 14.446.080 frames e 115.568.684 bytes (WAV float). A origem de 5min27s foi concluída antes dos testes adicionais de cancelamento/interrupção.

Maior PSS amostrado a aproximadamente cada três segundos: **225094 KiB** (**219.82 MiB**) no processo `com.atmytrack.app:separation`, incluindo as execuções normais registradas. É máximo amostrado, não pico absoluto nem consumo total do aparelho; o processo paralelo não foi monitorado por essa série. O heap nativo registrado ao final das sessões foi aproximadamente 12,5 MB, após liberar as redes; não deve ser confundido com memória durante inferência. Temperatura não foi medida de forma representativa em hardware físico.

Fontes dos dois exemplos: [release de modelos do sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx/releases/tag/source-separation-models). Áudios e stems de teste **não são distribuídos no repositório nem no APK**. SHA-256 das origens:

| Arquivo | SHA-256 |
| --- | --- |
| audio_example.wav | `5e66999e4f136b376227d790e12e2ede769844c656a286e841a0e9816a19f61a` |
| qi-feng-le-zh.wav | `90a083c7d59699c47497e02f807a6c311d4fa0758c88ec64073efd2ff865f2c3` |
| long.wav | `04be1248656feb373a271cd62b65ed53e363941d8b2c4edb8a243302cf1fe15c` |

Dados do teste: [PSS amostrado](separation-memory-0.15.0.csv), [resultados estruturados](SEPARATION-RESULTS-0.15.0.json), [captura da interface final](separation-0.15.0.png). Logs locais de build/instrumentação ficam em `verification/*.log` e são ignorados pelo Git.

## Arquivos principais

Novos: `separation/ModelStore.kt`, `SeparationDsp.kt`, `SeparationEngine.kt`, `FloatWav.kt`, `SeparationService.kt`, `SeparationController.kt`, `SeparationLock.kt`; `ui/SeparationDialog.kt`; `SeparationTest.kt`, `SeparationIntegrationTest.kt`; quatro arquivos de licença/notices; `tools/build-android.ps1`, `tools/monitor-separation.py`, este relatório e documento de arquitetura.

Alterados: `app/build.gradle.kts` (versão/runtime/variante de teste), `AndroidManifest.xml` (INTERNET, serviço privado e nome por variante), `PlayerViewModel.kt` (integração/persistência/limpeza), `AudioEngine.kt` e `PadEngine.kt` (proteção de reprodução), `SpeedAndMenu.kt`/`PlayerScreen.kt` (menu/tela), README. Ícone/logo/splash e engine de pitch/tempo não foram substituídos. Não houve alteração da versão web.

## Instalação e assinatura

APK normal: `dist/ATMyTrack-debug.apk`, pacote `com.atmytrack.app`, versão **0.15.0 (25)**, três ABIs. SHA-256: `2783aee4289f81f20e311518f9a8bc222007908b6dc70c6e0f62a26230c53969`.

APK paralelo: `dist/ATMyTrack-preview.apk`, pacote `com.atmytrack.app.preview`, nome **ATMyTrack Teste IA**, versão 0.15.0-preview. SHA-256: `7406f3fd97acb80092d13239d0e40fd814d067d4d9564f931610ac1cfcc69c24`. Pode ser instalado ao lado do aplicativo anterior. Possui armazenamento/modelo/biblioteca próprios; importe os arquivos originais pelo seletor, sem remover o ATMyTrack existente.

Certificado da release pública 0.14.4: `1f9c8116100c1b13aded62b0c2711e3aa045b60c77bb40f57f5f1a71f77e9c39`. Certificado deste computador: `656039fc2edf595be9c8e7042b1f7b197ad319042bc8de73ff52c4bd59a3d01c`.

A atualização do APK público 0.14.4 com a nova chave foi testada e recusada: **INSTALL_FAILED_UPDATE_INCOMPATIBLE**. Isso independe da compatibilidade do schema. A chave antiga não está nesta cópia; o usuário informou que talvez esteja no outro PC. Caminho comum: `%USERPROFILE%\.android\debug.keystore` (Linux/macOS: `~/.android/debug.keystore`). A chave nova fica nesse caminho neste computador. Não é incluída no Git ou na release.

**Não desinstale o aplicativo antigo como solução de assinatura**, pois os projetos privados seriam removidos. Para atualização no mesmo pacote preservando dados, é necessário localizar a chave cujo certificado corresponde ao hash antigo e recompilar/assinar com ela. Enquanto isso, o APK paralelo permite testar a IA preservando a instalação original.

## Como testar no celular

1. Instale o APK paralelo **ATMyTrack Teste IA**, mantendo o aplicativo original. Para o APK normal, observe a pendência de assinatura acima.
2. Abra menu → **Separar Instrumentos**. Use aparelho Android 64 bits com memória disponível; recomendado 4 GB ou mais.
3. Toque **BAIXAR MODELO GRATUITO** e confirme os 39.362.041 bytes. Reserve pelo menos 102 MiB para os pesos; use rede disponível nessa etapa.
4. Escolha um arquivo de áudio. Reserve aproximadamente 60,6 MiB/min + 64 MiB para processamento, além dos pesos e eventual cópia da origem.
5. Inicie, mantenha a tela aberta e acompanhe status/progresso. Se necessário, use **CANCELAR**; uma inferência já iniciada pode precisar terminar.
6. Ouça Voz/Instrumental, exporte pelos botões **WAV** ou importe no mixer. Para adicionar ao projeto atual, volte ao tom original/100%; as novas faixas entram no frame zero.
7. Depois do download, repita com arquivo local em modo avião. Avalie vazamentos, artefatos e qualidade auditiva antes de usar os resultados em apresentação.

Limitações: dois stems, banda vocal do modelo de aproximadamente 11 kHz, possíveis vazamentos/artefatos, WAV float com limite de 4 GiB por stem, sem retomada da inferência em background e sem certificação física ARM64/térmica. Cancelar não equivale a interrupção instantânea de um operador ONNX. Estes pontos mantêm a entrega como **experimental**, mesmo com execução real e testes técnicos aprovados.

## Reproduzir a validação

`tools/build-android.ps1` usa JAVA_HOME/ANDROID_HOME ou o runtime local em `%USERPROFILE%\.cache\atmytrack-android`. Requer JDK 17, SDK/build-tools 36, NDK 28.2.13676358 e CMake 3.22.1. Executa testes JVM, lint e exporta os dois APKs. Para os testes Android, prepare `/data/local/tmp/atmytrack-separation` com os exemplos acima e `long.wav` (30 repetições PCM), e execute a classe `com.atmytrack.app.SeparationIntegrationTest` com AndroidJUnitRunner. `-PatmytrackTestBuildType=preview` permite compilar `assemblePreviewAndroidTest` para o pacote paralelo. Os APKs de instrumentação anteriores precisam ser removidos ao alternar, pois o provider de fixtures legado usa a mesma authority; os dois APKs **do aplicativo** coexistem normalmente. O teste modifica somente os dados de teste, modo avião (restaurado ao terminar) e o processo privado da IA.

O argumento de instrumentação `skipLong=true` repete os testes de interface/integração/cancelamento sem reprocessar integralmente os 5min27s. O ensaio longo completo foi executado antes do ajuste visual final; o núcleo da IA permaneceu igual, e a interface final foi verificada novamente com os exemplos curtos.
