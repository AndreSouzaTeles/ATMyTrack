# Identidade ATMyTrack 0.5

A geometria do A aprovado na 0.4 foi preservada. Toda a aplicação agora usa azul: símbolo #3B82F6, travessa #60A5FA, fundo #0B0D12 e texto #F8FAFC. Não há verde/turquesa nos recursos ativos.

- `symbol-0.5.svg`, `logo-0.5.svg`: vetor isolado e assinatura horizontal.
- `0.5/icon-{48,72,96,144,192,512}.png`: exports do VectorDrawable Android real.
- Foreground, monochrome, symbol e recortes circle/rounded em `0.5/`.
- Ícones PNG por densidade, adaptive v26/v33, monochrome e splash no app.
- Teste verifica todos os pixels essenciais dentro da área segura circular de raio 33 dp do viewport 108 dp.

Assets antigos permanecem somente como histórico. Capas importadas pelo usuário conservam suas cores originais.
