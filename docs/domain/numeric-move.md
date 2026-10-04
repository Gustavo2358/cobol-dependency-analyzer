# MOVE, tipos numéricos e transferências locais — SP 2.66

## Contrato e regras correntes

O frontend publica `scalarNumber` (precisão, escala, sinal, representação e TRUNC)
e `numericTransfers` por receptor, além das provas textuais e regionais existentes.
Este caminho substitui o antigo recorte de inteiro DISPLAY. DISPLAY, PACKED,
BINARY e COMP-5 têm regras próprias; o lower recebe descritores tipados, sem
reinterpretar PICTURE ou COBOL. O tipo não concede armazenamento independente.

MOVE numérico alinha pelo ponto decimal, ajusta sinal/escala e aplica a capacidade
do receptor. BINARY respeita TRUNC explicitamente fornecido; opção ausente não
seleciona STD/BIN/OPT. Resultado sem valor comum continua uma transferência
reconhecida, com resultado Unknown e causa preservada. COMP-5 usa sua largura
binária; edição numérica é uma receita tipada e comprimida.

MOVE alfanumérico não JUSTIFIED conserva o prefixo e preenche com espaços.
`textAdjustment` publica regra e extensão, sem expandir a PICTURE. ZERO é distinto
do literal 0; LOW/HIGH não inventam CCSID ou collation. Acessos compartilhados,
fatias e grupos só recebem fatos positivos quando suas próprias provas existem.
Provenance aproximada de COPY não invalida automaticamente um fato tipado.

O índice de declarações e USAGE herdado é memoizado; receptores são visitados na
ordem fonte. Geometria de intervalos e aliases equivalentes usa O(n log n), sem
enumerar posições de OCCURS/PICTURE. Valores/texto seguem expressões comprimidas.
Não há produto cartesiano de receptores, aliases ou opções de compilação.

Fontes primárias: [IBM elementary MOVE](https://www.ibm.com/docs/en/cobol-zos/6.4.0?topic=statement-elementary-moves),
[Language Reference 6.4](https://publibfp.dhe.ibm.com/epubs/pdf/igy6lr40.pdf),
[USAGE](https://www.ibm.com/docs/en/cobol-zos/6.4.0?topic=entry-usage-clause) e
[TRUNC](https://www.ibm.com/docs/en/cobol-zos/6.4.0?topic=options-trunc).
O [relatório da prioridade 2](../work/numeric-move-full.md) delimita capacidades,
regressão, inventário por ocorrência e fronteiras restantes.

## Destinos mistos na mesma instrução

IBM Enterprise COBOL 6.4 Language Reference, MOVE (p. 401–404), determina
transferências na ordem escrita e ajuste conforme cada receptor. Um emissor
numérico inteiro pode fornecer tanto receptor numérico quanto texto; uma
PICTURE editada admite escala fracionária. Um número fracionário para texto
alfanumérico comum não recebe essa prova. Fonte primária:
https://publibfp.dhe.ibm.com/epubs/pdf/igy6lr40.pdf.

O inventário numericTransfers conserva um certificado por receptor conhecido,
na ordem do fonte. Um receptor textual com descriptor de edição recebe o valor
numérico de origem; o lowering aplica o mesmo formatador usado pelo MOVE
simples. O receptor numérico recebe seu ajuste de sinal, escala e truncamento.
DATA só atravessa o prefixo de receptores provados; um receptor desconhecido
pode compartilhar a origem e interrompe a prova para os seguintes. Literais
não têm essa dependência de memória. Não há produto cartesiano: O(r)
receptores, além da saída AIR das projeções de famílias textuais.

Oracle: S9(4) com -23 para 99, X(6), +9999 e 99 produz 23, "0023  ",
"-0023" e 23. Literal -23 para +9999 e 99 produz "-0023" e 23.
Receptor NATIONAL sem modelagem e origem fracionária para X conservam gaps.

## Texto para número

Para emissor alfanumérico e receptor numérico, a regra IBM 6.4 MOVE o interpreta
como inteiro sem sinal. O certificado de DATA conserva a leitura textual,
sem afirmar que o conteúdo de runtime é válido nem publicar uma constante.
O consumidor testa a sequência não vazia de dígitos 0–9; somente a alternativa
válida executa conversão e ajuste. A alternativa inválida conserva controle e
efeitos desconhecidos, inclusive continuação possível. Não há trim, sinal
implícito, parse de PICTURE no consumidor ou fallback zero. O comprimento
tipado limita a prova comum de TRUNC quando nenhuma opção foi fornecida.
Oracle: "00052" para S999V99 COMP-3 produz 52.00; " 052", "A052" e espaços
seguem a alternativa explícita de dados inválidos. Fonte: IBM 6.4 Language
Reference p. 404 e NUMCHECK (dados numéricos incompatíveis).

## LOW/HIGH VALUES em receptores textuais

A categoria canônica distingue FIGURATIVE_LOW e FIGURATIVE_HIGH de texto literal
e de ZERO. A fonte IBM Language Reference 6.4, figurative constants, define a
repetição do caractere extremo da sequência de ordenação até preencher o receptor.
Sem prova dessa sequência, a publicação não inventa um logicalValue. A prova local
autoriza a escrita de todos os caracteres do receptor ou da fatia publicada;
sem prova de acesso, o gap permanece. O custo depende dos receptores e dos
segmentos declarados, sem expandir a extensão.

Oracles: fatia (3:2), múltiplos destinos, PIC X(1000000000), e receptor numérico
sem autoridade de escrita textual. Esta mudança invalida a admissão de literais
especiais e sua tradução; não invalida as regras aritméticas já qualificadas.

## Tipo numérico em memória compartilhada

PICTURE e USAGE determinam o descritor numérico mesmo quando REDEFINES impede
a prova de célula independente. A regra de MOVE não depende de alocação separada.
A prova de tipo exige fatos locais de declaração e um bound de armazenamento
publicado e disponível; COPY/modelo ausente não satisfaz essa prova. O produto
conserva a relação de compartilhamento, sem acrescentar LOCAL_CELL falso.

A tradução de uma leitura numérica sem célula exata conserva uma alternativa
de valor válido e outra de representação inválida com controle/efeitos abertos.
Conversões de DATA só recebem certificado quando os bounds publicados provam
que o receptor não pode sobrescrever o emissor. Um literal não lê armazenamento.
Os controles que exigem célula inteira exata conservam essa exigência própria.
O trabalho visita declarações e receptores; não enumera valores ou aliases.

Oracle: DATE-N sobre YEAR/MONTH/DAY recebe 20261004; MONTH pode ser convertido
para OUT-A, mas nenhuma das quatro declarações ganha uma célula independente.
A leitura regional não certifica o valor 10 sem uma prova de representação.
Esta wave invalida admissão numérica e lowering de armazenamento compartilhado;
as provas aritméticas locais permanecem reutilizáveis.

### Texto em localização compartilhada

A regra de MOVE alfanumérico (IBM 6.4, elementary moves e MOVE statement)
permanece conhecida quando o descritor e o limite de armazenamento são provados.
REDEFINES não remove o tipo PIC X. A publicação conserva scalarText e a escrita
com fitting; não concede LOCAL_CELL. O consumidor usa UnknownBinding dentro do
limite publicado, portanto aliases continuam sujeitos à invalidação conservadora.
Uma fatia constante altera o valor lógico completo por concatenação, sem afirmar
a posição física da view. Duas views sem coordenadas comuns exigem limites
disjuntos para uma cópia DATA. Sobreposição não provada continua explícita.
O índice dos limites é construído uma vez; não enumera posições ou combinações
de aliases. Oracles: texto sobre número, literal, fatia, receptor disjunto,
sobreposição rejeitada e preservação de um CALL independente.
Fontes: https://www.ibm.com/docs/en/cobol-zos/6.4.0?topic=statement-elementary-moves
e https://www.ibm.com/docs/en/cobol-zos/6.3.0?topic=statements-move-statement .

### Truncamento binário sem um resultado único

A tipagem e o acesso do MOVE não dependem de escolher STD, BIN ou OPT. Um
NumericTransfer literal sem value representa conversão reconhecida cujo
resultado não está certificado: isso só é permitido para BINARY com TRUNC
UNSPECIFIED/OPT e valor fora da PICTURE. Valores comuns conservam value.
Emissor DATA preserva a leitura, com a mesma decisão pelo limite do descritor.
No lower, o resultado incerto é unknown INT/DECIMAL com razão explícita e
dependências. A incerteza do resultado recebido não cria um ramo de controle. Guards se
aplicam à validade não provada do emissor textual ou compartilhado. Os receptores
permanecem lineares e não enumeram opções combinadas.
Não é solicitado um manifesto nem assumida uma opção.
Fonte: https://www.ibm.com/docs/en/cobol-zos/6.4?topic=options-trunc .
Oracles: 123451 para S99 COMP, STD=51, BIN=-7621; OPT/UNSPECIFIED mantêm
resultado aberto. Valor 12 continua provado sob todas as opções.
