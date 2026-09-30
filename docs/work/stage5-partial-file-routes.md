# STAGE5-PARTIAL-FILE-ROUTES

Status: IN_PROGRESS

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
