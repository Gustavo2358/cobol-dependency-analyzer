# CARDDEMO-CONTROL-DEPENDENCIES — W2

ID: CARDDEMO-CONTROL-DEPENDENCIES-W2
Status: DONE — implementação deste repositório mergeada em 28/09/2026; [fechamento da integração](carddemo-control-integration.md).
Scope: D1 embedded comments and D4 documented FILE/DATASET aliases. No new command families.

Os checkpoints abaixo registram a qualificação da onda na data em que foi executada. Seus estados de revisão e paradas são históricos; o estado vigente está no [fechamento da integração](carddemo-control-integration.md).

## Rule and authority

COBOL fixed column-7 * and / lines are normalized to floating comments upstream.
Embedded-language syntax must ignore comments while retaining quoted payload and
source offsets. Floating comments start outside quotes at a separator boundary,
and stop at the record terminator. Replace comment characters by spaces in a
private lexical view, preserving CR/LF and UTF-16 offsets; retain raw text and
physical source unchanged. The preprocessor blanks lexer-owned COMMENTLINE spans
before flattening CICS records; retained host spans keep their original mapping. The existing translated EXEC wrapper is handled before
masking. Invalid non-separated markers must not become valid syntax. The scanner
is linear and bounded by payload size.

IBM: [comments](https://www.ibm.com/docs/en/cobol-zos/6.4.0?topic=b-comment-lines),
[floating indicator rules](https://www.ibm.com/docs/en/SS6SG3_6.2.0/pdf/lrmvs.pdf),
[embedded CICS comment](https://www.ibm.com/docs/en/cics-ts/5.5.0?topic=messages-dfh7031i-w).

The explicit DATASET alias table covers READ, WRITE, REWRITE, DELETE, STARTBR,
READNEXT, READPREV, RESETBR, ENDBR and UNLOCK, plus the existing separately admitted
SET alias. INQUIRE is not widened. Canonicalization occurs before option meaning,
so target, direction, errors, RESP and NOHANDLE retain the existing FILE semantics.
[IBM compatibility table](https://public.dhe.ibm.com/ps/products/wsed/fixes/v7.6.0.1/topics/cics_errormessagesthatyoucanignore.html).

## Oracles before code

Comment removal and FILE/DATASET substitution must preserve semantic facts and
control destinations, with original offsets/provenance retained. Cover quote
escapes, comment-like literals, CR/LF, comments inside operands, invalid markers,
duplicate targets, unknown options, handler/PERFORM composition and FILE_STATUS.
Reuse existing normalization and native multi-FILE regressions. Replay all 73
frontends; downstream every changed product, with candidate/support preservation.
