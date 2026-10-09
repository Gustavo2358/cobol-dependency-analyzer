# Continuações compartilhadas: implementação e medição

Data: 2026-10-09. Base de produção: `87255b08d38456efbc3caf3bd6082f03dee82362`.
Implementação medida: `bbbb182`, após os checkpoints abaixo. Há um único solver
de produção; escapes permanecem ativos. Nenhuma flag de ablação foi usada para
qualificar a correção. Os [dados normalizados](shared-continuation-fix-20261009.json)
registram hashes, contadores, resultados e referências aos artefatos brutos.

**A família que falhava por memória convergiu, sem aumentar o heap.** O caso
dinâmico N=32 passou nas três repetições, com mediana de 3,13 s; N=36, em 3,69 s.
N significa caixinhas por hub, com dois hubs. N=36 contém 72 caixinhas,
seletores/flag externos renovados, PERFORMs cruzados, EXIT PARAGRAPH e GOBACK.

## Checkpoints

| Commit | Mudança verificável |
| --- | --- |
| `f686be7` | Registra a investigação e as propostas fundamentadas em literatura. |
| `82968a4` | Saídas e marcas de entrega em bitsets dinâmicos; dados temporários limitados à construção do controle. |
| `7f5121a` | Propagação por deltas, com assinantes indexados e entrega de resultados anteriores a chamadores/arestas novos. |
| `a969305` | Percurso de escape compartilhado sob endpoint e política de ancestrais; remove chamadas internas de reinvocação. |
| `bbbb182` | Remove o trecho sem uso de reinvocação de entradas independentes; documenta métricas e adiciona replay protegido do corpus. |
| Checkpoint final de evidências | Regressão de fanout com orçamento limitado, FAST e comparação de dependências/desempenho. |

Os commits anteriores a `f686be7`, também incluídos na revisão, são o discovery,
seus instrumentos e fixtures. As ablações históricas ficam exclusivamente nas
ferramentas de investigação; não existem caminhos alternativos na aplicação.

O segundo JAR foi preservado atualizando o snapshot do primeiro com as classes
compiladas em `7f5121a`. Os demais foram empacotados pelo Maven. Todos os hashes
estão no JSON. Resultados originais, inclusive falhas e timeouts, não foram alterados.

## O que mudou no mecanismo

Antes, quando o corpo de um PERFORM atravessava uma fronteira que não encerrava
aquela invocação, o solver cadastrava outra chamada interna ligada ao mesmo
binding. A continuação dessa chamada podia descobrir outras fronteiras e criar
mais chamadas. Cada uma recebia as saídas conhecidas de sua entrada:

```text
PERFORM A, esperando a fronteira A
    corpo -> fronteira B -> chamada interna ao trecho seguinte
                                  -> fronteira C -> outra chamada interna
                  cada chamada nova recebe as saídas daquele trecho
```

Agora, a obrigação de retorno acompanha o percurso. Cruzar B ou C conserva a
mesma invocação. Só uma saída que realmente encerra esse escopo é entregue ao pai:

```text
chamador R1 ---+
              +--> (endpoint A, ancestrais S): corpo -> B -> C -> hub -> ...
chamador R2 ---+                                         |
                                           saída compatível com A ou S
                                                        |
                                      interpretar a saída no escopo do pai
```

Um PERFORM real continua criando seu vínculo de retorno. Seus endereços de
retomada e entradas de valores permanecem separados. O escopo adicional na chave
é a cadeia de ancestrais semanticamente relevante, não o ID do chamador nem do
binding. Corpos e intervalos THRU com a mesma política podem compartilhá-lo.
Escapes ancestrais desenrolam uma invocação por vez; o pai interpreta novamente
a saída. Isso conserva escapes de seção e de PERFORMs inline aninhados.

O controle guarda saídas em bits e propaga apenas novidades. Uma saída nova vai
para os assinantes existentes; um assinante novo recebe as saídas já conhecidas.
Uma aresta nova também recebe o resultado anterior do sucessor. Esse tratamento
simétrico evita perder resultados por causa da ordem de descoberta.

Depois do ponto fixo e da seleção do fluxo observável, os resultados, as marcas
de entrega e os índices de construção deixam de ser retidos. Permanece o grafo
usado por ciclos, ordenação e relevância. Não há restrição de 64 saídas: o teste
com 150 saídas verifica crescimento do bitset para mais palavras.

## Medições por checkpoint

JVMs sequenciais, Temurin 21.0.12+1.1, **heap 256 MiB**, teto RSS 512 MiB,
reserva do host 2.048 MiB, `--max-work 100000000`, timeout 35 s. Nesta tabela,
todos os checkpoints usam JFR de perfil e log de GC; cada célula é uma execução.

| Versão | N=8 | N=16 | N=32 |
| --- | --- | --- | --- |
| Original | PASS 3,59 s | Interrompido em 35,03 s | OOM em 4,75 s |
| Bitsets e vida útil | PASS 3,44 s | Interrompido em 35,02 s | Interrompido em 35,03 s |
| Deltas indexados | PASS 3,89 s | Interrompido em 35,02 s | Interrompido em 35,01 s |
| Escopo compartilhado | PASS 1,31 s | PASS 1,82 s | PASS 3,49 s |

As duas primeiras mudanças atravessaram a parede de armazenamento, mas mantiveram
as relações numerosas na fase de valores. A terceira removeu o multiplicador de
continuações. O overhead observado em N=8 no checkpoint de deltas é registrado;
uma execução curta não permite afirmar uma regressão estatística de tempo.

## Repetições da versão final

JVM fria por execução, sem JFR, três execuções por caso. Medianas de tempo externo
e de pico RSS; os três resultados e picos individuais ficam no JSON.

| Versão / tamanho | Tempo mediano | RSS mediano | Resultado |
| --- | ---: | ---: | --- |
| Original / N=8 | 3,18 s | 276,6 MiB | 3/3 exatos |
| Final / N=8 | 1,06 s | 173,9 MiB | 3/3 exatos |
| Final / N=16 | 1,57 s | 247,6 MiB | 3/3 exatos |
| Final / N=32 | 3,13 s | 323,3 MiB | 3/3 exatos |
| Final / N=36 | 3,69 s | 379,1 MiB | 3/3 exatos |

Em N=8, a comparação com o mesmo protocolo dá cerca de **3 vezes menos tempo**
e **37% menos RSS mediano**. Não calculamos speedup exato para N=16/32, pois o
original não convergiu no protocolo limitado. O maior pico entre as repetições
finais foi 391,8 MiB. A execução final adicional com JFR em N=36 passou em 5,52 s,
com 418,7 MiB; ela também está preservada, sem misturar seu tempo às medianas.

| Contador em N=8 dinâmico | Original | Final |
| --- | ---: | ---: |
| Contextos de valores | 260 | 35 |
| Trabalho de valores | 959.355 | 2.367 |
| Entregas de resultados de valores | 933.926 | 563 |
| Pares de controle chamada/saída | 65.842 | 545 |

O contador de pares não existia na CLI original. Seu valor original é observado
no checkpoint de representação compacta, que preserva os pares e o trabalho de
valores da versão original. Não atribuimos essa métrica a uma saída inexistente
do JAR original. `controlWorkItems` mudou de unidade para lotes de deltas;
seus números antigos e novos não medem a mesma operação.

Na família fixed, N=4/8/12/16/24/32/36 passou com exatamente três contextos de
valores. Os pares foram 145/545/1.201/2.113/4.705/8.321/10.513: seguem
`8*N² + 4*N + 1` nesses tamanhos. O discovery anterior havia medido
`16*N⁴ + 4*N² + 6*N + 2` no original. Isso caracteriza essa família medida,
não estabelece uma cota universal para qualquer programa COBOL.

O perfil final contém 223 amostras da main: 131 em controle, 78 em relevância,
14 nas outras categorias. O grafo e seus índices ainda concentram trabalho.
Há uma compactação Full GC, de 256 MiB para 89 MiB, seguida de conclusão; não
há o ciclo indefinido de GC observado na execução corporativa. Amostras de
alocação são estimativas acumuladas, não medição de memória residente.

## Dependências e validação

- **FAST: 1.084 testes em 141 suites, sem falhas, erros ou skips**, mais os testes
  Python do harness. Heap dos JVMs de build/testes também limitado a 256 MiB.
- Regressões específicas: escape alheio, escape de seção ancestral, PERFORM
  inline aninhado, THRU com endpoint comum, correlação entre chamadores, recursão
  sem retorno, terminais, handlers, chamador/aresta registrados depois de uma
  saída e 150 saídas distintas. A nova regressão de caixinhas usa orçamento de
  100.000 e exige os oito nomes e menos de 1.000 pares/entregas.
- **CardDemo: 73/73 fontes, 815 relações iguais, zero ausentes ou adicionais**,
  frente ao produto anterior e à pipeline congelada. Os 185 hashes de fontes e
  includes coincidem com o manifest histórico antes/depois. Mantidos os 47
  PARTIAL; não são promovidos artificialmente a análises completas.
- O corpus levou 122,59 s, com maior RSS de 326,5 MiB, no mesmo heap de 256 MiB.
  Este ensaio verifica paridade; não é comparação de velocidade do corpus com
  os ensaios históricos que usavam outro heap.
- Sentinela: **AFTER e ESCAPED** preservados no original e na correção. A perda
  de ESCAPED provocada pela ablação histórica continua documentada.
- FIXTURE02 original: PASS 1,87 s antes / 1,72 s depois, resultado idêntico e
  sem dependências de programa; os PERFORMs sem retorno impedem alcançar seus
  CALLs. Não usamos esse resultado vazio como oráculo do fanout dinâmico.
- Hub simples com 255 destinos: PASS antes/depois, JSON idêntico. Seu tempo
  1,26 / 1,31 s não mostra ganho relevante; a correção ataca reinvocação e fanout.

Falhas de desenvolvimento foram corrigidas e os logs preservados: o primeiro
escopo incluía identidade do corpo e piorava compartilhamento de caudas; um
escape ancestral era exportado antes de ser interpretado pelo pai. Os testes
detectaram ambos. A guarda de nomenclatura também exigiu mover a nova fixture
para os recursos de teste. Os testes finais foram reexecutados depois dos fixes.

## Reprodução e limites

Para um tamanho, com o JAR compilado nesta branch:

```sh
python3 -B benchmark/run-hub-dispatch.py OUTPUT \
  --jar target/cobol-dependency-analyzer.jar --mode return-fanout \
  --boxes 8 16 32 36 --heap 256 --rss 512 --system-reserve 2048 \
  --max-work 100000000 --timeout 35 --telemetry
```

Use diretório OUTPUT novo em cada repetição. Para os checkpoints instrumentados,
adicione `--profile --profile-duration 40`. Para isolar o controle da variação
dos valores, use `--mode return-fanout-fixed --boxes 4 8 12 16 24 32 36`.
`run-carddemo-bounded.py --help` descreve o replay com os relatórios e o manifest
congelados. Logs, JFR, comandos, telemetria e JSONs completos ficam em
`benchmark/results/shared-continuation-fix-20261009/`; esse diretório é ignorado
pelo Git. O relatório versionado registra seus hashes, sem publicar builds.

O programa corporativo privado não foi executado. Seu diagnóstico de GC e
relações é compatível com o mecanismo corrigido; não demonstra que ele não
encontrará outra parede. Pontos por endpoint, relações legítimas, assinaturas de
recorrência e resolução de valores ainda podem crescer. Não houve aumento de
heap nem cortes de destinos, candidatos, saídas ou relações legítimas para obter
convergência. Veja a [fundamentação algorítmica](solver-discovery-literature-20261009.md).
