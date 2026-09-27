# Historical qualification failures — repair

Status: IN_PROGRESS. Authorized after the D1–D5 review handoff.

Classify each existing failure against current product contracts before changing production or expectations. Initial evidence: six failures and one error in four frontend characterization classes; W2D hardcodes SP2.38 while the producer lock is SP2.50. Baseline reproductions are preserved.

Scope: repair application defects if proved; otherwise replace obsolete expectations with explicit current capability and negative assertions. Retain independent inventory, provenance, no synthetic kill, exact source pins and feature version floors. Do not regenerate oracles from outputs or accept arbitrary versions. No merge.

Validation: targeted RED/GREEN; complete frontend and CFG qualification to exercise stages previously blocked; FAST in changed repositories. Reuse corpus evidence only if production inputs/code remain identical. If any production change is needed, rerun the affected corpus and investigate deltas.

## DLI provenance defect — rule and algorithm before correction

The DLI host-effects contract requires operands to retain their own original payload origin. The old region-only test never observed this: it incorrectly required no operands. Independent source slices now expose operands anchored at the EXEC prefix. AstBuilder already requests embeddedOperandProvenance, but preprocessing uses transformedSlice, losing the retained map.

Preserve the raw payload and its retained segments when adding framing markers; keep the ordinary whole-command map and exact=false unchanged. Use the existing SourceMap retained-segment channel, not text reconciliation downstream. Work is linear in the number of overlapping segments plus payload length; no new inference or control rule. Nested COPY, replacements, Unicode, multiline operands and following sentinels must remain coherent. Corpus provenance changes must match DLI operand locations only; candidates/support relationships must survive.

Checkpoint H1: obsolete characterization expectations replaced with explicit current SQLCA, copybook, DLI and PERFORM assertions; missing SQL INCLUDE stays opaque. W2D reads its version ceiling from the exact producer lock, keeps feature floors, and has five positive/negative guard tests. DLI retained provenance is corrected through framing and replacements; whole-command provenance and confidence remain unchanged.

## Review handoff

Implementation and historical gate repair are complete for review. The work item
remains IN_PROGRESS until merge. Final results, exact reuse boundaries, remaining
opt-in skip and corpus deltas are in [campaign qualification](cics-control-qualification.md).
No merge was performed. Current source locks identify the review commits.
