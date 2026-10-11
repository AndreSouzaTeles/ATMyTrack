# ATMyTrack 0.14.2 — cadeados de volume e PAN

## Implementado
- Android: PAN desabilitado visualmente; proteção do modelo impede alterar volume/PAN de tracks bloqueadas e volume/PAN do Master. MUTE/SOLO permanecem editáveis.
- Web: PAN desabilitado junto do fader e protegido no callback, inclusive contra eventos sintéticos. MUTE/SOLO permanecem livres.
- Desbloqueio individual sob Master bloqueado libera volume e PAN da track. DCA/BUS mantêm sua proteção de ganho; não têm PAN próprio.
- Sem novos campos ou migrations; projetos e configurações existentes preservados.

## Testado
- 51 testes JVM: zero falhas/erros.
- Build APK e lint Debug: sucesso; lint com 0 erros e 31 avisos.
- Chrome headless no Windows: suíte npm test completa aprovada. Testes específicos cobrem PAN individual/global, rejeição de evento sintético, MUTE/SOLO e exceção individual.
- Emulador Android API 35 x86_64: instalação sobre versão anterior, sem remover dados; teste Compose panLocksPreserveMuteSoloAndIndividualOverride aprovado. Projeto anterior carregou e ficou pronto. PAN desabilitado, MUTE/SOLO editáveis e exceção individual confirmados.
- O primeiro teste Compose encontrou três tracks com nomes iguais; seletor do teste foi corrigido para selecionar a primeira, e a nova execução passou.
- Site local atualizado em http://127.0.0.1:4173/ e site público publicado via Sites: https://atmytrack-web.andmtu.chatgpt.site (versão hospedada 4).

## APK
- versionCode 22; versionName 0.14.2.
- dist/ATMyTrack-debug.apk e dist/ATMyTrack-0.14.2-debug.apk, 12.772.144 bytes.
- SHA256: 2c9eb5ed2f5a907dd72af3f6709309b82aebc98985ca2881bfb906b817307423.

## Limites
Não testado em celular físico nesta entrega. DSP e engine não foram modificados; não foi realizado novo benchmark multitrack.
