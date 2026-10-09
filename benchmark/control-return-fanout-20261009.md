# Testemunho COBOL de acúmulo de pares de chamada e saída

Foi reproduzido acúmulo de `DependencyControl.Returned`, com contextos de valores
constantes, e falha no JAR original durante as mesmas linhas do solver apontadas
na evidência corporativa. O testemunho de 24 caixinhas por hub tem heap 256 MiB,
GC repetido próximo da capacidade e OOM em 5,37 s. Isso reproduz o mecanismo em
escala reduzida; **não reproduz 70 minutos sem OOM nem prova a topologia do fonte
privado**. Nenhuma correção de produção foi aplicada.

## Pins e proteção

Baseline de produção main `87255b08d38456efbc3caf3bd6082f03dee82362`;
HEAD da investigação ao iniciar `a205ebb`; JAR SHA-256
`b7cc0654207091e1872071a7a3267547daa170ce9b1fb1026702c99e69fa5ab1`.
Temurin 21.0.12.1+1. Todas as 25 execuções usaram heap de 256 MiB,
max-work 100.000.000, proteção RSS de 512 MiB e reserva do host de 2.048 MiB.
Menor memória disponível observada: 3.788,6 MiB. Nenhum corte de memória foi
acionado. As JVMs de análise rodaram sequencialmente; os leitores JFR usam
128 MiB e compiladores até 128 MiB.

JFR profile com janela máxima de 40/45 s e retenção configurada de 24 MiB;
telemetria de RSS/CPU a cada segundo. Tempos são execuções únicas com profiling,
não medianas nem projeções para o programa corporativo. A instrumentação adiciona
contadores e prints; seus tempos não são baseline de desempenho do produto.

## Construção que provoca o crescimento

Os [fontes e oráculos](fixtures/control-return-fanout/README.md) vêm do
[gerador de hubs](generate-hub-dispatch.py). Há dois hubs, seletor renovado por
ACCEPT e GO TO DEPENDING ON para todas as caixinhas ou FINAL-BOX. Cada caixinha
chama um programa literal, tem EXIT PARAGRAPH condicional, PERFORM condicional
da próxima caixinha do outro hub, e GO TO para o outro hub. FINAL-BOX tem GOBACK.

O resumo estrutural acompanha essas alternativas sem usar o valor da condição.
Uma saída ESCAPE fora da fronteira esperada do PERFORM passa por
`DependencyFlow.continuationPlan`: gera uma chamada interna ao sucessor normal
da fronteira, mantendo o binding de retorno. Portanto, a lista de chamadas do
plano pode crescer além dos PERFORMs escritos naquele ponto COBOL.

Essas chamadas internas reencontram a rede com muitas saídas possíveis. O solver
guarda uma relação para cada combinação de chamada e saída, em cada plano
distinguido por ponto/fronteira. Com 16 caixinhas por hub, os planos mais pesados
têm 32 chamadas, 1.024 pares entregues e 33 saídas no resultado. O produto
32 chamadas × 32 saídas se repete em muitos planos. A saída adicional no resultado
não significa que cada chamada tenha 33 saídas.

Quando um ponto é reagendado, os conjuntos completos são percorridos novamente.
O controle de duplicação impede nova entrega de um par já conhecido, mas a
consulta ao HashSet e a construção de Returned ainda ocorrem. A união das saídas
dos sucessores também é repetida. A instrumentação conta tanto pares retidos
quanto tentativas totais e membros examinados nas uniões.

## Isolamento do controle

O modo return-fanout-fixed faz MOVE 0 TO EXTERNAL-FLAG no MAIN e não renova essa
flag. Os GO TOs continuam dinâmicos. A estrutura de controle ainda contém as
alternativas; a fase de valores elimina as condições falsas e fica com três
contextos. Assim, o crescimento medido abaixo não depende de vetores combinatórios
de dados. Cada caso concluído tem tableDemandPasses=1 e nenhum elemento de tabela.

| Caixinhas por hub | Planos de controle | Pares retidos | Tentativas de pares | Contextos de valores | Visitas de valores |
| ---: | ---: | ---: | ---: | ---: | ---: |
| 4 | 1.118 | 4.186 | 6.948 | 3 | 46 |
| 8 | 4.014 | 65.842 | 93.126 | 3 | 78 |
| 12 | 8.702 | 332.426 | 448.998 | 3 | 110 |
| 16 | 15.182 | 1.049.698 | 1.387.910 | 3 | 142 |
| 20 | 23.454 | 2.561.722 | 3.346.086 | não concluído | não publicado |

O controle chega ao ponto fixo em N=20, mas a execução instrumentada falha depois,
sem resultado publicado. Não atribuímos essa falha ao laço de propagação nem
concluímos o comportamento do JAR original nesse tamanho: ele não foi executado
em N=20. N=24, ao contrário, falha antes do ponto fixo e foi confirmado no original.

Nos cinco tamanhos com controle fechado, pares retidos coincidem exatamente com
`16*N^4 + 4*N^2 + 6*N + 2`; planos coincidem com `56*N^2 + 52*N + 14`, e chamadas
armazenadas com `8*N^3 + 2*N + 2`. Os produtos nos planos mais carregados sustentam
o mecanismo além do ajuste da curva. Essas fórmulas descrevem os tamanhos medidos
do testemunho, não todos os programas COBOL.

## Contrastes causais e semânticos

Na variante fixed-12, trocar somente EXIT PARAGRAPH por EXIT reduz pares retidos
de **332.426 para 601**, e entradas nos conjuntos de resultados de 171.925 para
8.077. Os 12 programas esperados continuam presentes. Isso isola as saídas
distintas; remover saídas de programas reais não é uma correção válida do produto.

A variante external-8 renova também a flag por ACCEPT e preserva todas as
alternativas: 65.842 pares, 260 contextos e todos os oito candidatos exatos.
Trocar o nome de um CALL nos dois hubs altera o conjunto exatamente para OTHER000
mais PGM00001..PGM00011, conservando 332.426 pares. O oráculo verifica identidade
dos candidatos, não apenas quantidade ou existência de saída.

As quatro execuções originais de N=4,8,12,16 e os três contrastes têm JSONs byte
a byte iguais aos correspondentes instrumentados. Contextos, visitas de valores
e cardinalidade dos planos coincidem. Visitas de controle variam em até uma entre
alguns pares de JVMs; os conjuntos são HashSets e sua ordem não é um contrato.

## Profiling do JAR original: N=24

O original termina com código 2 e `OutOfMemoryError: Java heap space`, em 5,37 s,
RSS máximo de 402,6 MiB. Não foi interrompido por RSS, tempo ou max-work. Não há
output de dependências nem métricas de sucesso. O GC registra 25 Full GCs,
2,08 s de pausas completas e 2,38 s de pausas totais. Nas compactações finais,
255 MiB caem apenas para 254 MiB de um heap de 256 MiB. Depois da falha, a última
coleta libera a maior parte do heap; essa amostra não representa o pico retido.

O JFR original contém 53 amostras Java de main: 48 com DependencyControl.<init>
na pilha, **42 nas linhas 44–48**. Destas, 17 estão na linha 45, 15 na 47 e oito
na 48. Portanto, não estamos inferindo a fase somente pelo wrapper analyze.
Amostras Java não representam a proporção total de CPU: threads de GC e pausas
precisam ser avaliadas pelos registros de GC.

As estimativas de alocação cumulativa de main incluem 229,9 MB de HashMap$Node
e 97,3 MB de Returned. Não são bytes retidos nem histograma de objetos vivos.
A cópia instrumentada de N=24 registra, antes da falha, 2.044.574 pares retidos,
1.294.237 entradas de resultados e 33.518 planos; sua fila ainda tem 2.102 pontos.
As 136.654 visitas desse snapshot estão muito abaixo do orçamento de 100 milhões.
Esse orçamento não limita diretamente cada par ou cada membro dos conjuntos.

## Reproduzir

Use JDK 21 no PATH. Diretórios de saída devem ser novos.

```sh
python3 -B benchmark/run-hub-dispatch.py OUTPUT --mode return-fanout-fixed --boxes 4 8 12 16 24 --heap 256 --rss 512 --system-reserve 2048 --max-work 100000000 --profile --profile-duration 45 --timeout 50 --telemetry
python3 -B benchmark/profiling/build-control-probe.py OVERLAY
python3 -B benchmark/run-hub-dispatch.py OUTPUT-PROBE --mode return-fanout-fixed --boxes 4 8 12 16 24 --heap 256 --rss 512 --system-reserve 2048 --max-work 100000000 --profile --profile-duration 45 --timeout 50 --telemetry --overlay OVERLAY/classes
```

O overlay compila somente DependencyControl a partir do fonte atual, com âncoras
verificadas. Acrescenta contadores, leituras de relógio e prints; preserva as
equações, transferências e regras de agendamento. Fonte, classes, hashes e comando
de compilação ficam na saída. As fontes de produção e o JAR não são alterados.

O [JSON de evidência](control-return-fanout-20261009.json) preserva 25 rodadas:
19 concluídas com candidatos exatos e seis incompletas. As incompletas incluem
um timeout de 45 s na fase de valores de uma tentativa anterior, dois OOMs
da rodada isolation, dois OOMs instrumentados canônicos e o OOM original de N=24.
Na tentativa anterior, VALUE 0 na declaração não fechava o remainder inicial;
o controle foi corrigido no fixture por MOVE explícito. Os resultados antigos
permanecem identificados como pilot/isolation; não foram substituídos por PASS.

Raw em `results/control-return-fanout-20261009/`: pilot, isolation, canonical-probe,
original, contrasts, overlay/overlay-v2, perfis, GC, telemetria e leitores JFR.
Não foi feito heap dump nem forçado GC completo para obter diagnóstico.

## Validação e limites

Novos checks: oráculos exatos em 19 execuções concluídas; sete comparações de
JSON original/overlay; seis hashes e fontes FIXED até 72 colunas; 48 combinações
de modos antigos do gerador byte a byte iguais ao HEAD; rejeição de âncora stale
do probe; propagação de falha do compilador e preservação de overlay existente;
compilação, sintaxe Python, links locais, JSON e whitespace.

FAST e CardDemo não foram reexecutados: esta alteração é C1/C5/C0 (fixtures,
instrumentação de benchmark e evidência), sem mudanças semânticas de produção
ou de contratos. A qualificação anterior permanece com os mesmos fontes e JAR;
ela não foi promovida a uma nova qualificação do programa privado.

O testemunho usa EXIT PARAGRAPH para forçar saídas distintas. A origem COBOL
dos pares no fonte privado continua pendente. Também não medimos suas passagens
de demanda. O testemunho já produz o gargalo em uma única passagem, sem OCCURS;
reconstrução do solver entre passagens não é necessária para esta reprodução.
