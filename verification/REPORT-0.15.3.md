# ATMyTrack 0.15.3 (28) — ATMyTrack_v2 temporário

Solicitação explícita do usuário após o conflito de assinatura: criar temporariamente um aplicativo separado chamado ATMyTrack_v2.

O build padrão agora usa identificador `com.atmytrack.app.v2` e nome ATMyTrack_v2 no launcher e na interface. Mantém o namespace do código e a mesma arte minimalista, recursos e formato de projetos. Pode coexistir com `com.atmytrack.app` e com a prévia histórica `com.atmytrack.app.preview`, sem exigir a chave antiga para esta nova instalação.

A biblioteca, permissões e o download do modelo de IA são próprios do v2. Não há cópia automática dos dados privados de outro pacote. Nada é removido do aplicativo original. Atualizações futuras deste v2 devem conservar identificador e chave deste computador.

A opção Gradle `-PatmytrackV2=false` prepara o pacote original quando a chave antiga estiver disponível. O build padrão gera `dist/ATMyTrack_v2.apk` e a cópia habitual `dist/ATMyTrack-debug.apk`, idênticas.

Validação: build, lint e 57 testes JVM sem falhas. Instalação e inicialização no emulador Android 15/API 35 x86_64 com o aplicativo original presente; teste Android de download/instalação do modelo com progresso numérico e conclusão após SHA-256 aprovado (15,850 s). A primeira tentativa automática usou a configuração de paisagem remanescente da conferência visual anterior e não abriu o separador; a execução em retrato passou sem alteração no aplicativo. Não houve benchmark de desempenho ou teste em aparelho físico.

Logs locais: `build-0.15.3.log` e `android-0.15.3.log`. Screenshot da tela inicial do aplicativo: `v2-0.15.3.png`.

Entrega publicada na release v0.15.3, com APK ATMyTrack_v2.apk e SHA-256. O aplicativo antigo continua dependendo da chave anterior para receber atualizações sobre a instalação existente.
