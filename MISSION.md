# Missão: construir um analisador COBOL de dependências monolítico, rápido e independente

## 1. Objetivo e contexto

Você é um agente de engenharia de software responsável por implementar, de ponta a ponta, um novo analisador estático de dependências COBOL.

O ponto de partida é o repositório:

https://github.com/imd/proleap-poc

O objetivo é clonar esse projeto e transformá-lo em um **analisador especializado exclusivamente em descoberta de dependências**, com CFG e dataflow próprios, sem depender da pipeline atual de múltiplos repositórios.

A pipeline existente possui aproximadamente esta arquitetura:

```text
COBOL
  ↓
proleap-poc
  ↓
COBOL Semantic Product
  ↓
cobol-lower
  ↓
AIR / Analysis IR
  ↓
analysis-cfg
  ↓
Dataflow / Possible Values
  ↓
Dependency Analysis
  ↓
dependencies.json
```

Essa arquitetura foi desenvolvida para suportar análise estática abrangente, mas apresenta complexidade significativa, contratos intermediários volumosos, múltiplas etapas de materialização e riscos de crescimento excessivo de memória.

O novo projeto pretende responder a uma pergunta objetiva:

**Conseguimos obter as mesmas dependências, de maneira substancialmente mais simples e rápida, utilizando diretamente a AST COBOL e um CFG/dataflow especializado?**

A arquitetura desejada é:

```text
COBOL
  ↓
Preprocessor existente
  ↓
AST existente
  ↓
Symbol Tables / Binding existentes
  ↓
CFG COBOL direto
  ↓
Dataflow lógico especializado
  ↓
Dependency Resolver
  ↓
dependencies.json
```

Toda a análise deve acontecer:

- Em um único repositório.
- Em um único processo Java.
- Sem AIR.
- Sem Analysis IR.
- Sem lowering.
- Sem serviços externos.
- Sem serializações intermediárias obrigatórias.
- Sem modelo de memória física.

A prioridade, em ordem, é:

1. Encontrar corretamente as dependências.
2. Não perder valores e candidatos relevantes.
3. Manter a implementação simples.
4. Evitar crescimento explosivo de memória.
5. Maximizar o desempenho ponta a ponta.

O produto final é um `dependencies.json` pequeno, contendo apenas as dependências encontradas e seu provenance mínimo.

Não estamos construindo uma nova plataforma de análise estática. Estamos construindo uma ferramenta especializada de descoberta de dependências COBOL.

---

## 2. Preparação do repositório

Execute inicialmente:

1. Clone `https://github.com/imd/proleap-poc.git` em um novo diretório de trabalho dedicado.
2. Entre no diretório clonado.
3. Execute `git remote remove origin`.
4. Confirme que `git remote -v` não apresenta nenhum remote.
5. Preserve o histórico Git local.
6. Trabalhe exclusivamente nessa cópia.
7. Faça commits locais normalmente.
8. Não faça push, não abra PR e não crie repositório remoto.

O responsável pelo projeto criará posteriormente um novo repositório para hospedar o analisador.

Não modifique o `proleap-poc` original ou qualquer outro repositório da pipeline atual.

Você poderá consultar os repositórios existentes e executar ferramentas de referência para validar os resultados.

**O novo analisador deverá funcionar independentemente de todos eles.**

---

## 3. Filosofia de engenharia: simplicidade radical

Este projeto existe justamente para evitar a complexidade acumulada pela pipeline original.

Não reproduza essa complexidade em um único repositório.

### 3.1. Não implementar

É expressamente proibido introduzir sem necessidade:

- AIR ou Analysis IR.
- Nova linguagem intermediária.
- Lowering.
- Framework de análise estática multilíngue.
- Modelo de memória física.
- Simulação de endereços ou armazenamento em bytes.
- Engine genérica de alias analysis.
- Evidence packs.
- Certificados de correção semântica.
- Grafos de provas.
- Sistemas extensos de coverage e readiness.
- Versionamento complexo de contratos internos.
- Frameworks de plugins.
- Microserviços, RPC ou filas.
- Serialização entre as etapas internas.
- Infraestrutura de orquestração.
- Arquitetura excessivamente genérica.
- Documentação extensa e burocrática.
- Campanhas de formalização que não produzam melhoria direta na descoberta de dependências.

Não implemente uma engine de interpretação completa de COBOL.

Não tente compreender todos os comportamentos possíveis de execução quando apenas uma pequena parte deles influencia dependências.

### 3.2. Implementar somente o necessário

O analisador deve ser composto essencialmente por:

1. Frontend COBOL existente.
2. CFG compacto.
3. Propagador de valores lógicos.
4. Resolvedor de dependências.
5. Publicador JSON.

Esses componentes podem possuir classes auxiliares, mas devem continuar pequenos e coesos.

Utilize algoritmos conhecidos, coleções Java, índices, grafos e worklists.

Prefira uma implementação direta de 200 linhas a uma arquitetura extensível de 2.000 linhas quando ambas resolverem corretamente o mesmo problema.

Isso não significa sacrificar correção, testes ou manutenibilidade. Significa não criar abstrações sem necessidade concreta.

### 3.3. Sem obsessão por provas

A pipeline atual frequentemente exige evidências fortes de identidade física, alocação, controle, independência e fechamento semântico para resolver valores.

Este projeto deve adotar uma abordagem mais pragmática.

Quando existirem candidatos sustentados pelo código, publique-os.

Quando houver incerteza, preserve os candidatos e mantenha essa incerteza internamente.

Não bloqueie a descoberta de uma dependência porque não foi possível provar alguma propriedade que não é essencial ao objetivo.

Ao mesmo tempo, não transforme candidatos impossíveis em dependências válidas apenas para aumentar cobertura.

A regra é:

**Preservar todas as possibilidades sustentadas pela análise lógica, eliminando candidatos somente quando houver informação suficiente para descartá-los.**

Não invente valores.

---

## 4. Reaproveitamento do proleap-poc

Antes de escrever código novo, faça uma inspeção objetiva dos componentes existentes.

O projeto já possui recursos importantes, incluindo:

- `DirectCobolParser`.
- Parser ANTLR existente.
- `Ast`.
- `CompilationUnitModel`.
- `CompilationUnitSymbolTableBuilder`.
- `CobolReferenceResolver`.
- `ControlTopologySemantics`.
- `PerformSemantics`.
- `GoToSemantics`.
- `NominalValueSemantics`.
- `LogicalMoveSemantics`.
- `SourceDependencySemantics`.
- `CicsProgramControlAnalyzer`.
- `Db2SourceExtractor`.
- Inventários de FILE.
- Inventários de COPY, DCLGEN e SQL INCLUDE.
- SourceMap e provenance.

Os nomes podem ter evoluído. Inspecione o código real e identifique as APIs disponíveis.

### 4.1. Reutilizar sem duplicar

Não reimplemente parser, AST, resolução de nomes ou preprocessing.

Não crie outra tabela de símbolos.

Não faça parsing textual de comandos para os quais já existam representações semânticas adequadas.

O novo CFG deve aproveitar as estruturas já disponíveis.

`ControlTopologySemantics` merece atenção especial, pois já concentra uma parte relevante da semântica de controle COBOL.

Se for possível converter sua representação diretamente para um grafo compacto, faça isso.

Se o custo de materializar todo o Semantic Product for excessivo, utilize os objetos internos diretamente.

### 4.2. Referência de dataflow existente

Consulte também:

https://github.com/imd/analysis-cfg/blob/main/analysis-dependencies/src/main/java/io/github/imd/analysis/dependencies/SourceValuesProvider.java

Esse componente contém lógica útil de propagação de valores nominais.

Ele pode servir como referência ou fonte de código reutilizável, respeitando as licenças aplicáveis.

Entretanto:

**Não importe toda a arquitetura do analysis-cfg apenas para reutilizar esse algoritmo.**

A implementação nova deve consumir diretamente o CFG e os fatos semânticos do frontend.

---

## 5. CFG COBOL direto

Implemente um CFG específico para COBOL, construído diretamente a partir das informações existentes no frontend.

O CFG deve representar corretamente o fluxo necessário para descobrir os valores que alcançam operações de dependência.

### 5.1. Construções de controle

Suportar as construções relevantes do corpus, incluindo:

- Sequência de statements.
- Parágrafos.
- Seções.
- `IF / ELSE`.
- `EVALUATE`.
- `PERFORM` inline.
- `PERFORM` de parágrafo.
- `PERFORM` de seção.
- `PERFORM THRU`.
- `PERFORM UNTIL`.
- `PERFORM TIMES`.
- `PERFORM VARYING`.
- `GO TO`.
- `GO TO DEPENDING ON`.
- `EXIT`.
- `EXIT PARAGRAPH`.
- `EXIT SECTION`.
- `EXIT PROGRAM`.
- `GOBACK`.
- `STOP RUN`.
- Continuação após CALL.
- Handlers de I/O.
- Transferências CICS.
- Fluxos especiais já reconhecidos pelo frontend que influenciem operações de dependência.

Não basta conectar statements na ordem textual.

O grafo deve representar corretamente os caminhos de execução.

### 5.2. Atenção especial ao PERFORM

`PERFORM` é uma das principais fontes potenciais de complexidade e falsos positivos.

Considere:

```cobol
PERFORM PARA-A.
PERFORM PARA-B.
CALL WS-TARGET.
```

Os parágrafos podem modificar variáveis relevantes para o CALL.

Além disso, um mesmo parágrafo pode ser chamado por vários pontos.

O analisador deve respeitar os retornos contextuais e evitar misturar continuações incompatíveis.

Entretanto, não duplique indiscriminadamente o corpo dos parágrafos para cada chamador.

Prefira:

- CFG compartilhado.
- Contexto de retorno limitado, quando necessário.
- Resumos de efeitos locais, quando úteis.
- Propagação por ponto fixo.
- Reutilização de resultados.

Não implemente enumeração de caminhos completos.

Não crie expansão recursiva ilimitada de `PERFORM`.

**A construção e análise do CFG devem possuir crescimento controlado em programas com muitos PERFORMs, loops e branches.**

### 5.3. CFG mínimo

Não adicione propriedades ao CFG que não sejam utilizadas pelo dataflow.

Uma representação com nós, successors, predecessors e identificação de statement pode ser suficiente.

O CFG é um detalhe interno.

Não precisa ser serializado, versionado ou publicado.

---

## 6. Dataflow lógico especializado

Este é o núcleo do novo analisador.

O objetivo é descobrir os possíveis valores de variáveis que influenciam dependências, respeitando o fluxo de controle.

### 6.1. Valores dinâmicos desde a primeira versão

A primeira versão funcional já deve resolver dependências dinâmicas.

Não é aceitável entregar inicialmente somente CALLs literais e deixar valores dinâmicos para uma fase posterior.

Exemplo:

```cobol
01 WS-PROGRAM PIC X(8) VALUE 'PROGA'.

IF FLAG = 'Y'
    MOVE 'PROGB' TO WS-PROGRAM
END-IF.

CALL WS-PROGRAM.
```

Se os dois caminhos forem possíveis, as dependências são:

```text
PROGA
PROGB
```

Outro exemplo:

```cobol
MOVE 'PROGA' TO WS-A.
MOVE WS-A TO WS-B.
CALL WS-B.
```

Resultado:

```text
PROGA
```

Outro exemplo:

```cobol
MOVE 'PROGA' TO WS-TARGET.
MOVE 'PROGB' TO WS-TARGET.
CALL WS-TARGET.
```

Se a segunda atribuição sobrescrever integralmente a primeira, o resultado correto será:

```text
PROGB
```

Não publique `PROGA` apenas porque seu literal aparece anteriormente no código.

A análise deve considerar os valores que podem alcançar o ponto de uso.

### 6.2. Capacidades obrigatórias

O dataflow deve suportar:

- Literais textuais.
- Variáveis COBOL.
- Inicialização por `VALUE`.
- Valores declarados em WORKING-STORAGE.
- MOVE de literal.
- MOVE de variável.
- MOVE para grupos.
- MOVE de grupos.
- Atribuições sucessivas.
- Sobrescritas completas.
- Escritas parciais.
- Branches.
- Junção de fluxos.
- Loops.
- Propagação entre parágrafos.
- `PERFORM` e seus retornos.
- `REDEFINES`.
- `RENAMES`, quando relevantes ao corpus.
- Aliases lógicos.
- Comparações e predicados relevantes.
- Refinamento de candidatos por condições conhecidas.
- Transformações textuais simples já suportadas pelo frontend.
- Operações desconhecidas que possam interferir nos valores.

A análise deve consultar os valores **imediatamente antes da operação de dependência**.

### 6.3. Domínio abstrato simples

Comece com uma representação semelhante a:

```java
record ValueState(
    Set<String> possibleValues,
    boolean unknownRemainder
) {}
```

Utilize um estado separado para fluxo comprovadamente inalcançável.

Essa representação deve distinguir:

- Valor conhecido.
- Conjunto finito de valores conhecidos.
- Valores conhecidos com possibilidade adicional desconhecida.
- Valor completamente desconhecido.
- Fluxo inalcançável.

O join combina possibilidades.

Uma escrita integral conhecida pode substituir valores anteriores.

Uma escrita parcial ou incerta não pode eliminar candidatos indevidamente.

Valores desconhecidos não podem ser tratados como conjuntos vazios de possibilidades.

Evite criar múltiplos lattices ou domínios abstratos sofisticados sem necessidade funcional demonstrada.

### 6.4. Inicialização por VALUE

Não ignore declarações como:

```cobol
01 WS-TARGET PIC X(8) VALUE 'PROGA'.
```

O literal deve participar da análise.

Mas respeite as condições de inicialização e persistência do programa COBOL.

Não assuma automaticamente que toda ativação reinicializa WORKING-STORAGE.

Quando não for possível garantir esse comportamento, preserve o valor declarado como possibilidade sustentada, juntamente com a incerteza correspondente.

O objetivo é evitar tanto a perda de candidatos quanto a afirmação injustificada de um valor único.

### 6.5. Propagação sob demanda

O analisador não precisa descobrir todos os possíveis valores de todas as variáveis do programa.

Ele precisa descobrir os valores que influenciam dependências.

Implemente uma análise orientada pelos pontos de consulta.

Por exemplo:

```cobol
CALL WS-TARGET.
```

Partindo de `WS-TARGET`, identifique as variáveis e operações capazes de influenciar seu valor.

Construa o fechamento de dependências necessário para essa consulta.

Se o programa contém milhares de variáveis mas apenas algumas dezenas influenciam nomes de dependências, evite analisar profundamente todo o restante.

Use índices, worklists e estados compartilhados.

Não copie o estado inteiro de milhares de variáveis para cada basic block se isso for desnecessário.

### 6.6. Loops e terminação

Utilize algoritmos de ponto fixo.

Não enumere todos os caminhos possíveis.

Não desenrole loops indefinidamente.

A análise deve convergir ou sinalizar explicitamente uma limitação de recursos.

Se houver grande quantidade de candidatos, controle o consumo de memória sem descartar silenciosamente as dependências já encontradas.

Não introduza limites arbitrários por nome de programa, fixture ou tipo de código.

---

## 7. REDEFINES, RENAMES e grupos: somente lógica

**Não implementar memória física.**

Não reproduza o sistema de `Region`, `Cell`, `ObjectPlace` e layouts físicos da pipeline atual.

O novo analisador deve trabalhar com identidade e relações lógicas de declarações COBOL.

### 7.1. Relações lógicas

Utilize as informações existentes na AST e na tabela de símbolos para identificar:

- Campos elementares.
- Grupos.
- Relações pai-filho.
- `REDEFINES`.
- `RENAMES`.
- Visões alternativas.
- Possíveis aliases.
- Transferências entre grupos.

Quando houver equivalência lógica suficiente, propague os valores.

Quando uma operação modificar possivelmente outra visão do mesmo conteúdo, atualize conservadoramente os candidatos relevantes.

Não trate campos relacionados por `REDEFINES` como completamente independentes.

### 7.2. Exemplo

```cobol
01 WS-AREA.
   05 WS-PROGRAM PIC X(8).

01 WS-ALIAS REDEFINES WS-AREA.
   05 WS-ALIAS-PROGRAM PIC X(8).

MOVE 'PROGA' TO WS-PROGRAM.
CALL WS-ALIAS-PROGRAM.
```

O analisador deve identificar `PROGA` como candidato, utilizando a relação lógica entre as declarações.

Não é necessário criar um simulador de armazenamento físico para resolver esse exemplo.

### 7.3. Group MOVE

Exemplo:

```cobol
01 WS-GROUP-A.
   05 WS-TARGET-A PIC X(8).

01 WS-GROUP-B.
   05 WS-TARGET-B PIC X(8).

MOVE 'PROGA' TO WS-TARGET-A.
MOVE WS-GROUP-A TO WS-GROUP-B.
CALL WS-TARGET-B.
```

A transferência deve preservar as relações lógicas necessárias para descobrir `PROGA`, quando a correspondência for semanticamente válida.

Considere as regras reais de COBOL para essas operações.

Não presuma correspondência por nome dos campos.

Utilize estrutura, ordem declarativa, características textuais e relações de grupo quando forem suficientes.

Quando a operação não puder ser interpretada de maneira segura no domínio lógico escolhido, preserve a incerteza e os candidatos sustentados, em vez de inventar equivalências.

### 7.4. Limites da modelagem

São permitidas operações simples de manipulação lógica de texto, como:

- Ajuste de valores ao comprimento lógico de `PIC X`.
- Padding.
- Truncamento textual conhecido.
- Propagação entre campos logicamente equivalentes.
- Atualização conservadora de possíveis aliases.

Não implemente:

- Endereços físicos.
- Alocadores de memória.
- Layouts completos de armazenamento.
- Simulação de bytes EBCDIC.
- Engine genérica de sobreposição física.

Se um caso exigir semântica física não representável com segurança no modelo lógico, mantenha os candidatos comprovadamente sustentados e a incerteza; não declare paridade desse caso sem validá-lo.

**O requisito principal é não perder dependências devido a VALUE, REDEFINES, RENAMES ou group MOVE.**

---

## 8. Cobertura de dependências

O analisador deve identificar todas as categorias relevantes dos produtos atuais.

Na primeira implementação funcional, a resolução dinâmica deve estar presente.

### 8.1. Programa → programa

Suportar:

- `CALL 'PROGA'`.
- `CALL WS-PROGRAM`.
- `EXEC CICS LINK PROGRAM(...)`.
- `EXEC CICS XCTL PROGRAM(...)`.
- Targets declarados por VALUE.
- Targets recebidos por MOVE.
- Targets propagados entre variáveis.
- Targets alterados condicionalmente.
- Targets obtidos através de grupos.
- Targets obtidos através de REDEFINES.
- Outras formas reconhecidas pela pipeline atual e presentes nos corpora obrigatórios.

### 8.2. Programa → arquivo

Reutilizar as estruturas existentes para:

- `SELECT`.
- `ASSIGN`.
- `FD` e `SD`.
- `OPEN`.
- `READ`.
- `WRITE`.
- `REWRITE`.
- `DELETE`.
- `START`.
- `CLOSE`.
- Operações CICS FILE.
- Nomes dinâmicos quando existentes e suportados.

Distinguir identificadores lógicos de arquivos de nomes físicos externos.

Não inventar datasets quando não há evidência.

### 8.3. Programa → tabela DB2

Reutilizar o extrator SQL existente para:

- SELECT.
- INSERT.
- UPDATE.
- DELETE.
- MERGE.
- JOIN e referências de tabelas.
- SQL estático reconhecido.
- Outras referências DB2 encontradas pela pipeline de referência.

Não construir um parser SQL completo se não houver necessidade.

### 8.4. Programa → artefatos de fonte

Incluir:

- COPY.
- DCLGEN.
- EXEC SQL INCLUDE.
- Outros artefatos de fonte já reconhecidos.

Preservar a associação com o programa de origem.

### 8.5. Outras categorias

Investigue os tipos efetivamente publicados pela pipeline atual e utilizados pelos corpora.

Se houver uma categoria adicional de dependência relevante, implemente o menor suporte necessário para reproduzir seu resultado.

Não amplie o projeto para um parser JCL, Control-M ou outro subsistema separado, a menos que seja indispensável para as dependências COBOL explicitamente exigidas.

---

## 9. dependencies.json — schema mínimo, sem bloating

Esta seção é obrigatória e possui prioridade sobre qualquer contrato legado.

**O novo `dependencies.json` deve conter somente as dependências encontradas.**

Não reutilize o schema antigo.

Não transporte o modelo interno da análise para o JSON.

Não preserve compatibilidade estrutural com a pipeline atual.

A compatibilidade exigida é exclusivamente semântica: encontrar os mesmos destinos de dependência.

### 9.1. Schema desejado

Exemplo:

```json
{
  "program": "EXAMPLE",
  "dependencies": [
    {
      "type": "program",
      "name": "PROGA",
      "at": {
        "file": "EXAMPLE.cbl",
        "line": 120
      }
    },
    {
      "type": "program",
      "name": "PROGB",
      "at": {
        "file": "EXAMPLE.cbl",
        "line": 145
      }
    },
    {
      "type": "file",
      "name": "CUSTOMER-FILE",
      "at": {
        "file": "EXAMPLE.cbl",
        "line": 32
      }
    },
    {
      "type": "db2-table",
      "name": "DB2.CUSTOMERS",
      "at": {
        "file": "EXAMPLE.cbl",
        "line": 210
      }
    },
    {
      "type": "copybook",
      "name": "CUSTOMER-REC",
      "at": {
        "file": "EXAMPLE.cbl",
        "line": 18
      }
    }
  ]
}
```

Use uma lista única de dependências tipadas.

Não crie arrays separados por método de descoberta.

### 9.2. Campos permitidos

O schema inicial deve possuir somente:

- `program`: identificação do programa analisado.
- `dependencies`: lista de dependências.
- `type`: categoria da dependência.
- `name`: identificador da dependência.
- `at`: localização simples no código.
- `file`: arquivo-fonte original.
- `line`: linha original.

Campos adicionais só poderão ser introduzidos se forem realmente necessários para identificar ou consumir corretamente uma dependência.

Não adicione campos por conveniência de debugging.

Não crie metadados redundantes.

### 9.3. Provenance

Para uma dependência dinâmica, a linha deve apontar preferencialmente para a operação consumidora.

Exemplo:

```cobol
MOVE 'PROGA' TO WS-TARGET.

...

CALL WS-TARGET.
```

O provenance da dependência deve apontar para o `CALL`.

Não é necessário publicar toda a cadeia de MOVEs que produziu o valor.

Internamente, o algoritmo poderá manter essas informações caso sejam necessárias.

Se a operação estiver em um copybook, preserve o arquivo e a linha originais quando disponíveis.

### 9.4. Deduplicação

Uma mesma dependência pode ser encontrada:

- Diretamente por literal.
- Por VALUE.
- Por MOVE.
- Por um alias.
- Por múltiplos caminhos.
- Em múltiplas operações.

A saída deve consolidar dependências por:

```text
(type, name)
```

Dentro de cada programa.

Publique uma única entrada por dependência distinta.

Para ocorrências repetidas, uma localização representativa escolhida deterministicamente é suficiente.

Não é necessário publicar todos os caminhos ou ocorrências de descoberta.

### 9.5. Proibições expressas

Não publicar no `dependencies.json`:

- CFG.
- Basic blocks.
- AST.
- IR.
- Estados de dataflow.
- Symbol tables.
- Lista de MOVEs.
- Conjuntos de definições.
- Cadeias de provenance.
- Evidence packs.
- Proofs.
- Support graphs.
- Gaps extensos.
- Coverage.
- Readiness.
- Scores de confiança.
- Metadados de execução.
- Tempo de análise.
- Memória utilizada.
- Versões de contratos intermediários.
- Snapshots semânticos.
- Informações de algoritmos internos.
- Separação entre dependências literais e dinâmicas.
- Chaves herdadas do dependencies.json anterior.

O JSON não é um relatório de análise estática.

É somente uma relação de dependências com localização mínima.

### 9.6. Incerteza e diagnósticos

Quando existirem candidatos conhecidos e possibilidades desconhecidas, publique os candidatos conhecidos.

Não crie dependências artificiais chamadas `UNKNOWN`, `DYNAMIC` ou semelhantes.

Registre incertezas em logs ou stderr quando necessário.

Uma limitação local pode resultar em candidatos conhecidos com incerteza registrada fora do JSON.

Entretanto, se a execução for interrompida por erro, limite de memória, timeout interno ou análise incompleta global, não publique silenciosamente um resultado como se fosse completo.

Sinalize a falha de maneira inequívoca.

### 9.7. Determinismo

A saída deve ser determinística.

Ordene as dependências de maneira estável.

Não dependa da ordem de iteração de HashMap, HashSet ou da ordem de execução paralela.

### 9.8. Independência do schema antigo

O schema legado não define o novo contrato.

O novo analisador não precisa reproduzir nenhuma das suas estruturas.

Caso o agregador que alimenta o GopherGraph precise ser atualizado para consumir esse JSON, trate isso como integração externa separada.

**Não complique o analisador para preservar um contrato que estamos deliberadamente abandonando.**

---

## 10. Desempenho como requisito de arquitetura

O novo analisador deve ser rápido desde o início.

Não implemente toda a funcionalidade primeiro para pensar em desempenho somente depois.

### 10.1. Diretrizes

- Um processo Java.
- Parser direto quando disponível e qualificado.
- Sem arquivos intermediários obrigatórios.
- Sem serialização da AST.
- Sem publicação de Semantic Product.
- Sem publicação de CFG.
- Sem geração de páginas HTML.
- Sem construção de snapshots de visualização.
- Sem duplicação desnecessária de árvores.
- Sem materialização excessiva de objetos.
- Sem copiar grandes mapas de estado em cada nó.
- Sem expansão ilimitada de caminhos.
- Sem clonagem indiscriminada de parágrafos.
- Sem depender de processos externos na execução normal.

O fluxo deve ser essencialmente:

```text
parse → analyze → write JSON
```

### 10.2. Algoritmos

Priorize:

- Worklist.
- Ponto fixo.
- Análises orientadas por demanda.
- Índices de consulta.
- Estados compactos.
- Estruturas compartilhadas.
- Reutilização de cálculos.
- Processamento iterativo.
- Liberação de estruturas temporárias quando não forem mais necessárias.

Evite algoritmos cujo custo dependa da enumeração de caminhos possíveis.

### 10.3. Evitar OOM

O analisador deve conseguir processar corpora grandes sem crescimento explosivo.

A pipeline atual já apresentou problemas desse tipo em programas com determinados padrões de fluxo.

Não copie essas fragilidades.

Se uma fixture causar OOM, investigue a classe geral de crescimento que ela representa.

Não crie correções específicas por programa.

Não estabeleça hardcodes de nomes ou tamanhos de fixtures.

Se algum algoritmo não conseguir trabalhar dentro do orçamento disponível, informe explicitamente a incompletude em vez de descartar silenciosamente resultados.

### 10.4. Benchmarks

Compare o analisador novo com a pipeline atual.

Meça:

- Tempo total de processamento.
- Tempo de parsing.
- Tempo de construção do CFG.
- Tempo de dataflow.
- Tempo de resolução de dependências.
- Tempo de publicação.
- Memória máxima observada.

As medições devem utilizar ambiente, corpus e condições comparáveis.

Não apresente apenas o tempo isolado do solver.

O indicador mais importante é:

**Quanto custa, do arquivo COBOL até o dependencies.json final, descobrir o mesmo conjunto de dependências?**

A redução expressiva de tempo e memória é o objetivo de desempenho, não uma licença para perder cobertura.

---

## 11. Validação diferencial com a pipeline atual

A pipeline atual é o principal oráculo funcional.

Repositórios relevantes:

- https://github.com/imd/proleap-poc
- https://github.com/imd/cobol-lower
- https://github.com/imd/air-java
- https://github.com/imd/analysis-ir
- https://github.com/imd/analysis-cfg

Consulte o código e os testes necessários.

Não modifique esses repositórios.

### 11.1. CardDemo completo

Localize o corpus CardDemo utilizado pela pipeline atual.

A referência de trabalho é o conjunto de 73 programas.

O novo analisador deve processar todos eles.

Não aceite apenas um subconjunto.

### 11.2. Comparação semântica

Não compare os arquivos JSON byte a byte.

Não compare schemas.

Não compare metadados.

Extraia das duas implementações as relações normalizadas:

```text
(programa_origem, tipo_dependencia, destino)
```

Na pipeline antiga, utilize a união das dependências executáveis e source-qualified, com deduplicação.

Compare os conjuntos resultantes.

Para cada programa, identifique:

- Dependências encontradas por ambas.
- Dependências ausentes no novo analisador.
- Dependências adicionais no novo analisador.
- Diferenças em targets dinâmicos.
- Casos inconclusivos por ausência de informação suficiente.

O objetivo é obter equivalência dos conjuntos válidos.

### 11.3. Diferenças

Não considere automaticamente uma dependência adicional como melhoria.

Uma dependência adicional pode ser falso positivo.

Uma dependência ausente pode ser falso negativo.

Investigue cada diferença.

Não altere o algoritmo apenas para reproduzir resultados incorretos do oráculo.

Se encontrar um erro demonstrável na pipeline antiga, preserve a evidência e classifique a divergência.

Entretanto, a missão não estará concluída enquanto houver divergências sem explicação ou validação.

Não relaxe a comparação simplesmente para atingir 100% artificialmente.

### 11.4. Fixtures obrigatórias

Localize os testes relevantes da pipeline atual para:

- CALL literal.
- CALL dinâmico.
- VALUE de WORKING-STORAGE.
- MOVE literal.
- MOVE entre variáveis.
- MOVE de grupos.
- REDEFINES.
- RENAMES.
- Sobrescritas completas.
- Escritas parciais.
- Condições.
- Loops.
- PERFORM.
- PERFORM THRU.
- Múltiplos chamadores.
- Retornos contextuais.
- GO TO.
- CICS LINK.
- CICS XCTL.
- CICS FILE.
- DB2.
- Arquivos COBOL.
- COPY.
- DCLGEN.
- SQL INCLUDE.
- Missing COPY.
- Missing INCLUDE.
- Campos potencialmente afetados por chamadas externas.
- Casos de aliases.
- Falsos positivos conhecidos.
- Falsos negativos anteriormente corrigidos.
- Fixtures que causavam crescimento excessivo de memória.

Não é necessário migrar toda a infraestrutura de testes da pipeline.

Selecione os insumos e oráculos relevantes para dependências.

Faça com que os testes do novo analisador possam ser executados sem a presença dos outros repositórios.

### 11.5. Proibição de overfitting

Não crie regras especiais para:

- Nomes de programas.
- Nomes de fixtures.
- Padrões exclusivos do CardDemo.
- Diretórios específicos.
- Resultados conhecidos do oráculo.

As correções devem ser semânticas e gerais.

Quando descobrir um problema, implemente uma regra que resolva a classe de problema.

Não faça um fix isolado por fixture.

---

## 12. Estratégia de desenvolvimento

A implementação deve ser incremental, mas o objetivo é entregar o produto completo.

Não confunda checkpoints internos com conclusão da missão.

### Etapa A — preparar o monolito

- Clonar o projeto.
- Remover origin.
- Identificar as APIs existentes.
- Criar um caminho de execução sem UI.
- Preservar o frontend necessário.
- Remover do caminho operacional as materializações desnecessárias.

Resultado esperado: executar parsing, símbolos e binding em um processo simples.

### Etapa B — construir o CFG

- Reaproveitar ControlTopology e semânticas existentes.
- Construir o grafo compacto.
- Implementar continuations de PERFORM.
- Suportar branches, loops e retornos.
- Garantir crescimento controlado.

Resultado esperado: representar os fluxos necessários ao dataflow.

### Etapa C — implementar dataflow lógico

- Criar domínio de valores possíveis.
- Implementar worklist.
- Implementar ponto fixo.
- Implementar MOVE e sobrescrita.
- Implementar VALUE.
- Implementar alias lógico.
- Implementar REDEFINES.
- Implementar group MOVE.
- Implementar propagação entre parágrafos.
- Implementar refinamento de candidatos.

Resultado esperado: resolver variáveis dinâmicas nos pontos de uso.

**Não adie VALUE, REDEFINES ou resolução dinâmica para uma versão posterior.**

Esses recursos fazem parte do primeiro analisador funcional.

### Etapa D — conectar dependências

- CALL.
- LINK.
- XCTL.
- FILE.
- DB2.
- COPY.
- DCLGEN.
- SQL INCLUDE.
- Outras categorias efetivamente exigidas.

Resultado esperado: produzir dependências completas no novo formato mínimo.

### Etapa E — validar CardDemo

- Executar todos os 73 programas.
- Comparar com os oráculos.
- Corrigir falsos negativos.
- Corrigir falsos positivos.
- Investigar divergências.

Não encerrar esta etapa enquanto houver divergências não explicadas.

### Etapa F — validar fixtures

- Executar os testes especializados.
- Corrigir problemas gerais encontrados.
- Repetir a validação integral.

Não declarar sucesso apenas porque o CardDemo passou.

### Etapa G — otimizar

- Medir processamento ponta a ponta.
- Identificar hotspots.
- Eliminar computações redundantes.
- Melhorar o uso de memória.
- Corrigir qualquer crescimento explosivo.

Não introduzir complexidade arquitetural sem ganho observado.

### Etapa H — finalizar

- Criar CLI funcional.
- Produzir README curto.
- Documentar como executar testes.
- Publicar benchmarks locais reproduzíveis.
- Fazer commits locais.
- Apresentar o resultado final.

---

## 13. Critérios de conclusão

A missão só está concluída quando:

### Repositório e arquitetura

- [ ] O repositório foi clonado independentemente.
- [ ] Nenhum remote permanece configurado.
- [ ] Nenhum repositório da pipeline original foi modificado.
- [ ] O novo projeto compila sozinho.
- [ ] O novo analisador executa sozinho.
- [ ] Todas as etapas estão no mesmo repositório.
- [ ] Não existe dependência operacional de AIR, IR, lower ou analysis-cfg.
- [ ] Não existe modelo de memória física.
- [ ] Não existem produtos intermediários obrigatórios.

### CFG e dataflow

- [ ] O CFG representa os fluxos exigidos pelos corpora.
- [ ] PERFORM e seus retornos funcionam corretamente nos casos testados.
- [ ] O dataflow converge.
- [ ] CALLs dinâmicos são resolvidos.
- [ ] MOVE funciona.
- [ ] VALUE funciona.
- [ ] REDEFINES funciona dentro das relações lógicas exigidas.
- [ ] Group MOVE funciona nos casos exigidos.
- [ ] Sobrescritas não preservam valores impossíveis.
- [ ] Incertezas não eliminam candidatos válidos.
- [ ] Não existem explosões de estado nas fixtures obrigatórias.

### Dependências

- [ ] Programa → programa.
- [ ] Programa → arquivo.
- [ ] Programa → tabela DB2.
- [ ] Programa → copybook.
- [ ] Programa → DCLGEN.
- [ ] Programa → SQL INCLUDE.
- [ ] Demais categorias exigidas pelos oráculos.
- [ ] Targets dinâmicos incluídos.
- [ ] Deduplicação correta.

### JSON

- [ ] Schema novo e minimalista.
- [ ] Apenas programa e dependências.
- [ ] Tipos e nomes corretos.
- [ ] Provenance mínimo.
- [ ] Sem CFG.
- [ ] Sem AST.
- [ ] Sem evidence packs.
- [ ] Sem coverage/readiness.
- [ ] Sem bloating herdado da pipeline.
- [ ] Determinismo garantido.
- [ ] Nenhuma obrigação de compatibilidade estrutural com o JSON antigo.

### Validação

- [ ] 73/73 programas CardDemo processados.
- [ ] Todas as categorias de dependências comparadas.
- [ ] Nenhum falso negativo conhecido nos oráculos obrigatórios.
- [ ] Nenhum falso positivo conhecido introduzido nos oráculos obrigatórios.
- [ ] Nenhuma divergência sem investigação e classificação.
- [ ] Fixtures pertinentes executadas.
- [ ] Nenhuma fixture obrigatória ignorada.
- [ ] Nenhum crash ou OOM.
- [ ] Benchmark ponta a ponta disponível.

### Entrega

- [ ] CLI executável.
- [ ] README simples.
- [ ] Testes automatizados.
- [ ] Comando de análise documentado.
- [ ] Comando de regressão documentado.
- [ ] Commits locais organizados.
- [ ] Nenhum push realizado.

Não declare o trabalho concluído com um MVP que resolve apenas uma fração desses requisitos.

Não substitua a comparação real por percentuais estimados.

---

## 14. Autonomia e execução

Você está autorizado a trabalhar autonomamente no clone.

Pode:

- Investigar o código.
- Criar classes.
- Modificar a estrutura do projeto.
- Reaproveitar componentes.
- Remover caminhos operacionais desnecessários.
- Refatorar quando isso simplificar o analisador.
- Criar testes.
- Executar corpus e fixtures.
- Corrigir falhas.
- Executar benchmarks.
- Fazer commits locais.

Não precisa solicitar aprovação a cada etapa.

Não interrompa o desenvolvimento para apresentar apenas um discovery.

Não produza uma proposta arquitetural como entrega final.

Não crie burocracia documental.

Não implemente funcionalidades especulativas.

Não utilize o tempo de desenvolvimento para transformar este monolito em outra plataforma genérica.

Ao encontrar um erro:

1. Identifique sua causa.
2. Corrija o algoritmo de maneira geral.
3. Adicione ou reaproveite um teste que represente o caso.
4. Repita a comparação diferencial.
5. Continue.

Ao encontrar um problema de memória:

1. Identifique a estrutura ou operação que causa o crescimento.
2. Corrija a complexidade ou o padrão de alocação.
3. Evite soluções específicas por fixture.
4. Valide que a correção não elimina candidatos.
5. Repita os testes.

Se houver um bloqueio externo genuinamente incontornável, registre-o de maneira objetiva. Não invente validações nem declare sucesso sem os resultados exigidos.

---

## 15. Instrução final e condição de parada

**Sua missão é implementar o analisador, não planejá-lo.**

O resultado esperado é um software funcional, monolítico, rápido e independente que:

- Recebe fontes COBOL.
- Reutiliza o frontend existente.
- Constrói um CFG direto.
- Executa dataflow lógico.
- Resolve dependências literais e dinâmicas.
- Propaga VALUE, MOVE e aliases lógicos.
- Trata REDEFINES e grupos sem memória física.
- Produz um `dependencies.json` extremamente simples.
- Encontra as mesmas dependências válidas da pipeline atual.
- Passa pelo CardDemo completo e pelas fixtures relevantes.
- Apresenta medições de tempo e memória.

**Não pare após um MVP, após a construção do CFG, após conseguir resolver CALLs literais ou após implementar o primeiro dataflow.**

Continue avançando pelas etapas de implementação, correção, comparação e otimização até satisfazer os critérios obrigatórios de conclusão ou encontrar um bloqueio externo incontornável que impeça comprovadamente a continuidade.

Não substitua trabalho de implementação por documentação, propostas ou relatórios de progresso.

O relatório final deve ser curto, contendo somente:

1. **CardDemo:** programas processados, dependências iguais, ausentes e adicionais.
2. **Fixtures:** testes aprovados, reprovados e limitações reais.
3. **Desempenho:** tempo total e memória frente à pipeline original.
4. **Execução:** comando para rodar o analisador.
5. **Git:** confirmação de commits locais e ausência de remote.
6. **Pendências:** somente limitações reais, se houver.

### Princípio definitivo

**Construir o menor e mais rápido analisador COBOL capaz de descobrir corretamente todas as dependências exigidas pelo corpus, usando CFG e dataflow lógico, sem reproduzir a infraestrutura complexa da pipeline atual.**

A complexidade necessária para encontrar dependências deve permanecer dentro dos algoritmos.

O `dependencies.json` deve conter apenas o resultado dessa descoberta.

**Menos arquitetura. Menos objetos. Menos memória. Menos JSON. Mesmas dependências.**
