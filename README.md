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

O dataflow usa um único solver de resumos, com uma worklist até o ponto fixo.
Cada resumo é identificado por entrada de controle normalizada, endpoint de
retorno e estado relevante. Parágrafos alcançados por fallthrough, GO TO,
PERFORM ou handlers compartilham esse mesmo mecanismo. Componentes fortemente
conexos são identificados por Kosaraju iterativo: um ciclo permanece na mesma
worklist para juntar seus estados, e trechos entre componentes podem compartilhar
resumos. Isso evita transformar iterações em uma sequência de novas entradas.
O estado contém valores lógicos, handlers ativos/salvos e os fatos de alcance
necessários às continuações. A chave retém as entradas que podem ser lidas antes
de uma sobrescrita completa ou conservadas em algum caminho de retorno. Escritas parciais,
leituras anteriores e aliases que conservam texto continuam exigindo a entrada.
Handlers omitidos da chave são restaurados a partir do estado de cada chamador.

A relevância usa um índice compartilhado de pontos `(posição, endpoint)`, ampliado
sob demanda. Uma chamada entra no endpoint próprio do callee; seu retorno é
analisado no escopo do chamador. Para cada ponto, a necessidade é a união das
leituras locais com a necessidade dos sucessores, retirando as entradas que a
instrução certamente sobrescreve. Uma análise de sobrescritas possíveis e
garantidas também conserva a entrada quando uma escrita condicional deixa um
caminho intacto. Escritas de UNKNOWN ficam explícitas no resultado para substituir
o valor anterior do chamador. Conjuntos são bitsets com índices densos.
Sucessores são processados antes dos predecessores; ciclos usam worklist até o
ponto fixo. Efeitos locais e componentes já fechados são reutilizados. O endpoint
continua presente porque altera a regra de retorno.

O resultado de um resumo conserva o tipo de saída: conclusão, escape ou término
do programa. A continuação aplica a regra correspondente: seguir o trecho,
retornar/repetir uma invocação ou encerrar uma entrada independente. Essas regras
representam diferenças da linguagem no mesmo solver. Não há seletor de estratégia,
modo antigo ou restrição global a programas estruturados. As conclusões também
passam pela worklist, evitando desempilhar recursivamente caudas longas.

Consultar uma dependência continua usando o estado anterior à instrução. Grupos
conservam alternativas textuais correlacionadas, e REDEFINES/RENAMES relacionam
declarações e textos logicamente. O analisador não simula memória física.
As consultas são indexadas pelo consumidor: cada estado BEFORE é avaliado
separadamente, e só os valores resultantes são unidos. Isso preserva a correlação
entre valores de um chamador e evita varrer todos os estados para cada consulta.
A construção e validação do frontend também usam índices de membros e posições,
em lugar de buscas lineares repetidas. O contrato dos intervalos THRU é preservado.
UPPER-CASE, LOWER-CASE e TRIM usam expressões tipadas do frontend.

Compartilhar resumos elimina a repetição quando diferentes intervalos chegam ao
mesmo trecho com o mesmo estado relevante e endpoint. Entradas, endpoints ou
handlers diferentes podem exigir resumos diferentes: a solução geral não implica
um limite linear para todo programa COBOL. O orçamento explícito continua sendo
aplicado sem descartar candidatos para obter sucesso. Combinações de entradas
realmente observadas ainda podem criar exponencialmente muitos contextos. A
relevância conserva aproximações seguras: não compõe kills de um callee com a
continuação do chamador e não presume sobrescrita completa de grupos ou tabelas.

O JAR executa sozinho: não requer AIR, lowering, outros repositórios, serviços
externos ou produtos intermediários serializados.

## Compilar e executar

```sh
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

Os pacotes Java e o `groupId` Maven usam `com.imd.cobolexplorer`.
O ponto de entrada é `com.imd.cobolexplorer.DependencyMain`; o nome do JAR e
os argumentos do CLI permanecem os mesmos. Código que importava os pacotes
anteriores precisa atualizar seus imports e suas coordenadas Maven.
Os resultados da migração estão no [relatório de validação do namespace](benchmark/namespace-migration-20261008.md).

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

`--max-work N` limita visitas, estados, resumos, trabalho de relevância e produtos de candidatos (padrão 1000000).
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

A [unificação do solver](benchmark/solver-unification-20261008.md) passou em
294 testes do analisador, 856 FAST, 30 casos ampliados e 20 originais. O corpus
CardDemo foi reexecutado integralmente, preservando JSONs, códigos e diagnósticos.
Em OR1216, o trabalho caiu de 1.485.530 para 18.040 itens e o tempo de
9.98 s para 4.12 s na comparação nova. Transferências,
escapes e handlers usam o mesmo solver. O relatório registra também o overhead
nos casos já otimizados e os limites de crescimento restantes.

A [correção de relevância e índices](benchmark/relevance-fix-20261008.md) registra
os resultados sobre os 40 fixtures do discovery e as regressões do produto.
O FAST inclui as suítes do solver canônico, além dos contratos do frontend.
O [profiling da FIXTURE02](benchmark/fixture02-profiling-20261008.md) registra
um OOM na versão anterior. A [etapa 1 de compartilhamento](benchmark/shared-projection-20261008.md)
preserva os ramos das projeções e reduz a memória viva em 48% no experimento
limitado a 2.048 contextos, mantendo as mesmas entradas e o mesmo trabalho.
A multiplicação de resumos por estados distintos permanece; a execução completa
da FIXTURE02 ainda não está qualificada.

```sh
python3 -B scripts/harness/lean.py fast
mvn -Dtest=DependencySummaryTest,DependencyAnalyzerTest,DependencyRegressionTest,DependencySourceTest,DependencyEnvironmentTest,DependencyResourceTest package
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

Documentação e evidências distribuídas usam a identidade IMD. As cópias
históricas foram [anonimizadas](docs/identity-redaction.md); seus hashes originais
não descrevem os arquivos após essa transformação.
