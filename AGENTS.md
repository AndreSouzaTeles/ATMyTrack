# ATMyTrack — instruções do projeto

- A cada melhoria, compilar, executar os testes relevantes e disponibilizar um APK instalável em `dist/ATMyTrack-debug.apk`, com link e caminho na entrega. Só deixar de gerar APK quando o usuário pedir explicitamente para não gerar.
- Incrementar a versão para cada entrega e preservar compatibilidade com projetos já importados.
- Não prometer tempos de importação no aparelho do usuário com base apenas em resultados do emulador. Medir e registrar ambiente e arquivos do benchmark.
- Identidade vigente: base grafite neutra, cores harmonizadas por instrumento e função (azul, ciano, violeta, verde suave, coral e âmbar); evitar grandes áreas saturadas. Vermelho forte para erros e clipping.
- Ícone vigente: referência azul/ciano enviada em 20/10/2026 (play contornado e barras de áudio), com disco circular; manter ícone, logo, splash e PNGs consistentes.
- A separação de instrumentos foi removida por pedido explícito em 19/10/2026; não reintroduzir sem nova solicitação. Preservar faixas já importadas em projetos.

- Assinatura original restaurada em 19/10/2026: gerar por padrão `com.atmytrack.app` usando `debug.keystore` local, ignorado pelo Git. Não substituir essa chave nem publicar seu conteúdo. V2 somente por solicitação explícita.
