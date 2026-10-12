# ATMyTrack 0.14.4 — Globo exemplo e comprovante

## Implementado
Android e web: identificação “Globo exemplo”, mantendo todos os nomes. Formulário de nome e botão salvar removidos; WhatsApp abre com mensagem genérica sem depender do nome. Chave Pix atelestechnology@jim.com e cópia posicionadas abaixo do globo e antes do comprovante. Pesquisa e rotação mantidas. Sem alteração de banco ou DSP.

## Validação
- 51 testes JVM: zero falhas/erros.
- Build Debug, exportDebugApk e lint aprovados: 0 erros, 29 avisos.
- Suíte npm test completa aprovada no Chrome headless/Windows, incluindo copiar chave real no clipboard, ausência do formulário, ordem do botão de cópia antes do comprovante, identificação “Globo exemplo”, busca André Teles e abertura WhatsApp com mensagem sem nome.
- Local atualizado em http://127.0.0.1:4173/. Site público publicado via Sites no endereço existente.
- Sem teste em aparelho/emulador Android nesta entrega. Dados e nomes não foram removidos; sem migration.

## APK
versionName 0.14.4, versionCode 24. dist/ATMyTrack-debug.apk e dist/ATMyTrack-0.14.4-debug.apk existem, 12.652.789 bytes.
SHA256: 98e39f8e3aa5be3628f89c45042ba4ddabb71f5389756ca19357b3b68d2f9099.
