# ATMyTrack 0.25.2 (44) — campo de BPM sincronizado

## Correção

O RhythmEngine já atualizava seu estado de áudio, mas PlayerSession não registrava o callback que publica mudanças vinculadas no projeto da biblioteca. Com um projeto carregado, o metrônomo lia o BPM antigo desse projeto. A integração agora atualiza BPM, compasso e flags do projeto selecionado, preservando suas faixas, ganhos e demais configurações.

Com Sincronizar ativo, o BPM digitado ou definido por TAP na percussão aparece no campo do metrônomo. Fechar o metrônomo não restaura o valor antigo. Alterações no metrônomo continuam chegando à percussão. Foi adicionado identificador acessível ao campo do metrônomo para verificar o valor exibido, além do estado interno.

## Validação

Build, lint e exportação concluídos. **71 testes unitários Android**, **um teste de interface com projeto carregado no emulador** e **suíte web completa** aprovados, sem falhas nos resultados finais. O teste de dispositivo foi iniciado após o emulador terminar de inicializar.

O novo teste Android usa um projeto WAV carregado: parte de 83 BPM, digita 137 na percussão, verifica o projeto e o campo do metrônomo em 137, fecha/reabre sem perder o valor, faz TAP pela interface e verifica seu resultado no campo do metrônomo. Depois edita o metrônomo para 119 e verifica 119 no campo da percussão.

O site mantém o mesmo comportamento e recebeu teste equivalente com projeto real WAV sintético, edição, TAP acompanhado, campo do metrônomo, fechamento e edição inversa. Os dados ficam em `linked-bpm-web-0.25.2.json`.

Ambiente: Windows; teste Android no emulador ATMyTrack_OriginalKey_API35 / Android 15; web em Chrome instalado/Playwright, Node 24.19, pnpm 11.25 e servidor Python localhost:4173. Sem medição de tempos no aparelho do usuário ou certificação de hardware físico.

Projeto local, GitHub, APK com pacote/assinatura originais e site https://atmytrack-web.andmtu.chatgpt.site atualizados. Projetos existentes preservados; dados Android e web continuam separados.

APK: `dist/ATMyTrack-debug.apk`, **23921955 bytes**, pacote `com.atmytrack.app`, versão **0.25.2 (44)**. SHA-256 `79193196ad13de4de0cf5219ed1eca1900d332b06091b4c0ef9bbb25e2a78798`. Assinatura original verificada; certificado SHA-256 `1f9c8116100c1b13aded62b0c2711e3aa045b60c77bb40f57f5f1a71f77e9c39`.
