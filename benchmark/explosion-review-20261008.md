# Auditoria de explosão — 8/10/2026

**A rodada com recursos ampliados passou integralmente:** 30/30 casos na
matriz ampliada e 20/20 resultados esperados nos insumos importados. Os dois
casos que falhavam na configuração menor concluíram corretamente ao aumentar
heap e orçamento, sem mudar o algoritmo ou relaxar os destinos esperados.

JAR publicado, produção `f70a94b`, checkout `69c2aa8`; hash e resultados em
[explosion-review-20261008.json](explosion-review-20261008.json).
Não houve alteração de produção. Execuções sequenciais, JVM nova por caso,
Temurin 21.0.12+1 e `-Xms32m`. A rodada final usou **heap de 4 GiB, orçamento
de 100 milhões e timeout externo de 300 s**. A rodada inicial usou 512 MiB,
um milhão de visitas e timeout de 45 s na matriz ampliada. São amostras únicas.
RSS inclui memória nativa e pode superar o heap configurado.

## Rodada final com heap grande

Nenhum caso válido teve OOM, timeout ou falha de orçamento. Os dois negativos
de sintaxe do conjunto importado continuaram rejeitados como esperado.
A matriz ampliada levou 190,87 s; o conjunto importado, 16,21 s. Maior RSS
da rodada: 3.043,97 MiB, no overlap1800 dinâmico.

| Caso | Tempo | RSS | Trabalho / resultado |
|---|---:|---:|---|
| AND1216 | 24,31 s | 910,1 MiB | 2.221.544 visitas; três destinos corretos |
| OR1216 | 11,18 s | 804,5 MiB | 1.485.530 visitas; três destinos corretos |
| Overlap900 + CALL WS-TEXT | 9,63 s | 1.116,1 MiB | 2.469.724 visitas; H |
| Overlap1800 + CALL WS-TEXT | 44,91 s | 3.044,0 MiB | 9.800.224 visitas; H |
| 6.400 chamadas inalcançáveis | 2,87 s | 373,9 MiB | duas visitas; somente SYNPROG |
| 117 mil atribuições + CALL dinâmico | 37,37 s | 1.957,8 MiB | 117.002 visitas; PROGA |

Os 25 casos aprovados anteriormente produziram JSONs byte-idênticos na nova
configuração. O resultado de 117 mil também ficou idêntico à repetição com
768 MiB. A ampliação dos limites não substituiu candidatos por unknown nem
cortou resultados. Os valores finais foram comparados independentemente.

Heap maior pode elevar o RSS observado, pois permite reter mais alocações
entre coletas. Os 4 GiB configurados são um teto de heap, não memória fixa
reservada ou consumida por cada caso. O crescimento de trabalho aproximadamente
quadrático das faixas ainda existe: a configuração maior viabiliza os exemplos,
mas não representa uma otimização desse crescimento.

## Fixtures originais

Os 20 casos de `resource-stress` foram reexecutados, verificando seus hashes
contra os arquivos originais: 18 válidos e dois negativos de sintaxe, 20/20
resultados esperados, 16,91 s no total e maior RSS de 253,01 MiB. Nenhum OOM
ou timeout. Os destinos conhecidos foram comparados, sem eliminar adicionais.

Sete execuções adicionais cobriram os originais de OOM plain09/escape08/CICS09,
a cadeia de 10 mil parágrafos vazios, inline20, o controle AND76 e overlap900
compacto. Todas passaram. As 27 execuções originais representam 24 hashes
distintos, pois alguns insumos se repetem entre campanhas.

**Escopo importante:** originais sem CALL/CICS dependente publicam os fatos de
fonte e não executam dataflow. Na primeira matriz, somente seis casos tinham
workItems positivos. Isso valida a eliminação de trabalho irrelevante, mas
não prova a resistência do solver. Por isso acrescentamos consultas dinâmicas
a variantes explicitamente identificadas e curvas maiores das famílias AND/OR.

## Rodada inicial com recursos menores

27 execuções: **25 PASS, 1 RESOURCE_LIMIT e 1 OOM**. Dos PASS, 21 exercitam o
dataflow. As duas falhas são resultados incompletos, não aprovações.

| Caso | Heap | Tempo | RSS | Resultado |
|---|---:|---:|---:|---|
| CICS09 original | 512 MiB | 0,77 s | 134,0 MiB | PASS, DUMMY |
| 10 mil parágrafos vazios, original | 512 MiB | 1,72 s | 223,4 MiB | PASS, AFTER/BODY |
| AND608 | 512 MiB | 5,57 s | 335,3 MiB | PASS, três destinos |
| OR608 | 512 MiB | 3,82 s | 316,2 MiB | PASS, três destinos |
| 6.400 chamadas inalcançáveis | 512 MiB | 3,12 s | 276,0 MiB | PASS, somente SYNPROG |
| Overlap400 + CALL WS-TEXT | 512 MiB | 3,22 s | 350,6 MiB | PASS, H |
| Overlap900 + CALL WS-TEXT | 512 MiB | 5,32 s | 515,1 MiB | RESOURCE_LIMIT, código 2 |
| 64 mil atribuições + CALL dinâmico | 512 MiB | 14,44 s | 645,2 MiB | PASS, PROGA |
| 117 mil atribuições + CALL dinâmico | 512 MiB | 9,19 s | 636,2 MiB | OOM, código 2 |
| Mesmo caso de 117 mil, repetido | 768 MiB | 38,18 s | 934,8 MiB | PASS, PROGA |

Os casos AND/OR maiores reutilizam o gerador da investigação original. As
variantes plain/escape observam WS-OBS após seu MOVE; overlap observa WS-TEXT
após os PERFORMs. A hipótese esperada é independente da nova saída: A para
WS-OBS e H para o valor final de WS-TEXT. Fontes derivados, mudanças e hashes
estão identificados no manifesto bruto; não são apresentados como originais.

O caso de 6.400 chamadas é uma contraparte COBOL do mecanismo de frames mortos
da investigação de 6/10, cujo original existe somente em AIR. Não é o mesmo
input e não constitui uma comparação de tempo entre consumidores equivalentes.

## Interpretação dos limites da rodada inicial

**Faixas sobrepostas ainda têm custo aproximadamente quadrático nesta família.**
AND152/304/608 processaram 34.492/138.992/555.928 work items, respectivamente.
Compartilhar o corpo impede duplicação do código, mas faixas com entradas
distintas ainda exigem processar os mesmos trechos em diferentes contextos.
Overlap900 dinâmico atingiu o limite padrão de 1.000.000 visitas. Não houve
OOM nesse caso; a CLI falhou sem publicar JSON. Uma repetição com JSON anterior
confirmou sua preservação. Com o orçamento aumentado, a análise concluiu;
o limite menor não era uma incapacidade semântica do algoritmo.

**O frontend ainda pode esgotar o heap em fontes grandes.** O probe diagnóstico
reproduziu o OOM de 117 mil em `DirectLedger.nodes → DirectCobolParser.parse`,
antes do dataflow. A stack identifica onde a alocação falhou, sem atribuir todo
o consumo a uma única classe. Com 768 MiB, o mesmo input publicou PROGA e
teve 117.002 work items, um contexto e uma declaração acompanhada. Tempos
internos: parsing 8,54 s, binding 1,93 s, preparação de controle/índices 26,52 s
e dataflow 0,81 s. Aumentar o heap viabilizou esse input; não resolve o custo
da preparação nem estabelece uma garantia para outros programas. Com 4 GiB,
o mesmo caso concluiu novamente, com os tempos e memória da tabela final.

O programa corporativo original de aproximadamente 117 mil linhas não está
disponível no workspace. O sintético tem 117.008 linhas e 117 mil atribuições,
sem reproduzir toda sua complexidade. As fixtures Java/AIR de memória física
também não são entradas COBOL do monolito e não foram contadas como aprovadas.

## Reprodução e evidência

```sh
python3 -B benchmark/run-resource.py benchmark/results/nova-label-original20
python3 -B benchmark/run-explosion-review.py benchmark/results/nova-label-expanded
python3 -B benchmark/run-explosion-review.py benchmark/results/nova-label-768m --heap 768 --case linear-statements117000
python3 -B benchmark/run-resource.py benchmark/results/nova-label-original20-largeheap --heap 4096 --max-work 100000000 --timeout 300
python3 -B benchmark/run-explosion-review.py benchmark/results/nova-label-largeheap --heap 4096 --max-work 100000000 --timeout 300 --extended
```

O runner ampliado requer os geradores/fixtures externos da investigação local,
somente para leitura. Saída deve usar uma label nova. Exit 1 do runner significa
que pelo menos um caso falhou; ele continua para registrar a matriz completa.
Comandos, fontes gerados, métricas, logs, stacks e saídas ficam em
`results/explosion-review-20261008-original20/` e
`results/explosion-review-20261008-expanded/`, preservados e ignorados pelo Git.
As execuções finais ficam em `results/explosion-review-20261008-largeheap/`
e `results/explosion-review-20261008-original20-largeheap/`.
O relatório JSON registra seus hashes e resultados individuais.

CardDemo, FAST e suíte completa anteriores permanecem como evidência reutilizada:
o hash do JAR é o mesmo. Não foram reexecutados para esta auditoria de recursos.
