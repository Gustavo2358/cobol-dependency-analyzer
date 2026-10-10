# COBOL Dependency Analyzer

Ferramenta de análise estática que recebe fontes COBOL e produz um JSON com
os programas, arquivos, tabelas DB2, copybooks, DCLGENs e SQL INCLUDEs dos quais
cada programa depende. Resolve tanto nomes literais quanto nomes construídos
ou armazenados em variáveis, respeitando as atribuições e o fluxo de controle.

O objetivo é facilitar o inventário de dependências de aplicações COBOL e a
avaliação do impacto de mudanças, com execução simples e custo de memória
controlado. A análise acontece em um único processo Java.

O mesmo JAR oferece dois algoritmos de análise:

- `--solver precise` (padrão): preserva correlações entre condições, valores e chamadores.
- `--solver reaching-definitions`: compartilha o fluxo e as definições para analisar programas
  que exigem muitos contextos; pode acrescentar candidatos ao ignorar essas correlações.

```sh
java -Xmx512m -jar target/cobol-dependency-analyzer.jar \
  --solver reaching-definitions --source programa.cbl --output dependencies.json \
  --max-work 100000000 --metrics metrics.jsonl
```

Não é necessária compilação adicional nem classpath especial. O modo reaching definitions
sinaliza a aproximação em stderr e retorna `1` (PARTIAL) com o JSON publicado. Erros de
argumentos ou limites de recursos retornam `2` e preservam o output anterior. `--metrics`
registra o algoritmo escolhido e o trabalho realizado. A [documentação da alternativa](benchmark/experiments/dependency-witness-demand/README.md)
explica a arquitetura, a validação e os limites conhecidos.

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

No modo `precise`, o dataflow usa um solver de resumos, com uma worklist até o ponto fixo.
Cada resumo é identificado por entrada de controle normalizada, endpoint de
retorno e entradas que precisam determinar o controle ou uma recorrência.
As demais entradas relevantes são parâmetros: valores diferentes podem usar
o mesmo fluxo e receber resultados específicos. Parágrafos alcançados por fallthrough, GO TO,
PERFORM ou handlers compartilham esse mesmo mecanismo. Componentes fortemente
conexos são identificados por Kosaraju iterativo: um ciclo permanece na mesma
worklist para juntar seus estados, e trechos entre componentes podem compartilhar
resumos. Isso evita transformar iterações em uma sequência de novas entradas.
O estado contém valores lógicos, handlers ativos/salvos e os fatos de alcance
necessários às continuações. A chave retém as entradas que podem ser lidas antes
de uma sobrescrita completa ou conservadas em algum caminho de retorno. Escritas parciais,
leituras anteriores e aliases que conservam texto continuam exigindo a entrada.
Handlers omitidos da chave são restaurados a partir do estado de cada chamador.

Antes de propagar valores, um índice de controle calcula as saídas possíveis
de cada `(posição, endpoint, escopo de escape)`. A continuação de um PERFORM só fica alcançável
quando seu corpo produz uma saída compatível. Um ciclo sem saída não inventa
um retorno. Esse ponto fixo independe dos valores dos chamadores e permite
descartar leituras que só alimentariam consultas inalcançáveis.

Se dois chamadores passam `A='PROGA'` e `A='PROGB'` para um corpo com
`MOVE A TO B`, o resumo guarda a transformação de B em função do parâmetro A.
O fluxo do corpo é percorrido uma vez; cada retorno aplica a transformação à
entrada inteira de seu chamador. Consultas internas também guardam expressões
paramétricas sobre o estado BEFORE. Árvores persistentes armazenam tanto
valores quanto expressões, e um grafo compartilhado evita copiar as operações.
Substituir a entrada inteira conserva as correlações nas escritas parciais.

A relevância usa um índice compartilhado de pontos `(posição, endpoint, escopo de escape)`, ampliado
sob demanda. Uma chamada entra no endpoint próprio do callee; seu retorno é
analisado no escopo do chamador. Para cada ponto, a necessidade é a união das
leituras locais com a necessidade dos sucessores, retirando as entradas que a
instrução certamente sobrescreve. Uma análise de sobrescritas possíveis e
garantidas também conserva a entrada quando uma escrita condicional deixa um
caminho intacto. Escritas de UNKNOWN ficam explícitas no resultado para substituir
o valor anterior do chamador. Conjuntos são bitsets com índices densos.
Sucessores são processados antes dos predecessores; ciclos usam worklist até o
ponto fixo. Efeitos locais e componentes já fechados são reutilizados. O endpoint
continua presente porque altera a regra de retorno. O escopo de escape identifica
a cadeia de ancestrais que pode encerrar uma invocação; corpos com a mesma política
compartilham esse escopo, sem incluir a identidade do chamador.

O resultado de um resumo conserva o tipo de saída: conclusão, escape ou término
do programa. A continuação aplica a regra correspondente: seguir o trecho,
retornar/repetir uma invocação ou encerrar uma entrada independente. Essas regras
representam diferenças da linguagem dentro do modo `precise`. A escolha pela CLI
seleciona o algoritmo para a análise inteira. As conclusões também
passam pela worklist, evitando desempilhar recursivamente caudas longas.

Cruzar uma fronteira alheia ao endpoint e aos ancestrais continua no mesmo
resumo, sem inventar outra chamada de continuação. Um escape real desenrola uma
invocação por vez e é interpretado no escopo do pai; sua saída não é descartada.
O solver de controle propaga apenas saídas novas aos predecessores e aos
chamadores registrados. Chamadores novos também recebem as saídas já conhecidas.
O grafo canônico guarda posições físicas e arestas tipadas em arrays de IDs,
com índices nas duas direções. Uma aresta comum conserva a obrigação de retorno;
uma chamada instala a obrigação do callee. Cada obrigação guarda endpoint e
política de escape juntos. Nas fronteiras, as obrigações que retornam param,
enquanto as demais continuam pela mesma topologia. Somente as combinações
alcançadas recebem IDs lógicos; não há matriz antecipada de todas as posições
por todas as obrigações, nem uma cópia das arestas por combinação.

As saídas são propagadas em colunas: uma saída e uma obrigação identificam um
bitset das posições que já produzem esse resultado. Cada ligação física de
chamada aplica a continuação uma vez por saída, conservando as obrigações dos
chamadores. Chamadores e arestas descobertos depois recebem os resultados antigos.
Resultados temporários são descartados antes das fases seguintes. Observação,
ciclos, ordenação e relevância consomem o mesmo grafo; não há cópia final dos
planos nem reconstrução de adjacência por fase. Fatos iguais de relevância usam
compartilhamento imutável com referências fracas, e a fila calcula a prioridade
uma vez por item. Estados inteiramente iguais também compartilham o mesmo
payload imutável em um pool local à análise, com chave e valor fracos. A igualdade
inclui valores conhecidos/desconhecidos, parâmetros, handlers, fatos e guardas;
posições e continuadores de cada chamador permanecem separados. O mesmo pool
serve aos fatos de relevância. Há um único caminho de produção, sem seletor especial para hubs.

A [medição das duas etapas](benchmark/compact-factor-control-20261009.md) compara
essa representação com a versão que armazenava pontos e adjacências por endpoint.
As combinações lógicas, relações de saída e trabalho de valores ainda podem
crescer quadraticamente. Compartilhar a topologia reduz memória e trabalho
repetido, mas não estabelece convergência para qualquer programa ou escala. A
[ampliação com heap de 4 GiB](benchmark/factored-large-heap-scale-20261009.md)
confirma N=384 e registra N=512 como inconclusivo por proteção do host. A
[avaliação de flyweight](benchmark/state-flyweight-20261009.md) elimina payloads
de estado repetidos, com economia pequena de memória retida nesta família;
as obrigações distintas continuam crescendo.

Consultar uma dependência continua usando o estado anterior à instrução. Grupos
conservam alternativas textuais correlacionadas, e REDEFINES/RENAMES relacionam
declarações e textos logicamente. O analisador não simula memória física.
As consultas são indexadas pelo consumidor. Expressões do estado BEFORE são
resolvidas sobre os vínculos com os chamadores, e só os valores resultantes são unidos. Isso preserva a correlação
entre valores de um chamador e evita varrer todos os estados para cada consulta.
A construção e validação do frontend também usam índices de membros e posições,
em lugar de buscas lineares repetidas. O contrato dos intervalos THRU é preservado.
UPPER-CASE, LOWER-CASE e TRIM usam expressões tipadas do frontend.

Predicados fora de recorrências também são parâmetros. Um resumo pode guardar
`TARGET = escolha(FLAG = 'S', 'SPECIAL', ORIGIN)`, sem criar um contexto para
cada combinação de flags. Um DAG de decisões ordenadas reúne ramos iguais e
compartilha seus sufixos. Ao aplicar uma operação, todos os operandos seguem a
mesma decisão; isso mantém a correlação entre campos e escritas parciais.
A worklist segue a ordem do controle, para reunir os ramos antes de propagar
uma junção pelo restante do programa. Isso evita gerar muitas versões
transitórias do DAG. A memoização de cada operação dura somente sua aplicação;
o índice de nós usa referências fracas para permitir a coleta de decisões
sem consumidores. Escritas comprovadamente independentes da entrada viram
constantes. Comparações com um operando desconhecido independente da entrada
não excluem candidatos; seu filtro conserva o valor original e mantém o guarda
do ramo. Essas simplificações usam propriedades
do domínio, sem seleção por nome de programa ou construção CICS.
As condições acompanham consultas, saídas, handlers e fatos de controle.
Vínculos de um mesmo contexto são reunidos antes de resolver a camada seguinte,
para não enumerar combinações de chamadores desnecessárias.

Recorrências continuam convergindo pelo domínio finito de valores: escritas,
refinamentos repetidos e controles de repetição não podem formar uma cadeia
infinita de expressões. Predicados abertos em ciclos conservam a aproximação
por iteração que o analisador já usa. Essa necessidade é fechada sobre fontes,
aliases, grupos e tabelas. É uma regra de convergência do mesmo solver.
Endpoints, estados de handlers e entradas de recorrência diferentes ainda podem
exigir resumos diferentes. O DAG, os produtos de candidatos e as resoluções
podem crescer muito em outros programas; compartilhar decisões não estabelece
um limite linear universal. O orçamento explícito falha sem descartar candidatos.
A relevância conserva aproximações seguras: não compõe kills de um callee com a
continuação do chamador e não presume sobrescrita completa de grupos ou tabelas.

O JAR executa sozinho: não requer AIR, lowering, outros repositórios, serviços
externos ou produtos intermediários serializados.

## Compilar e executar

```sh
cd cobol-dependency-analyzer
mvn package
java -Xmx512m -jar target/cobol-dependency-analyzer.jar \
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
Tabelas usam posições lógicas sob demanda e um valor compartilhado para as
posições restantes. A extensão declarada não cria antecipadamente seus elementos
nem o produto das dimensões. Índices conhecidos distinguem as posições consultadas;
subscritos desconhecidos usam o resumo e escritas incertas conservam valores anteriores.
Quando um índice calculado revela uma posição nova, o mesmo solver fecha a demanda
antes de publicar respostas; projeções e caches são reconstruídos com a demanda
ampliada. `--metrics` informa `materializedTableElements` e `tableDemandPasses`.
Tabelas de extensão não estabelecida permanecem abertas. Leituras de grupos como
texto ainda podem exigir trabalho proporcional ao texto efetivamente reconstruído.
Veja a [medição de OCCURS esparso](benchmark/sparse-occurs-20261009.md). Representações binárias/edições numéricas complexas
não são interpretadas como textos. PERFORM TIMES acima de uma iteração usa
ponto fixo, podendo sobreaproximar alvos que dependem da contagem exata.
Não há garantia de paridade universal de COBOL; a paridade medida refere-se
aos insumos e oráculos documentados.

`--max-work N` limita visitas, estados, resumos, trabalho de relevância e controle,
expressões/resoluções paramétricas e produtos de candidatos (padrão 100000000,
ou 100 milhões, na CLI e na API). Esse é o orçamento usado na qualificação de
N=2048 com reaching definitions; `--max-work N` permite substituí-lo.
Ultrapassar o orçamento falha explicitamente; não corta candidatos para obter
sucesso. `--metrics arquivo.jsonl` grava tempos e contadores fora do JSON de
produto. `evaluations` conta transformações locais calculadas e `reusedEvaluations`
conta visitas que reaproveitam esses resultados; `workItems` conta visitas ao
fluxo de valores e entregas de resultados. `instantiationEvaluations` conta
aplicações concretas das expressões; `resolutionWorkItems`, a resolução das
consultas paramétricas; `controlWorkItems` conta lotes de propagação de saídas
novas, descobertas por posição física e entregas do solver de controle;
`controlSummaries` conta combinações lógicas de posição e obrigação de retorno.
`controlResultPairs` conta pares distintos de chamada/saída entregues;
`controlResultFacts`, inserções distintas de ponto/saída. Os três contadores de
trabalho e resultados de controle somam todas as passagens de demanda. A unidade
de `controlWorkItems` mudou com a propagação por colunas e descobertas agrupadas;
não equivale às visitas das versões anteriores. `physicalControlNodes` e
`physicalControlEdges` contam a topologia compartilhada; `controlObligations`
conta descritores correlacionados de retorno; `controlResultColumns` conta as
colunas distintas de saída/obrigação, antes de liberar os temporários. Esses
quatro contadores e `controlSummaries` representam a última passagem de demanda
por unidade, somados entre unidades. O limite de controle em
`--max-work` se aplica a esses lotes e à quantidade de pontos, e não é um limite
de bytes, segundos ou número de dependências. `parametricCalculations`, `specializedDeclarations`
e `resultDeliveries` tornam visíveis as expressões, entradas especializadas e
entregas a chamadores. `predicateInputs` conta os predicados internados;
`decisionNodes` conta cumulativamente os nós criados, inclusive os já coletados.
`decisionOperations` e `liftedOperations` somam as células visitadas nas
aplicações da álgebra e das operações sobre decisões. Não representam memória
residente: os caches de aplicação são descartados ao concluir cada operação,
e seu orçamento limita uma aplicação individual. `dataflowNanos` inclui esses
custos internos; reduzir `workItems` sozinho não mede o ganho total.
Heap é configurado pelo Java.

A auditoria histórica de escala usou heap de 4 GiB e 100 milhões de visitas.
As investigações posteriores e a validação do DAG usam heap de 512 MiB,
com supervisão externa de memória e tempo. Esses resultados não autorizam
extrapolar o consumo para qualquer programa do mesmo tamanho.

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
A [etapa 2 de compartilhamento](benchmark/shared-work-20261008.md) reutiliza
transformações por instrução e seus operandos, aplicando somente as mudanças
ao estado específico de cada chamador. Na comparação com a etapa 1, calculou
120 transformações para 15.282 visitas, reduziu o tempo do solver em 32,8%
e os bytes alocados em 17,2%; a memória viva aumentou 5,7% pelos caches.
Esse experimento histórico usa heap de 512 MiB e parada em 2.048 contextos.
Os 40 fixtures do discovery e os 73 programas CardDemo preservaram JSONs e
diagnósticos byte a byte nessa etapa.

O [compartilhamento de fluxo entre entradas](benchmark/shared-flow-20261009.md)
passa a usar resumos paramétricos de dados e controle balanceado compartilhado.
Com 400 entradas diferentes, os contextos caíram de 401 para 2, preservando as
400 dependências; o tempo interno da análise caiu 46,3%. A FIXTURE02 original
agora termina com 25 contextos e 200 visitas, em cerca de 1,86 s, usando o mesmo
heap de 512 MiB. Nela, os CALLs são inalcançáveis porque os PERFORMs anteriores
entram em ciclos sem retorno; a saída vazia foi conferida. Esse resultado não
garante custo linear para combinações diferentes de controle ou recorrência.
O [fixture de combinações de controle](benchmark/control-signatures-20261009.md)
reproduziu esse limite na versão anterior: dez flags geraram 2.048 contextos e doze geraram 8.192,
mantendo somente duas dependências. O controle com uso direto dos dados
fica em 12 e 14 contextos, respectivamente.

O [DAG de decisões compartilhadas](benchmark/guarded-flow-20261009.md) elimina
essa enumeração na família: doze flags passam de 8.192 para 14 contextos e o
dataflow mediano cai 80,3%, com heap de 512 MiB. Vinte flags usam 22 contextos.
O candidato preserva os 40 fixtures do discovery e os 73 programas CardDemo,
incluindo seus diagnósticos. O relatório registra os custos nos casos pequenos,
as tentativas de profiling e os limites que permanecem.

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
