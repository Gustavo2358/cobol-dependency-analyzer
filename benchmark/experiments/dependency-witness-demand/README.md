# Dependency witness demand — research prototype

Branch: `experiment/dependency-witness-demand`, based on main
`52c1b82dc6accbb615818cf5b5298843a85b0f01`.

The canceled bitset experiment branch was deleted. Its two commits were saved
in the verified, ignored bundle at
`benchmark/results/failed-experiment-20261009/failed-experiment.bundle`; its
benchmark documents were archived next to that bundle. The origin-graph
experiment branch remains untouched.

## Question and intervention

Can we stop doing work when it can only reproduce dependencies already found?
This prototype starts **before each CALL**, walks backward through assignments,
and records each discovered (dependency type, name) once, at its earliest
source site. Queries are visited in source order. A flow-insensitive scalar
value bound tells us which names remain possible; finding one abstract witness
is sufficient to retire that name. Later CALLs only search for missing names.
The pending bound is shared per (type, declaration), so constructing it does
not itself reproduce a query × candidate matrix.

The backward goal contains a physical location, a declaration and a compact
text-fit transform. It does not contain a complete environment or a sequence
of context/state snapshots. Full scalar MOVE kills the old definition. Text
copies preserve padding/truncation, including chains with different widths.
ACCEPT conservatively retains the known candidate and opens the unknown part,
matching the current product's behavior.

A second adversary puts an impossible name in the bound: VALUE 'DEAD' is
unconditionally replaced with 'SAME' before entering the dispatch loop.
Stopping after positive findings alone repeats a failed search at every CALL.
An **exhausted** traversal certifies that the names still pending cannot be
obtained from any visited goal. The pending set only shrinks, so these negative
certificates can be reused at later sites for the same (type, declaration).
A traversal stopped early never creates a negative certificate.

## Scope and precision

This is an isolated compiler overlay under `benchmark/experiments`, not a
production implementation, feature flag, alternate production path or fallback.
`build.py` compiles against the frozen main JAR and reuses the actual frontend,
nominal binding, control topology, source dependencies and JSON writer.

The control graph is deliberately a **may approximation**: it ignores predicate
correlations and shares PERFORM boundary returns between callers. A witness in
this graph does not prove a balanced, feasible concrete COBOL execution.
Negative sentinels explicitly expose both sources of false positives. Unknown
remainders are retained conservatively; diagnostic and exit-code parity are
not claimed. Known names and their earliest locations are compared separately.

Value support is restricted to bound scalar text references, basic text VALUE
and literal/scalar MOVE, and conservatively open input. Indexed/group/partial
references, numeric value transforms, aliases, event/file control and unsupported
mutations fail with `UNSUPPORTED_EXPERIMENT`. No old solver is invoked to make
an unsupported case look successful. Unsupported corpus cases are gaps, not
passes or evidence of preserved dependencies.

## Reproduce

Use the configured JDK; the baseline JAR is:
`benchmark/results/remote-requalification-20261009/remote.jar`, SHA-256
`6bd96f31911976c097c9fb47377587c11bf3ebe6dc60305190104314bc39b036`.

```sh
python3 benchmark/experiments/dependency-witness-demand/build.py /tmp/witness-build --jar benchmark/results/remote-requalification-20261009/remote.jar
python3 benchmark/experiments/dependency-witness-demand/run.py /tmp/witness-small --jar benchmark/results/remote-requalification-20261009/remote.jar --build /tmp/witness-build --sizes 32 64 128
python3 benchmark/experiments/dependency-witness-demand/run.py /tmp/witness-large --jar benchmark/results/remote-requalification-20261009/remote.jar --build /tmp/witness-build --sizes 512 2048 --modes full stop
python3 benchmark/experiments/dependency-witness-demand/run.py /tmp/witness-negative --jar benchmark/results/remote-requalification-20261009/remote.jar --build /tmp/witness-build --family negative-bound --sizes 128 512 2048 --modes main stop-no-cache stop
python3 benchmark/experiments/dependency-witness-demand/laws.py
python3 benchmark/experiments/dependency-witness-demand/check.py /tmp/witness-small /tmp/witness-large /tmp/witness-negative
```

`full` and `stop` use the same frontend, graph, transfer semantics and bounds.
Only pruning and its negative cache differ. `stop-no-cache` isolates the cache.
All runs are sequential, max work 100M, timeout 60 seconds, RSS cap 768 MiB and
host reserve 2 GiB. Large comparisons use the same 512 MiB heap. Smaller
qualification runs use 128/256 MiB; the heap cap was not increased.
Raw stderr/stdout, input copies and hashes, JSON, time, telemetry, commands and
instrumentation remain in `benchmark/results/witness-demand-20261009/`.

## Qualification boundary

Production sources, build settings and FAST scripts remain identical to main.
The frozen main FAST qualification is reused only for that unchanged product;
FAST has not qualified the new solver. New evidence consists of the overlay
compile, 1,024 fit-composition checks, ten positive/negative CLI cases, synthetic
scaling and corpus admission/comparisons. See `results.json` and `results.md`
here for the measured outcomes. This branch is not ready for production merge.
