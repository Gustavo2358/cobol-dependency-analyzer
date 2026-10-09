# Caixinhas com heap maior: processamento versus pressão de GC

Sete execuções novas separam custo do algoritmo e custo de memória. Cinco
concluíram com todos os candidatos esperados; duas terminaram em OOM. Não foi
reproduzida uma execução de dezenas de minutos sem OOM. O crescimento de trabalho
permanece, e um RSS aparentemente estável pode esconder heap saturado e GC caro.
Nenhuma correção foi aplicada ao produto.

## Condições do experimento

Baseline operacional main `87255b08d38456efbc3caf3bd6082f03dee82362`, JAR SHA-256
`b7cc0654207091e1872071a7a3267547daa170ce9b1fb1026702c99e69fa5ab1`, sem mudança
de produção. Temurin 21.0.12.1+1. Insumos do [gerador de hubs](generate-hub-dispatch.py),
dois hubs com seletor renovado por ACCEPT e saída FINAL-BOX. A dimensão informada
nas tabelas é caixinhas por hub, além de FINAL-BOX.

O pedido de heap maior autoriza este contraste; não altera o heap padrão nem
os limites do produto. A máquina tinha aproximadamente 4,5 GiB disponíveis e
swap quase cheio. Usou-se uma JVM de análise por vez:

- Heap 1.024 MiB: corte de RSS em 1.408 MiB.
- Heap 1.536 MiB: corte de RSS em 1.920 MiB.
- Corte adicional se MemAvailable cair abaixo de 2.048 MiB. O mínimo amostrado
  das sete execuções foi 2.716,1 MiB. Nenhum desses cortes foi acionado.
- max-work 100.000.000, evitando o antigo limite de um milhão de estados/visitas.
- Timeout de 135/150 s por caso; não foi acionado. Um timeout não provaria OOM.
- RSS, CPU de processo e memória disponível amostrados a cada segundo; proteção
  consultada aproximadamente a cada 50 ms. CPU inclui threads de GC/JIT.
- JFR profile, duração máxima 120 s / 24 MiB; leitor streaming com heap 128 MiB.

As primeiras três execuções usam diretamente o JAR original. As quatro com
PERFORM de corpo comum usam a mesma cópia diagnóstica com contadores de fases,
sem mudar transferência de valores, filas ou orçamento. A comparação entre
heaps usa fontes, JAR, instrumentação e contadores finais idênticos. Tempos são
execuções únicas, não medianas nem previsão para o fonte privado relatado.

## PERFORM de outro hub: mais memória permite terminar alguns tamanhos

Cada caixinha marca sua flag e executa PERFORM do outro hub. FINAL-BOX consulta
as flags para decidir quais CALLs executar; o vetor de entrada é relevante.

| Caixinhas | Heap MiB | Resultado | Parede s | Pico RSS MiB | Contextos | Visitas de valores |
| --- | ---: | --- | ---: | ---: | ---: | ---: |
| 12 | 1.024 | candidatos exatos | 4,65 | 594,9 | 16.395 | 589.774 |
| 14 | 1.024 | candidatos exatos | 21,64 | 1.220,4 | 65.549 | 2.719.686 |
| 15 | 1.024 | OOM, código 2 | 81,68 | 1.308,7 | não publicado | não publicado |

No caso de 15, entre 60 e 81 s o RSS ficou entre 1.287,4 e 1.298,5 MiB,
enquanto o tempo de CPU observado aumentou 186,16 s (várias threads) em 20,36 s
de parede. Portanto, houve um patamar de memória acompanhado de CPU alta.
Ele **não** demonstrou que a cardinalidade lógica ou os objetos vivos estavam
estáveis: as compactações completas deixavam cerca de 997–1.011 MiB ocupados
em heap de 1.024 MiB. Foram 143 Full GCs e 55,15 s de pausas GC somadas antes
do OutOfMemoryError: Java heap space.

[Gráfico de RSS e CPU ao longo do tempo](hub-large-heap-20261009.svg), gerado
diretamente da telemetria preservada, mostra esse patamar. Ele não deve ser
interpretado como gráfico de objetos vivos: RSS inclui outras áreas da JVM.

O JFR tem 1.471 amostras Java da thread main; 716 passam por workRank.
Isso descreve os trechos Java amostrados, não a proporção total de CPU do
processo: o GC também consome CPU e deixa main parada nas pausas. O patamar do
RSS neste testemunho é compatível com saturação do heap, não evidência de um
algoritmo infinito. O mesmo diagnóstico não pode ser atribuído ao programa real
sem medir seu GC e progresso.

## PERFORM de corpo comum: custo após construir os estados

Nesta variação, as caixinhas acumulam flags e chamam COMMON-BOX, que consulta
todas as flags antes de retornar. Elas voltam por GO TO ao hub. Há mais trabalho
por contexto, mesmo com menos contextos do que no testemunho exponencial acima.

| Caixinhas | Heap MiB | Resultado | Parede s | Pico RSS MiB | Contextos | Visitas de valores | Visitas de resolução |
| --- | ---: | --- | ---: | ---: | ---: | ---: | ---: |
| 64 | 1.024 | candidatos exatos | 32,73 | 1.354,5 | 8.065 | 1.577.105 | 1.023.811 |
| 80 | 1.024 | OOM, código 2 | 30,25 | 1.290,4 | não publicado | não publicado | não publicado |
| 64 | 1.536 | candidatos exatos | 16,13 | 1.735,1 | 8.065 | 1.577.105 | 1.023.811 |
| 72 | 1.536 | candidatos exatos | 24,28 | 1.854,3 | 10.227 | 2.245.263 | 1.461.961 |

O caso de 80 falha durante a propagação de valores. A última amostra completa
registra 7.904 contextos, 1.892.286 estados BEFORE e 1.900.544 visitas, ainda
com outros itens pendentes. Não foi truncado pelo max-work antigo.

### Mesmo trabalho, menos GC

Nas duas execuções de 64, a propagação termina com os mesmos 8.065 contextos e
1.548.235 estados BEFORE. O contador final de transformações calculadas é 713,
com 1.568.129 reaproveitamentos: reaproveitar operações não elimina o trabalho
de organizar/propagar estados e resolver consultas em tantos contextos.

| Fase observada por probes | Heap 1 GiB | Heap 1,5 GiB |
| --- | ---: | ---: |
| Propagação: primeira visita até primeira resolução | 8,50 s | 8,66 s |
| Resolução: primeiro até último probe periódico | 19,47 s | 5,61 s |
| Pausas GC somadas | 10,97 s | 0,99 s |
| Full GCs | 24 | 0 |

As durações de resolução são spans observados, não timers completos: o último
probe antecede o fim da fase. Ambas as execuções têm exatamente 1.023.811 visitas
de resolução. O heap maior não reduz esse trabalho; reduz o custo de realizá-lo
sob pressão de GC. O contraste tem execução única e também pode sofrer variação
de JIT/agendamento; contadores e logs sustentam a identificação da pressão.

No caso de 72, depois de 2.245.263 visitas, os 10.227 contextos e 2.208.755
estados BEFORE deixam de ser construídos pelo laço de valores. A resolução
continua por um span observado de 9,77 s, fazendo 1.461.961 visitas no total.
Esse contador identifica uma estrutura diferente da contagem de visitas e da
quantidade de chaves de resolução.

A resolução usa outro grafo/cache: pares de contexto e termo, dependências de
entradas e propagações de resultados. Os probes mostram suas chaves e visitas
aumentando depois do laço de valores. Não é correto inferir falta de progresso
apenas porque o número de contextos ou o RSS quase não muda nesse intervalo.

No JFR de 64 com heap 1,5 GiB, 108/394 amostras main estão em resolve; 66/394
passam por workRank. Em 72, são 113/538 em resolve e 115/538 em workRank.
O controle tem somente três amostras em cada gravação. As amostras localizam
custo Java; não equivalem a percentuais do tempo de parede ou de CPU total.

DependencyFlow.resolve acontece **dentro de DependencyFlow.analyze**. Seu tempo
entra em dataflowNanos do sidecar. O campo resolutionNanos de DependencyAnalyzer
mede outra etapa de montagem de resultados e não deve ser usado para concluir
que esta resolução levou poucos milissegundos.

## Conclusão e limites

Mais heap pode remover uma falha imediata e expor milhões de operações. Também
pode prolongar um caso até OOM: a memória parece estável porque a JVM já ocupou
quase todo o heap. Nos testemunhos concluídos ainda existe custo de propagação,
comparação da fila e resolução de consultas; eles terminaram em 4,65–32,73 s.

Não há reprodução de 40 minutos sem OOM nem evidência para atribuir um desses
mecanismos ao fonte privado. Um RSS relatado como estável precisa ser confrontado
com ocupação do heap após GC, pausas/concurrent GC, amostras internas da pilha e
contadores de progresso nas fases. Quantidade de linhas ou RSS sozinho não
mede as combinações de entrada nem o trabalho pendente.

## Reprodução e preservação

```sh
python3 -B benchmark/run-hub-dispatch.py OUTPUT --mode hub-perform-flags --boxes 12 14 \
  --heap 1024 --max-work 100000000 --rss 1408 --system-reserve 2048 \
  --telemetry --profile --profile-duration 120 --timeout 150
```

Use JDK 21 em PATH. Para os casos de corpo comum, use --mode perform-flags e os
tamanhos da tabela; os comandos exatos com overlay constam no JSON. A fonte das
cópias diagnósticas e suas classes ficam no arquivo bruto local; não são
caminhos adicionais do produto. O [leitor JFR](profiling/JfrPhaseStats.java)
funciona em streaming; compile com javac e execute em JVM com -Xmx128m.

[JSON de evidência](hub-large-heap-20261009.json) preserva comandos normalizados,
hashes, sete resultados, telemetria, marcadores, perfis resumidos e controles do
runner. Os outputs brutos e perfis permanecem em
benchmark/results/hub-dispatch-20261009/large-heap-*; as evidências anteriores
não foram alteradas. Os cinco resultados concluídos têm oráculos exatos. As
duas falhas por OOM continuam explícitas, sem métricas de sucesso inventadas.

Foram exercitados os caminhos de término normal, timeout, RSS e reserva do
host do runner. Uma primeira checagem de CPU em um processo muito curto ficou
sem amostra positiva; foi repetida com trabalho suficientemente longo para o
intervalo de coleta. CPU observada é amostrada, não a medição final completa.
Produção/JAR permanecem idênticos; FAST/CardDemo não foram rerodados nesta
investigação de fixtures e harness.
