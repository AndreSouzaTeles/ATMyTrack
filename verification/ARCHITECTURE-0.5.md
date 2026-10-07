# ATMyTrack 0.5 — preparo e reprodução

## Diagnóstico da 0.4

Havia um único AudioTrack/clock, mas o mixer podia realizar I/O PCM direto ou esperar `Future.get()` de um decoder comprimido. Até 19 decoders continuavam trabalhando durante a reprodução. O estado READY do importador indicava somente metadata. A soma era truncada em ±1, sem proteção dinâmica e sem suavização de mute/pan/ganho.

O perfil curto da 0.4 em Android 15 x86_64 não reproduziu os underruns relatados no aparelho: seis lotes, de 1 a 19 stems, tiveram zero underruns. Com 19, foram 7.282 ms de CPU por 12 s de reprodução (~60,7% de um núcleo), e 1.144 blocos saturados. Os arquivos dessa comparação eram sete fontes sintéticas repetidas; isso não estabelece a causa exclusiva do defeito físico. Fonte: `profile-baseline.txt`.

## Fluxo da 0.5

1. **DISCOVERED:** seleção, permissão/cópia de origem, metadata e formato. Não libera PLAY.
2. **PREPARING:** dois workers no máximo preparam leitores e acesso. Comprimidos ou sample rates diferentes recebem cache float estéreo na taxa do projeto. WAV local compatível permanece no formato original; WAV via content provider recebe cópia binária local, sem transcodificação. Cada reader valida e carrega seu primeiro bloco.
3. **READY:** todos os leitores essenciais estão preparados, sem decoder comprimido ativo na reprodução, e a saída está aberta. Uma falha bloqueia PLAY, informa arquivo/motivo e permite tentar novamente ou remover a track. Cancelar interrompe em fronteiras de blocos; arquivos parciais são descartados.
4. **PLAY/SEEK:** saída pausada; o produtor preenche a reserva do próprio AudioTrack com escritas não bloqueantes e a fila comum de oito blocos antes de iniciar o clock. Todas as tracks recebem o mesmo índice absoluto de frame. Seek em PCM preparado tem custo independente da posição no arquivo.
5. **Produção:** `ATMyTrack-Mixer` processa comandos, lê blocos locais, interpola os ganhos, aplica pan/mute/solo/DCA/buses e mixa. Read-ahead de 4.096 frames (32 KiB por stem) amortiza I/O em oito blocos.
6. **Saída:** `ATMyTrack-Output`, prioridade áudio, consome ring SPSC prealocado e escreve AudioTrack. Não abre arquivos, acessa SAF, roda codecs, cria buffers, espera mutex/Future nem executa UI. Fila cheia desacelera o produtor, nunca descarta frames. Fila vazia aguarda o produtor; esperas do produtor maiores que um bloco são registradas. Starvation exige também reserva de AudioTrack de no máximo um bloco; underruns são lidos diretamente do Android. Uma espera coberta pela reserva não é contada como dropout.

## Recursos e sincronização

- Fila comum: 8 × 512 frames (~85 ms a 48 kHz), independente de 2 ou 19 tracks; reserva AudioTrack adicional. No emulador, a reserva Android foi de 8.720 frames (~182 ms), totalizando aproximadamente 267 ms de áudio antecipado. Latência de controles inclui essa antecipação, apropriada a playback, não a monitoração de instrumentos ao vivo.
- Clock: playback head do único AudioTrack, com base comum após seek. Nenhum player ou relógio por stem.
- Resampling: filtro sinc polifásico com janela de 48 taps/1.024 fases, aplicado durante o preparo, com posição derivada do frame absoluto. Quantidade de frames convertida pela razão exata de taxas. Testes verificam frequência, busca tardia e rejeição de alias.
- Ganhos de tracks, pan, mute/solo/DCA, buses e master interpolam ao longo de um bloco. As alterações não reabrem a saída.
- Proteção de pico vinculada entre canais, ataque imediato e release de 80 ms. Medidores mostram picos **antes** do limiter e a UI pede redução de ganho quando necessário. Não substitui uma mixagem com headroom; pode reduzir dinâmica sob sobrecarga.
- PCM float preparado ocupa `frames × 8` bytes. Uma stem de 5min30/48kHz ocupa ~121 MiB. Os arquivos não são carregados inteiros na RAM.
- Cache persistente, chave SHA-256 de versão/identidade/fingerprint/formato/duração/taxas; temporário exclusivo e rename ao concluir; tamanho validado ao reutilizar. Origem externa continua sujeita à validação de acesso/fingerprint ao abrir projeto. O fingerprint amostra extremos e metadata, não todo o conteúdo.
- Waveform espera a preparação essencial e cede quando há playback. Smart Click e pitch continuam independentes e sob demanda.
- Loop enfileira o início imediatamente após o fim, sem parar/recriar AudioTrack; cursor lógico usa módulo da duração comum. Não corrige a descontinuidade musical entre fim/início nem o padding de codecs. USB físico e aparelho do usuário exigem teste próprio.

## Interface

Transporte fora de qualquer região rolável, cards imediatamente abaixo, waveform/seções e mixer no conteúdo rolável. PAGE 1/2 conservam a mesma engine. Paleta escura azul, medidor azul/âmbar/vermelho; cores antigas conhecidas de marcadores migram na leitura. Ícones adaptive, PNG e splash acompanham a paleta.

Resultados de build e ensaios desta entrega estão em `REPORT-0.5.md`.
