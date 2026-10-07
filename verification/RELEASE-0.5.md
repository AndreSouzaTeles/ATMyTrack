# ATMyTrack 0.5.0

Preparação real antes do PLAY, cache seletivo persistente e prebuffer no AudioTrack. A thread de saída recebe blocos já mixados, sem esperar arquivos ou codecs. Controles suavizados e limiter com indicação de sobrecarga.

Transporte fixo, cards abaixo e identidade azul aplicada à interface, ícone e splash. Biblioteca anterior preservada.

Validação em emulador Android 15: 30 testes JVM; regressão Android com 24 testes, testes adicionais de alinhamento, loop e layout; 19 stems distintas por dez minutos e 24 stems repetidas por três minutos, sem underruns detectados. CPU no perfil comparável de 19: 60,7% → 22,8% de um núcleo.

Primeiro preparo de 19 arquivos mistos de 5min30: 119 segundos; reabertura dos leitores pelo cache: 34 ms (exclui metadata/UI). O cache usa armazenamento; não há garantia de tempos no aparelho físico. Os limites e evidências constam em `verification/REPORT-0.5.md`.

Instale o APK abaixo sobre a versão anterior, sem desinstalar. Android 8.0+, arm64-v8a/armeabi-v7a/x86_64. Assinatura de desenvolvimento preservada; checksum SHA-256 anexo.
