# Ablação provisória da reinvocação de escape

Desligar a reinvocação permite medir os custos encobertos por ela, mas muda a
semântica. A sentinela abaixo perde uma dependência com a ablação ativa. Nenhum
fonte de produção ou JAR foi alterado. O próximo gargalo da execução corporativa
continua **não medido**; os resultados são locais e diagnósticos.

## Instrumentação e proteção

O [construtor de overlay](profiling/build-control-probe.py) agora aceita
`--flow-phases`, compilando classes instrumentadas em um diretório separado.
O [transformador](profiling/flow-phase-probe.py) exige uma única ocorrência de
cada âncora e acrescenta tempos por fase, progresso de valores/resolução e
contadores de relevância. A reinvocação fica ativa por padrão;
`-Dprobe.noescape=true` a desliga somente no overlay.

A ablação suprime o fallback de `continuationPlan` para um ESCAPE de chamada
com binding, fora da fronteira esperada e depois das verificações de ancestrais.
Ele deixa de criar a chamada interna ao sucessor normal da fronteira. ESCAPEs
de entradas independentes e retornos compatíveis conservam suas regras.
Como o plano é compartilhado, a ablação afeta controle, relevância e entrega
de valores. Portanto, não isola apenas a memória de Returned.
`suppressedEscapes` conta construções de planos suprimidas, não todas as entregas
de valores evitadas. O overlay-v2 apenas separa as fases `cycles` e
`forward-order`, antes agrupadas como `cycles-and-order`.

Baseline main: `87255b08d38456efbc3caf3bd6082f03dee82362`; HEAD ao iniciar:
`cf21e7dc80a8868373118598df3e2ae410ceed02`. JAR SHA-256:
`b7cc0654207091e1872071a7a3267547daa170ce9b1fb1026702c99e69fa5ab1`.
Manifests registram hashes dos fontes originais/instrumentados e a compilação.

As 11 execuções da matriz usaram Temurin 21.0.12.1+1, heap 256 MiB, max-work
100.000.000, RSS máximo 512 MiB, reserva do host 2.048 MiB e timeout 50 s.
Rodaram sequencialmente; nenhuma proteção externa foi acionada. Menor memória
disponível registrada: 4.255,6 MiB. As três execuções da sentinela usaram heap
128 MiB e RSS máximo 384 MiB. Compiladores/leitores JFR usaram até 128 MiB.
Não houve aumento de heap.

Casos instrumentados têm JFR profile, janela de até 45 s, retenção configurada
de 24 MiB, GC log e telemetria a cada segundo. Tempos são observações únicas,
incluem instrumentação e não são medianas ou projeções para o fonte privado.
Dados resumidos: [JSON](escape-ablation-20261009.json). Logs, perfis, comandos,
fontes gerados, saídas e manifests ficam em
`benchmark/results/escape-ablation-20261009/` (ignorado pelo Git).

## O trabalho encoberto no caso pequeno

São os casos `return-fanout` do [gerador](generate-hub-dispatch.py): dois hubs
renovam seletores e flag por ACCEPT; cada caixinha tem CALL literal, EXIT PARAGRAPH
e PERFORM condicionais e GO TO para o outro hub. N significa caixinhas **por
hub**. Não há OCCURS; todos os casos concluídos têm uma passagem de demanda.

| N | Reinvocação | Tempo total | Visitas de valores | Entregas de valores | Contextos |
| ---: | --- | ---: | ---: | ---: | ---: |
| 8 | ativa, overlay | 3,99 s | 959.355 | 933.926 | 260 |
| 8 | desligada | 1,47 s | 12.405 | 9.026 | 50 |
| 12 | ativa, overlay | 20,62 s | 7.439.211 | 7.354.422 | 580 |
| 12 | desligada | 1,92 s | 36.845 | 29.474 | 74 |

Em N=12, a fase de valores cai de 18,62 s para 0,26 s. A relevância inicial
leva 0,13 s / 0,07 s; a resolução leva 0,10 s / 0,03 s. Esses dados não apontam
relevância ou resolução como o custo dominante nesse tamanho. No perfil ativo,
654 das 1.460 amostras da thread main têm `workRank` na pilha: a fila de valores
reordena enorme quantidade de trabalho. Isso não explica, por si só, os 70
minutos privados.

O original, sem probes/JFR, levou 3,79 s e 19,36 s. Seus JSONs são byte a byte
iguais aos do overlay ativo e suas métricas de trabalho coincidem, exceto uma
visita de controle em N=12. A ordem de HashSets pode variar entre JVMs.

## O custo restante: conjuntos de saídas por ponto e fronteira

Em N=12, os pares Returned entregues caem de 332.426 para 15.601. Entretanto,
os planos continuam em 8.751, os resultados em 173.150 entradas e as uniões
continuam examinando 398.661 membros. Esses três contadores coincidem entre
as versões ativa e ablada desse caso.

Mesmo sem criar novas chamadas de escape, cada ponto sob cada fronteira conserva
as saídas possíveis. Se muitos pontos alcançam as mesmas fronteiras, cada
conjunto armazena entradas para essas saídas. O objeto Exit pode ser compartilhado;
a entrada do HashSet/HashMap fica armazenada para cada relação. Quando um
sucessor cresce, o laço de DependencyControl une novamente seus resultados
completos. Remover reinvocação reduz a relação chamada/saída, mas não remove
essa relação ponto/saída nem as uniões repetidas.

| N, ablação ativa | Planos | Pares entregues | Entradas nos resultados | Membros examinados nas uniões |
| ---: | ---: | ---: | ---: | ---: |
| 8 | 4.047 | 4.897 | 54.638 | 128.545 |
| 12 | 8.751 | 15.601 | 173.150 | 398.661 |
| 16 | 15.247 | 35.905 | 397.518 | 904.841 |
| 24 | 33.615 | 117.601 | 1.298.990 | 2.921.841 |
| 28 | 45.487 | 185.137 | 2.043.678 | 4.580.885 |
| 32 | 59.151 | 274.561 | 3.029.390 | 6.772.441 |

O crescimento é compatível com pontos × fronteiras × saídas nesta família:
planos crescem aproximadamente quadraticamente e resultados, aproximadamente
cubicamente. Isso não é uma lei para qualquer programa COBOL.

Em N=28, a execução ablada termina em 5,76 s. Dentro da análise: controle 1,75 s,
ciclos 0,36 s, ordem de fluxo 0,17 s, assinatura inicial/relevância 0,50 s,
valores 1,59 s e resolução 0,12 s. O custo divide-se principalmente entre
controle e valores; não há um novo gargalo universal confirmado.

Em N=32, o controle fecha o ponto fixo com no máximo **65 saídas por conjunto**,
mas 3,03 milhões de entradas no total. O heap utilizado amostrado nesse ponto
é cerca de 239 MiB, incluindo lixo ainda não coletado. A fase `cycles` inicia
e ocorre OOM antes de seu fim. Não chega à relevância nem aos valores. O primeiro
overlay falhou no agrupamento ciclos/ordem; o segundo confirmou `cyclicNodes()`.

Esse método faz postorder, monta o grafo reverso, identifica componentes e
propaga os efeitos chamados a partir dos ciclos. O grafo e os conjuntos de
controle anteriores continuam vivos. A falha local ocorre quando o trabalho
adicional precisa de espaço em um heap já pressionado; **não demonstra que
componentes estejam enumerando sequências combinatórias**. Na repetição N=32
há 33 Full GCs e 2,76 s de pausas; 98/104 amostras JFR da main têm métodos de
DependencyControl na pilha. Amostras de alocação representam volume acumulado
estimado, não heap retido.

## Por que a ablação não pode virar correção

Na [sentinela](fixtures/control-return-fanout/escape-ablation-sentinel.cbl), MAIN
faz PERFORM A. A pode retornar normalmente ou fazer GO TO B. B sai por EXIT
PARAGRAPH, mas a fronteira aguardada ainda é A. A continuação normal de B é C,
que tem CALL 'ESCAPED' e volta para A. A reinvocação permite visitar C ainda
esperando o retorno de A. A ablação elimina esse caminho.

| Execução | Candidatos |
| --- | --- |
| JAR original | AFTER, ESCAPED |
| Overlay, reinvocação ativa | AFTER, ESCAPED |
| Overlay, reinvocação desligada | AFTER |

Os dois primeiros JSONs são byte a byte iguais. Os hubs ablados concluídos
conservam seus nomes esperados, mas isso não qualifica a semântica: a sentinela
prova uma perda. O wrapper marca `productionQualified=false` com `--noescape`,
inclusive se o oráculo de nomes coincidir, e mostra mensagens de diagnóstico.

## Aplicação na execução privada

Use fontes correspondentes ao JAR privado para construir o overlay. Se as âncoras
falharem, revise a instrumentação para essa versão. As propriedades diagnósticas
não estão disponíveis no JAR de produção sozinho. Execute com uma saída separada,
conservando heap e limites existentes:

```sh
python3 -B benchmark/profiling/build-control-probe.py /tmp/escape-probe \
  --flow-phases --jdk /caminho/do/jdk --jar target/cobol-dependency-analyzer.jar

java -Xmx16g -Dprobe.noescape=true \
  -cp /tmp/escape-probe/classes:target/cobol-dependency-analyzer.jar \
  com.imd.cobolexplorer.DependencyMain --source /caminho/FONTE.cbl \
  --output /tmp/dependencias-diagnosticas.json --max-work 100000000 \
  2>/tmp/escape-probe/progresso.log
```

O exemplo mantém os 16 GiB informados para a máquina corporativa; esses limites
**não foram usados nesta máquina**. Observe a última phase-start sem phase-end
e os contadores periódicos. Controle mostra planos, pares e resultados;
relevância mostra nós, arestas, extensões e uniões; valores mostram contextos,
fila, visitas e entregas; resolução mostra chaves e visitas. Use o
[coletor de JVM](running-jvm-diagnostics.md) para obter pilhas/JFR da fase demorada,
inclusive em GC quando os probes não avançarem. Repita com reinvocação ativa
para conferir se o custo também ocorre no caminho canônico.

## Validação e limites

Verificados: compilação dos dois overlays, sintaxe dos scripts, rejeição de
âncoras alteradas, rejeição de `--noescape` sem overlay antes de criar diretório,
propagação de falha pelo wrapper (matrizes com OOM e caso max-work=1 retornam
código 1; o analisador retorna código 2), oráculos
de candidatos e contraste de perda na sentinela. O JAR manteve seu hash.
As duas falhas OOM são evidências preservadas, não PASS.

FAST/CardDemo não foram executados nesta etapa: não houve mudança no caminho
de produção nem alegação de paridade do overlay ablado. Para identificar o
próximo gargalo privado faltam tempos de fase, pilhas e contadores dessa execução.
Nenhuma correção, publicação ou merge foi feito.
