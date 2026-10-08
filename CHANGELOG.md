# 0.8.0 — Afinador fiel à nota, menu e versão web

- Separa nota captada e corda-alvo; mantém sustenidos mesmo longe da afinação. Refina interpolação do YIN e adiciona regressão cromática de B0 a E6.
- Logo acima do nome no menu. DEIXE SUA MARCA e Central de dúvidas no rodapé, com topo rolável em landscape.
- Versão web com projetos locais/backup, mixer, DCA/BUS/rotas estéreo, tom/velocidade preparados em Worker WASM, click em AudioWorklet, markers/loop, afinador local e apoio/ajuda.
- Remove fontes, workflow e release da portabilidade nativa iOS. Android versão 10, assinatura e persistência mantidas.

# 0.7.0 — Velocidade, afinador e navegação

- Time-stretch real 50–200%, seleção independente de pitch, defaults seguros para bibliotecas antigas e alerta de dessincronização personalizada.
- Cache combinado de pitch/tempo, bypass, limpeza de versões antigas e limite com proteção da sessão ativa.
- Relógio, BPM, metronomo, seek e loops convertidos sem reescrever markers originais.
- Hamburger menu com Projetos, Afinador, Plano / Pagamento e Central de dúvidas.
- Afinador com microfone real, YIN local, notas/cents, instrumentos e afinações, A4 configurável e liberação via lifecycle.
- Versão 9; mesma assinatura. Atualização sobre 0.6.1 sem apagar biblioteca.

# 0.6.1 — QR Code enviado pelo usuário

- Substitui o QR gerado pela imagem JPG fornecida, sem edição.
- Texto decodificado corresponde ao Pix Copia e Cola já configurado.

# 0.6.0 — Biblioteca, seções e novos controles

- Arte de ícone enviada pelo usuário aplicada a logo, splash e todas as densidades.
- Imagem de projeto com prévia e fundo atenuado; busca por nome/tom e biblioteca lateral.
- Tracks centralizadas, arraste visual do canal inteiro, defaults unity e botão 0dB.
- Canais físicos mono e pares de saída para buses; loop de seção conectado à engine.
- Editor de seção com timeline/posição; toolbar agrupada, páginas Playback/Mixer e remoção de aviso textual de clip.
- Smart Click como flag com aplicação automática; Play/Pausar e Fechar fixos no metrônomo.
- Globo de nomes interativo, 20 exemplos claramente identificados, rascunho local de nome e compartilhamento manual; link, Pix e QR verificado.

# 0.5.1 — Identidade T azul

- Ícone, logo e splash com T geométrico e propagação sonora, exclusivamente em azuis sobre fundo escuro.
- PNGs em todas as densidades e versões vetoriais atualizados.
- Seções com quatro tons azuis; cores antigas normalizadas ao abrir a biblioteca.
- Fader com acabamento azul-acinzentado. Alertas técnicos de nível alto/clip preservados.
- Ícone padrão não oferece recoloração monocromática pelo tema do launcher.

# Histórico

## 0.5.0

- Preparação real antes de PLAY, estados por track, cancelar, tentar novamente e remover track com erro.
- Cache persistente seletivo, dois workers de preparo, PCM local sem conversão desnecessária e resampling de taxas diferentes.
- Separação entre produtor/mixer e saída AudioTrack com fila SPSC limitada; prebuffer comum em PLAY/SEEK.
- Read-ahead PCM, suavização de ganhos/pan/mute/solo/DCA/buses/master e limiter com indicação de excesso.
- Transporte fixo no topo, cards abaixo e paleta/identidade azul, incluindo migração dos marcadores antigos.
- Perfil progressivo, sessão longa de 19 stems distintas e documentação de reprodução/build; projeto publicado no GitHub.

## 0.4.0

- Double tap retorna todos os faders a unity; ajuste fino numérico e ±0,1 dB, ganho permanente e escala adaptada à altura.
- Nova identidade original: A geométrico turquesa, travessa violeta; logo, símbolo, adaptive, monochrome, splash e PNGs 48–512 px.
- Importação parcial com estados por arquivo e relatório; verificação de decoder disponível sem decodificação integral.
- Correções de timestamps ADTS, priming M4A e buscas exatas por pacotes em MP3/Vorbis.
- Antecipação de decoding com buffers limitados e pool compartilhado; menos alocações na mixagem e preparo antes de iniciar a saída.
- Troca dos leitores de pitch entre blocos sem recriar o AudioTrack; liberação de leitores aposentados fora do mixer.
- Reordenação nas bordas sem inversão causada pela ancoragem da lista; projeto selecionado permanece visível nos cards.
- PAGE 2 reserva mais altura ao mixer; escalas completas em áreas altas e simplificadas em landscape compacto, preservando alvos de toque e rolagem.
- Serialização compartilhada das transações da biblioteca entre instâncias, protegendo leituras e gravações atômicas concorrentes.
- Ampliação dos testes de formatos, arquivos de três minutos, 24 stems, pitch, persistência, gestos e identidade.

## 0.3.0

- Importação por URI persistente com metadata concorrente limitada; cópia apenas quando necessária e decoding comprimido sob demanda.
- Waveform progressivo e caches com detecção de alteração da origem; projetos antigos preservados.
- Pitch real de −12 a +12 semitons por track, com Signalsmith Stretch MIT, cache e compensação de latência.
- Duas páginas compartilhando reprodução; transporte acima do mixer; reordenação por long press/arraste com rolagem nas bordas.
- DCA relativo, buses com áudio/meter, roteamento estéreo, preset split e pares USB quando anunciados/aceitos pela plataforma.
- Metrônomo com TAP, sete timbres, acento configurável e Smart Click com confirmação do BPM sugerido.
- Escala/curva de console −∞…+10 dB em todos os faders; cards com exclusão confirmada e novo projeto no final.
- Novos testes de DSP, decoding/seek, grupos, interfaces, persistência e desempenho; APK atualizado.

## 0.2.0

- WAV PCM deixa de ser integralmente convertido na importação: cópia direta com leitura do formato original pelo mixer.
- PCM mono permanece mono no armazenamento; WAV 24-bit conserva sua precisão original.
- Decoders recebem entradas em lote e drenam saídas disponíveis; espera ocorre apenas quando o pipeline não avança.
- PCM do decoder é escrito em blocos sem expansão obrigatória para float estéreo.
- Verificação de espaço a cada 8 MiB, progresso de cópia/preparação limitado a 5 atualizações por segundo.
- Fontes sem seek/tamanho conhecido são copiadas para acesso offline; seleção por SAF preservada.
- Compatibilidade com projetos/caches da 0.1.0.
- Novo símbolo: T turquesa com curva roxa de oscilação decrescente, aplicado ao ícone adaptativo, splash e logo do app.
- Versão e APK atualizados. Não foram adicionadas bibliotecas de produção.

## 0.1.0

Primeira Fase 1 funcional: importação, biblioteca, reprodução multitrack, mixer, metrônomo, markers e persistência.
