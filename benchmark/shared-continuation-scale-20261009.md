# Escala ampliada: OOM na implementação de continuações compartilhadas

Data: 2026-10-09. Implementação: `e1a4c2682d72066871431e87d39376966ce19219`.
JAR SHA-256: `6bf93c8e362eebd1301a5a289402eb9e11452fadf538358e34f5024d2483d943`.
Nenhuma alteração de produção, ablação ou hack de convergência nesta rodada.
Os [dados e hashes](shared-continuation-scale-20261009.json) incluem as falhas.

**Aumentar a mesma família reintroduz OOM, inclusive com heap de 1,5 GiB.**
Esse resultado limita a qualificação anterior de N=32/36. Não invalida suas
dependências ou a redução do multiplicador de reinvocação; revela que a
representação do grafo ainda não suporta toda a escala testada.

## Experimento

Mesma família `return-fanout`, dois hubs, seletores e flag renovados por ACCEPT,
PERFORMs cruzados, EXIT PARAGRAPH e término por GOBACK. N é o número de
caixinhas **por hub**. Os nomes de CALL são repetidos nos dois hubs, portanto
o oráculo contém N nomes distintos, apesar de haver 2*N caixinhas.

Uma JVM de análise por vez, Temurin 21.0.12+1.1, `--max-work 100000000`, reserva
mínima de 2.048 MiB para o host, metaspace limitado a 128 MiB e memória direta a
32 MiB. A máquina tinha aproximadamente 4,5 GiB disponíveis e swap ocupada.
Não usamos heap de 16 GiB; o maior heap desta investigação foi 1.536 MiB.

| Heap | N / caixinhas totais | LOC | Resultado | Tempo externo | Pico RSS |
| --- | --- | ---: | --- | ---: | ---: |
| 256 MiB | 48 / 96 | 916 | PASS exato | 5,86 s | 418,5 MiB |
| 256 MiB | 64 / 128 | 1.212 | OOM | 6,06 s | 408,0 MiB |
| 256 MiB | 96 / 192 | 1.804 | OOM | 3,14 s | 396,9 MiB |
| 1.024 MiB | 64 / 128 | 1.212 | PASS exato | 10,20 s | 706,3 MiB |
| 1.024 MiB | 96 / 192 | 1.804 | PASS exato | 26,47 s | 1.248,3 MiB |
| 1.024 MiB | 128 / 256 | 2.396 | OOM | 16,98 s | 1.209,1 MiB |
| 1.024 MiB | 192 / 384 | 3.580 | OOM | 9,95 s | 1.206,9 MiB |
| 1.536 MiB | 128 / 256 | 2.396 | PASS exato | 96,96 s | 1.826,2 MiB |
| 1.536 MiB | 192 / 384 | 3.580 | OOM | 12,76 s | 1.747,9 MiB |
| 1.536 MiB | 254 / 508 | 4.726 | OOM | 14,11 s | 1.754,2 MiB |

Todos os dez ensaios usam o CLI original, com JFR e log de GC. São execuções
únicas, sem estimativa de confiança estatística. Os quatro casos concluídos
preservaram exatamente os nomes esperados e terminaram com código 0. Os seis
OOMs terminaram com código 2 e `OutOfMemoryError: Java heap space`.

**Nenhuma proteção encerrou esses processos:** zero RSS_GUARD,
SYSTEM_MEMORY_GUARD, TIME_GUARD e zero RESOURCE_LIMIT. O menor valor de memória
disponível do host amostrado foi 2.588 MiB. Os tetos RSS foram 640 / 1.408 /
1.920 MiB, respectivamente. Timeout de 90 s no heap de 256 MiB e de 120 s nos
demais. JFR foi limitado a 24 MiB, com duração de 60 / 90 s.

## Localização das falhas, além do diagnóstico genérico de heap

As últimas amostras do JFR em N=128 com 1 GiB estão na cópia dos planos do
controle. Para localizar a alocação que falha, duas execuções adicionais usam
uma cópia isolada do CLI cujo único delta é `error.printStackTrace()` no catch
existente. Todos os bytecodes de análise e solver permanecem no mesmo JAR.
Essa sonda não usa JFR e não contém intervenções semânticas.

**N=128, heap de 1 GiB: OOM em 13,08 s, sem proteção acionada.**

```text
HashMap.resize
HashMap.putVal
HashMap.put
DependencyControl.lambda$new$0              linha 32
DependencyControl.<init>                    linha 32
DependencyFlow.<init>                       linha 586
DependencyFlow.analyze                      linha 528
```

A linha 32 monta o mapa final de planos a partir dos planos temporários. A
construção ainda mantém resultados, assinantes, mapas e conjuntos de arestas;
a representação final começa a acumular suas próprias entradas e listas.
O pico ocorre enquanto as duas representações coexistem. Descartar os dados
temporários no fim do construtor não evita esse pico intermediário.

**N=192, heap de 1,5 GiB: OOM em 13,19 s, sem proteção acionada.**

```text
HashMap.resize
HashMap.computeIfAbsent
DependencyControl$Construction.parent       linha 177
DependencyControl$Construction.add          linha 181
DependencyControl$Construction.solve        linha 144
DependencyControl.<init>                    linha 29
DependencyFlow.<init>                       linha 586
```

Aqui a falha acontece antes: na expansão do índice `parents`, que relaciona
cada ponto aos predecessores usados pela observação. O solver ainda está
construindo o controle; não iniciou a propagação de estados de valores.
A pilha identifica a alocação que não coube, não a participação de cada estrutura
no heap total. Não coletamos heap dump ou histograma de retenção nesta rodada.

As repetições sem JFR confirmam que o profiler não é necessário para provocar
as duas falhas. Ambas também ocorreram no CLI original instrumentado somente
com JFR; a impressão da pilha não é a origem do problema.

O log original de N=128 / 1 GiB registra 22 Full GCs e termina com uma
compactação de `1022M -> 1022M`. Em N=192 / 1,5 GiB há nove Full GCs; uma das
últimas compactações deixa `1532M -> 1519M`. O GC não encontra espaço suficiente
antes da falha. A redução de heap após desenrolar o erro em outras execuções
não significa que aquele heap estava disponível durante a construção.

## Crescimento observado e tempo mesmo com memória suficiente

Nos casos concluídos:

| N | Contextos de valores | Pontos de controle | Pares chamada/saída | Trabalho de valores |
| --- | ---: | ---: | ---: | ---: |
| 48 | 195 | 131.631 | 18.625 | 75.567 |
| 64 | 259 | 232.847 | 33.025 | 133.519 |
| 96 | 387 | 521.295 | 74.113 | 298.575 |
| 128 | 515 | 924.431 | 131.585 | 529.167 |

São somente três declarações rastreadas e uma passagem de demanda nesses casos.
O índice de controle combina posição, endpoint e política de escape. O mesmo
trecho pode precisar de pontos distintos porque atingir uma fronteira retorna
sob um endpoint e continua sob outro. Nesta família, os pontos concluídos seguem
`56*N² + 54*N + 15`, e os pares `8*N² + 4*N + 1`. Os contextos seguem `4*N + 3`.
São padrões observados, não garantias para todo fonte COBOL. Não atribuimos
contadores finais às execuções que falharam antes de publicá-los.

Assim, eliminar o multiplicador de reinvocação não elimina o produto estrutural
posição/endpoint nem o custo dos objetos que o representam. Uma fixture de
2.396 LOC chega a quase um milhão de pontos; contar LOC sozinho não dimensiona
essa análise. N=192 falha antes mesmo de começar o fluxo de valores, portanto
este OOM não exige enumerar mundos de entrada nem um OCCURS grande.

N=128 / 1,5 GiB também revela trabalho de CPU depois de atravessar as fases de
memória. Das 7.752 amostras da main no JFR, 3.512 são de `workRank` na fila de
valores, 2.355 de controle e 1.748 de relevância. `Exit.hashCode` aparece como
primeiro método do produto em 3.476 amostras. Isto localiza trabalho de cálculo
e consulta de prioridade; não demonstra por si só patologia de hash ou sua causa.
O JFR dura 90 s e não cobre integralmente os 96,96 s externos. As pausas de GC
somam 2,78 s nesse caso; não atribuímos seus 97 s somente a GC thrash.

## Alcance e próximos alvos de investigação

Esta rodada sustenta a hipótese de que a escala da fixture anterior era
insuficiente para mostrar as paredes restantes da nova implementação. Ela
reproduz OOM em estruturas concretas do controle mesmo com um heap seis vezes
maior que o anterior. Não estabelece que a nova parede corporativa tem a mesma
pilha, nem reproduz sua escala de 16 GiB ou sua duração de 70 minutos.

Os alvos concretos agora são a cardinalidade e representação dos pontos/arestas
por endpoint, o índice de predecessores e o pico ao congelar o grafo. Há também
trabalho mensurável no cálculo repetido de prioridade. Compactar pares e remover
reinvocações foi uma melhora medida, mas não resolve essas paredes de escala.
Nenhuma correção adicional foi aplicada aqui.

## Reprodução e validação

```sh
python3 -B benchmark/run-hub-dispatch.py OUTPUT \
  --jar benchmark/results/shared-continuation-fix-20261009/final.jar \
  --mode return-fanout --boxes 128 192 254 --heap 1536 --rss 1920 \
  --system-reserve 2048 --max-work 100000000 --timeout 120 \
  --profile --profile-duration 90 --telemetry
```

Não execute essa linha se o host não tiver folga para o heap e a reserva. As
proteções encerram o processo de teste quando necessário, e esse encerramento
deve ser registrado como proteção, não como OOM do Java.

Fontes gerados, logs, telemetria, JFRs, pilhas, snapshot de CLI e manifest da
sonda ficam em `benchmark/results/shared-continuation-scale-20261009/`, ignorado
pelo Git. Os hashes estão no JSON versionado. A sonda altera apenas uma cópia
do CLI nesse diretório, nunca o fonte de produção.

Não repetimos FAST nem CardDemo: o JAR e os bytecodes do solver são os mesmos
da [qualificação anterior](shared-continuation-fix-20261009.md), que registrou
1.084 testes e 815 relações iguais no corpus. Essa evidência semântica é reutilizada,
não apresentada como uma execução nova. Esta rodada adiciona dez ensaios de
escala e duas coletas de pilha; não amplia a alegação de capacidade do produto
para além dos casos que efetivamente concluíram.
