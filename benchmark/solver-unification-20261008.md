# Solver canônico de resumos — 8/10/2026

O analisador usa um único mecanismo para controle estruturado, transferências,
escapes e handlers. Foram removidos o seletor de elegibilidade global, o modo
de referência, os dois cálculos alternativos de relevância, os assinantes
específicos de caudas e a busca de histórico na cadeia de chamadores. Não existe
um fallback para o solver anterior no código de produção ou nos testes.

Produção: `f9f77dd49d2f2e7b490119cc224ce1cf8edefc88`, JAR `98e1a30464b3c18349f6311c275f760fc3a032d9b5498cc5b741f4c7502782d4`.
Referência imediata: `981e1c535b47f7ffeba27cbaa16084dac825b249`, JAR
`64c403308574e82916c93e80e2ba58912c4de601668b5c3ba70bc61eb209d554`; checkout documental `2b0ebd6`.
Os dados, comandos, hashes, negativos e limites estão em
[solver-unification-20261008.json](solver-unification-20261008.json).
Os produtos originais da pipeline não foram editados.

## Organização do solver

`DependencyAnalyzer` mantém parse, binding, CFG COBOL, consultas e publicação.
`DependencyFlow` mantém uma worklist, um memo de resumos, os estados BEFORE e
um índice de relevância por endpoint. Nenhum intermediário AIR ou processo novo
foi introduzido. A interpretação lógica de valores e aliases foi preservada.

A chave de um resumo contém a entrada normalizada, o endpoint e o estado
projetado. Esse estado contém os valores relevantes, handlers ativos/salvos e
os fatos de alcance necessários às continuações de fonte. O histórico que
influencia o resultado está explícito na entrada; não é consultado através de
pilhas de chamadores ocultas. Valores exclusivos do chamador são restaurados
quando o resultado é composto com sua continuação.

O índice de relevância calcula leituras, escritas, aliases, grupos, elementos de
tabelas, possíveis handlers e pré-requisitos. Uma única worklist retrógrada
propaga uniões até o ponto fixo. O grafo é ampliado somente para entradas ainda
não indexadas, até seus destinos alcançáveis. Preparar o programa inteiro para
cada endpoint inline recriaria um custo quadrático; essa tentativa foi medida e
seus logs foram preservados, mas sua implementação foi substituída.

Os componentes fortemente conexos são calculados por **Kosaraju iterativo**,
em duas passagens pelo grafo, com custo O(V + E) por índice calculado. Um ciclo
entre parágrafos permanece no mesmo contexto, onde os estados se juntam nas
mesmas posições BEFORE. Somente a chegada a um parágrafo de outro componente
pode delegar para outro resumo. Essa regra vem da estrutura do grafo e vale para
todo programa; não depende da presença de GO TO, CICS, CALL ou arquivo.

A propagação é uma interpretação abstrata com memoização e junção de estados.
Cada contexto guarda resultados por **tipo de saída e destino**, em vez de um
único estado final. Mudanças nos resultados e suas entregas passam pela mesma
worklist, evitando desempilhamento recursivo de caudas longas. Consultas
internas continuam sendo observadas em BEFORE; o resumo não guarda apenas os
valores na saída. Recursão e fases de repetição usam o mesmo memo/worklist.

## Diferenças semânticas que permanecem

Há três tipos explícitos de continuação, todos atendidos pelo mesmo solver:

| Continuação | Obrigação |
|---|---|
| `Forward` | Compor o estado e encaminhar a saída do trecho ao contexto anterior. |
| `ReturnTo` | Aplicar o retorno/escape e as fases de repetição do PERFORM ou retorno do USE. |
| `Entry` | Analisar uma entrada de programa ou handler independente, sem uma instrução de retorno a retomar. |

Conclusão, escape e término também são saídas diferentes da linguagem. Uma
conclusão de PERFORM pode repetir ou retornar; um escape pode atravessar um
limite; GOBACK/STOP não pode retomar o chamador como uma conclusão normal. Usar
uma regra idêntica nesses três casos produziria destinos incorretos. São
ramificações semânticas locais, com testes de negativos e retornos, sem seleção
entre motores ou formatos de resumo.

## Problema encontrado e corrigido durante a qualificação

O protótipo `33ac660` dividia todos os parágrafos, inclusive os de um ciclo.
Os 30 sintéticos passavam, mas a rodada CardDemo foi interrompida após 22 saídas,
quando `CBSTM03A` não terminava. A sequência de estados nas reentradas podia
virar uma sequência de novas chaves, em vez de convergir no mesmo contexto.
Essa rodada permanece em `results/unified-carddemo-final-20261008/`, com status
**INTERRUPTED**, sem ser contabilizada como uma qualificação completa.

A decomposição em componentes corrige a causa sem criar uma estratégia de
exceção para essa fonte. Um teste pequeno exige junção do ciclo no mesmo resumo.
O contraexemplo real foi executado isoladamente, preservou seu JSON e terminou
em 3,78 s; a rodada integral abaixo foi executada novamente em outro diretório.

## Resultados finais

Foram aprovados **294 testes específicos**, **856 FAST**, execução isolada do
JAR e **20/20 casos originais** (18 fontes válidas e dois negativos de sintaxe,
com falha explícita e nenhuma saída publicada). A suíte herdada completa não
foi reexecutada: seus 1.720 PASS e um skip opcional são evidência histórica.

A matriz ampliada teve **30/30 PASS**, 114.89 s agregados e pico de
1696.2 MiB. São JVMs separadas e sequenciais, Temurin 21,
heap de 4 GiB, orçamento de 100 milhões e timeout de 300 s. Os 30 JSONs,
códigos de saída e diagnósticos foram iguais aos da versão com elegibilidade e
à versão anterior à otimização. A comparação verifica hashes de fontes e bytes
de saída; um negativo com diagnóstico injetado é rejeitado mesmo com JSON igual.

Comparação nova de seis casos com a versão imediatamente anterior, usando os
mesmos recursos. Uma execução por versão/caso; variação entre rodadas aparece
nos dados brutos e não representa intervalos de confiança:

| Caso | Tempo anterior → canônico | RSS anterior → canônico | Itens de trabalho anterior → canônico |
|---|---:|---:|---:|
| and1216 | 2.62 → 3.62 s | 362.2 → 408.5 MiB | 9,685 → 10,846 |
| or1216 | 9.98 → 4.12 s | 866.4 → 493.0 MiB | 1,485,530 → 18,040 |
| or152 | 1.27 → 1.12 s | 188.4 → 162.6 MiB | 23,594 → 2,080 |
| or304 | 1.82 → 1.42 s | 251.8 → 192.0 MiB | 93,818 → 4,360 |
| or608 | 3.77 → 2.27 s | 538.1 → 327.5 MiB | 372,890 → 8,920 |
| overlap-dynamic1800 | 5.07 → 5.22 s | 568.8 → 550.2 MiB | 33,926 → 32,166 |

A curva OR para 152/304/608/1.216 parágrafos foi **2080 / 4360 / 8920 / 18040** itens; a versão
anterior tinha 23.594/93.818/372.890/1.485.530. Em OR1216, a comparação nova
teve 2.42 vezes de ganho no tempo e 43.1% de redução no RSS.
Os testes também exigem crescimento abaixo do quadrático com transferências e
braços de handlers. O modelo geral acrescenta custo: AND1216 é mais lento que
a otimização restrita na comparação nova. Não se afirma melhoria em cada caso.

O caso sintético de **117.000 atribuições** terminou em
45.25 s e
1616.2 MiB, com uma consulta dinâmica.
A fonte corporativa de tamanho parecido não está disponível; esse resultado
não é uma medição dela. Fontes sem consultas podem dispensar dataflow, e os
casos derivados com consultas são identificados separadamente nos dados.

CardDemo: **73/73 entradas**, **815 relações iguais**, nenhuma ausente ou
adicional; **73 JSONs byte-idênticos**, códigos de saída e diagnósticos
preservados. Houve 26 códigos 0 e 47 códigos 1 (PARTIAL).
Com os mesmos recursos grandes desta qualificação, levou 157.92 s,
com pico de 746.8 MiB. O corpus antigo usava heap de 768 MiB;
não se usa esse par de tempos para alegar ganho. Os 185 hashes de fontes e
includes da referência foram verificados antes da execução.

## Limites e reprodução

A solução é geral dentro da semântica suportada pelo analisador, mas o custo
não é universalmente linear. Endpoints e estados relevantes realmente distintos
exigem resumos distintos; candidatos textuais, aliases e conjuntos propagados
podem crescer. Operações de efeito amplo conservam a demanda inteira. O
orçamento continua falhando explicitamente, sem truncar candidatos e sem
publicar um resultado global incompleto. Os 47 PARTIAL não foram convertidos
em sucesso fechado pela igualdade com os oráculos.

```sh
python3 -B scripts/harness/lean.py fast
mvn -Dtest=DependencySummaryTest,DependencyAnalyzerTest,DependencyRegressionTest,DependencySourceTest,DependencyEnvironmentTest,DependencyResourceTest package
python3 -B benchmark/smoke-standalone.py target/cobol-dependency-analyzer.jar
python3 -B benchmark/run-explosion-review.py benchmark/results/NOVO-DIR --heap 4096 --max-work 100000000 --timeout 300 --extended
python3 -B benchmark/run-resource.py benchmark/results/NOVO-DIR-ORIGINAIS --heap 4096 --max-work 100000000 --timeout 300
python3 -B benchmark/run-carddemo.py /caminho/results.json benchmark/results/NOVO-DIR-CARDDEMO --heap 4096 --max-work 100000000 --timeout 300
python3 -B benchmark/compare-suffix-results.py /caminho/antes /caminho/depois /caminho/comparacao.json
```

Os testes e o produto não dependem da pipeline. Os scripts diferenciais de
escala usam somente leitura dos insumos externos identificados; logs, fontes
geradas, sidecars e outputs ficam nos diretórios ignorados `results/` e `.tmp/`,
com hashes e resultados normalizados versionados. O relatório anterior de
[caudas estruturadas](suffix-optimization-20261008.md) permanece histórico.
