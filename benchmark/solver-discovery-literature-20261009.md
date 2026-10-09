# Discovery algorítmico: continuações, deltas e armazenamento de saídas

Data: 2026-10-09. Fonte inspecionado: `fcb645d0be60f06611d421a204e0f185b8c26ae6`.
Baseline de produção: `87255b08d38456efbc3caf3bd6082f03dee82362`.
JAR SHA-256: `b7cc0654207091e1872071a7a3267547daa170ce9b1fb1026702c99e69fa5ab1`.
Esta etapa inspeciona código, evidências existentes e fontes científicas primárias.
Não executa novas análises JVM, não altera produção e não qualifica correção.

A recomendação é manter um solver canônico, com resultados compactos e propagação
incremental, e fatorar a continuação de uma invocação para que atravessar fronteiras
não gere outra família de chamadas internas. A terceira parte é uma proposta
semântica a validar, não uma transformação cuja equivalência já foi demonstrada.

## O que está medido

As evidências estão em [fanout de retornos](control-return-fanout-20261009.md),
[ablação de escape](escape-ablation-20261009.md) e
[paredes até a convergência](wall-discovery-20261009.md), com dados brutos preservados.

| Observação local | Consequência para o diagnóstico |
| --- | --- |
| Família fixed: três contextos de valores, mas 4.186 / 65.842 / 332.426 / 1.049.698 pares para N=4/8/12/16 | A parede de controle não exige explosão de estados de dados. |
| Nos cinco tamanhos fechados, pares seguem `16*N^4 + 4*N^2 + 6*N + 2` | Crescimento de grau quatro nesta família; não é prova de crescimento exponencial nem fórmula universal. |
| N=32 original falha no construtor de controle | A primeira parede está presente sem instrumentação que altere sua semântica. |
| N=32 sem reinvocação fecha controle, mas falha construindo grafo reverso | A segunda fase encontra resultados anteriores ainda vivos. |
| N=32 sem reinvocação e com descarte conclui; N=36 falha antes do descarte | Encurtar a vida dos resultados não resolve o pico durante a construção. |
| N=36 sem reinvocação e com bitsets conclui: 4.289.918 relações, 73 saídas distintas | Relações numerosas sobre universo pequeno favorecem armazenamento por bits. |
| Sentinela perde ESCAPED quando reinvocação é desligada | A ablação não pode ser promovida a correção. |

N significa caixinhas por hub; há dois hubs. Resultados N=32/36 com ablação
dimensionam a investigação, não o desempenho da futura correção correta. Tempos
são execuções únicas instrumentadas. O programa privado, seus 255 destinos e sua
execução de 70 minutos não foram qualificados por esses testemunhos.

## Onde as combinações entram

Em `DependencyControl.java:44`, cada plano cruza todas as suas chamadas com todas
as saídas conhecidas das respectivas entradas. `Plan.delivered` guarda objetos
`Returned(call, exit)`. Ao reagendar o plano, os mesmos conjuntos são percorridos;
o conjunto de deduplicação impede nova entrega, mas não a tentativa, a criação do
objeto temporário nem a consulta ao hash. Nas linhas 46–48, resultados completos
dos sucessores são unidos novamente.

Em `DependencyFlow.java:1268–1283`, um escape fora do endpoint e fora da regra de
região ancestral gera um novo `DependencyControl.Call` ao `ordinaryDefault`,
mantendo o binding. É uma chamada de resumo, não outro PERFORM executado pelo
programa nem evidência de crescimento da pilha Java. Entretanto, ela se torna
mais uma entrada no produto chamada/saída. Novos resumos podem voltar a revelar
escapes que geram outras entradas. `deliver` aplica essas continuações também
aos assinantes da análise de valores.

Portanto, a hipótese mais sustentada é uma relação de continuações insuficientemente
fatorada, combinada com a materialização dos pares e rescans. Não há evidência
de que o frontend esteja produzindo destinos falsos. O solver pode preservar o
fluxo corretamente e ainda representá-lo de forma cara.

## Proposta 1: representação compacta e vida útil por fase

Cada saída recebe um ID estável na análise do programa. Um resultado de controle
guarda os IDs em um bitset de tamanho dinâmico; não há limite semântico de 64
saídas. Inserção, presença e diferença operam nos bits; união pode operar em
palavras de máquina. A tradução para Exit acontece somente quando necessária.
O universo deve crescer preservando todos os IDs e todos os membros anteriores.

Com 73 saídas, um bitset denso precisa de duas palavras de 64 bits para seu payload.
Isso não é uma estimativa de heap total: objetos, índices, planos e temporários
continuam custando memória. A intervenção local já demonstrou ganho mantendo
as mesmas relações; não demonstrou que o produto inteiro passa em N=36 com
reinvocação ativa.

Após o ponto fixo e o cálculo de observação, manter somente o grafo necessário
para ciclos, ordenação e relevância. `results` tem um leitor externo de tamanho
em `DependencyAnalyzer.java:79`; substituí-lo por um contador explícito permite
que o mapa de resultados deixe de existir após a construção. `delivered` e os
índices de propagação também são candidatos a estado temporário de construção.
O descarte adicional deve ser validado; a evidência atual testou resultados.

Isto ataca a segunda parede e reduz pressão de GC da primeira. Não reduz a
quantidade lógica de combinações. Preferir uma representação direta no solver,
sem adaptador genérico de Set, flags de produção ou dois caminhos por tamanho.

## Proposta 2: fila de novidades e junções indexadas

O item de trabalho passa a ser um fato novo, não o pedido para reconsiderar o
plano inteiro. Dois eventos precisam ser simétricos:

```text
Nova saída S da entrada E:
    consultar chamadas já registradas para E
    combinar somente S com elas

Nova chamada C para E:
    consultar saídas já conhecidas de E
    combinar somente C com elas

Nova aresta P -> Q:
    propagar resultados existentes de Q para P
    depois propagar somente as novidades de Q
```

Um índice entrada -> assinantes elimina a busca em planos sem relação com a
novidade. A ordem de processamento deve assegurar que cada combinação seja
processada quando a segunda premissa chega; alternativamente, bits de entrega
por chamada fornecem deduplicação compacta. Não simplesmente remover `delivered`:
chamadas podem surgir durante a entrega e precisam receber resultados antigos.

Exemplo: chamadas A/B conhecem saídas X/Y. Quando Z chega, trabalhar em A-Z e
B-Z. Não reexaminar A-X, A-Y, B-X, B-Y. Se C chega depois, entregar X/Y/Z a C.
Só observar um dos dois eventos perderia trabalho legítimo.

A fundamentação é o algoritmo de dedução por fatos novos e índices descrito em
[McAllester, On the Complexity Analysis of Static Analyses, JACM 2002](https://www.cs.cmu.edu/~fp/courses/lp/handouts/mcallester02jacm-r.pdf),
Figura 1 e prova das listas indexadas. Sua análise distingue fatos derivados de
combinações das premissas. A aplicação ao nosso solver é uma adaptação: não exige
introduzir uma engine Datalog.

Se todas as C chamadas precisarem realmente das S saídas, ainda há C*S combinações.
Esta proposta elimina recomputação, não a cardinalidade desse produto. Na fase
de valores, uma saída existente pode ganhar informação: o evento precisa levar
a atualização ou versão do valor, e não apenas a primeira descoberta do Exit.

## Proposta 3: uma continuação compartilhada para cada invocação semanticamente equivalente

Considere uma invocação que deve terminar na fronteira A. O fluxo entra em B,
chega ao fim de B e segue para C, ainda com A pendente. Na modelagem atual, essa
continuação pode aparecer como outra chamada interna de resumo ligada ao binding.
Quando várias entradas encontram várias saídas, a mesma obrigação de retorno
é espalhada por muitas combinações.

O objetivo é separar o fluxo interno do compromisso de retorno:

```text
Chamador 1 -- retorno R1 --+
                         +--> execução compartilhada sob fronteira A
Chamador 2 -- retorno R2 --+       B -> C -> hub -> ...
                                           |
                                retorno compatível com A
                                           |
                                    avisar R1 e R2
```

Uma continuação descreve a região ativa, sua fronteira esperada e a política
semântica de saída. O endereço de retomada de cada chamador fica em uma relação
separada. Ao atravessar B, seguir para C sob a mesma obrigação de terminar em A,
sem cadastrar uma nova invocação lógica só para percorrer C. Um PERFORM real cria
uma invocação; uma transferência comum de controle conserva a invocação pendente.
Saídas terminais e escapes que realmente atravessam uma região ancestral mantêm
suas regras próprias de propagação, sem fabricar retorno normal.

O compartilhamento deve ser entre descritores semanticamente equivalentes.
Um endpoint sozinho pode ser insuficiente: a regra atual consulta a região do
binding e ancestrais. Não colocar o binding inteiro na identidade compartilhada,
pois seu endereço de retorno distingue chamadores sem necessariamente distinguir
a execução do corpo. As fases de retomada e controle de laços continuam no
receptor correto. Essa decomposição precisa ser demonstrada sobre as equações
atuais antes de escolher sua chave definitiva.

Não basta trocar `Call` por `next`: o ponto do chamador pode ter um endpoint
exterior, enquanto a continuação do corpo conserva o endpoint da invocação.
Também não basta compartilhar conjuntos mutáveis de saídas iguais em um instante;
eles podem divergir depois. A estrutura compartilhada precisa representar a
relação permanente entre execuções e retornos compatíveis.

A referência mais próxima é
[Chatterjee, Kragl, Mishra e Pavlogiannis, Faster Algorithms for Weighted Recursive State Machines, ESOP 2017](https://bkragl.github.io/papers/esop2017.pdf),
§3 e Figura 2: representar configurações por autômatos, distinguir passos internos,
chamadas e retornos e reutilizar resumos entrada/saída. A sugestão para COBOL é uma
adaptação desse princípio, não adoção integral do framework ou de suas cotas.

O fundamento anterior é
[Bouajjani, Esparza e Maler, Reachability Analysis of Pushdown Automata, CONCUR 1997](https://archive.model.in.tum.de/um/bibdb/info/esparza.BEM97.shtml.html):
conjuntos de configurações podem ser representados por autômatos finitos em vez
de listar cada configuração individual. Escapes entre intervalos COBOL exigem
uma modelagem explícita; não assumir que todo programa já é uma RSM estruturada.

Esta é a proposta de maior potencial para reduzir o multiplicador medido,
incluindo suas entregas na fase de valores. O ganho de ordem de crescimento
ainda não foi medido. Há relações entrada/saída genuínas que continuarão existindo.

## Fatorar não significa inventar independência

Se muitos receptores usam a mesma continuação, guardar a relação através desse
nó compartilhado em vez de expandir cada par é uma fatoração. A literatura de
[Olteanu e Závodný, Factorised Representations of Query Results: Size Bounds and Readability, ICDT 2012](https://www.cs.ox.ac.uk/dan.olteanu/papers/oz-icdt12.pdf)
estuda representações exatas de relações usando união e produto sem expansão
plana obrigatória. Isso fundamenta a escolha de representação, não prova a
equivalência do solver COBOL.

Se A só admite X e B só admite Y, representar {A,B} x {X,Y} inventa A-Y e B-X.
Conservar rótulos, condições e relações de compatibilidade é obrigatório. Os
estados de dados continuam específicos quando produzem dependências diferentes;
compartilhar controle não autoriza perder essas correlações.

## Alternativas e prioridade

| Opção | Recomendação | Razão |
| --- | --- | --- |
| Bitsets dinâmicos e descarte de estado temporário | Primeiro passo | Ganho local já observado; troca de representação, menor mudança semântica. |
| Deltas e índices por entrada/assinante | Segundo passo | Evita rescans e objetos de deduplicação; mantém as relações específicas. |
| Continuação compartilhada, com retorno compatível | Correção estrutural prioritária | Ataca a criação de chamadas sintéticas que alimenta o produto. |
| Substituir toda a análise de valores por IFDS | Não recomendado nesta etapa | Aplicabilidade do domínio não demonstrada e alcance maior que a parede medida. |
| Desligar escapes, limitar profundidade, fundir estados indiscriminadamente | Não promover | Perda medida ou mudança de precisão sem contrato adequado. |
| Colapsar toda SCC em um resumo | Insuficiente sozinho | Ciclo comum não torna endpoints, retornos ou estados equivalentes. |

[Reps, Horwitz e Sagiv, Precise Interprocedural Dataflow Analysis via Graph Reachability, POPL 1995](https://doi.org/10.1145/199448.199462)
oferece tabulação precisa para fatos finitos e funções distributivas. A ideia de
resumos e retornos compatíveis é relevante; as hipóteses do teorema não foram
provadas para nosso domínio de textos, filtros, escritas e correlações. Não usar
o artigo para prometer tempo polinomial do analisador completo.

Reuso do controle entre passagens de demanda é uma oportunidade secundária:
`analyze` reconstrói o objeto quando novas células são descobertas, mas o controle
é montado antes da materialização dessas células. Extrair dados imutáveis de
controle pode evitar reconstruções, após verificar os callbacks e seus leitores.
Não é requisito para reproduzir a parede: a família medida fecha com uma passagem.

## Critérios para a implementação futura

Os passos são mudanças sucessivas no mesmo caminho canônico; o caminho anterior
é removido ao substituir sua representação. Overlays e ablações seguem somente
como instrumentos de investigação. Não criar modo especial para hubs ou GO TO 255.

Validar cada transformação com reinvocação semanticamente preservada. A sentinela
deve manter AFTER e ESCAPED; incluir retornos a chamadores diferentes, PERFORM THRU,
escapes para ancestrais, recursão, ciclo sem retorno e handlers. Os casos negativos
devem impedir que um ciclo fabrique continuação normal ou misture valores de
chamadores distintos. Comparar com o solver anterior nos tamanhos em que ele fecha
e usar oráculos explícitos nos demais.

Medir em heap constante e com proteção do host: pontos, chamadas reais/sintéticas,
relações ponto/saída, pares tentados/úteis, atualizações propagadas, entregas de
valores, memória por fase, tempo e GC. A meta estrutural é que atravessar uma
fronteira não introduza nova invocação lógica quando só houve continuação da mesma.
Observar a curva N=4/8/12/16/20/24 antes de extrapolar, sem exigir fórmula antecipada.

FAST, fixtures relevantes e 73 programas CardDemo são necessários antes de afirmar
paridade de produção. Igualdade de nomes com ablação não substitui essa validação.
Nenhuma dessas qualificações foi executada ou alegada nesta etapa documental.
