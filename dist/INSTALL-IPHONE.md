# Instalação no iPhone a partir do Windows

## O arquivo entregue

`ATMyTrack-iOS-0.1.0-UNSIGNED.ipa` contém o aplicativo iOS ARM64 compilado. **Ele não possui assinatura nem perfil de provisionamento da sua conta. Tocar no arquivo no iPhone não instala o aplicativo.** Não é APK, não é versão web e não exige jailbreak.

Você precisa de iPhone/iPad com iOS/iPadOS 16 ou posterior e uma conta Apple. O Apple ID já usado na App Store do iPhone pode ser essa conta. Não envie sua senha no chat nem coloque credenciais no GitHub.

## Opção Windows: assinatura pessoal com AltStore Classic

O AltStore Classic/AltServer é uma ferramenta de terceiros que assina o IPA para o seu aparelho com sua conta Apple. Não é necessário contratar uma conta Developer paga para o fluxo pessoal básico, mas as limitações da assinatura gratuita continuam se aplicando. A instalação efetiva no seu aparelho não foi validada nesta entrega.

1. Siga o [guia oficial do AltStore Classic para Windows](https://faq.altstore.io/altstore-classic/how-to-install-altstore-windows). Ele descreve AltServer, iTunes/iCloud e os requisitos de instalação atuais. Use os downloads do fornecedor, sem sites de IPA modificados.
2. Conecte o iPhone por USB, desbloqueie e confirme **Confiar neste computador**.
3. Pelo AltServer, instale o AltStore Classic no iPhone. Faça a autenticação diretamente na ferramenta, incluindo a verificação de dois fatores quando solicitada.
4. Siga as instruções exibidas para confiar no perfil da sua conta e ativar o Modo de Desenvolvedor, quando exigido pelo iOS.
5. Transfira o arquivo `ATMyTrack-iOS-0.1.0-UNSIGNED.ipa` para o app Arquivos do iPhone. Com o AltServer disponível, use **My Apps → +** no AltStore Classic e selecione o IPA. O AltServer fará a assinatura pessoal e instalação.
6. Abra ATMyTrack e importe as tracks pelo app Arquivos. A primeira preparação cria os arquivos de reprodução no armazenamento interno.

Contas gratuitas normalmente exigem renovar os aplicativos em até **7 dias**, e há limite de aplicativos ativos. Confira as regras atuais e o procedimento de renovação no [guia do AltStore](https://faq.altstore.io/altstore-classic/your-altstore). Mantenha backup da pasta ATMyTrack antes de remover/desativar o app.

AltStore **Classic** é o fluxo descrito aqui. A loja AltStore PAL possui condições diferentes e não substitui automaticamente este procedimento.

## Alternativa oficial Apple

Com acesso a Mac/Xcode, abra o projeto gerado e habilite assinatura automática com sua conta. Uma Personal Team permite testar no próprio dispositivo; distribuição por TestFlight requer participação no Apple Developer Program e configuração no App Store Connect. Veja [contas Apple Developer](https://developer.apple.com/help/account/basics/about-your-developer-account/).

Para gerar o projeto em um Mac:

```sh
git submodule update --init --recursive
brew install xcodegen
cd ios
xcodegen generate
open ATMyTrack.xcodeproj
```

Em Build Settings, altere `CODE_SIGNING_ALLOWED` para `YES`, escolha seu Team em Signing & Capabilities e, se necessário, um Bundle Identifier próprio. Selecione o iPhone conectado e execute o aplicativo. Credenciais/certificados devem permanecer somente no seu ambiente de assinatura.

## O que não está incluído

- Certificado e perfil Apple pessoais, App Store ou TestFlight.
- Instalação já efetuada no iPhone do usuário.
- Garantia de funcionamento de ferramentas de terceiros ou de desempenho de áudio sem testes no aparelho físico.

Fontes consultadas em 07/10/2026. Regras de instalação/assinatura podem mudar.
