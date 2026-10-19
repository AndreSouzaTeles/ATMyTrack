> Histórico da implementação 0.15.0. A partir de 0.16.0, o modelo ativo é Demucs 6 fontes. Veja [REPORT-0.16.0](REPORT-0.16.0.md) para contrato, memória e validação atuais.

# Separação local Android — 0.15.0

## Auditoria (Etapa A)

- Kotlin, Compose Material 3, coroutines; API mínima 26, target/compile 36, Java 17, AGP 9.0.1. APK mantém arm64-v8a, armeabi-v7a e x86_64. IA exige processo 64 bits; o mixer em 32 bits permanece disponível.
- `AudioEngine`/`OutputPump`: um relógio multitrack, AudioTrack, leitores PCM em blocos; `PlaybackCache`, `StreamingReader`/MediaCodec e `ResampledReader` preparam/decodificam áudio. Tom/velocidade usam Signalsmith Stretch via JNI/C++.
- `AudioImporter` aceita SAF/pastas/arquivos, preserva as origens e registra formato, duração e fingerprint. O separador reutiliza o leitor, sem mudar o fluxo existente de importação.
- `ProjectStore` salva JSON versão 1 atomicamente. Stems geradas usam os campos existentes de PCM, formato, sourceRate/sourceFrames. Nenhuma migração de schema.
- ONNX Runtime tem biblioteca JNI própria. Não substitui Signalsmith nem os decoders existentes. Não são usados NNAPI, GPU ou bibliotecas CUDA. Não há alegação de aceleração.

## Escolha do modelo (Etapas A/B)

| Candidato | Evidência / decisão |
| --- | --- |
| Spleeter 2 stems, conversão ONNX fp16 do mantenedor sherpa-onnx | Escolhido: dois U-Nets de 19.681.017 e 19.681.024 bytes; entrada float32 `[2,1,512,1024]`, opset 13. Os autores declaram explicitamente MIT para código **e modelos pré-treinados**. Exportação de dois stems documentada e passível de teste no Android. |
| Spleeter 4 stems | O projeto original tem quatro stems, mas a conversão pesquisada/documentada oferece dois. Uma nova conversão e validação de quatro redes exigiria outra etapa. Esta entrega anuncia somente voz/instrumental. |
| MDX-Net/UVR | Existem modelos ONNX, mas não foi estabelecida nesta auditoria uma cadeia inequívoca de licença comercial/redistribuição para os pesos específicos. Não são distribuídos ou oferecidos pelo aplicativo. |
| Demucs | Código MIT não é prova suficiente da licença de todo checkpoint. Adaptação, operadores e memória de modelos híbridos também exigem prova Android própria. Não foi escolhido ou redistribuído. |

Fontes primárias:

- [Spleeter, artigo dos próprios autores](https://github.com/deezer/spleeter/blob/master/paper.md), seção de licença explícita de código/pesos; [licença MIT](https://github.com/deezer/spleeter/blob/master/LICENSE).
- [Exportador ONNX](https://github.com/k2-fsa/sherpa-onnx/blob/v1.13.8/scripts/spleeter/export_onnx.py), [rede](https://github.com/k2-fsa/sherpa-onnx/blob/v1.13.8/scripts/spleeter/unet.py), [referência de processamento](https://github.com/k2-fsa/sherpa-onnx/blob/v1.13.8/scripts/spleeter/separate_onnx.py).
- [Modelos documentados](https://k2-fsa.github.io/sherpa/onnx/source-separation/models.html), [pesos com revisão imutável](https://huggingface.co/csukuangfj/sherpa-onnx-spleeter-2stems-fp16/tree/93ba771920ade509f8cbd6825b1a90856c797e08).
- [ONNX Runtime Android](https://onnxruntime.ai/docs/get-started/with-java.html), [MIT](https://github.com/microsoft/onnxruntime/blob/v1.23.2/LICENSE), notices em `app/src/main/assets/licenses`.
- [Demucs oficial](https://github.com/facebookresearch/demucs), [UVR oficial](https://github.com/Anjok07/ultimatevocalremovergui).

Não se presume que o formato ONNX garanta compatibilidade. O resultado da execução no Android e os limites da validação são registrados no relatório da entrega. Não foi medido SDR de qualidade contra stems de referência; os resultados publicados pelos autores não são benchmarks deste APK.

### Manifesto dos pesos

Revisão HF: `93ba771920ade509f8cbd6825b1a90856c797e08`. Versão interna `spleeter-2stems-fp16-93ba771`. Total **39.362.041 bytes**.

| Arquivo | SHA-256 |
| --- | --- |
| vocals.fp16.onnx | `24cef84aedcd1fe87c0b743ef3370ad34dc1fabf6c9014d6128a75a538c7b668` |
| accompaniment.fp16.onnx | `d14cea55793cc531a5875f5f4da08207d1c5ab9292e8e0099a104eecb014fcc0` |

O aplicativo só conecta ao repositório fixo do modelo e aos redirecionamentos HTTPS do host de arquivos. Não aceita URLs/modelos arbitrários nem executa código remoto. Não verifica atualizações automaticamente. Arquivos parciais podem ser retomados via Range, se o servidor responder corretamente; resposta 200 reinicia o arquivo. Arquivo instalado só é substituído após conferir tamanho e hash. A integridade é conferida em cada execução.

## Processamento e integração (Etapas C/D)

- Serviço privado em processo `:separation`, ligado à tela. A inferência não pertence ao callback de áudio. Saídas e modelos em armazenamento privado; INTERNET é a única permissão adicionada.
- Durante a operação a tela permanece acesa. Sair do aplicativo solicita cancelamento; não há serviço de longa duração em background. Retomar não reinicia um trabalho automaticamente. Fechar a tela após terminar libera o processo nativo.
- Os pesos fp16 usam ONNX Runtime Android **1.23.2**, CPU, duas threads intra-op/uma inter-op. FP16 descreve o armazenamento dos pesos; não promete execução aritmética FP16 na CPU. Arenas e padrões de memória desativados para limitar retenção.
- Requer Android de 64 bits, pelo menos 3 GiB de RAM total e 900 MiB disponíveis no início, sem sinal lowMemory. Isso é uma barreira conservadora, **não garantia de caber** em todos os aparelhos. A interface recomenda 4 GB ou mais. Falhas nativas do processo separado são informadas pela desconexão do Binder, preservando o mixer.
- Decodificação/resampling de origem mono/estéreo para estéreo 44.100 Hz em arquivo PCM privado. Número de frames deriva de uma única timeline; canais mono são duplicados. Cache em disco cresce com a duração; tensores/blocos de inferência têm tamanho fixo.
- STFT Hann periódica: FFT 4096, hop 1024, rede com 512 frames; 64 frames de contexto em cada lado e 384 frames centrais por bloco. Máscaras Wiener entre as duas predições; bins acima do limite vocal do Spleeter ficam no instrumental. A soma complementar preserva também a banda alta da origem.
- ISTFT com overlap-add normalizado, carregando caudas entre blocos. Padding é removido pelas posições absolutas da origem. Não há stretch ou deslocamento independente por stem.
- WAV IEEE float32 estéreo preserva headroom, sem saturar a conversão nem normalizar cada faixa separadamente. O mixer mantém seu limitador existente. WAV float pode exigir um editor compatível em outros aplicativos; não foi implementado export PCM16/24 ou RF64 (limite 4 GiB por WAV).
- Estimativa de disco: `frames * 24 + 64 MiB` (entrada float e duas saídas), além do modelo e de eventual cópia necessária do provedor. Espaço conferido antes do processamento e a cada bloco; o download também reserva espaço e verifica durante a transferência.
- Progresso de download usa bytes reais. Decodificação informa frames lidos; carga de modelo é indeterminada; separação informa a fração de frames de blocos já concluídos. Durante a primeira inferência, 0% pode permanecer parado por um período; não há timer de porcentagem.
- Cancelamento é cooperativo entre operações/blocos; um `session.run` em andamento pode precisar terminar. Parcial de áudio não é publicado. Processo interrompido deixa pasta incompleta que é removida na próxima abertura do serviço. Resultados completos não importados podem ser recuperados nessa abertura.
- Mixer e PAD são parados antes de separar. Um arquivo de exclusão e verificações na engine bloqueiam reprodução ao vivo durante a inferência, inclusive por controles da notificação. A proteção é removida ao terminar, cancelar ou detectar falha do processo.
- Prévia por stem usa MediaPlayer a -6 dB e libera recursos ao sair; exportação usa SAF CreateDocument, sem permissão geral de armazenamento.
- Novo projeto recebe duas stems privadas. Projeto atual preserva faders, mute/solo, DCA/BUS, PAN, rotas, locks e demais configurações; as duas novas faixas começam no frame zero. São exigidos tom original/100% e no máximo 62 tracks antes de adicionar. A taxa do projeto atual é tratada pelos leitores existentes. O usuário pode voltar a usar tom/velocidade após importar.
- Salvar/reabrir usa o schema existente. Resultados importados são marcados como consumidos após a gravação da biblioteca; limpeza posterior remove pastas consumidas sem referência. As origens nunca são apagadas.

## Limites de aceitação

Esta é uma implementação experimental de **dois stems**, não paridade de qualidade com Moises. Modelo realmente executado no Android é critério de validação separado do build. Qualidade musical, autonomia, aquecimento e desempenho ARM64 precisam ser avaliados em aparelho físico com músicas representativas. Emulador x86_64 não permite prometer tempo no celular. Arquivos longos precisam de espaço proporcional; não se promete continuar em background ou retomar inferência interrompida.

Veja `REPORT-0.15.0.md` para resultados executados, ambiente, fixtures, APK e pendências reais.
