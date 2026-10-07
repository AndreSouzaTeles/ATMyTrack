# Verificação 0.2.0 — importação e identidade

## Comparação medida

Emulador Android 15/API 35 x86_64 no Windows, fontes no armazenamento local do emulador. Arquivos sintéticos gerados antes da medição. Duas passadas por formato, invertendo a ordem antigo/novo na segunda. Tempos incluem a cópia interna da nova versão. Resultados brutos em `import-benchmark-0.2.0.txt`.

| Arquivo | Importador 0.1.0, média | Importador 0.2.0, média | Relação | Cache antigo / novo |
|---|---:|---:|---:|---:|
| WAV mono 16-bit, 48 kHz, 5 minutos | 1,805 s | 0,018 s | 100,3× | 115,2 / 28,8 MB |
| WAV estéreo 24-bit, 48 kHz, 3 minutos | 1,612 s | 0,022 s | 73,3× | 69,1 / 51,8 MB |
| AAC/M4A estéreo, 48 kHz, 1 minuto | 3,429 s | 3,217 s | 1,07× | 23,0 / 11,5 MB |

Esses resultados não reproduzem os dois minutos por track relatados no aparelho físico e não são promessa de tempo no dispositivo do usuário. Cache de sistema e armazenamento do emulador tornam a cópia especialmente rápida. O ganho estrutural em WAV vem da remoção da conversão integral; AAC ainda exige decoder. Downloads e mídias lentas continuam limitados pela origem.

Os tempos finais incluem validação do cabeçalho. O benchmark também compara frames, taxa e amostras em início, meio e fim. WAV16 e AAC tiveram erro amostral zero na comparação. WAV24 preserva a precisão de 24 bits; o caminho antigo via plataforma neste emulador quantizava em 16 bits (diferença máxima observada de 0,0000304).

## Testes

- APK final `versionCode=2`, `versionName=0.2.0`: build concluído, lint sem erros, assinatura v2 validada, atualização instalada com `adb install -r` e Activity iniciada com sucesso.
- 15 testes JVM passaram: núcleo musical, PCM, metadados WAV/padding/extensible, rejeição de truncamento e persistência/migração dos projetos anteriores.
- Regressões instrumentadas de importação, playback, pause/seek/stop, taxas diferentes e arquivo corrompido passaram.
- Benchmark Android passou para WAV e AAC, em ambas as ordens; o importador antigo existe apenas no APK de teste.
- Ícone PNG renderizado pelo próprio Android a partir do VectorDrawable final: `branding/icon-0.2.0.png`.
- Provedor com tamanho desconhecido e pipe sem seek: passou (`provider-0.2.0.txt`). A reprodução foi conferida lendo a cópia local, sem depender da URI de origem.

Total: 15 testes JVM e 6 testes instrumentados passaram. A primeira execução do provedor de teste expôs uma ausência de runtime Kotlin no processo isolado do APK de testes; a fixture foi corrigida para Java e o teste passou. Esse provedor não é incluído no APK distribuído.

Uma fonte de testes adicional emula um provedor com stream sem seek e tamanho desconhecido, para validar o contrato usado por provedores remotos sem acessar contas reais. Isso não certifica todos os aplicativos de nuvem.

Não houve ensaio acústico nem conexão ao aparelho físico do usuário. O mixer mantém um relógio de saída e não carrega arquivos inteiros em RAM.
