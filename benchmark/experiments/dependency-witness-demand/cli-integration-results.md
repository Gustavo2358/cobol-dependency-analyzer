# Packaged CLI solver selection

Implementation: `8c78ec5c492bb7958b8635d84cbf1b2373d84ddf`. Frozen main: `52c1b82dc6accbb615818cf5b5298843a85b0f01`.
Final JAR SHA-256: `87a7eebddb1194302210772c397ed1ff26faf396e533317f803fd6ae71cb1572`.
Comparison JAR SHA-256: `71a04306ccc8058e0c802ba8f3263ebd9393e06e022595d0bef6edb7e8c2aa13`.

`--solver precise` is the default; `--solver reaching-definitions` selects the
shared definition graph in the same JAR. No overlay compiler, shadow classpath,
automatic fallback, fixture admission or per-query selection remains. The local
evaluator, sparse table allocator and dependency publication are shared.

Solver identity and graph/work counters appear in `--metrics`, not dependency
JSON. Reaching definitions discloses its correlation approximation and returns
1/PARTIAL with output. Bad arguments and max-work failure return 2 and preserve
the previous output. Tests cover both algorithms, the default, a missing/invalid
solver, sparse cells and full-table INITIALIZE of proven equivalent views.
That INITIALIZE correction now applies to both modes; its source oracle excludes
OLD after the reset. This removes a false positive also present in frozen main.

| Check | Result |
| --- | --- |
| Final FAST | 1,096 tests / 142 suites; zero failure/error/skip |
| CardDemo | 73/73 JSON identical to frozen main, in both modes |
| Standalone, reaching definitions | 36/36 complete; zero lost names; the same five audited extras and 32 earlier locations |
| Standalone, precise | 35/36 complete and identical; return-fanout-128 exhausts heap128 |
| Table source oracles | 14/14 pass, including million-position tables and alias reset |
| Boundary/handler source oracles | 15/15 pass |
| Final adversarial CLI | 50/50 runs; zero losses; four previously expected extras; both identity metamorphisms pass |
| Final dynamic-only N=2048 | 2,049 expected names; 7.93 s; 561.6 MiB RSS; heap512 |
| Negative N=2048 | SAME only; DEAD excluded |
| Packaged failure/default checks | 4/4 pass; old output preserved |
| Previous qualified overlay versus CLI | All 109 corpus and 25 adversarial JSON documents identical |

The constrained-heap precise failure is explicitly retained, not counted as a
pass. Frozen main was rerun with exactly heap128 and failed with the same JVM
exit 3 (ExitOnOutOfMemoryError), in 26.87 s. Its historical successful
qualification used heap512. No heap was raised during this wave. Reaching
definitions completes this case at heap128. The corporate source was not tested.

## Evidence reuse and resources

The broad corpus, table and handler comparisons use the comparison JAR above.
Afterward, cleanup removed one unused Flow convenience overload and reorganized
only Analyzer metric aggregation. All active Flow methods have identical
normalized instruction listings; every reaching-definitions/control/value/
publication class is byte-identical. The complete class comparison and empty
Flow bytecode diff are preserved. Final FAST, packaged adversarial cases, scale,
argument failure/atomicity and default alias initialization were newly rerun on
the final JAR. Corpus and source-oracle evidence is reused under that equivalence,
not described as a new final-JAR execution. Full unrelated frontend campaigns
were omitted because no frontend contract changed.

Raw commands, telemetry, stdout/stderr, source/JAR hashes, both JUnit waves and
comparison reports: `benchmark/results/rd-cli-integration-20261010/`.
The initial naming-gate rejection and javap formatting-only comparison failure
are retained separately; neither raw evidence nor semantic oracles was edited.
Table fixtures were moved byte-for-byte to
`src/test/resources/dependency-definitions/` so they can be product test resources.

All analyzer JVMs ran sequentially: heap128 for qualification, heap512 for scale,
RSS guard768, host reserve2 GiB, timeout60 s and max-work100M. Compiler/Maven used
heap128; FAST had a separate 180 s aggregate-process guard. Reaching definitions
continues to overapproximate predicate/caller-value correlations; these results
are empirical dependency preservation, not a proof for all COBOL programs.
