# Identidade ATMyTrack 0.4

Símbolo original construído em vetor: duas hastes e topo fechado formam um A; uma travessa deslocada em violeta remete a um controle sobre uma linha de áudio. A geometria simples preserva leitura em tamanhos pequenos. Substitui integralmente o T com onda anterior.

- Turquesa: `#39E0B8`; violeta: `#8E89FF`; fundo: `#101214`; texto: `#F4F4F4`.
- `symbol-0.4.svg`: símbolo isolado. `logo-0.4.svg`: assinatura horizontal ATMyTrack.
- `0.4/icon-{48,72,96,144,192,512}.png`: versões rasterizadas a partir do recurso Android real.
- `0.4/foreground.png`, `monochrome.png`, `symbol.png`: PNGs transparentes, 512 px.
- `0.4/circle.png` e `rounded.png`: conferência de recortes.
- Android: `ic_launcher_foreground`, `ic_launcher_background`, `ic_launcher_monochrome`, adaptive icon v26/v33; splash reutiliza o foreground. PNGs mdpi a xxxhdpi incluídos.

O foreground ocupa a região central do viewport 108 dp. O teste percorre todos os pixels visíveis e verifica a circunferência segura de raio 33 dp; também exporta e revisa máscaras circular e quadrada arredondada. Os assets históricos continuam guardados como histórico, mas não são usados no app atual.
