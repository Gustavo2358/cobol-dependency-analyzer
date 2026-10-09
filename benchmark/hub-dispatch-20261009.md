# Hubs externos, caixinhas e PERFORM: descoberta de crescimento

O padrão hub → GO TO DEPENDING ON → caixinha → PERFORM de outro hub reproduziu
crescimento exponencial de contextos quando as caixinhas acumulam flags que
afetam CALLs. A largura de 255 destinos, isoladamente, não reproduziu a falha.
Também foi encontrado um mecanismo separado de crescimento estrutural quando
cada caixinha é entrada de um PERFORM cruzado.

Não foi aplicada correção de produção. Estes resultados identificam mecanismos
nos fixtures, não demonstram qual deles ocorre no fonte privado relatado.
Uma pilha em DependencyFlow.analyze não distingue as fases: ela engloba controle,
relevância, valores e resolução. É necessário olhar os frames mais internos.

## Insumos e proteção

Baseline: main `87255b08d38456efbc3caf3bd6082f03dee82362`; JAR
`b7cc0654207091e1872071a7a3267547daa170ce9b1fb1026702c99e69fa5ab1`, igual ao
qualificado para OCCURS esparso. Temurin 21.0.12.1+1. Uma JVM de análise por vez,
heap nunca acima de 512 MiB, orçamento 1.000.000. As rodadas iniciais foram
supervisionadas em 640 MiB de RSS; após os primeiros cortes, em 576 MiB.
Há pequena ultrapassagem entre amostras de 50 ms: o maior pico inicial observado
foi 665,4 MiB antes do SIGKILL. Os experimentos diagnósticos posteriores usaram
heap menor, 256 MiB. Encerramento RSS_GUARD não significa OOM confirmado.

O [gerador](generate-hub-dispatch.py) cria fonte FIXED. Cada hub renova seu seletor
externo com ACCEPT em toda iteração. Cada caixinha retorna por GO TO ao próximo
hub, e FINAL-BOX termina com GOBACK. O seletor fora do intervalo volta ao mesmo
hub. Há dois hubs; 254 caixinhas mais FINAL-BOX dão 255 destinos por despacho.
Variações isolam CALL literal, alvo dinâmico acumulado, flags, PERFORM de corpo
comum, PERFORM cruzado entre caixinhas e PERFORM de outro hub. Não há corte na
quantidade de iterações da fonte.

[Fontes representativas e oráculos](fixtures/hub-dispatch/expected.json) incluem
os testemunhos de oito e 16 flags, os controles de 255 destinos e uma mutação
causal. Os candidatos são verificados exatamente, não somente a existência do
JSON. Os casos que excedem recursos não são classificados como PASS.

## Contrastes no JAR original, sem profiling

Uma execução por caso: tempos não são medianas nem estimativas de desempenho de
um programa real. A matriz inicial concluiu os 16 casos (4 tamanhos × 4 modos).

| Dois hubs, 255 destinos cada | Parede s | Contextos de valores | Visitas de valores | Pico RSS MiB |
| --- | ---: | ---: | ---: | ---: |
| CALL literal nas caixinhas | 1,31 | 3 | 1.029 | 178,0 |
| Caixinhas modificam alvo de CALL no hub | 2,42 | 3 | 66.563 | 213,1 |
| Caixinhas acumulam flags, CALLs na saída | 1,81 | 3 | 2.302 | 246,5 |
| Caixinhas modificam alvo e PERFORM de corpo comum | 3,53 | 257 | 133.360 | 275,6 |
| Caixinhas modificam alvo e PERFORM de outro hub | 3,38 | 767 | 391.427 | 416,7 |

Estas variações conservam todos os candidatos esperados. Os alvos dinâmicos no
hub conservam também o remainder registrado pelo produto; código 1 é PARTIAL,
não perda de candidatos. Muitas entradas no hub e seletor externo não bastaram,
sozinhas, para produzir enumeração de todos os históricos.

## Testemunho exponencial: flags + PERFORM de hub

Cada caixinha i faz MOVE 'Y' TO FLAG-i, PERFORM do outro hub e GO TO para esse
hub. FINAL-BOX consulta cada flag: CALL PGM-i se Y, CALL ZERO0000 caso contrário.
As flags, portanto, são relevantes para dependências. O mesmo hub pode receber
o conjunto de flags deixado por diferentes subconjuntos de caixinhas visitadas.

| Caixinhas por hub / flags | Contextos | Visitas de valores | Parede s | Pico RSS MiB |
| --- | ---: | ---: | ---: | ---: |
| 4 | 67 | 878 | 0,96 | 158,5 |
| 6 | 261 | 4.966 | 0,96 | 144,3 |
| 8 | 1.031 | 25.566 | 1,21 | 193,2 |
| 10 | 4.105 | 124.886 | 1,86 | 303,8 |
| 16 | análise interrompida por RSS_GUARD | — | 4,53 | 576,6 |

Nos quatro casos concluídos, os números coincidem com `4 × 2^N + N - 1`.
A evidência não é somente o ajuste dessa curva: a cópia instrumentada do caso
N=8 encontrou quatro pares de entrada/fronteira com 255 contextos cada, e
**255 máscaras distintas de oito flags em cada par**, cobrindo todos os
subconjuntos não vazios. Os exemplos começam em 00000001, 00000010, 00000011 etc.
O total de contextos e os contadores coincidem com o JAR original.

A instrumentação de N=16 mostra o controle encerrado após 1.201 visitas e
599 planos, antes de iniciar os valores. O fluxo depois chega a 48.122 contextos,
976.435 estados BEFORE e 374.323 itens pendentes (última amostra completa).
Falha por OutOfMemoryError com heap de 256 MiB. No caso N=32, o controle também
estabiliza, com 1.175 planos; o fluxo ultrapassa um milhão de estados e falha
explicitamente por RESOURCE_LIMIT. Portanto, aumentar max-work não corrige o
mecanismo e pode permitir crescimento maior antes da falha.

O código explica o mecanismo observado: variáveis de recorrências entram em
`specialized`; subscribe preserva seus valores concretos na chave SummaryKey.
Entradas com vetores de flags diferentes criam contextos diferentes. O cache de
transformações locais continua funcionando — em N=10 há só 88 evaluations e
100.211 reusedEvaluations — mas não reúne esses contextos nem seus estados.
O motor não enumera cada sequência completa; neste testemunho enumera efeitos
distintos dessas sequências, que também são exponenciais.

### Experimento causal

A mutação frozen-flags-16 estabelece todas as flags como Y no MAIN, mantendo
os dois hubs, ACCEPTs, GO TOs, PERFORMs e MOVEs dentro das caixinhas. Assim,
visitar outra caixinha não cria outra combinação de flags. O controle fica de
tamanho semelhante (615 planos); os valores caem para **23 contextos, 241 estados
BEFORE e 310 visitas**, concluindo em 1,01 s / 140,8 MiB com heap de 256 MiB.
Os 16 nomes PGM esperados são exatos; ZERO deixa de ser esperado porque esta
mutação altera deliberadamente os valores iniciais. Ela isola cardinalidade de
entradas, não é uma simplificação proposta para o produto.

## Profiling: causa de crescimento e custo adicional

JFR settings=profile, gravação limitada a dez segundos / 24 MiB; leitor em
streaming com heap 128 MiB. Na gravação **sem instrumentação** de N=16, 173
amostras de execução da thread main: 170 (98,3%) nas categorias de fluxo de
valores, nenhuma amostra de DependencyControl. A propagação de controle já
estabilizada nos probes confirma a distinção entre fases.

`workRank` é o primeiro frame do produto em 58/173 amostras (33,5%). O comparador
da PriorityQueue chama esse método, que cria Exit e Point para consultar a ordem
do fluxo. As estimativas de alocação somam 4,49 GB durante a janela gravada;
Point + Exit respondem por 2,50 GB (55,6%). São **alocações cumulativas amostradas**,
não memória residente nem bytes retidos; a JVM usa heap 256 MiB e recicla objetos.
Esse custo agrava o processamento da fila grande. Removê-lo, isoladamente, não
eliminaria as combinações de entrada nem os estados retidos.

O mesmo caso sem probes também termina em OOM com heap 256 MiB (15,77 s,
439,7 MiB de RSS). A janela JFR de dez segundos não cobre os segundos finais.

## Outro mecanismo: PERFORM cruzado entre caixinhas

Na variação cross-perform, cada caixinha faz CALL literal e pode executar
PERFORM da próxima caixinha antes de voltar ao hub. O teste usa uma condição
externa para permitir também o caminho sem PERFORM.

Com oito caixinhas por hub há 2.365 planos de controle; com 32, 33.997.
No caso de 128, a descoberta do grafo ultrapassa 245.760 planos e falha por OOM
com heap 256 MiB, **antes de começar a propagação dos conjuntos de saídas**.
O JFR confirma DependencyControl.discover como primeiro frame em 18/32 amostras.
Esse crescimento vem de pontos distinguidos pela fronteira do PERFORM. Não
confundir com o testemunho exponencial de valores nem atribuir essa falha apenas
às uniões repetidas das linhas 45–48.

## Reprodução e evidências

```sh
python3 -B benchmark/run-hub-dispatch.py OUTPUT --mode literal value flags perform
python3 -B benchmark/run-hub-dispatch.py OUTPUT --mode hub-perform --boxes 254
python3 -B benchmark/run-hub-dispatch.py OUTPUT --mode hub-perform-flags --boxes 4 6 8 10 16
python3 -B benchmark/run-hub-dispatch.py OUTPUT --mode hub-perform-flags --boxes 16 --heap 256 --profile
```

Use JDK 21 no PATH. OUTPUT deve ser novo; o runner recusa sobrescrever rodadas.
Os scripts preservam logs, fontes, métricas e exit codes e matam o grupo do
processo ao ultrapassar RSS/tempo. [JSON de evidência](hub-dispatch-20261009.json)
contém 46 medições, comandos normalizados, hashes, perfis resumidos
e resultados incompletos. Raw, JFRs, GC logs, fontes/classes das cópias diagnósticas
e máscaras estão em `benchmark/results/hub-dispatch-20261009/`, ignorado pelo Git.
A instrumentação acrescenta contadores/prints e não altera a transferência de
valores nem o agendamento; tempos instrumentados não são baseline de desempenho.
Os resultados do controle frozen-flags foram também conferidos no JAR original,
com os mesmos 23 contextos e JSON idêntico. A fonte literal-255 preserva a revisão
inicial do gerador sem a declaração auxiliar EXTERNAL-FLAG, não utilizada nesse
modo; as demais fontes não mutadas reproduzem byte a byte o gerador final.

Não foram rerodados FAST e CardDemo: o JAR de produção e as fontes de produção
continuam idênticos aos qualificados; esta investigação adiciona fixtures,
scripts e evidência. Nenhuma conclusão de robustez universal ou correção do
fonte privado é derivada desses testemunhos.
