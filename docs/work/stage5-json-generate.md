# STAGE5-JSON-GENERATE — checkpoint 4

Status: IN_PROGRESS

Scope: user-authorized JSON GENERATE grammatical support in frontend PR #77,
following the three publication fixes. Preserve operand provenance, statement
boundaries, nested exception bodies and the following source dependencies. No
merge, JSON serializer or runtime value/effect proof is part of this checkpoint.

## Authority and design

IBM Enterprise COBOL 6.4 Language Reference, JSON GENERATE, printed pp. 369–381
(format pp. 370–371), retrieved 2026-09-30:
https://publibfp.dhe.ibm.com/epubs/pdf/igy6lr40.pdf
and https://www.ibm.com/docs/en/cobol-zos/6.4.0?topic=statements-json-generate-statement.
NAME and CODEPAGE are context-sensitive words:
https://www.ibm.com/docs/en/cobol-zos/6.4.0?topic=appendixes-context-sensitive-words.

The grammar gets a dedicated JSON GENERATE alternative with ordered optional
COUNT, INDICATING, ENCODING, NAME, SUPPRESS and CONVERTING phrases, exception
bodies and optional END-JSON. A typed parser rule determines the boundary; no
wildcard scanning to a period or runtime string interpretation. Reuse the AST's
preserved-statement representation and retain nested statements, operands and
unknown effect coverage. Source control possibilities enter each distinct exception/success body or
its enclosing continuation when the respective body is absent. Nested statements
retain their established local control; no unconditional bypass of two present
bodies is added. Equal targets are coalesced by canonical identity. Preserved
clause groups stay structurally separate without claiming executable JSON semantics. INV-AST-001, INV-SP-001
and INV-SP-003 govern identities, complete inventory and producer authority.

Syntactic admission does not validate all IBM operand type/layout restrictions,
compute generated text or prove JSON-CODE/runtime effects. JSON PARSE is outside
the authorized fix. Invalid forms must still produce parser diagnostics.

## Validation plan

RED/GREEN grammar, AST, provenance and publication witnesses; exception nesting,
implicit/explicit scope, surrounding IF/EVALUATE/PERFORM, multiple instructions,
COPY and malformed forms. Production CLI pipeline verifies candidates before,
after and inside JSON exception bodies, with supports and remainders.

Lexer vocabulary and the statement alternatives are shared across every source;
fresh frontend corpus replay is required to detect collateral parse changes.
Downstream replay is required for changed SPs; byte-identical SPs with unchanged
consumer code permit explicit reuse. Frontend FAST and qualification-local cover
the grammar/AST surface. Consumer pin/build checks follow the repository rules.

## Discovery and focal results

Initial grammar RED: five of six new methods fail with parser diagnostics. After
grammar/AST support, the original sentinels survive, but E2E exposes missing source
paths into exception bodies. Two new control assertions demonstrate that gap and
an incorrect cross-clause structural IF continuation. Producer-side alternatives
and clause separation address it without changing wire types or consumers.

A nested test now uses END-CALL to disambiguate the inner CALL's exception scope
from the containing JSON statement; the first draft attached NOT ON EXCEPTION to
the unterminated CALL. Existing CALL grammar behavior was not changed.

The preserved JSON statement remains an executable unknown boundary. Success and
exception are admitted only as source possibilities with control remainder. A
following dependency and handler-local dependencies must retain provenance and
qualifications. Tests also reject a bypass after both branches return.

## Qualified checkpoint results

- Nine dedicated JSON test methods cover 22 phrase combinations, nine malformed
  forms, nested/implicit scopes, COPY provenance, condition-name resolution and
  JSON-CODE. The exhaustive statement witness now covers all 52 alternatives;
  grammar/reference inventories cover 645 rules (614 COBOL + 31 preprocessing).
  Existing statement fixture and its 15 preserved alternatives remain unchanged.
- Frontend FAST: 725 tests, zero failures/errors/skips. Full qualification-local: PASS, 1,296 Maven tests, zero failures/errors,
  one existing opt-in corpus skip (external configuration absent); full source
  normalizer regression and naming pass. The first run exposed three stale
  inventory/witness assertions; they were extended without relaxing coverage.
- Thirteen E2E scenarios in default and qualified profiles: 26/26 pass, all four
  production stages exit zero and all valid JSON inputs have zero parser errors.
  Four deliberate oracle mutations are rejected (lost candidate, lost
  qualification, missing handler arm, fabricated bypass).
- The exact previous unsupported sentinel (SHA-256
  `4b9e6f78d8cab621cd45003329da2965163473ca00d808f8d052253a93530190`)
  now retains BEFORE and AFTER in both profiles: 2/2 additional E2E runs.
- Fresh frontend replay: 560/560 SPs byte-identical to the immediately preceding
  qualified checkpoint. Includes all 73 CardDemo programs/variants, PERFORM 39,
  Chaos 48, aliases 14, PERFORM adversarial 25, frontend fixtures 331, earlier
  focals 29 and one frontier case. No unexpected corpus delta.
- All 2,036 consumer/runtime classes outside the frontend are byte-identical.
  The 2,240 existing downstream products are hash-checked and reused; they were
  not regenerated for this checkpoint. Current production source digest equals
  the compiled runtime digest. New JSON scenarios use fresh downstream runs.

Raw runs, commands, products and hashes remain under
`.shared-routine-bodies/evidence/json-generate-{runtime-03,e2e-03,original-01,corpus-01}`
in the aggregate workspace. Compact durable evidence and exact review pins are
in `artefatos-e2e/shared-routine-bodies-20260930/json-generate/`.

Lower/CFG require only authority repins. SP 2.62.0, AIR, qualified source and
dependency wire versions are unchanged. Consumer FAST is required after repins;
full consumer wrappers are not rerun because their implementation is unchanged,
all old SPs are identical and new source possibilities traverse the actual CLIs.

The parser limit is closed for the documented JSON GENERATE surface. Runtime
serialization, generated values, physical effects and complete operand typing
remain unsupported. Coverage/gaps and conditional source qualifications remain
visible; recognized grammar does not certify complete analysis.
