# COBOL Dependency Analyzer

Ferramenta de análise estática que recebe fontes COBOL e produz um JSON com
os programas, arquivos, tabelas DB2, copybooks, DCLGENs e SQL INCLUDEs dos quais
cada programa depende. Resolve tanto nomes literais quanto nomes construídos
ou armazenados em variáveis, respeitando as atribuições e o fluxo de controle.

O objetivo é facilitar o inventário de dependências de aplicações COBOL e a
avaliação do impacto de mudanças, com execução simples e custo de memória
controlado. A análise acontece em um único processo Java.

## Como funciona

```text
Fonte COBOL → preprocessing → AST e binding → CFG COBOL
           → dataflow lógico → resolução de dependências → JSON
```

O frontend existente fornece o parser, as declarações, a resolução de nomes e
a localização original do código. Sobre essas informações, um CFG em memória
representa branches, loops, parágrafos, seções e continuações de PERFORM.
O dataflow acompanha os valores que podem alcançar cada operação de dependência.
Somente declarações relevantes às consultas e ao seu controle são propagadas.

Por exemplo, após `MOVE 'PROGA' TO WS-TARGET` seguido de
`MOVE 'PROGB' TO WS-TARGET`, um `CALL WS-TARGET` depende de `PROGB`.
Uma sobrescrita completa elimina o valor anterior; se branches viáveis
atribuírem nomes diferentes, ambos permanecem candidatos.

PERFORM compartilha corpos e reutiliza resultados por entrada relevante e
retorno. Em programas com controle estruturado, intervalos sobrepostos também
compartilham as caudas dos parágrafos, por estado de entrada e destino de
retorno. O cálculo das variáveis relevantes é reutilizado por destino, evitando
percorrer a mesma cauda para cada intervalo. Consultas internas continuam usando
o estado anterior à instrução. Transferências explícitas, escapes contextuais
e handlers usam o solver anterior. Grupos conservam alternativas textuais
correlacionadas, e
REDEFINES/RENAMES relacionam declarações e textos logicamente. O analisador
não simula memória física. UPPER-CASE, LOWER-CASE e TRIM usam expressões
tipadas do frontend.

O JAR executa sozinho: não requer AIR, lowering, outros repositórios, serviços
externos ou produtos intermediários serializados.

## Compilar e executar

```sh
git clone https://github.com/Gustavo2358/cobol-dependency-analyzer.git
cd cobol-dependency-analyzer
mvn package
java -Xmx768m -jar target/cobol-dependency-analyzer.jar \
  --source programa.cbl --copy-dir copybooks --output dependencies.json
```

Requer Java 17+ e Maven para compilar; o JAR contém as dependências necessárias
para executar. Java 21 foi usado na validação. Aceita um arquivo ou diretório;
`--copy-dir` pode ser repetido. Diretórios leem `.cbl`, `.cob` e `.cobol` em ordem estável; outras extensões,
como `.cl2`, são aceitas por entrada de arquivo explícita.
O formato de fonte é FIXED; o charset padrão é UTF-8. O parser direto é padrão,
com fallback ANTLR; `--parser antlr` seleciona a outra rota explicitamente.

## Dependências e saída

CALL, CICS LINK e CICS XCTL produzem dependências de programa. Declarações de
arquivo e operações CICS FILE/DATASET identificam arquivos. A análise de fonte
extrai tabelas DB2, COPY, DCLGEN e SQL INCLUDE, incluindo código expandido.

A saída contém somente o programa, as dependências e sua localização de origem:

```json
{"program":"EXAMPLE","dependencies":[{"type":"program","name":"PROGA","at":{"file":"EXAMPLE.cbl","line":12}}]}
```

Vários programas, ou uma entrada de diretório, produzem um array desses objetos.
Há uma entrada por `(type,name)` em cada programa, ordenada deterministicamente;
a localização representa o consumidor original, inclusive dentro de COPY.
Tipos: `program`, `file`, `logical-file`, `db2-table`, `copybook`, `dclgen` e
`sql-include`. `logical-file` identifica um nome COBOL sem nome externo
estabelecido; não afirma a existência de um dataset. DCLGEN requer classificação
explícita em `--source-inventory` no formato do inventário já fornecido pelo
frontend; um SQL INCLUDE genérico não é presumido como DCLGEN.

## Incerteza e limites

O propagador conserva candidatos conhecidos junto à possibilidade desconhecida.
Os diagnósticos ficam em stderr, separados do JSON de dependências.

Saídas: **0** sem diagnóstico de incompletude; **1** candidatos publicados com
limitações locais explicitadas em stderr; **2** falha global, preservando o JSON
anterior. VALUE persistente, input externo, missing COPY/INCLUDE, SQL dinâmico e
operações sem transformação lógica suportada mantêm incerteza explícita.
Tabelas de extensão estática conservam os elementos lógicos para consultas com
índice conhecido; subscrito desconhecido usa resumo dos elementos. Tabelas de
extensão não estabelecida permanecem abertas. Representações binárias/edições numéricas complexas
não são interpretadas como textos. PERFORM TIMES acima de uma iteração usa
ponto fixo, podendo sobreaproximar alvos que dependem da contagem exata.
Não há garantia de paridade universal de COBOL; a paridade medida refere-se
aos insumos e oráculos documentados.

`--max-work N` limita visitas, estados e produtos de candidatos (padrão 1000000).
Ultrapassar o orçamento falha explicitamente; não corta candidatos para obter
sucesso. `--metrics arquivo.jsonl` grava tempos e contadores fora do JSON de
produto. Heap é configurado pelo Java, por exemplo `-Xmx768m`.

Para programas grandes, os testes de escala usaram heap de 4 GiB e orçamento
de 100 milhões de visitas:

```sh
java -Xmx4g -jar target/cobol-dependency-analyzer.jar \
  --source programa.cbl --copy-dir copybooks --max-work 100000000 \
  --output dependencies.json
```

A [auditoria de escala](benchmark/explosion-review-20261008.md) inclui
117 mil atribuições e 1.800 faixas sobrepostas com consulta dinâmica, com
destinos esperados conferidos. Consumo e tempo dependem também da estrutura
do programa, além da quantidade de linhas.

## Validação

O corpus CardDemo foi processado integralmente: **73/73 fontes, 815 relações
iguais à referência, nenhuma ausente e nenhuma adicional**. As fontes incluem
variantes de 45 nomes de programa. As duas execuções produziram JSONs idênticos.
Houve 26 fontes com código 0 e 47 com código 1 (PARTIAL): igualdade com os
oráculos não significa que todos os valores desconhecidos estejam fechados.

Na medição anterior à otimização de caudas, o monolito levou **123,2 s**,
contra **482,2 s** da referência,
com pico de RSS de **464,4 MiB**, contra **792,4 MiB**. A referência usa os
produtores congelados do corpus e o consumidor de 8/10/2026; SHAs, método e
repetição estão no [relatório das medições](benchmark/README.md).

Na versão anterior, foram aprovados 280 testes específicos e 20 casos de estresse, incluindo
18 fontes válidas e dois negativos de sintaxe. A suíte completa teve 1.720
aprovações, nenhuma falha e um teste herdado futuro opcional desabilitado.
Nenhum caso obrigatório de estresse apresentou crash ou OOM.

A [otimização de caudas de PERFORM](benchmark/suffix-optimization-20261008.md)
passou em 290 testes do analisador, 856 FAST e no corpus completo novamente,
com JSONs e códigos de saída preservados. Overlap1800 caiu de 42,09 s para
4,37 s e de 3.078,9 MiB para 514,8 MiB de RSS, com os mesmos recursos.
As famílias com transferências explícitas e handlers mantêm o solver anterior.

```sh
python3 -B scripts/harness/lean.py fast
mvn -Dtest=DependencySuffixTest,DependencyAnalyzerTest,DependencyRegressionTest,DependencySourceTest,DependencyEnvironmentTest,DependencyResourceTest package
python3 -B benchmark/run-carddemo.py /caminho/results.json benchmark/results/carddemo
python3 -B benchmark/run-reference.py /caminho/results.json benchmark/results/reference
python3 -B benchmark/smoke-standalone.py target/cobol-dependency-analyzer.jar
```

Testes automatizados e seus fontes/oráculos estão neste repositório e executam
sem a pipeline. Os scripts diferenciais usam o corpus e os artefatos de referência
externos somente para validação. Veja [as medições](benchmark/README.md) e
[a procedência dos fixtures](src/test/resources/dependency-regression/README.md).

O projeto preserva o histórico do frontend de origem. Código e testes herdados
do explorador permanecem para regressão do frontend; não integram o fluxo do
novo CLI. [MISSION.md](MISSION.md) registra o pedido original; a autorização
posterior de publicação substitui sua restrição inicial a commits locais.
