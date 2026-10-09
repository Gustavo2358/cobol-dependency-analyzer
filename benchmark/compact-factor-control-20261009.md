# Controle compacto e fluxo físico compartilhado

Data: 2026-10-09. PR #5, branch `fix/shared-control-continuations`.
Checkpoints: `313cd8e` (grafo canônico compacto), `b82ec51` (fatoração física).
[Contadores, comandos e hashes](compact-factor-control-20261009.json).

As duas etapas substituem a representação anterior no caminho canônico.
Não há modo especial para hubs, seletor por tamanho nem solver antigo de fallback.
Nenhum destino, candidato, obrigação de retorno ou escape é removido para convergir.

## O que mudou

A primeira etapa troca planos, mapas de predecessores e cópia final por um grafo
indexado com buffers primitivos e arestas tipadas. Construção, observação, ciclos,
ordenação e relevância usam a mesma topologia. Componentes e ordem são calculados
uma vez. A fila guarda a prioridade calculada na inclusão, evitando criar chaves
e consultar mapas de rank em cada comparação.

A segunda etapa guarda cada posição física e suas ligações uma vez. Uma obrigação
é o par correlacionado `(endpoint, escapeScope)`, não conjuntos independentes que
inventariam combinações. Ligações comuns preservam a obrigação; chamadas reais
instalam a obrigação do callee. Fronteiras decidem quais obrigações retornam e
quais continuam. Entradas independentes continuam separadas de chamadas que retornam.

```text
               uma ligação física
hub --------------------------------> caixa
  obrigações {A,B}                      obrigações {A,B}
                                              |
                                         fronteira A
                                         /         \
                                 retorna sob A   continua sob B
```

Combinações alcançadas de posição/obrigação recebem IDs esparsos. Suas arestas
lógicas são vistas calculadas da topologia compartilhada, sem listas clonadas.
Acesso reverso, observação, SCCs e relevância usam essa mesma interpretação.
Não se aloca o produto cartesiano completo para programas com escopos esparsos.

Saídas são colunas `(obrigação, saída)` de bitsets sobre posições físicas. A
worklist leva somente deltas. A descoberta agrupa novas obrigações por posição.
Uma ligação física de chamada entrega cada saída uma vez à continuação física;
esta conserva a obrigação de cada chamador. Chamadores e arestas tardios recebem
resultados anteriores. Ciclos partem de resultados vazios: recursão sem base
não inventa retorno. O retorno de um escape ancestral atravessa a fronteira do
pai e continua sendo interpretado sob a obrigação correta.

A relevância conserva fatos distintos quando necessário e compartilha bitsets
imutáveis iguais por referências fracas. Isso evita copiar o mesmo conjunto para
milhares de obrigações, sem guardar a história inteira de iterações no cache.
Resultados de construção são liberados antes das fases seguintes. Foram removidos
planos antigos, índices duplicados, aliases sintéticos de entrada e APIs sem uso.

## Escala e comparação

A família dinâmica `return-fanout` usa dois hubs. N é o número de caixinhas por
hub: N=254 representa 508 caixinhas. Seletores e flag vêm de ACCEPT, há PERFORMs
cruzados e EXIT PARAGRAPH condicional. São os mesmos fontes e hashes da
[ampliação anterior](shared-continuation-scale-20261009.md).

Os ensaios são sequenciais, JDK 21, max-work 100M, com JFR profile e GC log.
Reserva do host 2.048 MiB; teto RSS 640 MiB no heap 256 e 1.408 MiB no heap 1.024.
Os ensaios antigos com heap 1.536 usaram teto RSS 1.920 MiB.
Não houve aumento de heap nesta implementação; nenhum guard disparou.
Tempo inclui CLI e JFR. RSS inclui JVM/frontend, não mede apenas o heap do solver.
São ensaios únicos; tempos pequenos podem variar com o host.

| N/hub | Heap MiB | Anterior | Etapa 1 | Etapa 2 |
| ---: | ---: | --- | --- | --- |
| 48 | 256 | PASS 5,86 s / 418,5 MiB RSS | PASS 2,78 s / 343,6 | PASS 2,53 s / 238,2 |
| 64 | 256 | OOM | PASS 3,13 s / 433,9 | PASS 3,08 s / 275,5 |
| 96 | 256 | OOM | OOM | PASS 4,50 s / 321,5 |
| 128 | 256 | Não executado | Não executado | PASS 6,77 s / 425,4 |
| 128 | 1.024 | OOM | PASS 7,78 s / 1.205,7 | PASS 6,72 s / 526,5 |
| 192 | 1.024 | OOM | Não executado | PASS 14,25 s / 629,1 |
| 254 | 1.024 | Não executado | Não executado | PASS 24,86 s / 1.012,6 |
| 128 | 1.536 | PASS 96,96 s / 1.826,2 | Não executado | Não executado |
| 192, 254 | 1.536 | OOM | Não executado | Não executado |

A etapa 1 preserva todos os contadores da execução anterior em N=48 e seus
resultados. Sua melhoria vem da representação e do rank em cache, não da redução
do domínio. A etapa 2 conserva workItems, contextos, fatos de saída e entregas
de valores; remove uma entrada sintética, reduzindo pontos e pares em uma unidade.
A unidade de controlWorkItems muda: lotes de descobertas físicas e colunas não
são as visitas lógicas da implementação anterior. Sua razão não é speedup de CPU.

| N/hub | Posições físicas | Arestas físicas | Obrigações | Células lógicas | Colunas | Fatos de saída |
| ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 48 | 1.358 | 1.743 | 97 | 131.630 | 193 | 261.041 |
| 128 | 3.598 | 4.623 | 257 | 924.430 | 513 | 1.842.961 |
| 254 | 7.126 | 9.159 | 509 | 3.626.626 | 1.017 | 7.241.557 |

Nesta família, posições são `28N+14`, arestas `36N+15`, obrigações `2N+1` e
colunas `4N+1`. Células lógicas ainda são `56N²+54N+14`: a fatoração elimina
adjacência clonada e a construção de efeitos repetidos, não os fatos legítimos.
Arrays de metadados, SCCs/relevância lógicos, relações de saída e fluxo de valores
ainda podem exigir trabalho ou espaço quadrático. Não há promessa de análise
universalmente linear nem de que todo programa corporativo cabe em 16 GiB.

No replay do JAR final, N=128/256 MiB concluiu em **7,64 s / 426,1 MiB RSS**,
e N=254/1.024 MiB em **24,50 s / 1.015,9 MiB RSS**. Todos os contadores
não temporais coincidem com o snapshot da etapa 2. Ambos preservam exatamente
N nomes esperados. A menor disponibilidade amostrada do host fica registrada
por caso no JSON, junto aos comandos e limites.

O JFR final de N=254 contém 1.320 amostras da main: 689 em controle, 386 em
relevância e 82 em resolução de consultas. Em 822 amostras, o primeiro frame
do produto é `DependencyGraph.find`, usado também pelas vistas lógicas e SCCs.
Isso evidencia custo residual de consultar relações lógicas; não permite
atribuir todo o tempo a colisões do índice sem outra sonda. As 109 pausas de GC
somam 626,45 ms na execução de 24,50 s; o ensaio não fica preso em GC thrash.
Amostras de alocação são estimativas acumuladas, não memória residente.

## Validação e proveniência

FAST executado após estabilização: **1.088 testes, 141 suites, zero falhas,
erros ou skips**, além dos testes Python do harness. Build e testes com heap 256 MiB.
As regressões incluem recursão sem retorno, base recursiva, entradas independentes,
mais de 64 saídas, chamadores e sucessores tardios, políticas de escape distintas,
THRU, handlers e correlação entre chamadores. Os novos adversários exigem:

- 128 obrigações atravessam uma cadeia de 201 posições com apenas 200 arestas;
- uma chamada física entrega uma vez, mantendo as três obrigações dos chamadores;
- 512 pares de posição/escopo esparsos permanecem 512, sem criar 512² células.

**CardDemo: 73/73 fontes, 815 relações iguais, zero ausentes e zero adicionais**,
frente ao produto anterior e à pipeline congelada. Mantidos os 47 PARTIAL;
os 185 hashes de fontes/includes coincidem antes e depois. O replay levou
125,42 s, com pico RSS de 321,2 MiB, heap 256 MiB. A finalidade é paridade;
não se compara esse tempo a corpus histórico com recursos diferentes.

O CLI final preserva os JSONs completos de FIXTURE02 original, sentinela de
escape (AFTER e ESCAPED) e hub simples de 255 destinos. FIXTURE02 continua sem
dependências de programa porque seus PERFORMs sem retorno impedem alcançar os
CALLs; seu resultado vazio não é o oráculo do fanout dinâmico. A variante com
flag conhecida N=32 preserva os 32 nomes em 1,31 s, com apenas três contextos
de valores. O JAR também passa o smoke em diretório e ambiente isolados.

Os ensaios amplos usam o snapshot stage2. Entre ele e o JAR final só mudou a
classe Ints, com remoção de dois métodos sem chamadas; todos os outros
bytecodes coincidem. O JAR final é qualificado separadamente pelo FAST, corpus,
sentinelas e replay de escala. Não se sobrescrevem os resultados originais.
O script histórico de histogramas agora lê o solver por revisão Git explícita,
para que suas sondas continuem reproduzindo o baseline, e não procurem campos
removidos na implementação atual.

Logs, JSONs completos, telemetria, GC/JFR e JARs congelados ficam em
`benchmark/results/compact-factor-control-20261009/`, ignorado pelo Git.
O relatório JSON versionado registra hashes e resumos, sem versionar builds.

## Reprodução

```sh
python3 benchmark/run-hub-dispatch.py OUTPUT --jar target/cobol-dependency-analyzer.jar \
  --mode return-fanout --boxes 48 64 96 128 --heap 256 --rss 640 \
  --system-reserve 2048 --max-work 100000000 --timeout 90 \
  --profile --profile-duration 60 --telemetry
```

Para a escala maior, usar `--boxes 128 192 254 --heap 1024 --rss 1408
--timeout 120 --profile-duration 90`, em novo OUTPUT e uma JVM por vez.
O programa corporativo privado não foi executado. O resultado fecha a parede
reproduzida pela fixture; não exclui outras paredes em insumos diferentes.
