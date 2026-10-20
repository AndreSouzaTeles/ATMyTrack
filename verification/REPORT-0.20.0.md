# Validação 0.20.0 (33)

## Entrega

ATMyTrack original, `com.atmytrack.app`, versão 0.20.0 (33). Mesma chave original local, ignorada pelo Git; certificado SHA-256 `1f9c8116100c1b13aded62b0c2711e3aa045b60c77bb40f57f5f1a71f77e9c39`. O ícone circular aprovado não foi alterado. A separação de instrumentos continua removida.

APK canônico `dist/ATMyTrack-debug.apk` e cópia `dist/ATMyTrack-0.20.0-debug.apk`, idênticos, 13.513.721 bytes. SHA-256 `16f552af2e63afdb4b9ffab42ae8f9b4a2ab2783396b02c6638aa41e2531ac1d`. Assinatura verificada com apksigner, identidade/versão com aapt. Instalado por atualização (`install -r`) no emulador que já continha a versão original e projetos anteriores; sem desinstalar ou reimportar. A chave não integra o repositório nem a release.

## Mudanças verificadas

- Playback e mixer juntos; timeline fixa abaixo do transporte, ferramentas pequenas com ícones, rótulos acessíveis e dicas ao segurar.
- PAN giratório com posição/centro, toque, gesto e retorno ao centro; respeita cadeados. Cadeado fechado violeta. Nomes longos limitados a duas linhas com reticências.
- Editor abre após importação, antes das análises adicionais; cor por faixa salva e compatível com projetos sem esse campo. Ordenação de faixas preservada. Editar dentro do cartão e arraste dos projetos com persistência.
- Smart Click desmarcado por padrão e migração única dos projetos existentes; opção explícita posterior preservada.
- CrossMusic: seleção/configuração pela interface, volumes/mutes/PAD por projeto, avanço no fim natural, pausa/stop sem avanço, último fim desliga PAD, troca C→Bb com transição de 144.000 frames. Encerrar com Não mantém execução; Sim encerra só automação, preservando áudio/PAD. Alteração de tom em andamento não é cancelada. Sessão sobrevive à recriação da activity.
- Globo começa com André Teles à frente; contribuição e beneficiário exatamente conforme o pedido. Os mesmos ajustes de suporte foram sincronizados em `web/dist/app.js`; o restante da web permanece na versão anterior e o site público não foi republicado.

## Testes

- Build final `testDebugUnitTest lintDebug :app:exportDebugApk assembleDebugAndroidTest`: aprovado, 29 s.
- 58 testes JVM, zero falhas. Novos testes cobrem cores/compatibilidade, persistência da sequência, aplicação dos volumes/mutes sem perder cores, tonalidades e duração/continuidade do crossfade.
- 12 testes Android distintos aprovados: CrossMusic20Test (5), Sections09Test (3), PadLocks13Test (2), Timeline131Test (1), Routing12Test (1).
- Execução conjunta inicialmente teve 11/12: o teste antigo da timeline tentava rolar até uma região agora fixa. Atualizado para verificar visibilidade e repetido isoladamente: aprovado em 9,213 s. As cinco verificações novas de CrossMusic/interface passaram juntas.
- Falhas encontradas durante desenvolvimento e corrigidas antes da entrega: evento de término perdido ao publicar estado, drenagem do último bloco de áudio, primeiro movimento rápido do PAN e drop de projeto antes do próximo frame de animação. Testes específicos confirmam os comportamentos corrigidos.
- `node --check web/dist/app.js` e `git diff --check`: aprovados.
- Capturas inspecionadas após o último APK; sem teste em aparelho físico. A emulação não permite julgar qualidade sonora de todos os sistemas de som. O crossfade foi verificado numericamente e no estado da reprodução.

Ambiente: Windows, JDK 17.0.20.1, SDK 36, Android 15 Google APIs x86_64; AVD ATMyTrack_OriginalKey_API35, 4 GiB / 4 cores, SwiftShader, 1080×1920 / 420 dpi. Fixtures novas: WAV PCM16 mono sintético 220 Hz, 48 kHz, duas faixas de 20 s por projeto (1.920.044 bytes por WAV), geradas no cache do app. Biblioteca do emulador também contém o projeto preservado da atualização 0.14.4→0.19.0. Não foi feito benchmark de importação nem promessa de desempenho em celular.

## Capturas e uso

- [Player unificado e PAN](player-portrait-0.20.0.png)
- [Ferramentas](tools-0.20.0.png)
- [CrossMusic com faixas/PAD](crossmusic-0.20.0.png)
- [Globo inicial](globe-0.20.0.png)
- [Texto e beneficiário](support-text-0.20.0.png)
- [Como funciona e limites](CROSSMUSIC-0.20.0.md)
- [Release v0.20.0](https://github.com/AndreSouzaTeles/ATMyTrack/releases/tag/v0.20.0)