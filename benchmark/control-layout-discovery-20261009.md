# Discovery da parede de armazenamento do controle

Data: 2026-10-09. Fonte: `5276279c72ea0f03598e5dac7d7d54806e2e381b`.
JAR: `6bf93c8e362eebd1301a5a289402eb9e11452fadf538358e34f5024d2483d943`.
[Contagens, histogramas normalizados e hashes brutos](control-layout-discovery-20261009.json).
Nenhuma alteração de produção. As propostas ainda não estão implementadas.

A próxima correção recomendada é **um grafo canônico de IDs e arestas tipadas**,
compacto durante a construção e reutilizado por observação, ciclos, ordenação e
relevância. Só retirar a cópia final não resolve o maior consumo medido. Isso
reduz o custo por relação; reduzir o produto posição/endpoint exige fatoração.

## Evidência nova

As [execuções ampliadas](shared-continuation-scale-20261009.md) localizaram OOM
em `Construction.parent` com N=192/1.536 MiB e na cópia dos planos com
N=128/1.024 MiB. A pilha de uma alocação não identifica sozinha o maior dono do
heap; esta rodada mede objetos vivos e relações armazenadas.

A sonda compila uma cópia isolada de `DependencyControl` com contadores e
histogramas. Equações, deltas, chamadas e escapes permanecem iguais. Outros
bytecodes vêm do JAR congelado. O `DiagnosticCommand` MBean da própria JVM
executa `gcClassHistogram`, com coleta de objetos vivos. Não usamos heap dump.

Três execuções sequenciais da mesma família **dinâmica** `return-fanout`, dois
hubs, heap 256 MiB, teto RSS 640 MiB, reserva do host 2.048 MiB e max-work 100M.
Todas concluem com código 0 e exatamente N dependências. Nenhuma proteção foi
acionada. Maior RSS: 415,6 MiB; menor disponibilidade amostrada: 2.896,5 MiB.
O GC forçado invalida comparações de tempo com produção. Igualdade destes três
oráculos não substitui qualificação do produto.

| N por hub | Posições físicas (`Exit`) | Pontos especializados | Ligações comuns `next` | Ligações de chamada |
| --- | ---: | ---: | ---: | ---: |
| 16 | 463 | 15.215 | 18.383 | 1.057 |
| 32 | 911 | 59.087 | 71.567 | 4.161 |
| 48 | 1.359 | 131.631 | 159.567 | 9.313 |

N=48 tem 97 endpoints e duas políticas de escape. Os pontos quase preenchem
o produto das posições pelos endpoints. Isso mede especialização de controle,
não estados de valores; não prova produto completo em qualquer programa.

| N | Após resolver controle | Após copiar planos, temporários vivos | Após construtor, antes de ciclos |
| --- | ---: | ---: | ---: |
| 16 | 25,20 MiB | 26,81 MiB | 13,79 MiB |
| 32 | 72,03 MiB | 78,78 MiB | 27,93 MiB |
| 48 | 147,51 MiB | 161,46 MiB | 46,53 MiB |

São **tamanhos rasos agregados de todos os objetos vivos**, incluindo frontend
e plataforma. Não são RSS, heap reservado nem dominadores de um heap dump.
O desaparecimento de `PendingPlan` e seus bitsets no terceiro checkpoint
confirma o fim da vida da construção. A diferença total não deve ser atribuída
a uma única classe sem análise de retenção.

Em N=48, após resolver:

| Classe | Instâncias | Tamanho raso agregado |
| --- | ---: | ---: |
| `HashMap` | 658.816 | 30,16 MiB |
| tabelas `HashMap.Node[]` | 404.981 | 33,38 MiB |
| `HashMap.Node` | 730.007 | 22,28 MiB |
| `HashMap.TreeNode` | 57.586 | 3,08 MiB |
| `HashSet` | 526.751 | 8,04 MiB |
| `PendingPlan` | 131.631 | 6,03 MiB |
| `BitSet` | 281.895 | 6,45 MiB |
| arrays `long[]`, todos os usos | 284.367 | 7,31 MiB |

As cinco categorias de mapas/conjuntos somam **96,93 MiB, cerca de 66%** dos
147,51 MiB vivos. A cópia acrescenta 13,95 MiB; após o construtor o conjunto
vivo cai 114,94 MiB. Não é duplicação integral: chaves são compartilhadas,
mas mapas, planos e listas finais são novos.

## Mecanismo operacional

A ligação `hub -> caixa` pode existir aguardando a fronteira A e também B.
A obrigação de retorno fica na chave do ponto:

```text
aguardando A:  hub/A -> caixa/A
aguardando B:  hub/B -> caixa/B
```

Essa distinção é necessária: a fronteira A retorna sob A, mas pode continuar
sob B. Hoje ela repete também o armazenamento. Cada `PendingPlan` cria três
conjuntos, um mapa de chamadas, lista de assinantes e dois bitsets, mesmo quando
algumas coleções ficam vazias.

Uma ligação comum aparece em `plan.next`, `construction.parents` e
`child.resultParents`. Em N=48 são 159.567 referências em cada índice comum;
`parents` inclui ainda 9.313 ligações de chamadas: 168.880 ao todo. Os índices
têm funções diferentes; unificá-los exige conservar tipos de arestas.

Após o ponto fixo, os temporários continuam vivos para calcular observação,
enquanto outro mapa recebe planos finais. Mais tarde `cyclicNodes` reconstrói
predecessores e faz DFS; `forwardOrder` repete DFS e constrói prioridades.
A relevância reconstrói nós e predecessores a partir dos mesmos sucessores de
controle. Seus fatos são próprios, mas sua topologia pode ser compartilhada.
A sobreposição da relevância com controle não foi medida por histograma aqui;
a duplicação está identificada no código.

Há também CPU na fila. O JFR anterior localizou 3.512/7.752 amostras da main
em prioridade. A sonda mede consultas ao mapa imutável de ranks: uma consulta
por chave examina em média 4,46/3,63/4,19 posições em N=16/32/48; máximos
74/57/78. São colisões reais, mas não demonstram a distribuição em N=128.
Hoje o comparador cria `Exit`/`Point` e consulta rank a cada comparação;
guardar a prioridade no item remove essa repetição independentemente dos hashes.

## Correção recomendada

1. Um ID por ponto lógico, preservando exatamente `(Exit, endpoint, escapeScope)`.
   Um registro canônico resolve descritores; arrays por ID guardam trabalho,
   marcações de alcançabilidade, ordem e componentes, substituindo mapas por fase.
2. Arestas em buffers primitivos expansíveis **desde a construção**, com tipos
   para ligação comum, chamada com binding e entrada independente. Um índice
   reverso tipado atende observação e propagação; esta última filtra ligações
   comuns, e assinantes conservam a junção chamada/saída compatível.
3. Encerrar a mutação e descartar resultados/deltas/assinantes após seus últimos
   consumidores. Manter o mesmo grafo; remover a família paralela de planos
   finais. Eventual compactação deve liberar buffers por bloco e ter pico limitado.
4. Observação, ciclos, ordenação e relevância usam a mesma adjacência. Ordem e
   componentes são calculados uma vez quando seus contratos forem compatíveis.
   Reads, kills e fatos de relevância ficam em arrays próprios. Auditar pontos
   de consulta ausentes do grafo, hoje tratados com sucessores vazios.
5. Calcular rank uma vez por item enfileirado; comparar inteiros, conservando a
   prioridade especial de entregas de resultado e os desempates existentes.

Não trocar cada conjunto esparso de arestas por um bitset de todos os pontos:
isso pode gastar espaço quadrático. Arestas guardam IDs existentes; bitsets são
adequados aos universos de fatos que justificarem esse armazenamento.

[Wheatman e Xu, Packed Compressed Sparse Row, 2018](https://people.csail.mit.edu/hjxu/papers/pcsr.pdf),
§II, descreve offsets/destinos em arrays e o custo de inserções de CSR. Não
recomendo adicionar PCSR ou um framework: buffers primitivos expansíveis e
acesso por ID são uma intervenção menor. CSR puro a cada inserção deslocaria
o grafo, criando outra parede de tempo.

Em N=48, duas direções de adjacência com destino `int` por ligação e offset por
ponto exigiriam aproximadamente 2,29 MiB de **payload**. É uma conta de layout,
não ganho medido nem previsão de heap total: metadados, bindings, índices,
capacidade excedente, fatos e frontend também ocupam espaço.

Essa correção reduz bytes por relação e picos. **Sozinha não muda o crescimento
quadrático dos pontos** observado na família. Substitui o caminho anterior;
não cria modo especial para hubs, GO TO 255 ou programas grandes.

## Fatoração estrutural posterior

Para atacar o produto, guardar topologia física uma vez e propagar conjuntos
exatos de descritores de retorno. Uma ligação comum transporta o conjunto; na
fronteira A separam-se obrigações que retornam das que continuam:

```text
hub [obrigações A,B] ---> caixa [obrigações A,B]
                                  |
                             fronteira A
                             /         \
                    retorna sob A   continua sob B
```

Chamadas reais relacionam obrigação do callee, binding e obrigação do chamador.
Endpoint e escapeScope continuam correlacionados; seus conjuntos independentes
poderiam inventar combinações. Valores e guardas também continuam específicos.
Uma SCC não torna fronteiras equivalentes; desligar escapes não é correção.
Ainda haverá bits e relações genuínas, sem promessa de espaço linear universal.

[Olteanu e Závodný, Factorised Representations of Query Results, ICDT 2012](https://www.cs.ox.ac.uk/dan.olteanu/papers/oz-icdt12.pdf)
fundamenta representar relações exatas sem expansão plana obrigatória. A
[RSM ponderada, ESOP 2017](https://bkragl.github.io/papers/esop2017.pdf) oferece
representação por autômatos de configurações e retornos. Adaptar os princípios
à política de escape COBOL exige demonstração de equivalência; os artigos não
fornecem essa demonstração para nosso solver.

Recomendo primeiro o grafo compacto: a maior parcela de armazenamento foi
medida, e a identidade semântica permanece. Isso prepara a fatoração futura.
Introduzir agora um segundo solver simbólico aumentaria a complexidade.

## Aceite futuro e reprodução

Implementar em checkpoints do mesmo caminho: IDs/arestas tipadas desde a
construção; depois adjacência compartilhada e prioridade armazenada. Comparar
com mesmos heaps/guardas: N=128/1.024 MiB, N=192/1.536 MiB e curva 16–96.
N=254/1.536 MiB é uma meta de escala, não resultado. Medir objetos vivos por
fase, pico RSS, relações, trabalho, tempo e GC, distinguindo redução de constante
de mudança de crescimento.

Qualificar retornos diferentes, THRU, escapes ancestrais, handlers, recursão e
ciclo sem retorno, incluindo negativos de mistura de chamadores e retorno
fabricado. FAST e CardDemo 73 fontes antes de afirmar paridade da futura mudança.
Aqui o runtime não mudou: FAST/CardDemo não foram repetidos. A parede corporativa
ainda precisa de pilha/histograma para confirmar identidade com a da fixture.

```sh
export JAVA_HOME=/path/to/temurin-21
python3 benchmark/profiling/control-layout-probe.py benchmark/results/layout-novo \
  --jar benchmark/results/shared-continuation-fix-20261009/final.jar \
  --boxes 16 32 48 --mode return-fanout
```

Tentativas `run1`/`run2` preservadas: contaram relações, mas o coletor externo
encontrou ambiguidade wrapper/JVM e depois falha de Attach. Não são evidência
válida de histograma. `run3`, usado nas tabelas, coleta na própria JVM. Seu
manifest preserva fonte instrumentado, JAR, compilação e hashes. O bootstrap
com modo inválido terminou antes de iniciar uma análise. Nenhuma falha de coleta
foi promovida a PASS experimental.
