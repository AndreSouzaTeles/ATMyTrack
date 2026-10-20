# Identidade 0.19.0 — borda circular

Mantém a paleta funcional e os controles da [0.18.0](BRAND-0.18.0.md). A alteração visual é a borda do ícone: um disco circular com símbolo play/equalizador ciano, azul e violeta, ampliado dentro do espaço disponível. Cantos externos transparentes; sem placa quadrada sob a arte. A mesma imagem é usada por logo, splash e web local.

Arte: [brand_art.png](../app/src/main/res/drawable-nodpi/brand_art.png). Exportações Android: [0.19.0](0.19.0/).

## Android

Adicionado `android:roundIcon` com camadas adaptativas e PNGs nas cinco densidades. A margem proporcional de 16,6667% na camada de primeiro plano compensa a expansão das camadas do AdaptiveIconDrawable e permite aproveitar o viewport. A borda circular é decorativa; o play e as barras permanecem no centro. O teste verifica transparência dos cantos, ausência de placa quadrada e ocupação das quatro extremidades em todas as densidades.

O launcher determina a máscara externa e o tamanho apresentado na grade. A arte circular e o recurso roundIcon atendem ao pedido dentro desse limite. Referência técnica: [Android Developers — Adaptive icons](https://developer.android.com/develop/ui/compose/system/icon_design_adaptive).

## Geração

Ferramenta: imagegen integrada, edição da arte aprovada em 0.18.0. Prompt utilizado:

> Edit this exact ATMyTrack app icon. Keep the recognizable central play triangle, equalizer bars, cyan blue violet magenta palette, gradients and dimensional finish as closely as possible. Change ONLY the outer dark navy rounded-square tile and its illuminated rim into a PERFECT CIRCULAR DISC. Circle centered on a square transparent canvas, outer circle diameter about 98% of canvas width, even very thin transparent margin, completely transparent outside circle, no rounded-square remnants, no external drop shadow, no stray pixels. Thin cyan/blue edge light at left changing to violet/magenta at right. Scale the play and equalizer symbol to be as large as possible inside the circular disc without cutting any bars or triangle; balanced tight composition. One circular app icon only, no text, mockup, duplicate, or background. Preserve design rather than inventing a new symbol.
