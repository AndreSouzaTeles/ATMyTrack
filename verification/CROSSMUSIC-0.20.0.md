# CrossMusic 0.20.0

Abra Ferramentas (ícone de ajustes no cabeçalho), depois CrossMusic. Marque os projetos na ordem desejada; as setas ↑/↓ alteram a sequência. A seta ao lado de cada projeto expande suas faixas, volumes, mute e PAD. O PAD pode seguir o tom efetivo do projeto (incluindo transposição) ou usar um tom maior/menor escolhido, com timbre e volume próprios. Sem tonalidade válida, escolha um tom explícito antes de iniciar.

O início de cada entrada aplica ao projeto seus volumes e mutes configurados, limpa os solos dessas faixas e desliga o loop. Essas alterações são salvas no projeto. Os outros ajustes, cores e arquivos são preservados. A configuração do CrossMusic é salva separadamente e pode ser reutilizada; alterações ao vivo continuam disponíveis no player. A seleção da sequência fica somente para leitura enquanto ela está em execução.

O término natural avança para o próximo projeto, após sua preparação normal. Pause e Stop não avançam a sequência. A troca de acordes/timbres do PAD mistura as vozes durante 144.000 amostras a 48 kHz (três segundos), sem deslizar a afinação. O PAD sustenta durante a preparação da próxima música. Não há promessa de áudio musical sem intervalo; o tempo de preparo depende dos arquivos e aparelho.

Ao lado do Play aparece o andamento da sequência e a opção de encerrar. “Não” fecha a confirmação. “Sim” desliga somente a automação: não pausa música, não interrompe alterações de tom/velocidade em preparo e não desliga o PAD atual. Ao chegar naturalmente ao último projeto, o CrossMusic termina e desliga seu PAD. O usuário pode operar a timeline, mixer, tom, velocidade e transporte normalmente.

A sessão pertence ao aplicativo e sobrevive à recriação da tela e à ida para segundo plano durante a reprodução. O áudio usa o serviço de reprodução existente. Chamadas, perda de foco de áudio e desconexão de fones mantêm o comportamento de pausa de segurança. Encerramento forçado do processo não reinicia uma sequência sozinho; somente seu plano é salvo.

A primeira abertura desta versão desmarca Smart Click nos projetos existentes uma única vez; escolhas explícitas posteriores são preservadas. Cores de faixas são opcionais no mesmo formato de biblioteca: projetos antigos sem esse campo mantêm suas cores automáticas. Nenhum arquivo importado precisa ser reimportado.