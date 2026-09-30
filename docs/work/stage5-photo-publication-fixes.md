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
