# STAGE5-PHOTO-PUBLICATION-FIXES

Status: IN_PROGRESS

Scope: three confirmed frontend aborts, authorized in existing PR #77;
separate implementation commits for preprocessing SUPPRESS, EVALUATE structural
composition and CICS nominal gaps. No merge or memory/capacity campaign.

## Checkpoint 1 — SUPPRESS outside COPY

`SUPPRESS` is recognized by the preprocessor lexer for COPY but was absent from
ordinary COBOL text. ANTLR recovery exposed the terminal at the root, where the
closed policy catalogue correctly rejected an unclassified construct.

IBM Enterprise COBOL 6.4 uses SUPPRESS both in COPY (listing control) and in JSON
GENERATION (selection of fields). The preprocessor must preserve the latter as
ordinary text without interpreting its runtime semantics. Add it to
`charDataKeyword`, keeping COPY handling and the exhaustive policy check intact.
Authorities: [IBM JSON generation](https://www.ibm.com/docs/en/cobol-zos/6.4.0?topic=statement-operation-json-generate)
and [IBM COPY listing option](https://www.ibm.com/docs/en/cobol-zos/6.4.0?topic=options-suppress).

Tests: preserve text and physical provenance; JSON/XML/report-writer surfaces;
COPY SUPPRESS with/without REPLACING; missing COPY; rejected REPLACE; unknown
policy rejection; CLI publication with explicit JSON parser gaps. This does not
implement JSON GENERATE in the COBOL grammar. The diagnostic and incomplete
coverage must survive.

Discovery baseline and raw results are preserved at
`.shared-routine-bodies/evidence/new-bugs-discovery-02` in the aggregate workspace.

Checkpoint 1 validation: the unchanged producer failed two of the four new test
methods with the reported missing-policy exception. After the change, all 14
methods in PreprocessorSuppressTest, PreprocessorEnginePolicyTest,
SourceNormalizationPreprocessingIntegrationTest and SourceProvenanceTest pass.
The focal classes are part of FAST. Integrated and corpus checks follow after
all three checkpoints are assembled.

## Checkpoint 2 — partial EVALUATE and IF completion

The AST retains ordered WHEN groups even when provenance/readiness leaves the
EVALUATE observed. The structural index now visits each group independently,
keeping UNKNOWN containment where required; it never concatenates separate
WHENs into sequential siblings. If that partial structural view lacks an IF
completion, projection translates the already available canonical IF successor.
It does not replace a conflicting known successor or create a new proof.

Provenance remains approximate; EVALUATE's original capability predicate and all
SP validators remain unchanged. No graph edge is added here. This follows the
existing EVALUATE completion rule and INV-SP-003: frontend facts are authoritative,
not collection order. The fix also avoids a false structural next-WHEN successor
when missing data input prevents proving normal completion.

RED: four exception cases plus the missing-COPY false-continuation assertion
failed before the change. GREEN: all 50 methods in the focal EVALUATE/IF,
composition and topology-authority suites pass. Six new methods cover split
EVALUATE/WHEN, normal-source contrast, same-arm tail, OTHER, nested IF/EVALUATE,
unknown data input, source precision and the exact target of each continuation.

## Checkpoint 3 — localized CICS nominal gaps

CICS program target/options and FILE options published incomplete DATA bindings
without the originating reference-report gaps. Project them through the existing
`addReportGaps` adapter, as the typed-command and handler families already do.
No resolver, target, memory/control proof, wire version or validator is changed.
Unresolved and ambiguous bindings stay incomplete; literal targets and the
candidate set of an ambiguous name remain intact. Gaps retain their originating
reference provenance and owning statement, including under missing COPY input.

RED: five new methods abort at the nominal-gap contract; neighboring command and
handler coverage already passes. GREEN: 77 methods pass across CICS nominal,
program, FILE, command, handler, host-effects, memory and provenance suites.
The six new methods also ensure resolved statements do not inherit an unrelated
statement's gap and no candidate is selected from an ambiguous binding.
