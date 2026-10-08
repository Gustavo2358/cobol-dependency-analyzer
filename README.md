# COBOL Dependency Analyzer

Analisador de dependências COBOL em um processo Java. Reutiliza preprocessing,
AST e binding do frontend e resolve valores sobre seu controle COBOL em memória.
O caminho operacional não usa AIR, lowering, memória física, Semantic Product
serializado, HTML ou outros repositórios.

```sh
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

A saída contém somente:

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

O propagador conserva candidatos conhecidos junto à possibilidade desconhecida.
MOVE completo substitui valores anteriores; grupos conservam alternativas
textuais correlacionadas. PERFORM compartilha corpos e reutiliza resultados por
entrada relevante e retorno, sem enumerar caminhos ou clonar parágrafos.
REDEFINES/RENAMES são relações entre declarações e textos, sem simular bytes.
UPPER-CASE, LOWER-CASE e TRIM usam as expressões tipadas do frontend.

Saídas: **0** sem diagnóstico de incompletude; **1** candidatos publicados com
limitações locais explicitadas em stderr; **2** falha global, preservando o JSON
anterior. VALUE persistente, input externo, missing COPY/INCLUDE, SQL dinâmico e
operações sem transformação lógica suportada mantêm incerteza explícita.
Tabelas de extensão estática conservam os elementos lógicos para consultas com
índice conhecido; subscrito desconhecido usa resumo dos elementos. Tabelas de
extensão não estabelecida permanecem abertas. Representações binárias/edições numéricas complexas
não são interpretadas como textos. PERFORM TIMES acima de uma iteração usa
ponto fixo, podendo sobreaproximar alvos que dependem da contagem exata.
Esses limites não são paridade universal de
COBOL; a paridade medida refere-se aos insumos e oráculos documentados.

`--max-work N` limita visitas, estados e produtos de candidatos (padrão 1000000).
Ultrapassar o orçamento falha explicitamente; não corta candidatos para obter
sucesso. `--metrics arquivo.jsonl` grava tempos e contadores fora do JSON de
produto. Heap é configurado pelo Java, por exemplo `-Xmx768m`.

```sh
mvn -Dtest=DependencyAnalyzerTest,DependencyRegressionTest,DependencySourceTest,DependencyEnvironmentTest,DependencyResourceTest test
python3 -B scripts/harness/lean.py fast
mvn test
python3 -B benchmark/run-carddemo.py /caminho/results.json benchmark/results/carddemo
python3 -B benchmark/run-reference.py /caminho/results.json benchmark/results/reference
python3 -B benchmark/smoke-standalone.py target/cobol-dependency-analyzer.jar
```

Testes automatizados e seus fontes/oráculos estão neste repositório e executam
sem a pipeline. Os scripts diferenciais usam o corpus e os artefatos de referência
externos somente para validação. Veja [as medições](benchmark/README.md) e
[a procedência dos fixtures](src/test/resources/dependency-regression/README.md).

Esta é uma cópia independente com histórico preservado, commits locais e nenhum
remote. Código e testes herdados do explorador permanecem para regressão do
frontend; não integram o fluxo do novo CLI.
