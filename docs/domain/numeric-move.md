# Logical numeric values and integer DISPLAY MOVE

Checkpoint 2, SP 2.65.0. Status: IN_PROGRESS.

## Rule and invariant

IBM Enterprise COBOL 6.4 [elementary MOVE](https://www.ibm.com/docs/en/cobol-zos/6.4.0?topic=statement-elementary-moves) aligns numeric operands at the decimal point; the receiver determines capacity. This cut proves unsigned, unedited integer DISPLAY receivers and integral fixed-point literals that fit, or integer DATA sources whose capacity fits. No byte encoding is inferred. Unsigned DISPLAY pictures have 1–31 digits under the documented IBM 6.4 profile; floating literals and decimal-comma syntax without a dialect proof remain unavailable. Signed fields, scale, overflow/truncation, edited pictures, binary/packed storage, subscripts and reference modification remain explicit.

A numeric transfer requires exact source syntax, a unique whole-item binding and an independently proved local cell. VALUE clauses and enclosing groups do not by themselves invalidate the cell. COPY/model, alias and allocation obligations remain causal. Numeric literals have a numeric value independently of MOVE readiness.

The frontend publishes per-receiver integer transfers on the existing MOVE fact. The consumer validates each certificate at both JSON and in-memory boundaries, and emits AIR INT assignments through the existing MOVE handler. A complete certificate set discharges only MOVE value/whole-item gaps. Unsupported peers retain conservative effects.

## Algorithm and oracle

One declaration index, one reference index, then one pass over MOVE receivers; no range enumeration, power-of-ten allocation or Boolean expansion. Capacity is compared by digit count. Expected examples: MOVE 12 TO PIC 9(3) writes INT 12; PIC 9(2) to PIC 9(4) preserves the value; MOVE 123 TO PIC 99 remains partial. Literal transfers to several receivers are independent; DATA transfers are admitted through the prefix that preserves the source value; later reads after an unsupported peer retain uncertainty.

Validate parser parity, literals, local/nested cells, alias/input rejection, multiple receivers, hostile certificates, large PIC counts, AIR validation and all 73 CardDemo sources against checkpoint 1. Preserve program/file/source dependency sites, candidates and remainder.

Primary references: [IBM numeric literals](https://www.ibm.com/docs/en/cobol-zos/6.3.0?topic=literals-numeric), [IBM 6.4 Language Reference](https://publibfp.dhe.ibm.com/epubs/pdf/igy6lr40.pdf), elementary MOVE and alignment rules.

[IBM 6.4 USAGE](https://www.ibm.com/docs/en/cobol-zos/6.4.0?topic=entry-usage-clause) also applies group usage to elementary descendants. A memoized ancestor pass rejects inherited non-DISPLAY usage and unmodeled ancestor clauses (including GROUP-USAGE), even when the child has PIC 9. An explicit DISPLAY child cannot override an incompatible group usage.

## Priority 2 completion — active

The remaining work covers the complete MOVE/type priority, not only the first
integer DISPLAY cut. Baseline: frontend ea29a45, lower cd6e792; 4,928 MOVEs carry
9,182 identity, whole-access and literal-kind diagnostics. The causal inventory
is under the workspace priority2-evidence directory, with one row per occurrence.

The next rule extends the existing elementary text MOVE proof to fitting DATA
sources, literal truncation and uniquely resolved qualified names. IBM elementary
MOVE aligns non-JUSTIFIED alphanumeric receivers on the left, pads with spaces
and truncates the right. A DATA fit preserves its source expression and receiving
extent; it must not publish a fabricated constant. The existing AIR FitText
operation represents this rule. The algorithm indexes declarations/references
once and visits each transfer once. Oracles: 3→8 padding, 8→3 truncation,
qualified names, literal ABCDE→ABC, and unproved indexed/aliased access.

Type conversions, typed storage and representation, figuratives, indexed and
modified accesses, and source-input obligations remain in this active priority.
No missing definition or physical assumption may be converted into positive proof.
Qualification must retain every baseline dependency candidate and its uncertainty;
all 73 sources are required because both shared storage and MOVE semantics change.

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
dependências. A possibilidade de comportamento imprevisível de OPT conserva
um ramo opaco de efeitos/saída, sem destinos locais inventados. Um guard por
MOVE basta; os receptores permanecem lineares e não enumeram opções combinadas.
Não é solicitado um manifesto nem assumida uma opção.
Fonte: https://www.ibm.com/docs/en/cobol-zos/6.4?topic=options-trunc .
Oracles: 123451 para S99 COMP, STD=51, BIN=-7621; OPT/UNSPECIFIED mantêm
resultado e execução abertos. Valor 12 continua provado sob todas as opções.
