# Discovery: frontend, relevância e contextos — 8/10/2026

**Conclusão:** existem custos quadráticos no frontend e excessos concretos na projeção do solver. A explosão exponencial não vem de duplicação de corpos no frontend. O discovery recomenda corrigir os excessos e os índices antes de iniciar uma substituição do domínio de valores por resumos simbólicos. A memoização por entrada exata ainda tem um caso combinatório real que essas correções não prometem resolver.

Produção observada: `a1ddc8f4e20179425d7037df86ef4e5854a7a113`. JAR SHA256: `c962031a19228b1e52c13c8262a06e92fb07aeed3aae3660db5f28efa0b2c0b8`.

## Escopo e método

Não houve modificação de produção, feature flag, novo solver ou implementação de otimização. Foram criadas cópias instrumentadas de `DependencyFlow` e `ControlTopologySemantics` fora do build de produção, carregadas antes do JAR somente nas execuções diagnósticas. Instrumentação: contadores, temporizadores e dumps; nenhuma alteração intencional de arestas, transferências, projeções ou políticas.

40 fixtures qualificados, com duas execuções por fixture: CLI original e cópia diagnóstica. Em todos, conferimos nomes exatos, tipos, posições de origem válidas, ausência de diagnósticos de produto, igualdade de bytes da saída e igualdade de workItems, contexts e trackedDeclarations entre modos. Dois probes adicionais extraíram o caminho do grafo de relevância em testemunhos já qualificados, preservando os bytes da saída original.

Java Temurin 21; heap 4 GiB; --max-work=100000000; timeout externo de 900 s; JVMs novas e sequenciais. Uma observação por caso e modo. Tempos diagnósticos têm overhead e variabilidade; não são estimativas de melhoria. O contador memberContainsComparisons é derivado da estrutura e do algoritmo de busca, não medido com um incremento por comparação.

Dados resumidos: [JSON](frontend-solver-discovery-20261008.json). Evidência bruta local, ignorada pelo Git: [manifest](results/frontend-solver-discovery-20261008/manifest.json), [CSV](results/frontend-solver-discovery-20261008/measurements.csv), [scripts e logs](results/frontend-solver-discovery-20261008/).

## F1 — Relevância atravessa o retorno de uma chamada aninhada

**CONFIRMADO — excesso de projeção, com saídas corretas.** MAIN fornece 400 valores distintos a JUNK e chama OUTER. OUTER faz PERFORM INNER e termina. INNER escreve PROGA em TARGET e termina. Um parágrafo TAIL lê JUNK, mas não é alcançado pela execução.

Mover TAIL de depois de INNER para antes de INNER mantém os bytes da saída, os diagnósticos e os tamanhos da topologia. Com MOVE explícito inicializando TARGET, a única dependência é PROGA em ambos.

| Posição de TAIL inalcançável | Contextos totais | Contextos de OUTER | Variáveis projetadas em OUTER |
|---|---:|---:|---|
| Depois de INNER | 402 | 400 | JUNK, TARGET |
| Antes de INNER | 3 | 1 | TARGET |

O grafo diagnóstico mostra o caminho que introduz JUNK na relevância de OUTER:

```text
PERFORM INNER
  → MOVE PROGA TO TARGET
  → EXIT de INNER
  → COMPLETE de INNER
  → IF JUNK no TAIL inalcançável
```

Em `needed()`, LOCAL_INVOKE adiciona o corpo chamado e a continuação ao mesmo grafo. A conclusão do corpo chamado usa o endpoint de OUTER para decidir o fallthrough; como COMPLETE INNER não é COMPLETE OUTER, a relevância segue a continuação ordinária de INNER até TAIL. A execução real abre o contexto de INNER com seu próprio endpoint, retorna a OUTER e não executa TAIL. O frontend já publica entrada, endpoint e resume: a discrepância está no consumo dessas informações pela relevância.

Fontes: `DependencyFlow.java:695–752`, especialmente 707 e 717; bindings em `ControlTopologySemantics.java:250–265`. [Caminho bruto](results/frontend-solver-discovery-20261008/nested-paths.json).

Controles: retirar a chamada aninhada ou retirar TAIL reduz o excesso. Estender PERFORM para OUTER THRU TAIL torna TAILA/TAILB dependências esperadas e observadas. Um GO TO explícito de INNER para TAIL preserva PROGA e TAILB. Portanto, corrigir a conclusão ordinária não pode apagar transferências explícitas fora do intervalo.

## F2 — Menções sintáticas e escritas entram como entradas necessárias

**CONFIRMADO — excesso de projeção.** `DependencyDeclarations.reads()` percorre todas as referências dos filhos da AST. Em MOVE literal TO TARGET, o destino TARGET aparece tanto em localReads quanto em localWrites. `needed()` une essas referências/escritas e propaga conjuntos sem subtrair definições completas. Também pode coletar referências de corpos aninhados ao visitar uma instrução composta. Isso calcula uma pegada conservadora de variáveis, não os valores necessários na entrada.

| Testemunho com 400 invocações | Contextos totais | Resultado |
|---|---:|---|
| BODY sobrescreve TARGET completamente com PROGA | 401 | Somente PROGA |
| BODY sobrescreve JUNK antes do IF | 401 | Somente PROGA |
| BODY altera apenas o último caractere de TARGET | 401 | 400 nomes esperados, sem extras |
| BODY consulta TARGET antes de sobrescrevê-lo | 401 | 400 nomes de entrada + PROGA |
| BODY não usa TARGET; o CALL está no chamador | 2 | 400 nomes de entrada preservados |

Os dois primeiros casos conservam entradas que não influenciam nenhuma observação do corpo. Os três últimos são controles: atualização parcial, consulta BEFORE e estado exclusivo do chamador exigem tratamentos diferentes. Retirar destinos de qualquer instrução sem distinguir escrita completa, parcial e aliases não seria uma correção geral. O fixture originalmente chamado projection-read-first-400 contém CONTINUE no corpo e é o controle de valor exclusivo do chamador; a leitura efetiva antes da escrita está no fixture separado projection-read-before-write-400.

Fontes: `DependencyDeclarations.java:131–137`, `DependencyFlow.java:712`, atribuições e restauração no mesmo solver. A correção candidata é calcular necessidade de entrada com leitura semântica e kills de escritas comprovadamente completas, compondo efeitos de chamadas e preservando consultas BEFORE. Ainda não foi implementada ou qualificada.

## F3 — Handlers irrelevantes também fragmentam resumos

**CONFIRMADO — dimensão não projetada da chave.** Registrar 64 destinos diferentes de ERROR e chamar BODY, que apenas escreve TARGET, produz 65 contextos. Repetir o mesmo destino em 64 registros produz 2. Ambos geram somente PROGA; BODY não consulta/modifica handlers nem executa evento CICS. A chave mantém o mapa de handlers integralmente.

O controle handler-relevant executa CICS LINK dentro do corpo, alternando handlers ativos. O resultado exige FIRST, SECOND e TARGET; todos são observados. Assim, handlers não podem simplesmente sair da chave. Um eventual resumo deve conservar os handlers que afetam eventos e restaurar os demais no estado próprio do chamador. Handlers salvos/restaurados, pré-requisitos e efeitos desconhecidos precisam continuar cobertos pelos testes existentes e por novos controles antes de alterar essa projeção.

Fontes: `SummaryKey`, `subscribe()` em `DependencyFlow.java:642–655`; observações diagnósticas em handler-irrelevant-same-64 e handler-irrelevant-vary-64.

## F4 — Fechamento de relevância repetido por endpoint

**CONFIRMADO — multiplicador independente de entradas distintas.** Todos os predicados são inicializados explicitamente com N. Cada Pi contém uma condição falsa com GO TO HUB; HUB contém vários predicados também falsos. A execução não vai a HUB, mas a relevância estrutural indexa sua cadeia para cada endpoint. A saída de todos os casos é somente REACHED. Cada callee usa um contexto; o contexto principal acrescenta um endpoint/contexto à contagem.

| Callees/endpoints de PERFORM | Variáveis de HUB | Contextos totais | Junções de relevância | Dataflow original | Needed diagnóstico |
|---:|---:|---:|---:|---:|---:|
| 16 | 64 | 17 | 178,350 | 0.281 s | 0.224 s |
| 32 | 64 | 33 | 331,933 | 0.409 s | 0.376 s |
| 64 | 64 | 65 | 633,223 | 0.736 s | 0.605 s |
| 64 | 128 | 65 | 2,650,749 | 3.912 s | 4.256 s |

Com 64 callees e 128 variáveis, needed executa 2.650.749 junções e 2.131.593 desempilhamentos de propagação. São 34.245 vértices expandidos somando os índices separados. Na mesma execução diagnóstica, needed leva 4,256 s de 4,373 s em dataflow (aproximadamente 97%); queries levam 14,7 ms e SCCs 32,8 ms. O dataflow original leva 3,912 s. Não se deve dividir o tempo diagnóstico pelo tempo original para estimar uma participação.

O custo inclui revisitas até o ponto fixo, além de endpoints, vértices alcançados e uniões de conjuntos. Aumentar a largura de HUB de 64 para 128 amplia as junções aproximadamente quatro vezes neste testemunho, mantendo os mesmos 65 contextos. A imagem fornecida pelo usuário descreve um mecanismo compatível; seus números de 141 mil/176 mil/426 não foram reproduzidos literalmente.

Dentro de um endpoint, entradas já indexadas são reutilizadas: não há uma reconstrução integral para cada estado de entrada. Entre endpoints, os índices são separados. O endpoint afeta a condição de parada, portanto um cache global que ignore esse parâmetro não é semanticamente equivalente. Compartilhar estrutura e efeitos locais é candidato geral; o fechamento precisa representar corretamente chamadas, retornos e escapes.

## F5 — Custos quadráticos na construção e validação do frontend

**CONFIRMADO — estrutura/algoritmo, acompanhado por medições.** Há três fontes separadas:

1. Validação: para cada ocorrência, busca linear em region.members. Para uma região de n ocorrências, as buscas bem-sucedidas fazem n(n+1)/2 comparações, somando os comprimentos dos prefixos.
2. Construção: cada região filtra a coleção inteira de ocorrências para preencher members: R × V visitas. paragraph.indexOf e procedureOrder.indexOf acrescentam buscas lineares em loops.
3. Representação: cada intervalo PERFORM THRU materializa seus membros. N sufixos sobrepostos guardam N(N+1)/2 referências de regiões, mesmo que os corpos sejam compartilhados.

Fontes: `semanticproduct/ControlTopology.java:216–225`; `ControlTopologySemantics.java:113`, 253, 261 e 138.

| MOVE no bloco principal | Comparações de membership derivadas | Validação diagnóstica | Preparação original (cfg) |
|---:|---:|---:|---:|
| 5,000 | 12,512,503 | 85.7 ms | 228.1 ms |
| 10,000 | 50,025,003 | 230.8 ms | 413.8 ms |
| 20,000 | 200,050,003 | 669.8 ms | 1260.3 ms |

As contagens de comparações são derivadas dos tamanhos das listas e do algoritmo atual. O temporizador de validação inclui o construtor completo de ControlTopology, não só contains.

| Parágrafos extras | Visitas de agrupamento region × occurrence | Membros de intervalos (caso separado de THRU) |
|---:|---:|---:|
| 512 | 264,196 | — |
| 1024 | 1,052,676 | — |
| 2048 | 4,202,500 | — |
| 128 (THRU) | 66,564 | 8,256 |
| 256 (THRU) | 264,196 | 32,896 |
| 512 (THRU) | 1,052,676 | 131,328 |

Índices de membros e posições, e agrupamento único por região, podem substituir as buscas/varreduras preservando o modelo e suas validações. Comprimir a representação de intervalos é uma alteração de contrato maior; não é a primeira correção recomendada, pois o consumidor usa entrada e endpoint enquanto o contrato também valida a lista de membros.

## F6 — Contextos combinatórios reais continuam existindo

**CONFIRMADO — o frontend não expande as combinações.** Os controles retomam os fixtures com bits lidos no leaf.

| Profundidade | Ocorrências estáticas | Regiões estáticas | Bindings estáticos | Contextos medidos |
|---:|---:|---:|---:|---:|
| 10 | 93 | 64 | 21 | 2,048 |
| 12 | 111 | 76 | 25 | 8,192 |

A topologia cresce linearmente; os contextos seguem 2^(d+1) nos casos medidos. A evidência anterior do mesmo JAR, reutilizada, chega a 524.288 contextos em 244 linhas e 748,68 s. Todos os bits do fixture são realmente consultados no leaf. Corrigir a necessidade de entrada não oferece por si só um limite para essa combinação de valores anteriores.

A remoção do estado da chave seguida de junção de todas as entradas perde distinções entre chamadores. O controle caller-correlation exige somente ONLY: UNOBS é produzido numa segunda invocação cujo retorno não é consultado. Uma futura implementação tem de conservar esse resultado exato; não basta conservar uma união global de nomes possíveis.

Resumos paramétricos/fatoração de condições continuam sendo uma alternativa a investigar se a curva combinatória persistir depois das correções de projeção. Este discovery não demonstra que tal reescrita seja necessária para resolver os custos dominantes do programa corporativo.

## F7 — Coleta de respostas varre BEFORE para cada query

**CONFIRMADO — O(Q × B) no algoritmo atual.** A coleta usa um loop completo sobre before.entrySet para cada consulta, incluindo consultas em instruções inalcançáveis. No controle de profundidade 12: 24 queries × 122.889 posições BEFORE = 2.949.336 verificações. A coleta diagnóstica leva 308,8 ms nessa execução.

Fonte: `DependencyFlow.java:200–206`. O candidato simples é indexar queries por instrução e avaliar cada estado BEFORE somente para as queries daquele nó. É necessário avaliar a expressão em cada estado e depois juntar os valores; juntar estados de chamadores antes de avaliar pode inventar combinações. Nenhuma alteração foi implementada.

## Ordem recomendada de implementação

1. **Índices estruturais e de queries:** agrupamento de occurrences por region em uma passagem, membership/posições com consultas constantes, e queries por nó. Substituir os loops antigos; preservar formato e validações. São correções gerais, sem novo solver ou mudança deliberada de precisão.
2. **Necessidade real de entrada:** substituir a pegada de referências por leitura semântica e kills de escritas completas; compor chamadas com seus próprios limites e saídas; considerar handlers/fatos relevantes e restauração do estado exclusivo do chamador. Um único caminho para PERFORM, fallthrough, GO TO e handlers, com diferenças apenas de semântica.
3. **Repetir as medições:** testar os 40 testemunhos, selecionar os testes existentes de aliases/escapes/handlers/ciclos e os casos grandes relevantes. Medir quanto sobra do custo por endpoint e da curva de contextos.
4. **Só então escolher a mudança maior:** se o crescimento por entradas distintas continuar relevante, investigar representação paramétrica/fatorada no solver canônico. Não manter modos antigo/novo ou estratégias selecionadas por tamanho.

Esta ordem não promete eliminar todos os custos quadráticos ou exponenciais. Ela evita iniciar uma reescrita de valores para resolver excessos já demonstrados em outra parte do motor.

## Falhas iniciais preservadas e refinamentos de oráculo

- O primeiro harness exigia stderr vazio. A CLI publica totalMs e atomicPublishNanos ali; a saída de produto estava correta. O oráculo passou a distinguir diagnósticos de produto desse metadata. A tentativa bruta continua em runs-initial-oracle e initial-oracle*.
- O primeiro testemunho de relevância usava apenas VALUE N e esperava que todos os IF fossem definitivamente falsos. O solver abre VALUE com uma parcela desconhecida por seu contrato atual de ciclo de vida (test valueAndLifecycleRemainder). O resultado REACHED e UNREACH permanece preservado como divergência daquele oráculo; os testemunhos novos usam MOVE explícito e outros IDs/hashes. Esse primeiro caso não foi relabelado PASS.
- O primeiro controle de handler exigia CALL na linha de todas as dependências. CICS LINK também é uma query válida; os nomes e as posições estavam corretos. O harness foi corrigido para admitir o operando PROGRAM correspondente, preservando a tentativa inicial.

## Validação, reutilização e limites

- **Novo:** 40 fixtures × 2 modos, dois probes de grafo, equivalência de bytes/diagnósticos/work/contextos, controles positivos/negativos e duas mutações de posicionamento com igualdade de saída e tamanho da topologia.
- **Reutilizado:** medições anteriores de profundidades 16/18 e dos casos com 117 mil MOVE, fixadas no mesmo commit e JAR. FAST e CardDemo anteriores têm os mesmos insumos de produção; não são apresentados como executados aqui.
- **Não executado novamente:** FAST, full e CardDemo. Este discovery altera somente evidência; não invalida o código/JAR qualificado e não reivindica nova paridade. Quando houver implementação semântica, aplicar os gates locais, incluindo os 73 programas antes de reivindicar paridade.
- **Limites:** fonte corporativa indisponível; uma amostra de tempo por caso; overhead diagnóstico; ausência de otimização implementada; aliases complexos, pré-requisitos, handlers salvos e ciclos precisam de gates específicos na futura mudança. Não há garantia universal baseada em LOC.

## Matriz de todos os casos qualificados

| Caso | Contextos | Trabalho | Tempo original |
|---|---:|---:|---:|
| nested-tail-after-1 | 3 | 11 | 0.815 s |
| nested-tail-before-1 | 3 | 11 | 0.815 s |
| nested-tail-after-8 | 11 | 55 | 0.815 s |
| nested-tail-before-8 | 5 | 37 | 0.815 s |
| nested-tail-after-64 | 67 | 391 | 0.915 s |
| nested-tail-before-64 | 5 | 205 | 0.865 s |
| nested-tail-after-400 | 403 | 2,407 | 1.216 s |
| nested-tail-before-400 | 5 | 1,213 | 1.216 s |
| nested-no-tail-400 | 5 | 1,213 | 1.216 s |
| direct-tail-after-400 | 3 | 1,207 | 1.166 s |
| nested-tail-reachable-400 | 1,203 | 6,007 | 1.316 s |
| projection-full-write-400 | 401 | 2,402 | 1.266 s |
| projection-partial-write-400 | 401 | 2,402 | 1.266 s |
| projection-read-first-400 | 2 | 1,604 | 1.316 s |
| projection-killed-predicate-400 | 401 | 2,802 | 1.166 s |
| caller-correlation | 3 | 15 | 0.765 s |
| frontend-large-block-5000 | 1 | 5,003 | 1.817 s |
| frontend-large-block-10000 | 1 | 10,003 | 2.618 s |
| frontend-large-block-20000 | 1 | 20,003 | 4.672 s |
| frontend-paragraphs-512 | 1 | 3 | 0.915 s |
| frontend-paragraphs-1024 | 1 | 3 | 1.016 s |
| frontend-paragraphs-2048 | 1 | 3 | 1.216 s |
| frontend-overlap-128 | 129 | 641 | 0.915 s |
| frontend-overlap-256 | 257 | 1,281 | 1.066 s |
| frontend-overlap-512 | 513 | 2,561 | 1.266 s |
| relevance-move-e16-w64 | 17 | 132 | 1.266 s |
| relevance-move-e32-w64 | 33 | 196 | 1.367 s |
| relevance-move-e64-w64 | 65 | 324 | 1.717 s |
| relevance-move-e64-w128 | 65 | 388 | 5.022 s |
| context-growth-10 | 2,048 | 28,679 | 1.066 s |
| context-growth-12 | 8,192 | 131,081 | 2.271 s |
| nested-initialized-after-400 | 402 | 2,406 | 1.366 s |
| nested-initialized-before-400 | 3 | 1,209 | 1.316 s |
| projection-read-before-write-400 | 401 | 2,802 | 1.517 s |
| handler-irrelevant-same-16 | 2 | 54 | 0.865 s |
| handler-irrelevant-vary-16 | 17 | 84 | 0.815 s |
| handler-irrelevant-same-64 | 2 | 198 | 0.915 s |
| handler-irrelevant-vary-64 | 65 | 324 | 0.965 s |
| handler-relevant | 5 | 23 | 0.816 s |
| nested-explicit-transfer | 130 | 583 | 0.966 s |

Arquivo completo de evidência local: [tar.gz](results/frontend-solver-discovery-20261008.tar.gz). SHA256: `0b2fdb0452eec1f691fa3850f1a79d0c4755bc854e98de4b5109d0d4a473b130`. Classes compiladas reproduzíveis não foram incluídas; fontes instrumentadas, comandos de compilação, hashes e logs foram preservados.
