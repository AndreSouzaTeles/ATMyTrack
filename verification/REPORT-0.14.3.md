# ATMyTrack 0.14.3 — Deixe sua marca

- Android e Web: nova chave Pix visível/copiar: atelestechnology@jim.com. Links Nubank, código Pix antigo e QR Code removidos, inclusive os arquivos de imagem dos pacotes atuais.
- Removido texto indicado sobre confirmação/inclusão manual. Contribuição com valor livre. WhatsApp de comprovantes preservado.
- André Teles incluído uma vez no globo de ambas as plataformas, sem indicação de demonstração.
- Sem alteração de schema, persistência ou engine de áudio.

## Validação
- 51 testes JVM, sem falhas/erros; assembleDebug/exportDebugApk e lint aprovados (0 erros, 30 avisos).
- Suíte npm test completa aprovada no Chrome headless/Windows. Novo teste copia a chave e lê clipboard, verifica ausência de QR/link/texto antigo e busca André Teles no globo.
- APK inspecionado: QR ausente e nome presente em assets/supporters.json.
- Site local em http://127.0.0.1:4173/ atualizado. Publicação via Sites no endereço existente.
- Nenhum dispositivo/emulador conectado nesta entrega; instalação/clipboard Android não testados em aparelho. Compatibilidade mantida pela mesma aplicação/assinatura e ausência de migrations.

## APK
- versionName 0.14.3; versionCode 23.
- dist/ATMyTrack-debug.apk; cópia dist/ATMyTrack-0.14.3-debug.apk.
- 12.652.789 bytes; SHA256 e8766bed061773eadc95496ec18bb31168e3669a39e884599ee9e13fc69a9f50.
