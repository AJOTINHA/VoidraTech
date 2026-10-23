# Voidra Tech

Máquinas multibloco que disparam um laser até o vazio para extrair minérios, para **Minecraft 26.3 / NeoForge 26.3.0.95**, sem dependências (o JEI é opcional).

## Perfuratriz do Vazio

Multibloco com o controlador em cima da estrutura; o feixe sai do Suporte de Lente, embaixo, e precisa de caminho livre
até a bedrock ou o vazio. Os minérios vão para um baú (ou qualquer inventário) encostado no controlador: sem baú, ela não
minera. Clique no controlador com a mão vazia para ver o estado. Energia: FE, entra por qualquer lado do controlador.

| Nível | Material | Estrutura (largura x camadas, + controlador) | Modificadores | Tempo por minério | Consumo |
| --- | --- | --- | --- | --- | --- |
| 1 | Umbrita | 7x7 x 4 | 2 | 20 s | 200 FE/t |
| 2 | Nebulita | 7x7 x 5 | 4 | 10 s | 800 FE/t |
| 3 | Obscúrio | 9x9 x 6 | 8 | 5 s | 2 500 FE/t |
| 4 | Penumbrita | 11x11 x 7 | 12 | 2,5 s | 8 000 FE/t |
| 5 | Eclípsio | 13x13 x 8 | 16 | 1,5 s | 25 000 FE/t |
| 6 | Voidrio | 15x15 x 9 | 20 | 1 s | 80 000 FE/t |

Camadas: o controlador fica em cima de tudo; no topo, um emissor de feixe embaixo dele, um anel de molduras e
estabilizadores nas diagonais e em cruz; no meio, emissores na coluna central (o último, logo acima do suporte, é a
lente, obrigatória) e uma moldura em cada canto; embaixo, o suporte de lente, o anel de molduras, estabilizadores em
cruz, encaixes de modificador (Modificador Nulo ou outro) e Revestimento do Vazio no resto do chão.

### Receitas

O nível 1 sai de itens vanilla e Cristais de Umbrita; cada nível seguinte é um upgrade das peças do anterior com o
cristal do nível novo, então a perfuratriz é refeita nível a nível. Peças pedidas às
dezenas saem 8 por receita com 1 cristal; as poucas (emissores, suporte, controlador) custam mais.

| Peça | Nível 1 (Umbrita) | Níveis 2 a 6 |
|---|---|---|
| Moldura | 6 ferros + 2 cristais (anel, meio vazio) → 8 | 8 molduras do nível anterior + 1 cristal no meio → 8 |
| Estabilizador | 4 ferros nos cantos, 4 quartzos, 1 cristal no meio → 4 | 8 do nível anterior + 1 cristal → 8 |
| Emissor de Feixe | ferro nos cantos, vidro em cima/embaixo, 2 cristais e bloco de redstone no meio → 1 | 4 cristais nos cantos, 2 blocos de redstone em cima/embaixo, 2 vidros dos lados, emissor anterior no meio → 1 |
| Suporte de Lente | ferro e cristal em cima, 2 cristais dos lados, 3 obsidianas embaixo → 1 | 4 cristais nos cantos, diamante em cima, 2 obsidianas dos lados, bloco de cristal embaixo, suporte anterior no meio → 1 |
| Perfuratriz (controlador) | 6 cristais, bloco de diamante em cima, emissor de Umbrita no meio, obsidiana embaixo → 1 | 6 cristais, bloco de diamante em cima, perfuratriz anterior no meio, emissor do nível embaixo → 1 |

Iguais para todos os níveis: **Revestimento do Vazio** (ferro nos cantos, obsidiana dos lados, Umbrita no meio → 8),
**Modificador Nulo** (ferro nos cantos, vidro dos lados, Revestimento no meio), **Lente Clara** (7 vidros em H),
**lentes coloridas** (Lente Clara + corante; a magenta é a de cristal), qualquer lente colorida volta a ser Clara
sozinha na bancada, e o **Bastão de Montagem** (Umbrita na ponta e 2 obsidianas na diagonal). A
Célula de Energia Infinita é só para testes e não tem receita.

Do nível 1 ao 6 a estrutura pede 56/60/80/100/120/140 molduras, 24/28/40/56/68/84 estabilizadores, 2 a 7 emissores e
14/12/28/52/84/124 revestimentos; subir um nível custa uns 40 a 80 cristais do nível novo.

### Modificadores

Cada encaixe do chão leva um modificador; o Modificador Nulo só preenche o encaixe. Todos disputam os mesmos encaixes:

- **Modificador de Velocidade**: +10% de velocidade e +10% de energia por minério, cada um. Com todos os encaixes nele,
  o nível 1 fica 1,2x mais rápido (1,44x o FE/t) e o nível 6, 3x (9x o FE/t). Receita: blocos de redstone nos cantos,
  Cristais de Nebulita em cima e embaixo, Cristais de Umbrita dos lados e o Modificador Nulo no meio.
- **Modificador de Energia**: +10% de eficiência cada (a energia é dividida por 1 + 0,1 por modificador), sem mudar a
  velocidade; o chão do nível 6 cheio deles gasta um terço. Receita: a mesma, com glowstone no lugar da redstone.
- **Modificador de Foco**: aumenta o foco da lente colorida. A lente multiplica o peso dos minérios dela por 8 (o peso normal mais um bônus de 7x), e cada Modificador de Foco
  aumenta esse bônus em 10%: com o chão do nível 1 cheio deles a lente fica x9,4; com o do nível 6, x22 (bônus 3x,
  como a velocidade do chão cheio de Modificadores de Velocidade). Cada um gasta 5% a mais de energia (2x no nível 6
  cheio), sem mudar a velocidade; sem lente colorida (ou com a Lente Clara) não faz nada, mas o custo continua. O JEI
  mostra as chances sem modificadores. Receita: a mesma dos outros, com blocos de lápis-lazúli no lugar da redstone.

O FE/t fica `base x (1 + 0,1 x velocidade)² / (1 + 0,1 x energia) x (1 + 0,05 x foco)`; o buffer e a entrada de energia já cabem o caso com
todos os encaixes em velocidade. Clique no controlador para ver os modificadores e o consumo.

### Cristais

Cada nível tem o seu cristal. O de Umbrita é feito na bancada (2 cristais: fragmento de
ametista em cima e embaixo, quartzo dos lados, pérola do Ender no meio); os outros só saem da perfuratriz, e a de cada
nível tira o cristal do nível seguinte (a de Umbrita tira Umbrita e Nebulita, a de Nebulita tira Obscúrio, e assim por
diante). O cristal mais novo é sempre o mais raro, e com só minérios
vanilla os cristais são 8% a 14% do que sai. A Lente de Cristal (a lente magenta, que nenhum minério usa) foca todos
os cristais (8x); ela é feita com 4 Blocos de Cristal de Nebulita em volta de uma Lente Transparente.
9 cristais formam um Bloco de Cristal, que volta a dar 9. O Cristal de Voidrio é animado.

### JEI

Com o JEI instalado (opcional), cada nível de perfuratriz tem a sua aba: tudo que ela tira do vazio, com a chance por
minério sem lente e com a lente que foca cada um, o tempo médio para sair um e a dimensão. Ver a receita de um minério
ou cristal mostra em que perfuratrizes ele sai; ver os usos de uma lente mostra o que ela foca. As abas seguem a lista
de minérios do servidor, inclusive depois de um `/reload`.

Os minérios vêm de `data/<namespace>/voidratech/void_drops/*.json` (`item` ou `tag`, `weight`, `min_tier`), então
modpacks podem mudar a lista; tags vazias (ex.: `c:ores/tin` sem mod de estanho) são ignoradas.

## Licença

Todos os direitos reservados (`LICENSE`).

## Desenvolvimento

```bash
./gradlew runClient          # abre o jogo com o mod
./gradlew runGameTestServer  # roda os testes automáticos
./gradlew build              # gera o jar em build/libs
```

Para copiar o jar para uma instância do Prism a cada `build`, crie um `local.properties` (fora do git) com:

```properties
voidratech_prism_mods_dir=C:/caminho/da/instancia/minecraft/mods
```

Os JSONs de modelos, receitas, loot tables, tags e minérios do vazio são gerados por `tools/gen_resources.py`
(`python tools/gen_resources.py .`), e as texturas por `tools/gen_textures.py` (`python tools/gen_textures.py .`).
