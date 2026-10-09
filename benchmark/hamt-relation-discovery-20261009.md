# Discovery: HAMT, repetição de relações e custo de propagação

O discovery confirma uma grande oportunidade de compartilhamento estrutural.
**HAMT/CHAMP por contexto é uma opção de armazenamento, mas não é a correção
principal recomendada para o gargalo observado.** A prioridade é corrigir o hash
de `Location`, depois fatorar as relações exatas e propagar grupos/deltas de
contextos e escopos no solver canônico. Não houve alteração de produção.

## Escopo, pins e segurança

- Fonte: `9031dbc680576d60ee8a1bf1275fe9e19d556d24`.
- JAR congelado: `benchmark/results/state-flyweight-20261009/flyweight.jar`;
  SHA-256 `e623c8788d47a89552601bfa5e23699419fda5bcace51b97a99a94b73eb00dac`.
- Java 21.0.12+1.1, execução sequencial, heap 512 MiB, guarda RSS 768 MiB,
  reserva mínima do sistema 2.048 MiB. Nenhum aumento de heap.
- Overlays diagnósticos compilados em diretórios isolados; o JAR e os fontes de
  produção permaneceram intactos. As 2.508 classes de `target/classes` continuam
  byte a byte iguais às classes correspondentes do JAR.
- Raw: `benchmark/results/hamt-discovery-20261009/`, ignorado pelo Git. O JSON
  deste relatório registra pins, contagens, manifests e hashes das evidências.

## Medição das relações, não apenas dos objetos

O probe captura BEFORE como `contexto -> posição física -> estado exato`.
Para controle, captura `escopo -> posição física -> (parada, reads, kills,
needs, possible, definite)`. Payloads iguais recebem o mesmo ID por igualdade
completa. Ausência, estado desconhecido alcançado e paradas são distintos.

| N caixinhas, seletor dinâmico | BEFORE: entradas | mapas completos distintos | controle: células | posições físicas de controle |
| --- | ---: | ---: | ---: | ---: |
| 32 | 25.612 | 3 | 59.086 | 910 |
| 64 | 100.364 | 3 | 232.846 | 1.806 |
| 128 | 397.324 | 3 | 924.430 | 3.598 |

Em N=128 há 515 contextos, mas 257 têm o mesmo mapa de 1.545 posições,
257 têm o mesmo mapa de uma posição, e um tem duas posições. Há apenas dois
conteúdos de estado nesses mapas. Os 257 mapas de controle são diferentes,
mas a mediana da diferença contra um mapa de referência é somente quatro
entradas: as exceções de escopo são pequenas e precisam continuar explícitas.

Invertendo a relação, sem descartar nenhum membro:

| N=128 | pares originais | grupos `(posição,payload)` | máscaras distintas de membros |
| --- | ---: | ---: | ---: |
| BEFORE | 397.324 | 1.547 | 4 |
| Controle | 924.430 | 4.364 | 514 |

A reconstrução de **todos** os mapas a partir dos grupos reproduziu exatamente
cada entrada, valor e ausência. Isso prova a fatoração dos dados capturados;
não prova a correção de um solver novo. O dump não inclui os metadados SCC/rank,
nem todas as relações de chamada/retorno. Não é uma previsão de redução de RAM
ou tempo nessa mesma proporção.

A redundância já existe durante a convergência. Em N=64, nas visitas 20 mil,
60 mil e 100 mil há respectivamente 19.739, 55.426 e 87.852 entradas BEFORE;
os grupos `(posição,estado)` permanecem em 779. As máscaras variam: 124, 101 e
80, caindo para quatro no final. Portanto, apenas compartilhar raízes completas
iguais no final perderia parte da oportunidade durante a execução.

Na variante que consulta o alvo dinâmico antes de sobrescrevê-lo em cada
caixinha, N=16 tem 36 estados, 8.780 entradas BEFORE e 305 grupos exatos.
O oráculo conserva `BOOT0000` e todos os 16 nomes PGM. Um teste de controle
negativo com payloads diferentes em cada contexto não apresenta redução:
compartilhamento depende de repetição real, não transforma qualquer programa
em um caso linear.

## O custo que HAMT em BEFORE deixaria presente

Com seletor fixo, N=128 usa somente três contextos e 1.035 entradas BEFORE,
mas ainda materializa **923.917 células de controle**. O produto entre posições
e escopos continua grande mesmo sem explosão do fluxo de valores.

Na gravação JFR de produção N=384 já preservada na campanha de flyweight,
2.707 de 3.640 amostras da thread principal caem em controle/relevância
(**74,4%**). `DependencyGraph.find` é o primeiro método de produto em 2.022
amostras (55,5%). São proporções de amostras, não tempos exatos.

A instrumentação N=64 mede aproximadamente 7,40 milhões de consultas a `find`,
com aproximadamente 1,4 sondagens por consulta. A quantidade de consultas é
mais relevante que a colisão média nesse índice primitivo. Construção,
fechamento/SCC e relevância também caminham pelo produto lógico. Compactar
BEFORE sem mudar esse trabalho não trata a parte dominante do perfil.

## Descoberta adicional: colisões completas de Location

O hash automático do record combina os campos de maneira que as posições e
os IDs sequenciais de contexto se sobrepõem nesta família de entrada.

| N=128 | chaves BEFORE | hashes completos distintos | chaves em colisão completa | maior grupo |
| --- | ---: | ---: | ---: | ---: |
| Hash atual | 397.324 | 16.706 | 395.522 | 89 |
| Mistura experimental | 397.324 | 397.306 | 36 | 2 |

O overlay muda somente a composição do hash; a igualdade permanece intacta.
No histograma vivo, o hash atual mantém 387.166 `HashMap.TreeNode` (21.681.296
bytes rasos). A variante não tem instâncias dessa classe no histograma e usa
nós ordinários. A soma dos bytes rasos de `HashMap.Node` e `TreeNode` cai
8,85 MiB; as 397.325 instâncias de `Location` permanecem iguais. Esses números
não são o tamanho retido total do heap.

Três pares N=128, alternando a ordem, sem instrumentação/JFR nos runs cronometrados:

| Variante | tempo mediano | RSS máximo mediano |
| --- | ---: | ---: |
| Atual | 6,771 s | 423,8 MiB |
| Hash experimental | 6,165 s | 381,0 MiB |

Redução observada: **8,96% no tempo e 10,09% no pico RSS mediano**.
JSON completo e todos os contadores de trabalho, excluindo durações, são iguais
nos seis runs. É uma evidência limitada a uma família, três pares e esta máquina;
o hash experimental ainda não passou pela qualificação de produção/corpus.
Os runs com dump/histograma são marcados `timingQualified=false` e não entram
nessa comparação de tempo.

## O que a literatura muda na decisão

[CHAMP, Steindorfer e Vinju, OOPSLA 2015](https://michael.steindorfer.name/publications/oopsla15.pdf)
explica como tries persistentes compactos compartilham subárvores, melhoram
localidade e aproveitam identidade na igualdade. Também explicita que hashes
completos iguais continuam em buckets de colisão quando todos os bits acabam.
Portanto, migrar para HAMT não corrige por si só o hash observado. Forma canônica
também não significa interning automático de mapas construídos independentemente.

O modelo post-hoc de trie canônica de 32 vias, com interning global ideal, conta
520 nós únicos em BEFORE contra 133.384 nós expandidos; no controle, 2.926 contra
282.957. Isso sustenta a oportunidade de compartilhar mapas, mas é um modelo de
forma, mais forte que persistência comum. Não implementa CHAMP em Java, não mede
seu custo de atualização e não autoriza copiar os ganhos publicados pelo paper
para nossos arrays primitivos ou mapas mutáveis.

[IFDS, Reps, Horwitz e Sagiv, POPL 1995](https://www.cs.cornell.edu/courses/cs711/2005fa/papers/rhs-popl95.pdf)
mostra análise interprocedural por relações e caminhos que respeitam chamadas e
retornos, para fatos finitos e funções distributivas. A direção de fatorar relações
é relevante; não demonstramos essas hipóteses para todos os nossos estados,
predicados e transferências. Uma troca completa de solver por IFDS não está
justificada por este discovery.

## Correção recomendada

Primeiro, corrigir a composição geral de `Location.hashCode` e qualificá-la com
os gates semânticos. É uma mudança pequena com causa e efeito medidos, mas ainda
mantém o número de relações e de itens de trabalho.

Depois, representar a relação exata por posição/payload e conjunto de membros:

```text
hoje:                     relação fatorada:
(posição A, C1) -> S        (posição A, S) -> {C1, C2, C3}
(posição A, C2) -> S
(posição A, C3) -> S
```

O processamento usa um grupo enquanto posição, estado e comportamento de
transferência coincidirem. Guarda os IDs dos contextos/escopos em bitsets e
propaga apenas membros novos. Quando um limite de PERFORM ou escape modifica
o comportamento, separa a máscara correspondente. Chamadores, continuações e
retornos continuam ligados aos seus membros corretos; não há join aproximado
entre estados diferentes. Partes independentes do escopo podem ser calculadas
por posição física; partes dependentes precisam operar nas máscaras filtradas.

A mudança deve alcançar também a relevância e o agendamento do controle, pois
são a maior parte do custo observado. A estrutura canônica deve substituir a
representação expandida, preservando exatamente as exceções, sem fallback por
formato de fixture. Reconstruir o produto completo no agendamento eliminaria o
ganho: SCC/rank e relações de chamada são itens necessários desse projeto.

HAMT/CHAMP pode ser um componente dessa implementação se ainda for necessário
compartilhar versões de mapas. Não escolheria uma migração ampla para HAMT antes
de atacar a expansão e as varreduras das relações. Bitsets sobre IDs já existentes
combinam melhor com o agrupamento e as interseções que precisamos medir agora.
Isso é uma escolha fundamentada para a próxima implementação, ainda sem uma
comparação de desempenho entre dois solvers completos.

## Validação e reprodução

Cinco testes do analisador post-hoc passaram: mapas iguais, diferenças parciais,
exceção de fronteira, ausência distinta de payload desconhecido e dados sem
repetição. Os oráculos das fixtures instrumentadas passaram e todas as
reconstruções das relações foram exatas. Nenhuma guarda de memória/tempo disparou.

Como não há mudança de produção, os gates de produção permanecem os documentados
em [state-flyweight-20261009.md](state-flyweight-20261009.md): FAST 1.093 testes,
CardDemo 73 fontes/815 dependências, e fixtures standalone. Não foram novamente
executados e não qualificam o overlay experimental do hash.

Exemplo de reprodução, com Java 21 e diretórios de saída novos:

```sh
python3 benchmark/profiling/relation-sharing-probe.py /tmp/hamt-census \
  --jar benchmark/results/state-flyweight-20261009/flyweight.jar \
  --source-ref 9031dbc680576d60ee8a1bf1275fe9e19d556d24 \
  --boxes 32 64 128 --mode return-fanout return-fanout-fixed
python3 benchmark/profiling/analyze-relation-rows.py /tmp/hamt-census \
  --output /tmp/hamt-rows.json
python3 benchmark/profiling/test_relation_rows.py
python3 benchmark/profiling/compare-location-hash.py /tmp/hash-comparison \
  --jar benchmark/results/state-flyweight-20261009/flyweight.jar \
  --source-ref 9031dbc680576d60ee8a1bf1275fe9e19d556d24 --boxes 128
```

O probe aceita `--visits`, `--variant prewrite-call`, `--mix-location-hash` e
`--histogram` somente como instrumentos de laboratório. Nenhuma opção foi
adicionada ao produto. O programa corporativo não foi analisado nesta máquina;
não há promessa de eliminar todos os casos combinatórios nem de convergência
nesse programa a partir destes resultados.
