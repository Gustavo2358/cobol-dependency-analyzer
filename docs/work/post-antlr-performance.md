# Desempenho pós-ANTLR — implementação e qualificação

Implementação e merge aprovados em 2026-10-01 no
[PR #79](https://github.com/Gustavo2358/proleap-poc/pull/79).
O PR registra o HEAD final, os checks e o commit de integração.

## Escopo e algoritmos

A mudança reduz trabalho repetido na análise posterior ao parsing. Gramática,
ANTLR, contratos públicos e formatos de saída permanecem os mesmos.

- `CobolSemanticProduct.State`: avalia o grafo de provas uma vez quando existem
  condições iniciais `LOGICAL_TEXT`; consulta o conjunto de células provadas para
  cada condição. Uma célula sem prova continua rejeitada.
- `FactLocalitySemantics`: deduplica seções pelo ID canônico da unidade, evitando
  calcular o hash estrutural de toda a subárvore. Para `MODEL_STORAGE`, indexa os
  intervalos das declarações sintéticas por início e máximo fim de cada subárvore.
  A consulta conserva o predicado estrito de sobreposição e as regras de contexto
  e fechamento. A classificação geral de inputs por região ainda faz varreduras.
- `ProcedurePerformSemantics`: calcula o fechamento primário após finalizar os
  callees provisórios e reutiliza a prova de exclusão de entradas externas por
  conjunto de membros. Corpo, presença da chamada no primário, retorno e
  sobreposição continuam verificados para cada chamada. Muitos ranges distintos
  e sobrepostos ainda podem exigir trabalho quadrático.

O reaproveitamento termina na publicação ou unidade em análise. Não há cache
entre execuções nem paralelismo novo. As regras duráveis estão em
[localidade de fatos](../domain/fact-dependency-locality.md) e
[família PERFORM](../domain/perform-family.md).

## Resultados medidos

Base: `cc1fb20a0e19284b8b97b44628403a74630bd466`.
Implementação qualificada: `1c148e6914c559680f174c92b1341a0bfc2b1619`.
O fechamento posterior altera somente documentação; reutiliza as medições e a
regressão desse código, com novo Fast CI no HEAD final do PR.

Temurin 21.0.12, G1, `-Xms256m -Xmx2g`, JVM nova por execução. As versões foram
executadas em série, alternando AB/BA, sem builds ou testes concorrentes.
Cada linha usa cinco repetições da CLI sem instrumentação e três execuções
instrumentadas separadas para as fases pós-ANTLR. Valores abaixo são medianas,
na ordem base → implementação.

| Entrada | CLI, s | Fases pós-ANTLR, s | Alocações pós-ANTLR, MB |
| --- | ---: | ---: | ---: |
| COACCT01 | 4,673 → 3,971 | 2,689 → 1,989 | 616,983 → 331,842 |
| COACTUPC | 6,425 → 5,975 | 2,492 → 2,209 | 410,894 → 391,990 |
| 1.600 constantes, sintético | 19,398 → 3,069 | 17,030 → 1,596 | 16.503,261 → 300,447 |
| 3.200 PERFORMs, sintético | 5,424 → 2,819 | 4,379 → 1,948 | 7.794,848 → 609,094 |

| Entrada | CPU do processo, s | Pico de RSS, MiB |
| --- | ---: | ---: |
| COACCT01 | 12,382 → 11,091 | 453,633 → 454,570 |
| COACTUPC | 15,926 → 14,315 | 427,785 → 387,395 |
| 1.600 constantes, sintético | 25,980 → 8,776 | 376,801 → 380,828 |
| 3.200 PERFORMs, sintético | 12,684 → 8,746 | 441,043 → 379,145 |

Nos dois programas reais, a CLI ficou cerca de 15% e 7% mais rápida. O total de
bytes alocados pela thread principal nas fases pós-ANTLR caiu cerca de 46% e 5%.
Essa medida não representa heap retido nem memória residente: o RSS ficou
praticamente estável em COACCT01 e caiu em COACTUPC. CPU também caiu nos quatro
casos. Os sintéticos exercitam os padrões de repetição eliminados; seus ganhos
não são uma previsão para todo o corpus ou outras máquinas.

## Regressão e gates

- `qualification-local`: PASS; 1.301 testes em 190 suítes, zero falhas/erros e um
  teste histórico opcional ignorado (`SemanticConditionContextDiscoveryTest`).
  Regressão de normalização/artefatos e naming também passaram.
- FAST local e [Fast CI da implementação](https://github.com/Gustavo2358/proleap-poc/actions/runs/36891737862):
  730 testes em 118 suítes, zero falhas/erros/skips. O remoto usa Java 17.
- Os novos testes confrontam o índice com uma varredura independente, incluindo
  intervalos aninhados, coincidentes, pontuais e limites estritos; rejeitam
  invariantes sem prova; cobrem PERFORM inalcançável, entradas externas,
  retornos distintos, ranges sobrepostos e isolamento entre unidades/execuções.
- Regressão diferencial com a base recompilada: **586 casos**, sendo 73 CardDemo,
  39 PERFORM, 48 chaos, 14 aliases, 25 adversariais PERFORM, 331 fixtures,
  29 contratos focais, um frontier-payload-green e 26 JSON GENERATE.
- **11.128 dos 11.134 pares de arquivos são idênticos byte a byte.** Os seis
  restantes diferem apenas na ordem das chaves `relation` e `visibility` de
  atributos de `INDEX_NAME` em `symbol-data.js`. A construção existente com
  `Map.of` em `SymbolTableBuilder` permite essa variação entre JVMs. Seis execuções
  da main sem alterações reproduziram ambas as ordens; duas execuções da
  implementação também foram comparadas. Todos os valores e demais arquivos
  coincidiram nos oito controles; os bytes originais foram preservados.
- Os **586 Semantic Products e 586 envelopes de compilação são idênticos byte a
  byte**. IDs, fatos, gaps e estados `PARTIAL` foram preservados. AIR/CFG não foram
  reexecutados; seus inputs publicados pelo frontend são idênticos à base.
- Os benchmarks somam 80 execuções, incluindo escalas, perfis e controles de
  instrumentação, com 1.292 comparações adicionais de arquivos idênticas.

Não foi observada regressão de conteúdo no conjunto qualificado. A variação
preexistente de ordem de chaves permanece; a comparação admite somente a troca
dessas duas chaves, com igualdade dos valores JSON e dos demais bytes.

## Evidência preservada

No workspace de qualificação, os resultados brutos ficam em
`.proleap-post-antlr-implementation-20261001/evidence/`, relativo à raiz agregadora:

- `qualification-summary.json` e `qualified-corpus-results.json`: consolidação,
  casos e seis diferenças de ordem identificadas individualmente;
- `benchmark-summary.json` e `benchmarks/`: método, dispersão, resultados e
  produtos por execução;
- `runtime/manifest.json` e `implementation-commit.json`: fontes e binários
  usados, com hashes e vínculo ao commit da implementação;
- `full-surefire-reports/`, logs locais/remotos e `baseline-order-control/`:
  testes e reprodução independente da variação histórica.

As tentativas com inventários históricos ausentes foram mantidas; os 55 casos
afetados foram reexecutados com inventários do mesmo commit baseline. Os dados
brutos e builds permanecem fora do Git do produto. O PR e os checks registram a
integração, conforme a [política lean](../engineering/lean-harness.md).
