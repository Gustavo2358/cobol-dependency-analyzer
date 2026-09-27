# Nominal DFH copybooks

- id: NOMINAL-DFH-COPYBOOKS
- status: IN_PROGRESS
- scope: Missing, unqualified DFHAID and DFHBMSCA COPY declarations; no values or storage proofs.

## Rule and authority

The user authorized a nominal model: identifiers exist, but model data cannot
prove a branch impossible or justify a kill. IBM CICS TS documents both members
as sets of 01 declarations in WORKING-STORAGE:
https://www.ibm.com/docs/en/cics-ts/6.x?topic=reference-bms-constants .
The catalogue records names only. No PIC, VALUE, USAGE, extent, offset, constant
value or disjointness is supplied. EIB runtime registers and other DFH members
remain outside this pilot. Real source members take precedence.

Existing incomplete declaration semantics and UnknownBinding carry the model.
A typed input diagnostic marks the expansion as nominal-only. Missing content
and layout remain qualified to the containing data region; resolved names do not
certify globally complete input. Synthetic provenance uses a versioned model
identity and the actual COPY inclusion site. Source COPY relationships survive.

The resolver uses an explicit catalogue, never a DFH-prefix heuristic. Unknown
members, qualified COPY, and COPY REPLACING keep existing behavior when no real
member exists. REPLACING support for catalogue models is deferred: arbitrary
replacement must not manufacture PIC/VALUE or control facts from this model.
Real-member REPLACING, cycles and I/O handling remain unchanged.

## Algorithm and boundaries

Perform normal library lookup first. For a missing supported unqualified member
without replacement, expand a finite ordered nominal declaration list, retain
its include chain, and mark the full expansion with the typed incomplete-input
fact. Resolution and lowering use existing contracts. Catalogue lookup is bounded;
expansion is linear in emitted names. No downstream source parsing, guessed CFG
edges, new reachability rule or dependency deletion is authorized.

## Oracles before implementation

- Both catalogued members resolve referenced names with no value, scalar, logical
  text or physical layout proof; input remains partial and entry localization works.
- Real files win; unknown names and qualified/replaced requests do not gain models.
- Repeated includes, nesting and name conflicts retain normal ambiguity/provenance.
- IF/EVALUATE arms with model data preserve both possible targets; writes to model
  symbols and aliases cannot create a fabricated strong kill.
- Real proved overwrites still kill according to existing semantics.
- COACTUPC dependency sets and support chains are compared with frozen baseline,
  including qualified-source evidence and sites unreachable in the executable CFG.

## Validation

Focused preprocessing/provenance/storage tests, real producer-consumer E2E,
frontend FAST, PERFORM 39, Chaos 48, storage/alias adversaries and CardDemo 73.
No expected dependencies will be weakened. Reuse downstream evidence only when
input bytes and consumer binaries match. Keep unexplained deltas explicit.

## Discovery and result (2026-09-27)

The loss was in preprocessing: a missing member became a mapped placeholder,
so its declarations could not enter binding. Substituting ordinary PIC/VALUE
copybooks would also remove the missing-input boundary and could create storage
or value proofs. The pilot therefore resolves names while retaining a typed,
located content gap. No lower, AIR, CFG or dependency-engine implementation changed.

The existing data model accepts empty 01 declarations as opaque storage. Tests
prove that SOURCE_IDENTITY is available while LOGICAL_TEXT, LOCAL_CELL and
PHYSICAL_VIEW are unavailable, including with a physical storage profile enabled.
Unknown writes through model operands and aliases retain candidates in real fields;
ordinary proved overwrites still kill. IF, EVALUATE and XCTL preserve both arms.

COACTUPC: the two unresolved COPYs become two NOMINAL_COPYBOOK diagnostics.
The program targets remain CSUTLDTC and COMEN01C, with source-qualified evidence.
All seven FILE sites keep UNREACHABLE_IN_MODEL. The reproduced merged-main baseline
has no candidates at those FILE sites; it must not be described as containing file
names. The user's referenced output with file candidates was not identified.
This campaign proves equality against the frozen runtime recorded below.

CardDemo: 73/73 pipelines complete. Comparing candidates, original source positions,
include chains, exactness and supports finds no dependency delta. There are 76
intentional source-resolution changes in 38 programs: DFHAID/DFHBMSCA now resolve
to versioned model artifacts. No other source resolution changes. Coverage remains
PARTIAL; model use is not evidence of complete IBM content.

## Validation results

- Frontend FAST: 596 tests, zero failures/skips; final run 71.840 seconds.
- Input/classification/coverage refinement: 14 focused tests, zero failures.
- PERFORM: 39/39; final producer also reproduces all 39 SPs byte for byte.
- Chaos: 48/48. PERFORM adversaries: 25/25. Aliases: 14/14.
- Existing fixture matrix: 310/310 SPs byte-identical to the previous validated run.
- Storage/FILE adversaries: 13/13 SPs byte-identical. Their downstream evidence is
  reused with identical inputs and frozen consumer binaries, not counted as new runs.
- Nominal adversaries: six passing, plus one known capability limit reproduced on
  both baseline and candidate. The failing discovery oracle remains intact.
- CardDemo: all 73 programs newly executed through frontend, lower, CFG and dependencies;
  all 73 dependency comparisons equal.

After broad validation, the only changed Java classes were CoverageSnapshot and
ExplorerMain (presentation and debug logging). All semantic producer class bytes
are identical. The final FAST passes; COACTUPC and all 39 PERFORM SPs were checked
again for byte identity. The frozen binary, source SHAs, counts and evidence hashes
are recorded in [nominal-dfh-validation.json](nominal-dfh-validation.json). Raw
products, commands and logs remain under the local `.synthetic-dfh/` campaign.

The full historical frontend gate was not rerun: its previously recorded four
oracle failures and one skip are not claimed fixed here. This change is covered by
FAST, targeted input/storage tests, the 310-fixture comparison and the real corpus.
Consumer FAST gates were not rerun because their implementations and binaries did
not change; consumer admission and behavior were exercised by the real four-stage
runs. Existing expected files outside the new nominal suite were not changed.

## Remaining limits

The model is nominal only. An adversary assigns PROGA001 to DFHPF3 and then calls
that symbol. Both baseline and candidate report UNSUPPORTED_TARGET_EXPRESSION
with an unknown remainder and no candidate: there is no textual shape/extent to
compute that target. This is an existing limit of value admission, not a new kill.
The pilot does not invent a PIC to make the probe pass or change value contracts.
The E2E runner labels it KNOWN_LIMITATION only after reproducing the same failure
against an explicit baseline; the original missing-candidate oracle is retained.

Other DFH members, EIB runtime data, qualified missing COPYs and replacement of
models (including via ancestors) remain outside the pilot. Real members always
win. No ALTER work, unrelated pipeline fix, merge or auto-merge is included.

## Reproduction

Run `python3 -B scripts/harness/lean.py fast` with the configured Java/Maven cache.
For nominal E2E, use the frozen four-stage runtime and the existing CFG runner:

```sh
python3 scripts/harness/nominal-copybooks-e2e.py \
  --runner ../analysis-cfg/scripts/project/e2e_perform_completion.py \
  --runtime /path/to/runtime-reviewed.json \
  --baseline-runtime /path/to/baseline-runtime.json \
  --out /path/to/new-evidence-directory --java /path/to/java
```

Status stays IN_PROGRESS until human review and a separately authorized merge.
