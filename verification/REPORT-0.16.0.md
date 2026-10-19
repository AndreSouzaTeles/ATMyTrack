# ATMyTrack_v2 0.16.0 (29) — seis fontes e correção visual

## Resultado

Spleeter de duas faixas foi substituído por HT-Demucs 6 fontes. A rede recebe áudio estéreo 44,1 kHz e estima seis sinais distintos na ordem contratual Bateria, Baixo, Outros, Voz, Guitarra, Piano. Não são filtros por frequência, duplicatas ou renomeações de um único instrumental. Não identifica arbitrariamente cada timbre existente: pads, órgãos, sintetizadores e instrumentos fora das categorias podem ficar em Outros. Guitarra e piano continuam sujeitos a vazamentos e artefatos. Não há garantia de separação perfeita.

Interface menos branca: fundo #111315, painel #191D21, controles #232A31, texto #BAC7D3, secundário #9BA9B6 e azul #7398BA. Botões preenchidos escuros, contornos discretos e detalhes da separação recolhíveis. Ícone e arte aprovados na 0.15.2 mantidos byte a byte. Pacote e assinatura da instalação ATMyTrack_v2 0.15.3 preservados para atualizar por cima dela.

## Modelo, segurança e memória

- Arquivo `htdemucs_6s_fp16weights.onnx`, 136.428.532 bytes (130,11 MiB), SHA-256 `7ce55792e2231c93fbf92de95f5fd5b3a5e6c89f7db690dfd693e8f1dce56869`.
- Revisão fixada `49df9b6989cf2150840ea65b0bef77a2e471b678` do repositório [StemSplitio/htdemucs-6s-onnx](https://huggingface.co/StemSplitio/htdemucs-6s-onnx/tree/49df9b6989cf2150840ea65b0bef77a2e471b678). Não é usado código remoto do repositório no Android, somente o arquivo de pesos verificado.
- Entrada float32 `[1,2,343980]`; saída `[1,6,2,343980]`. ONNX Runtime 1.23.2 na CPU, duas threads. Pesos fp16 não significam execução fp16 nem metade da RAM de inferência.
- Blocos de 7,8 s com sobreposição de 25%, janelas lineares, contexto à esquerda e normalização dos pesos. Escrita WAV em disco, sem carregar uma música inteira na RAM. Contagem exata de frames, mesmo relógio entre todas as faixas. Sem limitação/normalização independente dos ganhos.
- Modelo significativamente mais pesado que Spleeter. Processo separado chegou a 2.339.295 KiB de PSS (~2,23 GiB) em uma amostra no emulador. Guarda de entrada: processo 64 bits, memória total compatível com aparelhos de 6 GB, pelo menos 2.500 MiB disponíveis e ausência de lowMemory. Isso não garante que o sistema manterá a operação em todos os aparelhos.
- Download com retomada, tamanho e SHA-256, percentuais por etapa. Música permanece no dispositivo; após instalar, inferência offline. Reserva de download: 195 MiB. Preparo + seis WAVs: aproximadamente 141,4 MiB/minuto mais 64 MiB.
- Licenças MIT do Demucs e da conversão incluídas em `app/src/main/assets/licenses`. [Demucs original](https://github.com/facebookresearch/demucs), [documentação da conversão](https://github.com/StemSplit/demucs-onnx/blob/main/docs/models.md).

## Compatibilidade

Formato JSON dos projetos permanece igual. Importação aceita seis faixas novas e resultados legados Voz/Instrumental, identificados pelo marcador de conclusão. Nenhuma stem já importada é apagada ou reprocessada. O modelo novo tem diretório versionado próprio. Não migra dados entre o pacote original e o v2.

O APK atualiza ATMyTrack_v2 (`com.atmytrack.app.v2`) assinado neste computador. O pacote original continua dependendo da chave antiga.

## Validação de áudio

Build, lint e 59 testes JVM aprovados, sem falhas. Três testes instrumentados aprovados: DemucsSixStemTest (68,082 s) e regressão SeparationIntegrationTest + ModelInstallProgressUiTest (281,495 s). Os últimos ajustes de aparência preservam o código de áudio testado; o APK final foi reinstalado e inspecionado visualmente. Instalação incremental com `adb install -r` aprovada. Certificado mantido: SHA-256 `656039fc2edf595be9c8e7042b1f7b197ad319042bc8de73ff52c4bd59a3d01c`.

Computador AMD Ryzen AI 9 465, 10 núcleos/20 threads, Windows. Emulador Google Android 15/API 35 x86_64, 6 GiB, quatro núcleos, Windows/WHPX; referência Python/ONNX Runtime 1.23.2 CPU no mesmo computador. Fixture upstream `audio_example.wav`, estéreo PCM16 44,1 kHz, 481.536 frames (10,919184 s), 1.926.188 bytes, SHA-256 `5e66999e4f136b376227d790e12e2ede769844c656a286e841a0e9816a19f61a`, mantida em `.cache/atmytrack-android/fixtures` e `/data/local/tmp/atmytrack-separation` no emulador.

O teste com o modelo real produziu seis WAVs finitos/distintos, com 481.536 frames e 44,1 kHz cada, verificou a integridade da origem, executou a prévia de voz, importou as seis tracks e reproduziu o projeto no mixer. Execução do separador: 51.424 ms no emulador; rodada instrumentada completa: 68,082 s. Não é promessa de tempo no celular.

Cada saída Android foi comparada amostra por amostra com execução Python do mesmo peso e mesma segmentação: **diferença máxima e RMS iguais a zero em todas as seis faixas**, registrada em [demucs-parity-0.16.0.json](demucs-parity-0.16.0.json). Isso valida a implementação para essa fixture; não mede qualidade perceptiva ou SDR contra stems originais e não prova a qualidade de músicas não fornecidas pelo usuário.

A primeira tentativa detectou leitura de blocos maiores que a capacidade do leitor PCM, corrigida antes da entrega. Testes JVM verificam sobreposição nas bordas/início/fim e compatibilidade dos resultados antigos. O download real do novo modelo foi aprovado com percentual numérico e conclusão apenas após SHA-256.

A regressão ampliada executou duas músicas reais em modo avião (10,919 s e 26,102 s), importação em projeto novo e adição em projeto existente, preservação de DCA/BUS/ganhos, cancelamento de arquivo longo, encerramento do processo da IA, recuperação e rejeição de peso corrompido. A segunda fixture, `qi-feng-le-zh.wav`, contém 1.151.115 frames e 4.604.504 bytes, SHA-256 `90a083c7d59699c47497e02f807a6c311d4fa0758c88ec64073efd2ff865f2c3`. A segunda música levou 62.743 ms no separador do emulador, sem promessa para celular.

Capturas do aplicativo: [visual final](visual-0.16.0.png) e [projeto com seis faixas](demucs-0.16.0.png).

Logs locais ignorados pelo Git: `build-0.16.0.log`, `demucs-0.16.0.log`, `regression-0.16.0.log`, `android-0.16.0.log`.

APK: `dist/ATMyTrack_v2.apk`, cópia versionada `dist/ATMyTrack_v2-0.16.0.apk` e cópia idêntica no caminho habitual `dist/ATMyTrack-debug.apk`, com SHA-256. Release `v0.16.0`.
