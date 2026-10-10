# Auditoria dos 30 adicionais standalone

Implementação auditada `1797f16`, build `rd-control-fix-20261009/build-02`, main
`52c1b82dc6accbb615818cf5b5298843a85b0f01`. Os 30 adicionais estão em **21 das
36 fixtures standalone**. Contamos um nome adicional por fixture/programa/tipo,
não instruções CALL nem programas distintos. Variantes de tamanho contam
separadamente. As 32 mudanças de localização, CardDemo e o grupo separado de
25 adversariais não entram nesta contagem.

Cada ocorrência foi confrontada com o fonte, o resultado congelado da main,
o resultado experimental e as regras de transferência. Os 36 hashes de fonte
e os hashes do JAR/build foram conferidos. A comparação do corpus é evidência
reutilizada, não uma nova execução. Nove contrastes pequenos foram executados
na main e no experimento: **18 runs novas**, todas concluídas com os resultados
previstos, heap de 128 MiB, RSS limitado a 768 MiB, reserva de 2 GiB e timeout
de 60 s. Pico observado: **107,4 MiB RSS**.

## Quantidade por mecanismo

| Categoria primária | Adicionais | Exemplo |
|---|---:|---|
| Condições ignoradas | **5** | Admite ramo impossível ou conserva INITIAL após ignorar todos os IFs |
| Mistura entre posições de OCCURS | **15** | Conteúdo de DEST(2) aparece em CALL DEST(1) |
| Sobrescrita de OCCURS não elimina o valor antigo | **9** | GOOD substitui OLD na mesma posição, mas ambos permanecem |
| INITIALIZE integral conserva o valor anterior | **1** | OLD sobrevive a INITIALIZE TABLE-DATA |
| **Total** | **30** | **25 ligados a OCCURS; 5 a condições/contexto** |

As categorias são uma contagem primária, não causas necessariamente exclusivas.
Dois dos cinco adicionais da primeira linha também podem surgir pela mistura
entre chamadas. São contados uma vez; essa sobreposição foi testada separadamente.

## Os cinco ligados a condições e chamadas

Em caller-correlation, o único CALL observado ocorre após FLAG='0'. UNOBS é
escrito no ELSE dessa condição; a execução posterior com FLAG='1' não tem CALL.
Eliminando a segunda invocação e deixando **um único chamador**, o experimento
continua acrescentando UNOBS. Ignorar a condição já basta: um retorno dirigido
a outra chamada não é necessário para produzi-lo.

Em caller-many-12/24, cada MODE atribuído tem um IF que escreve o programa
correspondente. INITIAL nunca deve sobreviver ao BODY. O experimento admite
que todos esses IFs sejam falsos, retendo INITIAL. O último nome, PGM00011/23,
é atribuído somente na última invocação, que não tem CALL posterior. Eliminando
essa última invocação, o adicional persiste: o IF do último MODE permanece no
fonte e pode ser admitido sem que FLAG jamais tenha esse valor.

Há também um segundo mecanismo para o último nome. Nos contrastes
no-predicates-12/24, atribuímos os valores diretamente **antes** de cada PERFORM,
e BODY contém somente EXIT. Não existe IF. A main exclui o último nome, mas o
experimento o entrega a CALLs anteriores pela união dos valores no retorno
compartilhado. A correção dos limites não recuperou o pareamento completo entre
chamador e valor. Não atribuímos os cinco exclusivamente a esse pareamento.

## Os 25 ligados a OCCURS

O experimento reúne o conteúdo das posições num canal por declaração repetida.
A leitura de tabela não distingue o índice. A escrita é fraca para repeated:
conserva o valor anterior para não apagar conteúdo de outras posições. Essas
duas escolhas explicam 24 dos adicionais, inclusive em fontes lineares sem IF.
REDEFINES, dimensões aninhadas e índices aritméticos exibem o mesmo problema
espacial, não uma nova enumeração de caminhos.

Em partial-cell, DEST(1) contém PROGA001 e DEST(2) contém OTHER. A alteração do
quinto caractere de DEST(1) deveria produzir apenas PROGB001. Aplicada ao resumo
que contém OTHER, fabrica **OTHEB**, nome que nenhuma posição concreta contém.
OTHER é mistura espacial; PROGA001 é valor antigo conservado; OTHEB combina
mistura espacial e transformação parcial. OTHEB é contado na categoria espacial.

INITIALIZE é um caso diferente: resetar a tabela inteira não exige saber o
índice de cada leitura. initialize chama write(..., false), mas writeLocal torna
a escrita fraca para repeated. A substituição forte posterior depende de
tableValueIds, ausente no experimento que eliminou os slots. OLD permanece.
É uma falha na integração do reset com o resumo, não uma necessidade inevitável
de materializar todos os elementos.

Os contrastes scalar-overwrite/one-cell-overwrite confirmam o problema mesmo
com OCCURS 1: o escalar elimina OLD e o repeated conserva. Os contrastes
scalar-initialize/one-cell-initialize mostram a mesma diferença para o reset.
Nenhum deles contém IF ou PERFORM.

## Classificação por fixture

| Fixture | Nomes adicionais e classificação primária |
|---|---|
| precision-caller-correlation | UNOBS: Condições ignoradas |
| precision-caller-many-12 | INITIAL, PGM00011: Condições ignoradas (último PGM também por mistura entre chamadas) |
| precision-caller-many-24 | INITIAL, PGM00023: Condições ignoradas (último PGM também por mistura entre chamadas) |
| occurs-sparse-100 | OLD: Sobrescrita de OCCURS não elimina o valor antigo; OTHER: Mistura entre posições de OCCURS |
| occurs-sparse-1000 | OLD: Sobrescrita de OCCURS não elimina o valor antigo; OTHER: Mistura entre posições de OCCURS |
| occurs-sparse-5000 | OLD: Sobrescrita de OCCURS não elimina o valor antigo; OTHER: Mistura entre posições de OCCURS |
| occurs-sparse-50000 | OLD: Sobrescrita de OCCURS não elimina o valor antigo; OTHER: Mistura entre posições de OCCURS |
| occurs-nested-10 | OTHER: Mistura entre posições de OCCURS |
| occurs-nested-50 | OTHER: Mistura entre posições de OCCURS |
| occurs-nested-200 | OTHER: Mistura entre posições de OCCURS |
| occurs-unknown-content | OLD: Sobrescrita de OCCURS não elimina o valor antigo |
| occurs-unknown-write | OTHER: Mistura entre posições de OCCURS |
| occurs-unknown-then-known | OLD: Sobrescrita de OCCURS não elimina o valor antigo |
| occurs-all-overwritten | OLD: Sobrescrita de OCCURS não elimina o valor antigo |
| occurs-initialize | OLD: INITIALIZE integral conserva o valor anterior |
| occurs-alias-write | OLD: Sobrescrita de OCCURS não elimina o valor antigo; OTHER: Mistura entre posições de OCCURS |
| occurs-alias-weak | OTHER: Mistura entre posições de OCCURS |
| occurs-full-text | FIRST: Mistura entre posições de OCCURS |
| occurs-partial-cell | OTHEB, OTHER: Mistura entre posições de OCCURS; PROGA001: Sobrescrita de OCCURS não elimina o valor antigo |
| occurs-index-through-perform | OTHER: Mistura entre posições de OCCURS |
| occurs-arithmetic-index | OTHER: Mistura entre posições de OCCURS |

## Ressalva sobre entrada desconhecida

Em occurs-unknown-content, MOVE INPUT-X TO DEST(3) elimina a origem local do
literal OLD. A main mantém um restante desconhecido; o experimento conserva OLD
como candidato concreto da escrita anterior. Essa retenção é demonstrável.
Contudo, INPUT-X é desconhecido no contrato da análise e poderia coincidir com
OLD: **não foi provado que o nome seja impossível para qualquer entrada**.
Ausência na baseline não é prova automática dessa impossibilidade.

Nos outros 29 adicionais, existe incompatibilidade concreta entre o candidato e
a execução descrita pelo fonte, considerando acessos válidos às tabelas. Índices
fora dos limites permanecem restantes; não demonstram que uma atribuição em
outra posição forneceu aquele nome concreto à posição consultada.

## Evidência e limites

O [JSON da auditoria](standalone-additions-audit.json) registra as 30 ocorrências,
as categorias primárias/secundárias, os motivos individuais, localizações,
fontes numerados e hashes dos outputs. Runs novas, comandos, contrastes,
outputs e telemetria: `benchmark/results/standalone-rd-audit-20261009/`.
Nenhum fonte original, oráculo ou implementação foi alterado.

FAST e o corpus completo não foram reexecutados: só mudaram evidências/docs,
e os executáveis qualificados continuam iguais por hash. Esta distribuição
descreve fixtures adversariais, não estima a frequência em programas reais.

## Subsequent correction

The sparse-table/INITIALIZE implementation and fresh qualification are recorded
in [table-fix-results.md](table-fix-results.md). It removes the 25 storage
additions audited here; the five predicate/caller-correlation additions remain.
This audit describes the earlier build and its original evidence, which has not
been rewritten.
