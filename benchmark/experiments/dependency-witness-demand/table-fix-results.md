# Sparse tables and INITIALIZE correction — research qualification

Branch `experiment/dependency-witness-demand`; frozen main `52c1b82dc6accbb615818cf5b5298843a85b0f01`.
Qualified overlay: `benchmark/results/rd-table-fix-20261009/build-05`.
Frozen JAR SHA-256: `6bd96f31911976c097c9fb47377587c11bf3ebe6dc60305190104314bc39b036`. Production `src/`, scripts and POM remain
identical to frozen main. This is a local experimental correction, not a change
to the default production solver or a published PR.

## Mechanism

The old declaration-only OCCURS override is removed. The experiment reuses
canonical logical table allocation and transfer: a remainder for unmaterialized
positions plus cells only for valid indexes learned from source expressions.
An exact full write replaces its selected cell; unknown-index writes remain
weak. Group text, partial-cell writes and aliases use the same local evaluator.
All sparse cells participate in the one reaching-definitions graph. No old
context solver, admission whitelist or table-specific control solver is added.

A computed index may request another cell. Only value equations are then rebuilt;
the physical control graph is constructed once and retained. BOTTOM address
operands postpone evaluation instead of being mistaken for runtime UNKNOWN.
Otherwise an early weak write would permanently retain old candidates in the
monotone union. Genuine unknown addresses still preserve possible dependencies.

Whole-table INITIALIZE resets both sparse cells and remainder, including proven
equivalent declaration views. Possible/partial aliases are not falsely treated
as proven equivalent. Two additional negative cases exposed conservative extras
in frozen main itself: a constant index copied through several MOVE statements,
and INITIALIZE followed by an exact REDEFINES-view read. Their source-level
oracles exclude OLD; separate `mainProgramNames` explicitly record main's OLD
candidate. The raw RED runs are preserved, not edited into PASS.

## Results against frozen main

| Cohort | Cases | Lost | Extra before | Extra after | Changed provenance after |
| --- | ---: | ---: | ---: | ---: | ---: |
| Standalone | 36 | 0 | 30 | 5 | 32 |
| CardDemo | 73 | 0 | 0 | 0 | 0 |

All 25 audited storage additions disappear: 15 position-mixing, 9 stale-write,
and 1 whole-table INITIALIZE candidate. Standalone now has 33 JSON documents
identical to main. The remaining five additions occur only in caller-correlation
and caller-many-12/24; they still require predicate/caller-value correlation.
CardDemo has all 73 JSON documents identical, preserving its 815 dependencies.
All 23 existing sparse-OCCURS expectations for DYNAMIC_REMAINDER also match.

Fourteen new independent source oracles pass on the final overlay (42 sequential
main/before/after runs). They exercise OCCURS 1 and 1,000,000, nested tables,
joined/unknown/copied indexes, exact and weak writes, numeric INITIALIZE, resets
through equivalent views and writes through REDEFINES. One million positions
allocate at most four cells in these cases (including aliases), plus one
remainder per declaration. This is not an assertion that arbitrary code can
never demand more cells. Final table-case peak RSS is
102.7 MiB with 128 MiB heap.
The sparse-100 and sparse-50000 standalone cases both build 16 definitions and
perform 126 work units: declared extent alone does not inflate that graph.

The prior 25 adversarial cases also pass: zero lost names, four known extras,
and the two identity/CONTINUE metamorphic checks remain green. Their extras
are unchanged approximation limits, not part of the 25 storage additions.

Dynamic-only dispatch fixture N=2048 completes with all 2049 expected
names in 8.186 seconds, 579.5 MiB RSS and 512 MiB heap.
It uses 12291 definitions and
1063002 work units. The immediately previous control-corrected
experiment measured 7.980 seconds / 578.3 MiB on this same fixture and limits;
that historical number is reused, not a fresh timing comparison. These are
single-run observations, not a statistically significant speed claim. The
negative-bound N=2048 returns only SAME, excluding DEAD.

The inherited FAST executed this wave: 1,093 tests, 142 suites, no failure,
error or skip; 64.72 seconds, 279.4 MiB
aggregate RSS. Maven and forked javac each retain 128 MiB heaps. FAST evidence
is reused for the last overlay-only INITIALIZE adjustment because production
source, scripts and POM are identical; FAST does not exercise the overlay.
The final overlay is qualified by the fresh corpus, table and adversarial runs.
Resource-limit failure with max-work=1 remains explicit and preserves the prior
output byte-for-byte. No JVM heap was raised; analyzer JVMs ran sequentially,
with 768 MiB RSS guard, 2 GiB host reserve and 60 s timeout per case.

## Evidence and limits

Raw results, stderr, telemetry, commands, source hashes and build manifests live
under `benchmark/results/rd-table-fix-20261009/`. Final evidence is in
`corpus-qualified`, `table-qualified`, `adversarial-qualified`, `scale-qualified`,
`negative-qualified`, `atomic-qualified`, `remainders.json` and `fast-final`.
Earlier attempts remain in `focused-01`, `table-01/02`, `copied-index-03`,
`initialize-view-01/02`, `corpus-final` and the associated build directories.
`table-fix-results.json` records hashes and exact counts. Historical audit
findings are not rewritten; this report records their subsequent correction.

The experiment remains PARTIAL: predicate/caller/value correlations are relaxed,
local candidate products and the number of genuinely demanded cells can still
be large, and the corporate source was not available for testing. No universal
no-loss or convergence proof is claimed. Existing control-specific oracles were
not repeated in this wave; the unchanged DefinitionControl source and fresh
73-program corpus cover the relevant control integration. No merge or push was
performed.
