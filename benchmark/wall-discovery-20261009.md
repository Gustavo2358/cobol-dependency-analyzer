# Discovery das paredes até a convergência dos hubs

A fixture-alvo de **32 caixinhas por hub** convergiu com a reinvocação de escape
desligada e uma intervenção no armazenamento dos resultados de controle.
O contraste de **36 caixinhas por hub** também convergiu. Nesta família e nesta
escala encontramos **duas famílias de problema**, não uma sequência longa de
novos algoritmos bloqueando o término. Isso dimensiona o testemunho local;
**não confirma a viabilidade, correção ou comportamento do programa privado**.

Todos os hacks permanecem no overlay de profiling. Nenhum fonte em src/main
ou JAR de produção mudou. A ablação de escape continua perdendo uma dependência
na sentinela; concluir a execução diagnóstica não qualifica sua semântica.

## Alvo, pins e proteção da memória

Foi mantida a fixture `return-fanout`, do [gerador de hubs](generate-hub-dispatch.py):
dois hubs com seletores e flag renovados por ACCEPT, GO TO DEPENDING ON,
caixinhas com CALL literal, EXIT PARAGRAPH e PERFORM condicionais e GO TO para
o outro hub, mais uma saída terminal. O número N significa caixinhas **por hub**;
N=32 tem 64 parágrafos de caixinha. Não há OCCURS. Casos concluídos têm uma
passagem de demanda. Os candidatos esperados são PGM00000 até PGM(N-1).

Baseline main: `87255b08d38456efbc3caf3bd6082f03dee82362`. HEAD ao iniciar:
`6db626bfba2d5c9fd9c8364b1d14b324d8064de4`. JAR SHA-256:
`b7cc0654207091e1872071a7a3267547daa170ce9b1fb1026702c99e69fa5ab1`.
Temurin 21.0.12.1+1. As oito execuções da matriz usaram heap **256 MiB**,
max-work **100.000.000**, limite RSS **512 MiB**, reserva do host **2.048 MiB**,
timeout 35 s (50 s nos dois primeiros casos e 20 s no caso pequeno). Nenhum
guard foi acionado. Menor MemAvailable amostrado: **3.768,2 MiB**; maior RSS:
**443,3 MiB**, no primeiro experimento de descarte.

As JVMs de análise rodaram sequencialmente. Os cinco testes da sentinela usaram
heap 128 MiB e RSS máximo 384 MiB; o teste do conjunto compacto usou 64 MiB.
Compiladores e leitores JFR usaram até 128 MiB. Não houve aumento de heap.
JFR profile: janela até 45 s e retenção configurada 24 MiB. GC log e telemetria
de CPU/RSS a cada segundo nos casos perfilados. Tempos são observações únicas,
não medianas ou projeções para o fonte privado.

Dados normalizados: [JSON](wall-discovery-20261009.json). Fontes gerados,
manifests com hashes/compilação, comandos, perfis, stdout/stderr, JSONs de saída,
telemetria, testes e leituras JFR estão em
`benchmark/results/wall-discovery-20261009/` (ignorado pelo Git).

## A sequência operacional observada

```text
Fixture N=32, JAR original
    |
    +-- falha na construção dos resumos de controle
    |
    | intervenção 1: desligar reinvocação de escape
    v
Controle fecha; guarda 3,03 milhões de relações ponto/saída
    |
    +-- falha ao montar o grafo reverso dos ciclos
    |
    | intervenção 2A: descartar resultados após a construção
    |            OU 2B: armazenar os mesmos conjuntos em bitsets
    v
Ciclos -> ordem -> relevância -> valores -> consultas -> JSON
```

O original N=32 falha com OOM em **4,60 s**. Há 22 Full GCs e 1,96 s de pausas.
No JFR, 61/63 amostras da main contêm DependencyControl; 51/63 passam pelas
linhas 44–48 do construtor (2 na 44, 17 na 45, 19 na 47 e 13 na 48).
São os laços que entregam combinações chamada/saída e unem resultados completos
dos sucessores. O [discovery anterior](escape-ablation-20261009.md) já isolou
a reinvocação como multiplicador e mostrou sua repercussão na fase de valores.
Este novo caso confirma a parede inicial no JAR original da **mesma fixture-alvo**.

Depois de desligar somente a reinvocação, o controle N=32 fecha:
59.151 planos, 274.561 pares entregues, 3.029.390 entradas nos resultados e
6.772.441 membros examinados nas uniões. Cada conjunto tem no máximo 65 saídas.
O postorder dos ciclos termina, mas a montagem do grafo reverso falha antes
da marca reverse-end. A execução termina com OOM em **7,92 s**, com 33 Full GCs
e 3,46 s de pausas. Não chega à relevância ou aos valores.

O grafo reverso não estava revelando outro crescimento combinatório: ao liberar
os resultados anteriores, o mesmo cálculo termina com **75.792 arestas**.
Essa é evidência causal de pressão por estruturas de uma fase anterior ainda
vivas enquanto a fase seguinte precisa de memória adicional.

## Intervenção 2A: encurtar a vida dos resultados

A busca por leitores mostrou que DependencyControl.results é consultado durante
a construção do solver. Os métodos posteriores de ciclos, ordenação e sucessores
usam o grafo dos planos; a análise de valores usa seus sucessores e os planos
de continuação. Há também um leitor de **tamanho** em DependencyAnalyzer,
para a métrica controlSummaries.

O primeiro hack foi results.clear() ao fim do construtor, depois do cálculo de
alcançabilidade e observação. Ele atravessou a parede e concluiu N=32 em **8,15 s**.
Seu efeito colateral na métrica foi controlSummaries=0, pois o analisador conta
as chaves do mapa. Isso **não significa que deixou de haver resumos**; seus
59.151 planos continuaram vivos. Essa medição inicial permanece preservada.

O overlay-v2 substitui os valores do mapa por Set.of(), conservando as chaves
para a métrica. Assim, os conjuntos e suas entradas tornam-se coletáveis, mas
controlSummaries continua em 59.151. N=32 conclui em **7,43 s**, com 7 Full GCs
e 0,91 s de pausas. O código não força GC. Os JSONs das duas versões do descarte
são byte a byte iguais; os contadores de valores também coincidem.

Essa intervenção parece compatível com os leitores existentes, mas ainda é
diagnóstica e não passou pela qualificação de produção/corpus.

## A mesma parede aparece mais cedo em N=36

Mantendo apenas reinvocação desligada e descarte final, N=36 falha dentro do
solver, **antes de chegar ao descarte**, em 5,56 s. O último probe tem 74.607
planos e 3.466.497 entradas de resultados, ainda sem ponto fixo. Portanto, o
descarte não remove o pico durante a construção.

Isso não foi contado como terceira família de problema. É o armazenamento das
relações de saída, já identificado, ultrapassando o mesmo heap em outra fase
de sua vida. Um hack de ciclo não resolveria esse contraste.

## Intervenção 2B: reduzir a representação durante a construção

O segundo hack para essa parede troca **somente os conjuntos persistidos em
DependencyControl.results** por AbstractSet<Exit> apoiado em BitSet. Um índice
compartilhado atribui um inteiro a cada Exit distinto. Cada ponto armazena bits
dos membros de seu conjunto, em lugar de entradas HashMap/HashSet individuais.
O teste de presença, a inserção, a cardinalidade e a iteração continuam operando
sobre as saídas específicas. Não foi colocado teto nem descartado membro.

Os laços e as uniões do solver continuam os mesmos; os temporários, planos,
conjuntos de pares Returned e fila não foram substituídos. Portanto, este hack
reduz representação, não elimina o custo lógico de propagar muitas relações.

N=32 conclui em **7,13 s**, sem descarte final, conservando exatamente os mesmos
3.029.390 resultados históricos, planos, pares e visitas de valores do contraste
com HashSets. Seus JSONs também são byte a byte iguais. O heap usado amostrado
ao fim da construção cai de cerca de 251 MiB no baseline desta rodada para
174 MiB com bitsets; esses valores incluem lixo ainda não coletado.

N=36 conclui em **7,99 s**, também **sem descarte final**. O controle armazena
**4.289.918 relações ponto/saída**, com universo de **73 saídas distintas**,
74.607 planos e 388.945 pares entregues. Esse tamanho mostra por que uma
representação por bits foi suficiente para passar pela construção.

## O que apareceu após atravessar as paredes

| Fase | N=32, descarte v2 | N=32, bitsets | N=36, bitsets |
| --- | ---: | ---: | ---: |
| Controle | 2,55 s | 1,86 s | 2,19 s |
| Ciclos | 0,39 s | 0,43 s | 0,57 s |
| Ordem de fluxo | 0,20 s | 0,21 s | 0,25 s |
| Assinatura inicial/relevância | 0,60 s | 0,65 s | 0,77 s |
| Propagação de valores | 2,24 s | 2,64 s | 2,82 s |
| Resolução de consultas | 0,11 s | 0,09 s | 0,10 s |

Setup, fechamento de demanda, footprint e especialização estão nos dados brutos.
As fases somam tempo de parede, inclusive GC; não equivalem a CPU exclusiva.
Após as intervenções, valores e controle dividem o custo, mas **ambos terminam**.
Não houve terceira fase bloqueando a convergência nesses dois tamanhos.

N=32 fecha 194 contextos, 50.371 estados BEFORE, 587.205 visitas e 536.834
entregas de valores; N=36 fecha 218 contextos, 63.579 estados BEFORE, 825.917
visitas e 762.338 entregas. A relevância tem uma extensão, respectivamente
59.086/74.534 nós e 71.631/90.375 arestas. Consultas fecham 8.192/10.368 visitas.
Todos os candidatos exatos esperados estão presentes nas execuções concluídas.

O JFR de N=36 com bitsets distribui suas 264 amostras da main entre controle
(111), fila de valores (58), outros métodos de valores (52), relevância (40)
e poucos outros frames. Não aponta uma fase oculta que continuou rodando após
os contadores terminarem. Amostras de alocação são volume acumulado estimado,
não heap retido; o relatório não as trata como histograma de objetos vivos.

## Contrastes de correção e validação

Um teste diferencial com Random(1), 10.000 operações e nove conjuntos compartilhando
o índice fez **550.590 verificações** contra HashSet: inserções, duplicatas,
addAll, cardinalidade, contains, igualdade nos dois sentidos, hashCode, iteração
e esgotamento do iterador. Isso cobre as operações usadas pelo solver; não
qualifica a classe como substituto completo para todo uso possível de Set.

Na [sentinela](fixtures/control-return-fanout/escape-ablation-sentinel.cbl),
overlay-v2 padrão, descarte sozinho, bitsets sozinhos e ambos com reinvocação
ativa produzem JSON byte a byte igual ao JAR original: AFTER e ESCAPED.
Com reinvocação desligada e ambos os hacks, permanece apenas AFTER. A perda
conhecida da primeira ablação **continua existindo**.

No caso N=8 com reinvocação ativa, bitsets + descarte produzem JSON byte a byte
igual ao original anterior, mantendo 260 contextos, 959.355 visitas e 933.926
entregas. Visitas de controle variam em uma entre algumas JVMs; ordem dos
conjuntos pode mudar. Não se usa igualdade de tempo como oráculo.

Verificados: sintaxe dos scripts, construção dos overlays, hashes de fontes,
transformação exata do overlay final, rejeição de âncora alterada, rejeição de
opções sem overlay compatível e propagação de falha pelo wrapper. Matrizes com
OOM retornam 1, com analisador retornando 2; não foram relabeladas como PASS.
Toda intervenção força productionQualified=false, mesmo se nomes coincidirem.
FAST/CardDemo não foram executados: não houve alteração no produto nem alegação
de equivalência do overlay ablado. Sua perda semântica está explicitamente medida.

## Reprodução e aplicação da investigação

```sh
python3 -B benchmark/profiling/build-control-probe.py /tmp/wall-probe \
  --flow-phases --wall-probes --jdk /caminho/do/jdk

# Execute com Java 21 no PATH; cada diretório de saída deve ser novo.
python3 -B benchmark/run-hub-dispatch.py /tmp/hub-result-release \
  --mode return-fanout --boxes 32 --heap 256 --rss 512 \
  --system-reserve 2048 --max-work 100000000 --timeout 35 \
  --overlay /tmp/wall-probe/classes --noescape --release-control-results \
  --profile --profile-duration 45 --telemetry

python3 -B benchmark/run-hub-dispatch.py /tmp/hub-result-compact \
  --mode return-fanout --boxes 32 36 --heap 256 --rss 512 \
  --system-reserve 2048 --max-work 100000000 --timeout 35 \
  --overlay /tmp/wall-probe/classes --noescape --compact-control-results \
  --profile --profile-duration 45 --telemetry
```

Propriedades Java, somente no overlay: probe.noescape,
probe.releaseControlResults e probe.compactControlResults. São independentes
e desligadas por padrão. Use saída diagnóstica separada; o JAR sozinho não tem
esses mecanismos. Compilar para o fonte privado exige seus fontes e JAR
correspondentes; âncoras diferentes exigem revisão da instrumentação.

## Base para a decisão

O resultado local favorece continuar investigando uma correção: duas famílias
observadas, a segunda com intervenções pequenas que permitem chegar ao fim,
e nenhum algoritmo posterior exigindo outra ablação até N=36. Há um problema
de amplificação de trabalho e um problema de representação/vida útil dos
resultados; não há evidência, nessa escala, de uma cadeia interminável de paredes.

Ainda falta corrigir a reinvocação mantendo seus retornos legítimos. Essa
correção pode conservar mais fluxo do que a ablação e expor outros custos.
Bitsets e descarte precisam de qualificação antes de qualquer promoção ao
produto. Não foram explorados tamanhos maiores, corpus completo ou a execução
corporativa nesta etapa. Não extrapolamos tempo ou memória para 117k LOC,
255 destinos, seletores diferentes, dependências dinâmicas ou mais variáveis
relevantes a partir deste testemunho. Nenhuma publicação ou merge foi feito.
