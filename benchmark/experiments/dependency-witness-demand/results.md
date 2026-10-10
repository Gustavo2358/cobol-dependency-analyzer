> Historical scalar witness prototype at 85a91e9. Current solver and measurements: [sparse reaching definitions](reaching-definitions-results.md).

# Resultado do experimento de busca por testemunhos

A hipótese teve resultado positivo nas famílias exercitadas: parar de procurar
provas redundantes retirou o crescimento quadrático medido. Isso **não qualifica
um substituto geral** para a análise atual. O protótipo também aproxima controle
e ainda não admite boa parte dos recursos do produto.

## Separando a poda da mudança de semântica

`full` e `stop` usam a mesma representação e a mesma aproximação de controle.
`full` esgota cada query; `stop` deixa de buscar nomes já encontrados em uma query
anterior, preservando a primeira localização da **mesma análise aproximada**.
Portanto a diferença entre esses dois modos mede a poda. Comparar somente o
protótipo com main confundiria essa melhoria com a perda de precisão de controle.

Tempos totais de execuções individuais, sem alegação estatística. Heap 512 MiB,
RSS máximo 768 MiB, reserva do host 2 GiB, timeout 60 s, max-work 100M. Nenhum
OOM nem encerramento pela proteção de memória ocorreu nas medições finais.

| N, caixinhas por hub | main | Busca completa | Busca com poda | Trabalho completo / com poda |
|---:|---:|---:|---:|---:|
| 32 | 3,33 s | 1,06 s | 1,06 s | 32.653 / 1.652 |
| 64 | 13,13 s | 1,36 s | 1,16 s | 126.733 / 3.284 |
| 128 | timeout 60 s | 1,67 s | 1,37 s | 499.213 / 6.548 |
| 512 | não reexecutada | 5,45 s | 2,58 s | 7.895.053 / 26.132 |
| 2.048 | não reexecutada | timeout 60 s | 6,42 s | incompleto / 104.470 |

Na busca completa, dobrar N de 32 para 64 aproximadamente quadruplica o trabalho.
Com a poda, quadruplicar N de 512 para 2.048 quadruplica o trabalho. É crescimento
aproximadamente linear **nesta família**, não uma prova de complexidade universal.
A maior execução final usou RSS máximo de 498,6 MiB e encontrou todos os 2.049
nomes esperados. Não houve aumento de heap para fazê-la passar.

As saídas completas, inclusive localizações, coincidiram entre `full` e `stop`
onde ambos terminaram: N=32,64,128,512. N=32 e 64 também coincidiram com main.
N=128,512,2048 têm oráculo independente de nomes; não existe uma saída concluída
de main nessa campanha para afirmar igualdade de suas localizações nesses N.

## Uma fraqueza da poda positiva e a correção adicional

Outro adversário declara VALUE 'DEAD', sobrescreve-o com 'SAME' antes do hub,
e coloca CALL P em todas as caixinhas. O limite conservador contém DEAD, mas ele
não chega a nenhum CALL. A parada positiva sozinha repete a busca desse nome
impossível a cada query. Isso expõe uma fraqueza **do novo protótipo**, não uma
nova falha reproduzida da main: a main já termina essa família rapidamente.

O cache de ausências compartilha somente buscas esgotadas. Os nomes pendentes
só diminuem; uma região já percorrida que não fornece nenhum desses nomes não
precisa ser percorrida novamente. Não se registra ausência quando uma busca
foi interrompida porque já encontrou os candidatos.

| N | Trabalho sem cache / com cache | Tempo sem cache / com cache | main |
|---:|---:|---:|---:|
| 128 | 50.186 / 910 | 0,96 s / 0,91 s | 1,01 s |
| 512 | 790.538 / 3.598 | 1,42 s / 1,16 s | 1,31 s |
| 2.048 | 12.599.306 / 14.350 | 4,04 s / 1,87 s | 2,22 s |

Nos três tamanhos, main, poda sem cache e poda com cache produziram o mesmo JSON.
O cache retirou outra fonte de crescimento quadrático, sem criar listas de
ambientes nem guardar as combinações de valores da execução.

## Dependências, precisão e cobertura

Os dez novos casos passaram pela CLI real nos três modos. Nenhum nome existente
foi perdido. Dois casos negativos registram **um falso positivo cada**: condição
correlacionada e retorno de PERFORM misturado. Os resultados de `full` e `stop`
foram idênticos: esses falsos positivos vêm da aproximação de controle, não da
poda. Foram exercitados kill de MOVE, ACCEPT preservando candidatos, texto com
larguras diferentes, código inalcançável, candidato que aparece depois de uma
busca negativa, não retorno, queries repetidas e caminhos repetidos.

| Conjunto existente | Tentativas | Concluídos | Não suportados | Nomes perdidos / adicionais | Localizações alteradas |
|---|---:|---:|---:|---:|---:|
| Fixtures standalone | 36 | 14 | 22 | 0 / 5 | 32 |
| CardDemo | 73 | 10 | 63 | 0 / 0 | 0 |

As 32 localizações antecipadas e os cinco nomes extras das fixtures vêm dos
casos de correlação de chamadores. A busca sem poda também produz exatamente
essas diferenças: a ablação dos seis casos com queries dinâmicas confirmou
igualdade do JSON entre os modos.

**Não há evidência de paridade completa com CardDemo.** Seis dos dez programas
concluídos não tinham queries de valores; os outros quatro só tinham queries
literais. Nenhum caso CardDemo com busca dinâmica foi qualificado. Os 63 casos
recusados continuam sendo gaps, não passes nem programas sem dependências.

Razões explícitas das 85 recusas no conjunto agregado: controle de eventos/I/O
(38), referências indexadas/parciais (22), statements sem modelo admitido (22)
e controle desconhecido (3). O protótipo não aciona o solver antigo como fallback.
Remainders desconhecidos são conservadores e a paridade de diagnósticos/exit
codes não é alegada.

## Evidência e decisão

Fonte principal inalterada no SHA `52c1b82dc6accbb615818cf5b5298843a85b0f01`.
Overlay final: `build-06`, hashes em `results.json`. A primeira medição de escala
pequena usa `build-05`, antes do cache de ausências; a busca positiva é a mesma.
`results.json` guarda o resumo e hashes dos resultados brutos. Logs, entradas,
saídas, comandos e telemetria ficam em
`benchmark/results/witness-demand-20261009/`. As tentativas de desenvolvimento
com fixtures inválidas e o erro inicial de ACCEPT estão preservadas; foram
corrigidos antes das medições finais, sem alterar outputs históricos.

Não foi reexecutado FAST: produto, POM e scripts permanecem idênticos à main.
A qualificação anterior vale somente para esse produto inalterado. O experimento
foi compilado e recebeu 1.024 verificações da composição de truncamento/padding,
casos positivos/negativos, ablações e as 109 tentativas de corpus.

Recomendação: continuar a linha de queries orientadas aos nomes ainda não
comprovados, com compartilhamento de buscas negativas. O ganho não dependeu de
compactar objetos ou aumentar heap. Antes de integrá-la ao produto, é necessário
preservar retornos válidos/condições e cobrir as operações hoje recusadas;
os resultados atuais não autorizam afirmar que essa etapa já foi concluída.
