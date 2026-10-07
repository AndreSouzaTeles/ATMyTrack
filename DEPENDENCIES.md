# Dependências e licenças

Versões fixadas, verificadas em 06/10/2026. Bibliotecas AndroidX/Kotlin são projetos mantidos por Google/JetBrains. As versões escolhidas formam a base compilada desta entrega; não são uma alegação de serem as versões mais recentes.

| Componente | Versão | Finalidade | Licença / fonte |
|---|---|---|---|
| Android Gradle Plugin | 9.0.1 | Build Android e Kotlin integrado 2.2.10 | Apache 2.0, [AOSP](https://source.android.com/docs/setup/about/licenses) |
| Gradle Wrapper | 9.3.1 | Build reprodutível | Apache 2.0, [Gradle](https://github.com/gradle/gradle/blob/master/LICENSE) |
| Kotlin / Compose compiler | 2.2.10 | Linguagem e compilação Compose | Apache 2.0, [Kotlin](https://kotlinlang.org/docs/faq.html) |
| Compose BOM | 2025.08.01 | Conjunto coerente de UI/Foundation/Material 3 | Apache 2.0, [AndroidX](https://android.googlesource.com/platform/frameworks/support/+/androidx-main/LICENSE.txt) |
| Activity Compose | 1.10.1 | Activity e contratos SAF | Apache 2.0, AndroidX |
| Lifecycle ViewModel | 2.9.2 | Estado de UI e tarefas assíncronas | Apache 2.0, AndroidX |
| Coroutines Android | 1.10.2 | Trabalho fora da thread principal, StateFlow | Apache 2.0, [fonte](https://github.com/Kotlin/kotlinx.coroutines/blob/master/LICENSE.txt) |
| JUnit | 4.13.2 | Apenas testes JVM | EPL 1.0, [fonte](https://github.com/junit-team/junit4/blob/main/LICENSE-junit.txt) |
| Robolectric | 4.14.1 | Apenas testes de persistência Android | MIT e avisos incluídos no projeto, [fonte](https://github.com/robolectric/robolectric/blob/master/LICENSE) |
| AndroidX Test Runner / Ext JUnit | 1.6.2 / 1.2.1 | Apenas testes no dispositivo/emulador | Apache 2.0, [fonte](https://github.com/android/android-test/blob/main/LICENSE) |
| Compose UI Test | BOM acima | Apenas testes de interação Compose | Apache 2.0, AndroidX |

MediaExtractor, MediaCodec, AudioTrack, AtomicFile, SharedPreferences e org.json são APIs da plataforma Android. A síntese do click, soma de canais e ganhos são operações básicas do mixer.

## DSP incluído na 0.3.0

- [Signalsmith Stretch](https://github.com/Signalsmith-Audio/signalsmith-stretch), MIT, commit `a670068d9aeb64913331d5cc29337b19a457a7df` (25/09/2026): pitch musical com preservação da duração. Projeto com atividade recente verificada antes da integração; API C++ header-only compilada via JNI/NDK nas três ABIs do APK.
- [Signalsmith Linear](https://github.com/Signalsmith-Audio/linear), MIT, tag 0.6.4, commit `de55e6a50ffcf6f8f43f649692d94691c7025151`: FFT/rotinas utilizadas pelo Stretch.
- Fontes fixadas em `app/src/main/cpp/vendor`; textos MIT preservados ali e em `app/src/main/assets/licenses`, incluídos no APK. A licença permite distribuição comercial mediante preservação desses avisos. Não há dependência GPL ou serviço pago de pitch.
- Compatibilidade de build verificada em arm64-v8a, armeabi-v7a e x86_64, com NDK 28.2.13676358 e CMake 3.22.1. Funcionamento do DSP medido no Android x86_64; isso não certifica todos os dispositivos/ABIs físicos.

A identidade atual usa a imagem PNG fornecida pelo usuário em 07/10/2026. As identidades vetoriais anteriores são preservadas somente como histórico.

## Ferramentas de teste da 0.4.0 (fora do APK)

`tools/generate-fixtures.py` gera sinais sintéticos originais e usa `imageio-ffmpeg` 0.6.0 (BSD-2-Clause) instalado apenas em `tools/fixture-runtime`, ignorado pelo controle de versão. O executável Windows fornecido é FFmpeg 7.1 essentials de gyan.dev, cuja própria saída `-L` declara GPL v3 ou posterior. Ele serve exclusivamente para produzir fixtures WAV/MP3/M4A/AAC/FLAC/Vorbis/Opus no computador de desenvolvimento; não é vinculado nem empacotado no aplicativo Android. Para reproduzir: `python -m pip install imageio-ffmpeg==0.6.0 --target tools/fixture-runtime`, depois `python tools/generate-fixtures.py`.

A correção do relógio ADTS foi conferida contra `AACExtractor.cpp` do AOSP; os comportamentos de seek foram investigados em `MP3Extractor.cpp` e `OggExtractor.cpp` (referências Apache 2.0 guardadas em `verification`). O APK continua usando os decoders do Android, sem adicionar FFmpeg.

## Referências da importação 0.2.0

Nenhuma dependência nova. A leitura de PCM/container segue [RIFF](https://learn.microsoft.com/en-us/windows/win32/xaudio2/resource-interchange-file-format--riff-) e [WAVEFORMATEXTENSIBLE](https://learn.microsoft.com/en-us/windows-hardware/drivers/ddi/ksmedia/ns-ksmedia-waveformatextensible); o processamento em lotes usa a API documentada de [MediaCodec](https://developer.android.com/reference/android/media/MediaCodec).

## QR Code 0.6 (ferramentas fora do APK)

qrcode 8.2 (BSD), Pillow 12.3.0 (MIT-CMU) e zxing-cpp 3.1.1 (Apache-2.0), instalados apenas em `tools/qr-runtime`, geram e verificam o PNG do Pix. Nenhuma biblioteca de pagamentos ou código dessas ferramentas entra no APK; apenas a imagem gerada. Reproduzir com `tools/generate-support-qr.py`.
