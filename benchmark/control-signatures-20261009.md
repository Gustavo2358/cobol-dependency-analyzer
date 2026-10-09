# Fixture de combinações de controle

O novo fixture reproduziu o crescimento combinatório na versão de produção
`88dbd63c7adab0705e8443ad16d98f6303c23614`, sem alterar o solver. Com dez flags,
174 linhas geraram **2.048 contextos**; com doze flags, 206 linhas geraram
**8.192 contextos**. Ambos produziram somente duas dependências, sem diagnósticos.

## Como o fixture provoca o caso

Em CHOOSE-01, um INPUT desconhecido determina se FLAG-01 recebe ONE00000 ou
ZERO0000. Cada branch invoca CHOOSE-02. Esse parágrafo faz a mesma escolha para
FLAG-02 e passa adiante. Depois de N níveis, o mesmo BODY pode receber 2^N
combinações. O código cresce apenas 16 linhas a cada flag adicional.

Em BODY, cada FLAG participa de um IF que escolhe um CALL literal. O domínio
atual especializa os valores necessários a esses predicados, mantendo entradas
diferentes como contextos diferentes. As entradas desconhecidas INPUT também
determinam branches, mas as dos níveis já percorridos deixam de ser necessárias
nas invocações seguintes. O total observado segue 2^(N+1): a raiz, os contextos
dos parágrafos intermediários e os 2^N possíveis contextos de BODY.

Cada execução concreta chega a BODY uma vez e faz N CALLs. Não há repetição,
recursão, caminhos sem retorno, handlers ou intervalos THRU. O crescimento vem
dos caminhos possíveis que o analisador considera, e não de uma execução
concreta exponencial ou de uma lista exponencial de chamadas escrita no fonte.

A versão `data` mantém as mesmas declarações e os mesmos branches de geração,
mas usa `CALL FLAG-nn` em BODY. As flags são relevantes para as consultas,
porém não determinam branches no consumidor; tornam-se parâmetros. O motor
compartilha os corpos, e o total de contextos observado passa a N+2. As duas
versões preservam os nomes ONE00000 e ZERO0000. A igualdade é dos destinos;
sites e localizações de origem são próprios de cada fonte.

## Resultado observado

| Flags | Linhas com IFs no BODY | Contextos com IFs | Contextos no controle de dados | Visitas com IFs | Visitas no controle |
|---:|---:|---:|---:|---:|---:|
| 4 | 78 | 32 | 6 | 268 | 41 |
| 6 | 110 | 128 | 8 | 1.340 | 59 |
| 8 | 142 | 512 | 10 | 6.396 | 77 |
| 10 | 174 | 2.048 | 12 | 29.692 | 95 |
| 12 | 206 | 8.192 | 14 | 135.164 | 113 |

O cache de transferências locais continua eficaz: com doze flags, há apenas
123 avaliações locais novas e 126.849 reutilizações. Mesmo assim, permanecem
8.192 contextos, 135.164 visitas e 8.192 entregas de resultados. O índice
estrutural fica em 309 pontos e 629 itens de trabalho. Portanto, reutilizar
cálculos locais e controle estrutural não elimina a enumeração das assinaturas
de predicados nesse caso.

Na versão de doze flags, a análise interna levou 719,48 ms, contra 79,08 ms no
controle de dados. São execuções únicas por caso; tempos servem para localizar
o efeito, não para estabelecer uma estimativa estatística de desempenho.
O sinal principal é o crescimento determinístico dos contadores.

Todos os dez casos encerraram com código 0 e exatamente duas dependências,
com posições de origem válidas e sem diagnósticos de incompletude. O maior
RSS foi **274.408 KiB (268,0 MiB)**. Não houve OOM nem acionamento das guardas.

## Método e reprodução

JAR preservado da implementação anterior:
`afae3a62c961dd5ae03a0cc29cbbfe9fca6d1aa54e0952d017431e0608ad200f`.
Temurin 21.0.12+1.1, JVMs novas e sequenciais, heap 512 MiB, metaspace 256 MiB,
memória direta 64 MiB, `--max-work 1000000`. Supervisor externo: RSS 640 MiB
e 30 segundos. Medições usam GNU time e o sidecar de contadores do produto.
Não foi necessário acrescentar instrumentação ao solver. Não executamos escalas
acima de 12 nem tentamos provocar OOM.

Os fontes de dez flags estão versionados em
[control-signatures](../src/test/resources/dependency-regression/control-signatures/README.md).
O [gerador](generate-control-signatures.py) permite reproduzir as outras escalas
e gera o conteúdo dos fontes versionados byte a byte. O README dos fixtures
contém os comandos de geração e execução com heap 512 MiB.

## Validação e limites

- `DependencySummaryTest`: 31 testes, zero falhas/erros/skips, incluindo o novo
  caso com os dois fontes e seus hashes/oráculos.
- CLI: 10/10 casos, nomes, tipos, cardinalidade, programa de origem, posições e
  ausência de diagnósticos conferidos.
- Código de produção e JAR inalterados. A qualificação FAST/CardDemo anterior
  refere-se à mesma produção; não foi reexecutada nem relabelada nesta etapa.
- O teste não exige crescimento exponencial para passar: os contadores ficam
  na medição, permitindo uma otimização futura que preserve o oráculo semântico.

Este fixture confirma uma fonte de crescimento restante. Não mede sua
frequência em programas reais nem implementa a representação condicional
proposta. Não é necessário derrubar o processo para evidenciar o problema:
duas flags adicionais quadruplicam os contextos com apenas 32 linhas a mais.

O [registro estruturado](control-signatures-20261009.json) contém os resultados,
comandos, hashes e manifesto da evidência bruta local preservada em
`benchmark/results/control-signatures-20261009/` (ignorado pelo Git).
