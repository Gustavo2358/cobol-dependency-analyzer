# Correção do controle no experimento de reaching definitions

Implementação `1797f16`, branch `experiment/dependency-witness-demand`.
Baseline `52c1b82dc6accbb615818cf5b5298843a85b0f01`; mesmo JAR congelado
SHA-256 `6bd96f31911976c097c9fb47377587c11bf3ebe6dc60305190104314bc39b036`.
Build qualificado: `rd-control-fix-20261009/build-02`.
Dados, hashes e métricas: [control-fix-results.json](control-fix-results.json).

## O que foi corrigido

A [auditoria dos adicionais](carddemo-additions-audit.md) encontrou duas
permissões indevidas na projeção experimental: seguir para o parágrafo seguinte
quando aquela invocação deve retornar, e admitir a continuação de um evento CICS
sem verificar sua disposição. A topologia do frontend já publica os limites,
as chamadas, os eventos e os registros necessários para distingui-los.

`DefinitionControl` substitui o antigo construtor incondicional do grafo físico.
É uma análise de alcance para preparar o mesmo reaching definitions, não um
fallback ao solver principal ou uma seleção de solver por construção/fixture.
Reutiliza as regras de limite e entrega do frontend/solver existente.

Imagine duas chamadas `PERFORM A` e `PERFORM A THRU B`. Ambas entram em A, mas
apenas a primeira precisa voltar no fim de A. O novo passo de controle acompanha
essa diferença com bits: em A, retira o bit da chamada curta da continuação e
deixa o bit do intervalo maior seguir para B. Chamadas com a mesma regra de
limite compartilham esse bit. As chamadas alcançáveis recebem os retornos de
sua regra; não se inventam retornos para todos os callsites cadastrados.

Os bits descrevem endpoint e escape por ancestral. Não guardam valores de
variáveis, pilhas enumeradas ou uma cópia do grafo por chamada. Os conjuntos
imutáveis iguais compartilham armazenamento. Após construir as arestas físicas,
os bits, registros e assinaturas de retorno são liberados antes das equações
de valores. O reaching definitions permanece compartilhado por todas as queries.

Para eventos CICS, uma pequena análise de registros HANDLE/IGNORE/default e
ABEND decide as arestas possíveis. Registrar um IGNORE em trecho inalcançável
não ativa a continuação. Um XCTL sem tratamento não passa para o próximo comando
somente porque a topologia informa a possibilidade de PGMIDERR. IGNORE,
handlers de condição/ABEND, CANCEL/RESET e conclusões explícitas NOHANDLE/RESP
mantêm seus fluxos publicados.

## Resultado semântico

| Grupo | Casos concluídos | Nomes perdidos | Nomes adicionais à main | Localizações alteradas |
|---|---:|---:|---:|---:|
| CardDemo | 73/73 | 0 | **0** (antes: 11) | 0 |
| Standalone | 36/36 | 0 | 30 (inalterado) | 32 (inalterado) |
| Adversariais existentes | 25/25 | 0 | 4 (inalterado) | 0 |

As **815 dependências do CardDemo** foram preservadas. Os 73 JSONs completos
ficaram iguais aos da baseline, incluindo os cinco pares CBL/CL2 auditados.
Não se usou o solver principal para preencher o resultado experimental.
Os 185 hashes do inventário de fontes/COPY foram conferidos pelo runner.

Quinze regressões novas têm oráculos pequenos definidos pelo fonte e foram
executadas sequencialmente na main, no experimento anterior e na correção.
Todas passaram na main e na correção; nove exibiram adicionais indevidos na
versão anterior. Cobrem limites simples e sobrepostos, fallthrough legítimo,
EXIT PARAGRAPH, XCTL default, IGNORE, registro inalcançável, substituição por
default, label de condição, retorno de handler dentro do intervalo, ABEND,
CANCEL/RESET e NOHANDLE/RESP. Os dois testes metamórficos com CONTINUE das
fixtures existentes também passaram.

## Escala das caixinhas

Família **dynamic-only**, dois hubs, todos os nomes provados através de
CALL TARGET-PGM; nenhum CALL literal usado como atalho de inventário.
Heap 512 MiB, RSS máximo 768 MiB, reserva do host 2 GiB, timeout 60 s e
max-work 100M. Qualificação semântica usou heap de 128 MiB. JVMs sequenciais.

| N por hub | Tempo | RSS MiB | Trabalho instrumentado | Nomes esperados encontrados |
|---:|---:|---:|---:|---:|
| 32 | 1,11 s | 115,8 | 16.697 | 33/33 |
| 128 | 1,46 s | 134,5 | 66.521 | 129/129 |
| 512 | 2,83 s | 239,3 | 265.817 | 513/513 |
| 2048 | **7,98 s** | **578,3** | 1.063.001 | **2049/2049** |

Para N=2048 há 4097 regras de limite, mas somente dois conjuntos distintos
de bits retidos ao fim do controle, somando 130 palavras de 64 bits. O grafo
tem 65.550 posições físicas e 81.935 arestas; os valores usam 12.291 definições.
Ao quadruplicar N de 512 para 2048, as posições, arestas, definições e o contador
de trabalho crescem aproximadamente 4x. Isso mede esta família, não prova
ausência de crescimento quadrático para qualquer programa; o contador não
contabiliza cada operação interna sobre palavras dos bitsets.

O experimento anterior terminava dynamic-only N=2048 em 7,48 s, RSS 545,5 MiB.
Essa medição anterior foi reutilizada, não reexecutada. As medições novas são
individuais: a diferença de cerca de 0,5 s não tem análise estatística. Uma run
intermediária da correção marcou 8,18 s e 579,5 MiB; ambas estão preservadas.
Negative-bound N=2048 terminou em 1,97 s, RSS 162,3 MiB, exatamente SAME,
sem ressuscitar DEAD após sua sobrescrita.

## Limites e evidências

O pareamento completo entre chamador e valor ainda é aproximado. A aresta física
de retorno pode unir valores de chamadas distintas mesmo que a regra de limite
seja a mesma. `return-matching` ainda acrescenta FIRST; `correlation` acrescenta
RIGHT. Os outros dois adicionais adversariais são da aproximação de índices.
Não se declara que todos os falsos positivos eram defeitos de controle, nem que
esta mudança recupera as correlações lógicas. O resultado permanece PARTIAL.

Com max-work=1, a versão final terminou explicitamente com RESOURCE_LIMIT e
exit 2, preservando byte a byte o arquivo de output anterior. Nenhuma run de
qualificação/escala teve OOM, timeout ou interrupção por guarda de memória.
O solver antigo não foi novamente submetido aos N grandes, pois seu timeout
na baseline já estava registrado.

Evidência bruta: `benchmark/results/rd-control-fix-20261009/`, com comandos,
telemetria, métricas, JSONs, hashes e fontes gerados. As duas primeiras tentativas
do runner de regressões permanecem separadas: a primeira teve erro no parser
de JSON do harness; a segunda expôs um oráculo de handler incorreto, pois seu
label estava fora do intervalo PERFORM. A fixture final testa explicitamente
o intervalo THRU que inclui esse label; nenhum output antigo foi alterado.

Produção `src/`, POM e scripts permanecem inalterados. Não houve push, PR ou
merge desta correção. FAST da aplicação principal passou novamente: **1093
testes, 142 suítes, zero falhas/erros/skips**, 62,93 s, pico agregado de RSS
278,6 MiB. Esses testes não executam o overlay; seus testes reais são os
CLI/oráculos acima.

A primeira tentativa de FAST esgotou o heap de 128 MiB ao compilar toda a
aplicação dentro do processo Maven, antes dos testes. A segunda separou javac
do Maven com `maven.compiler.fork=true`, mantendo **128 MiB por JVM**, sem
alteração de fonte/POM e com a mesma guarda agregada de RSS. Ela compilou e
passou o FAST. Logs da tentativa inicial e da final estão preservados em
`fast-final/` e `fast-fork/`. Os hashes de produção iguais aos da main estão
registrados no JSON de resultados.

A [auditoria posterior dos 30 adicionais standalone](standalone-additions-audit.md)
classifica os mecanismos por fonte, com contrastes separados de condição,
pareamento de chamadas e armazenamento repetido.
