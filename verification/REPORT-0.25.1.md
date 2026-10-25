# ATMyTrack 0.25.1 (43) — TAP BPM e sincronização dos ritmos

- **TAP BPM na percussão:** dois ou mais toques calculam o BPM, com média dos últimos intervalos, rejeição de toques acidentais muito rápidos, reset após pausa e limites de 30–300 BPM. Com sincronização ativa, atualiza metrônomo e percussão; em modo acompanhado, atualiza também o BPM original do projeto.
- **Flag Android corrigida:** o vínculo já existia no mecanismo, mas o painel do metrônomo não exibia o checkbox. Agora mostra **Sincronizar com percussão**. A percussão mostra **Sincronizar com metrônomo**. É a mesma preferência, selecionada por padrão quando ausente, com propagação de BPM em ambos os sentidos.
- **Ordem das ferramentas:** PAD, Metrônomo, Percussão. Metrônomo imediatamente à esquerda da percussão no tablet e no celular; o par permanece na mesma linha no menu móvel.
- Sem sincronização, TAP da percussão altera apenas seu BPM independente. Se Acompanhar música estiver ativo e a sincronização desligada, a interface pede desativar Acompanhar música antes de definir um BPM separado.
- Android e site atualizados, preservando projetos, biblioteca acústica, loops importados e assinatura original. Corrigida também a reabertura indevida de um diálogo web fechado durante preparação assíncrona.

## Validação

- Build, lint, APK e compilação dos testes instrumentados concluídos. **71 testes unitários Android aprovados**, zero falhas/erros. Caso novo verifica TAP de 120 BPM sincronizando os dois ritmos/projeto e TAP de 100 BPM independente após desvincular.
- Teste Compose no emulador **ATMyTrack_OriginalKey_API35 / Android 15**, 1080×1920, densidade 420: confirma checkbox visível/selecionado no metrônomo, checkbox correspondente na percussão, TAP pela interface e ordem/lado dos botões. É teste de emulador, não certificação do aparelho do usuário.
- Suíte web completa: regressões de áudio, importação, backup, routing, PAD, CrossMusic, vínculo dos ritmos, TAP e layout. Teste adicional confere TAP por cliques, BPM nos dois sentidos, desvínculo e metrônomo à esquerda da percussão na mesma linha em viewport 390×844. Teste determinístico cobre média, debounce, reset e limites.
- Ambiente web: Windows, Node 24.19, pnpm 11.25, Chrome instalado com Playwright e servidor Python localhost:4173. Não foram medidos tempos de importação no aparelho do usuário.
- APK `dist/ATMyTrack-debug.apk`: **23921955 bytes**, pacote `com.atmytrack.app`, versão **0.25.1 (43)**. SHA-256 `c03a846dbb3fa0a213dba0cab0bb54dd6802c8e70419609f3877d03e1d8e9bd2`.
- Certificado original verificado por apksigner: SHA-256 `1f9c8116100c1b13aded62b0c2711e3aa045b60c77bb40f57f5f1a71f77e9c39`.

Site: https://atmytrack-web.andmtu.chatgpt.site . Permanecem as diferenças de plataforma descritas nas entregas anteriores: armazenamento e projetos web/Android separados e limitações do navegador para segundo plano/USB multicanal.
