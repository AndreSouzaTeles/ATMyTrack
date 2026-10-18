# ATMyTrack 0.15.2 — grafite e azul discreto

Solicitação do usuário em 18/10/2026: interface minimalista inspirada na imagem de referência, predominância de preto/grafite e cinza, substituindo os pequenos destaques verdes por azul suave. A nova orientação suaviza a identidade de 07/10/2026 e preserva seu desenho.

Paleta Android: fundo #151617, painel #1E1F21, controle #282A2D, texto #EBECEE, texto secundário #C2C5C9, texto auxiliar #A5A8AD, destaque #8AA7C2. Sem tonalização azul das superfícies Material. Vermelho e âmbar para avisos técnicos/erros de áudio. Cores personalizadas salvas nas seções são preservadas; novas seções usam azul suave e os presets são neutros/azuis.

`app/src/main/res/drawable-nodpi/brand_art.png`: edição da arte original pelo imagegen integrado. Mantidos quadrado arredondado, arcos, barras e triângulo de reprodução. Mesma imagem usada na logo, menu, splash e ícone adaptativo. PNGs de launcher em cinco densidades renderizados pelo Android através de BrandAssetsTest, disponíveis também em `branding/0.15.2`.

Prompt utilizado (ferramenta integrada, sem CLI):

> Edit this exact existing ATMyTrack app icon, preserving the same geometry and arrangement: rounded square, two broken circular arcs, symmetrical equalizer bars and central right-pointing rounded play triangle. Recolor and simplify its finish into an understated minimalist professional dark audio mixer identity. Predominantly charcoal graphite neutral dark gray (#18191B, #242629), with subtle soft silver-gray arcs and equalizer bars. Only the central play triangle and very small accent details should have muted desaturated steel blue (#7892AA), softly shaded, not bright. Remove cyan, turquoise, purple, electric blue, neon, glow, vivid borders, glassy shine and strong gradients. Outer rounded square border should be thin dark gray, interior matte charcoal. Keep the symbol clearly legible with restrained light gray contrast. Do not change layout or add text. Keep transparent pixels outside the rounded square. Single centered square icon asset.
