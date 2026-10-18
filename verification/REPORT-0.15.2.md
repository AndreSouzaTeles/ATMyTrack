# ATMyTrack 0.15.2 (27) — visual minimalista

## Mudanças

Android em preto/grafite com textos e ícones neutros. Azul suave (#8AA7C2) em seleção, PLAY, linhas dos faders e estados ativos. Retirados fundos azulados da Master, cards, globo e superfícies Material; menus, diálogos, afinador, PAD e instalação do modelo seguem o tema neutro. Avisos técnicos de áudio mantêm vermelho/âmbar.

Arte original recolorida com imagegen integrado: mesmo desenho, predominância de grafite/prata, azul discreto no triângulo. Logo, menu, splash, ícone adaptativo e cinco PNGs de launcher consistentes. Prompt e arquivos em [BRAND-0.15.2](../branding/BRAND-0.15.2.md).

Novas seções têm presets cinza/azul e padrão suave. Cores personalizadas existentes continuam intactas. Sem alteração do schema, dos arquivos importados ou do DSP. Pacote único original `com.atmytrack.app`.

## Validação

- Build, lint e 57 testes JVM aprovados, sem falhas.
- Seis testes instrumentados passaram no emulador Android 15/API 35 x86_64, 6 GiB, quatro núcleos, Windows/WHPX: exportação dos ícones; três testes de seções (persistência das cores personalizadas, nova paleta/toolbar e waveform); arraste da timeline; instalação real do modelo com percentual intermediário visível e conclusão em 100% após validar os hashes. Rodada de 23,866 s, sem benchmark de aparelho físico.
- PNGs de launcher gerados pelo próprio Android, nas dimensões 48/72/96/144/192 px; cópias e máscaras circular/arredondada em `branding/0.15.2`.
- Inspeção visual em retrato e paisagem. Capturas: `colors-0.15.2.png`, `mixer-0.15.2.png`, `model-progress-0.15.2.png`.
- Instalação com `adb install -r` sobre a versão anterior assinada neste PC aprovada.

Logs locais ignorados pelo Git: `build-0.15.2.log`, `android-0.15.2.log`, `brand-0.15.2.log`.

## APK e assinatura

`dist/ATMyTrack-debug.apk`, com cópia `dist/ATMyTrack-0.15.2-debug.apk` e SHA-256 correspondentes. Somente o pacote original; não cria variante paralela. Release `v0.15.2`.

Permanece a limitação já registrada: a chave da instalação pública 0.14.4 não está neste PC. O APK atual não atualiza aquela instalação até ser assinado com a chave antiga, possivelmente no outro computador. Não desinstalar o aplicativo antigo para contornar essa incompatibilidade. Detalhes dos certificados em [REPORT-0.15.1](REPORT-0.15.1.md).
