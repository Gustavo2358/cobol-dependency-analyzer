# Relevância de entrada e índices — 8/10/2026

A correção remove projeções excessivas e varreduras repetidas, mantendo o solver canônico de resumos. O [discovery](frontend-solver-discovery-20261008.md) é o baseline anterior à implementação. Não há solver legado, fallback de solver, flag de otimização ou caminho selecionado pelo tamanho do programa.

## Arquitetura e algoritmo

A chave do resumo continua sendo `(entrada normalizada, endpoint, estado de entrada relevante)`. A mudança está no cálculo do último componente. Valores, handlers e fatos de alcance usam a mesma análise de necessidade. Efeitos locais são compartilhados por posição; pontos de controle conservam o endpoint, porque ele determina se uma conclusão retorna ou continua o fluxo ordinário. Uma invocação entra no endpoint do callee e conserva sua retomada no escopo do chamador.

O índice cresce sob demanda, sem reconstruir os componentes já fechados. Kosaraju iterativo identifica ciclos. As duas propagações seguintes usam a mesma worklist, em ordem de sucessores antes dos predecessores, e bitsets com posições densas:

```text
MAY(n)  = KILL(n) ∪ união MAY(sucessores)
MUST(n) = KILL(n) ∪ interseção MUST(sucessores)
NEED(n) = READ(n) ∪ (união NEED(sucessores) − KILL(n))
          ∪ (MAY(n) − MUST(n))
```

Nas saídas sem sucessores, MAY/MUST de sucessores são vazios. MAY/MUST descrevem as sobrescritas comprovadas pelos efeitos locais; garantias de chamadas são aproximadas conservadoramente. `MAY − MUST` preserva entradas que podem atravessar algum caminho intactas e participar do retorno. Isso impede que uma escrita em um branch elimine o valor do outro branch durante a junção do resumo. Um ciclo volta à worklist até estabilizar; uma cadeia acíclica é resolvida em ordem reversa.

KILL exige evidência de sobrescrita integral do estado lógico: MOVE escalar sem referência modificada, subscrito, repetição, declaração assumida, alias possível ou texto de grupo preservado. Escritas parciais, grupos, tabelas, CORRESPONDING e transformações conservadoras continuam lendo o estado anterior. Essa distinção é semântica; não escolhe outro solver. Registradores de handlers têm efeitos de leitura/sobrescrita próprios, incluindo handlers salvos para RESET.

O resultado registra uma escrita de UNKNOWN explicitamente, mesmo quando a entrada projetada não contém a variável. Ausência no resultado significa preservar o valor do chamador; UNKNOWN significa substituí-lo por um valor desconhecido. Handlers omitidos da chave são restaurados do próprio chamador, e mudanças produzidas pelo resumo prevalecem.

As consultas finais são indexadas por consumidor. O analisador avalia cada estado BEFORE e depois une os valores; não junta ambientes de chamadores antes da consulta. A construção do frontend agrupa ocorrências por região uma vez, indexa posições e limites de seções, e valida membership por conjuntos. Ordem, conteúdo e validações do contrato tipado são preservados.

## Finding posterior: FIXTURE02

A [investigação com profiling](fixture02-profiling-20261008.md) encontrou OOM na fonte reconstruída, tanto no baseline quanto neste fix com heap de 4 GiB. O fechamento de relevância estabiliza; a multiplicação de resumos por estado de entrada e a reconstrução de suas árvores dominam o crescimento. Os PASS abaixo não qualificam esse novo fixture. O PR é apresentado em rascunho com esse limite explícito.

## Qualificação

Base de produção: `a1ddc8f4e20179425d7037df86ef4e5854a7a113`. JAR anterior: `c962031a19228b1e52c13c8262a06e92fb07aeed3aae3660db5f28efa0b2c0b8`. JAR qualificado: `b3f266e5a553a120488841d1428f2947434bb52487bb03661728b504885785d4`. Os hashes de fontes, tempos e contadores estão no [resumo JSON](relevance-fix-20261008.json).

- Suíte Java: 1.742 aprovações, zero falhas/erros, um teste herdado opcional desabilitado (`SemanticConditionContextDiscoveryTest`).
- FAST ampliado: 1.035 testes, sem falhas ou skips. O manifesto inclui as suítes do solver, que passam a ser executadas no CI.
- Discovery: 40/40 fontes com SHA conferido, nomes esperados, mesmos bytes de JSON e mesmos diagnósticos que o baseline.
- CardDemo: 73/73 fontes, 815 relações, nenhuma ausente/adicional; 73 JSONs, diagnósticos e códigos preservados. São 26 completos e 47 PARTIAL; a paridade não fecha os restos desconhecidos.
- Matriz ampliada: 30/30 PASS, com fontes, bytes, diagnósticos e códigos iguais ao baseline. Inclui 117 mil atribuições, AND/OR1216 e overlap dinâmico1800.
- Matriz original: 20/20 resultados esperados; 18 programas válidos e dois negativos de sintaxe, com códigos e ausência de publicação preservados.
- JAR independente: execução com ambiente e diretório isolados aprovada.
- Contextos combinatórios de profundidade 18: código 0, 36 destinos, JSON idêntico, sem diagnósticos; 524.288 contextos e 11.534.351 itens de trabalho preservados.

Java Temurin 21.0.12+1.1; heap 4 GiB; `--max-work=100000000`; timeout externo de 900 s. Cada fixture executa em uma JVM nova e sequencial. Uma observação por fixture de cada versão; o baseline do discovery é reutilizado com fontes e JAR fixados. Os tempos são comparações descritivas, não uma estimativa estatística de ganho.

| Fixture | Contextos antes → depois | Dataflow antes → depois (CFG nos casos frontend) |
|---|---:|---:|
| nested-initialized-after-400 | 402 → 3 | 207.0 → 165.5 ms |
| nested-initialized-before-400 | 3 → 3 | 157.9 → 154.7 ms |
| projection-full-write-400 | 401 → 2 | 145.5 → 179.6 ms |
| projection-killed-predicate-400 | 401 → 2 | 96.4 → 179.3 ms |
| projection-partial-write-400 | 401 → 401 | 145.7 → 104.5 ms |
| projection-read-before-write-400 | 401 → 401 | 211.6 → 104.8 ms |
| handler-irrelevant-vary-64 | 65 → 2 | 36.1 → 53.3 ms |
| handler-relevant | 5 → 5 | 35.8 → 31.3 ms |
| relevance-move-e64-w128 | 65 → 65 | 3912.1 → 506.7 ms |
| frontend-large-block-20000 | 1 → 1 | 1260.3 → 442.0 ms |
| context-growth-12 | 8,192 → 8,192 | 1327.5 → 712.4 ms |

O caso que repetia grandes fechamentos de relevância cai de 3.912,1 ms para 506,7 ms (~7,7×). A preparação do frontend em um bloco de 20 mil MOVE cai de 1.260,3 ms para 442,0 ms. Nos fixtures pequenos com escrita completa, a redução de contextos vem acompanhada de overhead: full-write passa de 145,5 para 179,7 ms; killed-predicate, de 96,4 para 179,3 ms. A análise adicional de sobrescritas e os índices têm custo fixo e não garantem redução de tempo em todos os programas.

Nos casos sem MOVE de inicialização explícito, a mutação de parágrafo passa de 403/5 para 4/4 contextos. A projeção conservadora conserva o TARGET na chamada externa: o seed VALUE inicial tem resto desconhecido, e o retorno do callee fecha o valor. Os controles com MOVE inicializado ficam em 3/3. Isso não é um seletor para fixtures; são estados de entrada diferentes sob a mesma análise.

A matriz ampliada levou 71,30 s, com pico de 1.694.876 KiB de RSS. O caso de 117 mil atribuições concluiu em 13,64 s (antes: 27,82 s), com 117.003 itens de trabalho e um contexto. O overlap dinâmico1800 concluiu em 3,52 s, com 1.802 contextos (antes: 4,27 s e 3.562 contextos).

O caso combinatório de profundidade 18 passa de 748.68 s para 532,63 s, com RSS de 3.236.744 para 3.208.724 KiB. A quantidade de contextos permanece 524.288: todas as 18 entradas são observadas. Esse resultado confirma redução de custo por análise, mas conserva o crescimento exponencial real. O tempo anterior foi reutilizado com fonte e JAR fixados.

## Regressões encontradas durante a implementação

A primeira projeção reduziu contextos, mas a rodada CardDemo encontrou sete dependências ausentes em cinco fontes. O resultado foi preservado como falha, não incorporado ao esperado. Um experimento diagnóstico sem kills isolou o problema. A escrita de UNKNOWN estava sendo tratada como ausência de escrita quando o valor anterior saía da chave.

Três novos testes ficaram RED antes da correção: UNKNOWN sobrescrito; escrita condicional com caminho intacto atrasado; registro condicional de handler com caminho intacto atrasado. Os atrasos expõem o erro independentemente de uma entrega favorável dos resultados parciais da worklist. A correção conserva a entrada dos caminhos intactos e materializa UNKNOWN no resultado. Os cinco programas afetados recuperaram JSONs e diagnósticos byte a byte equivalentes aos anteriores. Os resultados finais são qualificados separadamente; a tentativa inicial fica em `results/relevance-fix-initial-20261008/`.

## Limites

- O índice conserva pontos distintos quando o endpoint muda a semântica. No pior caso, sua dimensão ainda pode ser endpoints × grafo. Bitsets, ordem de propagação e reutilização reduzem operações e cópias; não provam um limite linear universal.
- A projeção não compõe kills do callee como um transformador de retorno. Ela conserva entradas quando não consegue garantir a sobrescrita; isso pode reduzir a reutilização, mas evita descartar dependências.
- Intervalos THRU ainda materializam sua lista de membros exigida pelo contrato. Muitos intervalos sobrepostos podem manter crescimento quadrático dessa representação.
- Estados realmente distintos e observados continuam produzindo resumos distintos. Não há coalescimento de chamadores que apague correlação, nem promessa de eliminar crescimento exponencial combinatório.
- Tempos dependem do programa, da JVM e da máquina; menos contextos não garantem menor latência em cada fixture. A fonte corporativa de 117 mil linhas não está disponível. Os casos sintéticos não garantem seu comportamento.

Evidência bruta final e tentativa inicial preservadas no arquivo local `results/relevance-fix-20261008.tar.gz`, SHA-256 `5355a91c828b88fe259375a48ba3eb4b306730849fa43c79514ee2a63c0cd3cf` (2028 arquivos). O manifest confere os hashes. JARs e classes reproduzíveis foram omitidos; seus hashes e os insumos de construção permanecem registrados.
