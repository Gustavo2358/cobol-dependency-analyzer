# STAGE5-SOURCE-CONTINUATION

Status: IN_PROGRESS

Scope: fixed-format continuation at complete token boundaries. Preserve literal
values, split words, physical records and original source provenance. This is a
pre-existing normalization defect shipped with the stage-5 campaign. No grammar,
Semantic Product contract or downstream control reconstruction changes.

Authority: IBM Enterprise COBOL continuation lines; INV-PROV-001/002,
INV-AST-002 and SourceNormalizerTest. The declared short-record policy for open
literals stays unchanged. A closed literal followed by another literal must
remain two tokens; quote continuation at column 72 follows the physical record.

Validation: focused lexical/provenance negatives and positives, frontend FAST
and local qualification, then the existing 560-program campaign population and
new four-stage counterexamples against the stage-5 baseline. Focused tests: PASS after the expected RED (closed literals and column 72).
FAST: PASS, 42.169 s. Local qualification and 560-case replay are in progress.
