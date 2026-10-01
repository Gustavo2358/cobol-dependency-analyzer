# STAGE5-PARTIAL-FILE-ROUTES

[Current merge and qualification](analyzer-integration-20261001.md). The checkpoint scope below is historical.

Status: DONE / MERGED

Scope: FILE control-route publication when parser recovery did not prove normal
control. Ship in the existing stage-5 frontend PR #77; preserve the grammar,
FILE inventory, diagnostic state, source provenance and downstream contracts.

## Discovery and rule

The qualified stage-5 runtime reproduces a NullPointerException at
`ControlTopologySemantics.fileRoutes`: the statement has an unknown outcome,
`fileFlows` correctly omits unproved internal sequencing, but `fileRoutes`
dereferences the absent normal outcome. Its handler branch can also name a
region that was never admitted by the statement pass.

The IBM Enterprise COBOL 6.4 Language Reference, chapter 25, printed page 179
(PDF page 207), has distinct diagram branches `RECORD [IS]` and `RECORDS [ARE]`.
The grammar already reflects those branches. `LABEL RECORDS IS STANDARD` is a
recovery witness, not authorization to widen the dialect. An invalid
`MOVE TO WORK-TEXT` independently reproduces the publication abort.
Authority: <https://publibfp.dhe.ibm.com/epubs/pdf/igy6lr40.pdf>.

INV-SP-002 requires explicit incompleteness rather than omitted occurrences;
INV-SP-003 and the control-topology contract forbid consumers from inventing
successors. Each FILE destination role must therefore remain published with
UNKNOWN_LOCAL and PARTIAL_UNKNOWN evidence when the statement lacks proved
normal control. This applies before resolving handlers, declaratives, internal
operand chains or external continuation. Valid control remains unchanged.

Eval: PartialFileTopologyTest (native families, malformed input before/after,
handlers, composite OPEN/CLOSE, SORT/MERGE, callbacks and valid LABEL controls),
existing FILE authority/composition tests, real CLI pipeline and corpus
comparison. No synthetic normal continuation or grammar recovery edge is allowed.

## Qualification

- RED: five of six initial JUnit methods failed against the old producer:
  missing-normal NullPointerException and handler `target region` rejection.
- GREEN: seven focal methods / 24 CLI sources, including the contrast where
  a missing data COPY must not erase independently proved FILE control.
- Frontend FAST PASS (696 tests before the final COPY contrast; all seven final
  focal methods rerun afterwards). The class remains in the fixed FAST profile.
- The same 24 sources were executed against both frozen runtimes. Eighteen
  baseline frontend aborts now publish SP and complete all four stages. Six
  already successful controls preserve all five SP/AIR/CFG/source/dependency
  products byte for byte.
- Independent integrated checks preserve every FILE role, explicit unknown
  targets/proofs, source-native FILE inventory and provenance, open AIR control,
  and the qualified INDD candidate where supported. Six serialized corruptions
  of roles, target kinds, control remainder, candidates and provenance are rejected.
- Existing FILE composite oracle: 22/22 four-stage cases PASS, including order,
  PERFORM returns, error routes, status aliasing and callback boundaries.
- All 560 campaign sources were rerun through the changed frontend. Every SP is
  byte-identical to the qualified baseline: CardDemo 73, PERFORM 39, Chaos 48,
  aliases 14, PERFORM adversaries 25, frontend fixtures 331, prior focal 29 and
  frontier payload 1. No unexpected delta.
- The frozen jar comparison changes only `ControlTopologySemantics.class`;
  3,787 other entries are identical. The 2,240 downstream products from the
  baseline are explicitly reused for those 560 unchanged SPs. This is not a
  fresh full-pipeline replay or a new full-wrapper qualification.

The fix makes recovery publishable, not syntactically valid or fully executable.
Damaged-input SP coverage remains INPUT_MISSING, and no normal successor,
handler entry, operand chain or dynamic target is inferred from recovery alone.
No general improvement to control qualification after malformed syntax is claimed.
Raw runs and hashes are preserved locally under
`.shared-routine-bodies/evidence/file-partial-*`; durable report and runner
snapshots are in `artefatos-e2e/shared-routine-bodies-20260930/partial-file-routes`.
No merge. Consumer pin-only updates accompany the existing lower #52 / CFG #57.
