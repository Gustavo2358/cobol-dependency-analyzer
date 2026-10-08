# Incomplete native file effects

Status: DONE upon merge of [PR #63](https://github.com/imd/proleap-poc/pull/63); required technical gates passed.
Scope: F04/F07 and the same missing-target effect contracts.

The producer must preserve independently known record/control/operand identities.
A missing SELECT does not remove a unique FD record; a missing FD does not remove
an explicit INTO/FROM operand or a known SELECT status receiver. Unknown write
locations are MAY bounds, never a MUST kill. Profile/representation diagnostics
alone do not open memory bounds. No parsing or name lookup moves downstream.

Authority: existing FD-W3 conditional effect table, Dependency Preservation
Principle, and FileEffectAdmission. IBM SC27-8713-03 (6.4, READ pp429–434,
WRITE pp476–480) distinguishes the buffer, success-only INTO and pre-I/O FROM.
The official IBM READ/WRITE documentation confirms that ordering. An incomplete
source is not certified as valid COBOL. Strong steps retain every existing proof
including record ownership, precise text view and disjunction.

Algorithm: index unique description and control independently by canonical FILE
entity; resolve operations by existing bindings; collect observed destinations
independently. An unavailable required destination opens its direction's bound.
Known steps and provenance coexist with those bounds. No gap-string dispatch,
no global widening for a profile diagnostic. Complexity remains linear in the
indexed source inventory plus emitted effect targets; all traversals are finite.

Oracles before production: FD without SELECT retains buffer and INTO; SELECT
without FD retains status; unresolved FROM receiver/source retains uncertainty;
WS record operand without owner is MAY; complete FROM retains COPY_BYTES;
missing INTO cannot claim a closed empty plan. Lower must still reject removed
required steps with closed bounds. E2E must preserve old candidates across MAY,
add remainder, and eliminate candidates after a later proved MOVE.

Validation: focused producer contracts, consumer admission/codec, three original
inputs, additional adversaries, FAST. Rerun the fixture matrix and frontend of
CardDemo because the effect producer is shared; reuse downstream only with
byte-identical SP and identical consumers. Run PERFORM/Chaos regression requested
by the campaign. Existing unrelated qualification failures remain explicit.

## Focused qualification

Initial tests failed in four independent cases; after implementation the focused
family passed 37 tests. A later adversary caught loss of SORT/MERGE's incomplete
control qualification; the gap is preserved, without weakening positive memory
facts. Final FAST: 589 tests, zero failures/skips. The three original fixtures and
13 storage dependency adversaries (seven new) complete the four-stage pipeline.
The original six adversarial expected sets are unchanged. PERFORM remains 39/39.

The earlier hardcoded false read-bound expectation for an unresolved FROM was
inconsistent with FileEffectAdmission. Its oracle now requires the explicit
open bound; precise FROM, missing owner, independent profile gaps and kill rules
are tested separately. Source fixtures and existing dependency expected sets were
not relaxed. SP/compilation schema versions remain unchanged.

Remaining matrix, corpus and regression results are recorded in the local E2E
follow-up evidence and the review PR, with byte-equivalent reuse distinguished
from new execution. The user subsequently authorized documentation closeout and merge.


## FILE contract expectation follow-up

Two transport tests still required SP 2.41 even though their fixtures now publish
newer facts. Computed CALL operands select SP 2.47 (`nominalValues`); the FILE USE
fixture's SECTION regions select SP 2.48. The assertions now require those exact
versions, following the documented feature selection. All effect, allocation,
handler, continuation and deterministic publication assertions are preserved.

New execution: `mvn -Dtest=File*Test,IncompleteFileEffectsTest test` passes all
115 tests, including all ten tests in the two changed contract classes. No
failures, errors or skips remain in this family. Repository FAST also passes
589 tests, with no failures, errors or skips. Production, build inputs and
source fixtures are unchanged from `4802305d17e7b1684d1f749618aa76fe8e091e0f`,
so the integrated corpus evidence above remains valid and was not rerun for
this test-only correction. The earlier full qualification remains a historical
run; this follow-up does not claim the complete Maven suite is green.


## Integration and remaining limits

The final campaign completes all 310 fixture pipelines. PERFORM 39/39, Chaos
48/48, PERFORM adversaries 25/25, aliases 14/14 and storage/FILE adversaries 13/13
pass. CardDemo preserves all 73 products and its existing PARTIAL status. Known
candidates, supports and referenced provenance are preserved as detailed in the
E2E report. No additional corpus execution was needed for this documentation.

The remaining historical full-suite findings are three PERFORM inventory oracles,
one DLI oracle and one skipped discovery test. W2 ACCEPT and the copy-cycle oracle
also remain outside this delivery. These limits are not failures of the 39-case
PERFORM suite or the 48-case Chaos suite. The full suite is not claimed green.

Integrate this producer before [lower PR #38](https://github.com/imd/cobol-lower/pull/38).
The lower must pin the producer merge SHA with its tree and selected file hashes;
unchanged production permits reuse of the recorded integrated evidence.
