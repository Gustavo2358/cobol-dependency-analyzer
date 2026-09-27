# Storage boundary fixes

- id: STORAGE-BOUNDARY-FIXES
- status: DONE upon merge of [PR #63](https://github.com/Gustavo2358/proleap-poc/pull/63); required technical gates passed.
- scope: Canonical storage semantics: invalid RENAMES parentage and uncertain REDEFINES components.

## Rule and algorithm

RENAMES adds a view, never allocation. An unproved alias cannot assert a physical parent that is elementary. Preserve its owning base, provenance, unknown range and diagnostic. A component with an unproved subordinate REDEFINES cannot publish complete logical views or scalar cells; unrelated roots retain their proofs.

Authority: existing storage/compilation contracts and AIR bindings. IBM Enterprise COBOL 6.4 [scope of names](https://www.ibm.com/docs/en/cobol-zos/6.4?topic=programs-scope-names) preserves identity across contained programs; [language reference](https://publibfp.dhe.ibm.com/epubs/pdf/igy6lr40.pdf) describes FILLER and nonallocating level-66 renaming. Negative input is retained with uncertainty, not promoted to valid COBOL.

Algorithms traverse finite published node/component sets and indexed parent relations. Exact-view filtering is linear in component members. Root sibling ordering uses O(R log R) source-token sorting, where R counts roots and detached negative aliases. There is no source-text search or object-pair analysis. Existing cycle/identity validation remains authoritative.

## Oracle and validation

Before implementation, reproduce the failures. Assert that invalid relations retain unknown ranges and diagnostics; unaffected roots keep independent proofs; required captures alias their declared owner without invented storage; anonymous FILLER has no data object; RENAMES never enlarges physical allocation. Reject forged or incomplete exact-view chains. Check enumeration invariance, source provenance and AIR validation.

Run repository FAST and local qualification, the complete frontend fixture corpus, PERFORM, Chaos and CardDemo. Compare dependency relations and supports with the frozen baseline; keep existing PARTIAL and unrelated input limitations explicit. ALTER and incomplete file-effect findings F04/F07 are outside scope.

## Complete logical RENAMES discovered by the adversarial oracle

The new full-range alias oracle loses both known CALL targets even after admission
is repaired. The canonical logical range is present, but FactLocalitySemantics
unconditionally disallows an exact shared Cell whenever any RENAMES exists.
For an already complete logical family, an alias whose published logical range
has the same root, start and length can share that value Cell. Require every
member, including each alias, to belong to the complete family. Partial, invalid,
or unresolved aliases cannot establish this proof. Record the alias declaration
as an explicit proof dependency. This preserves a positive kill only for a
complete value overwrite, without relying on the physical profile.

## Validation and remaining limits (2026-09-27)

This section records the initial storage wave. The [FILE follow-up](incomplete-file-effects.md)
resolves the three remaining pipeline rejections and the two SP-version test expectations.

- FAST: 582 tests, PASS. Focused storage/RENAMES/locality tests: 23, PASS.
- Full local qualification: 1,157 tests, six failures and one existing skipped
  discovery case. The same six failures reproduce on unchanged main
  `f33fae3b13edc8e2ba044e948836875fe55cb7de`: two stale SP-version expectations,
  three stale PERFORM inventory expectations and the old empty DLI-host oracle.
  They were not changed in that initial wave. Normalizer artifact checks also passed.
- Integrated discovery corpus: 307/310, up from 296/310; all 11 targeted defects
  complete frontend → Semantic Product → lower → AIR → CFG → dependencies.
- PERFORM 39/39; Chaos 48/48; existing PERFORM adversaries 25/25; logical aliases
  14/14. Six new storage/capture dependency oracles pass, including the additional
  complete-RENAMES false negative; no expected target was removed.
- CardDemo: 73/73 pipelines completed, with existing PARTIAL status retained.
  Program/file/source relations remain 121/271/523, with no added/lost relation;
  candidate supports and 20,548 referenced origins are unchanged.
- The final RENAMES proof revision republishes all 310 discovery and 73 CardDemo
  inputs byte-identically to the prior integrated execution. Downstream evidence
  is reused only after exact SP equality and unchanged consumer hashes; the six
  new adversaries execute the final four-stage runtime.
- Historical W2: product oracles remain 23/24; `known-open` (ACCEPT) is unchanged.
  The historical all-Cell runner still reports 5/24; it is not reported as PASS.

Remaining discovery failures: `file-namespace-shadowing.cbl`,
`nested-global-through-local-file.cbl`, and `semantic/statements.cbl` retain their
previous incomplete file-effect rejection. The existing `copy-cycle` source
oracle still disagrees with its baseline product (54/55 source oracles pass).
No ALTER implementation, wire-version change, AIR change or CFG change is included.
The user authorized integration after review of the results. PR #63 records the merge;
its merged state closes this work under the lean policy.

## Authorized F04/F07 follow-up

The user subsequently authorized correction of the three incomplete file-effect
inputs. The previous exclusion records the first validation wave. Current work
and tests are described in [incomplete file effects](incomplete-file-effects.md).
