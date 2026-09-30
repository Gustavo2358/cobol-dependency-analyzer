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

## Validation and delivery

- RED: original implementation rejects complete literals and column-72 quote
  continuation; GREEN covers lexical boundaries and invalid split delimiters.
- FAST: PASS, 42.169 s. Local qualification: PASS, including Maven (1,264 tests,
  zero failures/errors, one existing skip), source-normalizer full regression,
  COPY/provenance checks and naming verification.
- 25 four-stage focal cases pass across both fixes in the stage-5 campaign.
- All 73 CardDemo programs pass; SPs are byte-identical to the qualified stage-5
  baseline and candidate/support/provenance deltas are zero. No grammar or SP
  contract changes.

Frontend PR #77 accompanies lower #52 and analysis-cfg #57. The complete campaign
population and its final comparison are recorded in analysis-cfg's
`docs/work/stage5-preexisting-fixes.md` and the local E2E report. Work stays
IN_PROGRESS under repository policy until review and merge. No merge performed.
