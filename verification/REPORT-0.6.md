# ATMyTrack 0.6.0 — entrega

## Implementação

- Arte fornecida pelo usuário aplicada ao ícone, logo e splash, sem alterar o PNG de origem. Densidades Android renderizadas a partir da mesma imagem.
- Editor de projeto com adicionar/trocar/remover imagem e prévia. Capa atenuada no card, texto em primeiro plano.
- Buses com canais mono individuais e pares estéreo aceitos pela saída Android. Em estéreo existem OUT 1/2 e OUT 2/2, além do par 1–2. Canais diretos bypassam o master; rotas indisponíveis são rejeitadas. Sem hardware USB conectado, não são inventados canais extras.
- Nomes centralizados horizontalmente, canal inteiro acompanha o arraste, botão 0dB em todos os faders e defaults unity para novas imports. Ganhos já salvos são preservados. Rodapés de tracks e master têm a mesma altura para alinhar os faders.
- Smart Click como flag ao lado do TAP: aplica BPM após análise válida ou reutiliza análise válida já salva. Desativar impede aplicação pendente. Play/Pausar do metrônomo e Fechar ficam fixos; Play liga o click e inicia o transporte quando parado.
- Seção selecionável por long press; LOOP usa seu intervalo real na engine, com divisão dos blocos exatamente no limite. Seleção persistida. Editor com waveform, seek, tempo em segundos e marcar início/fim. Trocar o intervalo durante reprodução reposiciona e prepara a reserva, como um seek; não há quantização/gapless prometidos.
- Toolbar agrupada, páginas Playback/Mixer, edição duplicada e avisos textuais de clipping removidos. Limiter e medidores preservados.
- Lupa pesquisa nome/tom e oferece filtro de tom exato; logo abre biblioteca lateral, Central de ajuda e SeuNomeNoApp.
- Globo com rotação lenta, arraste, foco por toque e busca. Vinte nomes explicitamente fictícios para demonstração. Formulário salva rascunho local; compartilhar identificação abre o seletor Android. Não envia silenciosamente dados, não verifica Pix e não publica nomes automaticamente. Inclusão real é manual em `supporters.json` com `demo=false`.
- Link Nubank e Pix Copia e Cola fornecidos pelo usuário, mais QR gerado desse mesmo payload. Decodificação independente ZXing confirma bytes UTF-8 idênticos; CRC C2A9 válido. Nenhum pagamento executado.

## Verificação

- Build completo de debug, APK de teste, 35 testes JVM aprovados e lint com zero erros (26 avisos de versões/estilo/compatibilidade revisados).
- [Regressão Android](regression-0.6.txt): 15 testes aprovados, cobrindo loop, saídas, Smart Click, biblioteca, imagem, formulário, controles, arraste e layout.
- [Celular](phone-0.6.txt): três testes aprovados; [paisagem](landscape-0.6.txt): um. O ajuste final de alinhamento dos rodapés foi recompilado, instalado e verificado novamente em [layout final](final-layout-0.6.txt).
- [Sessão com 19 stems distintas](session-0.6.txt): 65.756 ms, 61 ciclos de interação, seeks em 30/60/120/180 s, zero underruns e zero starvation detectados. CPU do processo 32.050 ms (~48,7% de um núcleo, incluindo automação), PSS máximo amostrado 153.536 KiB (~150 MiB), 17 GCs. Cache existente: metadata 512 ms, READY total 601 ms, abertura dos leitores 35 ms. Não é benchmark de primeiro preparo nem promessa de tempos no aparelho do usuário.
- Ambiente: emulador Android 15/API 35 x86_64; tablet 1920×1200/240dpi, celular 1080×1920 e 1920×1080/420dpi. USB multicanal físico e captura elétrica da saída não disponíveis.
- Capturas: [tablet](tablet-0.6.png), [faders no celular](phone-faders-0.6.png), [globo](supporters-phone-0.6.png).

## APK

- `D:\Projetos\ATMyTrack\dist\ATMyTrack-debug.apk` e cópia `ATMyTrack-0.6.0-debug.apk`.
- Versão 0.6.0 / versionCode 7; assinatura v2 e certificado anterior preservados. Instalação sobre a versão anterior verificada.
- SHA-256: `5d7f8adc767b1dbba729bf571a9f3bc3ab26fc49c7fcbcdd2373e858b1c6df46`.
- [Release](https://github.com/AndreSouzaTeles/ATMyTrack/releases/tag/v0.6.0).
