# Sparse reaching definitions — research experiment

Branch `experiment/dependency-witness-demand`, baseline main
`52c1b82dc6accbb615818cf5b5298843a85b0f01`.

This is the generalization of the witness-search prototype. `WitnessFlow.java`
and its scalar-fit helper were removed. There is one experimental solver,
`SparseDefinitions`; there is no admission whitelist, old-solver fallback,
fixture-specific dispatch, or per-query algorithm selection. The old experiment's
measurements remain in Git and in `results.md` / `results.json` as historical
evidence, not executable code.

## What is shared

Build physical control once, using the frontend's existing topology, including
file/exception handlers and alternate entries. `DefinitionControl` propagates
sets of boundary policies forward as immutable, shared bitsets. A policy contains
the endpoint and ancestor escape rule, never a value environment. A boundary
stops only the policies that must return there; other policies may fall through.
Reachable PERFORM sites subscribe to completions of their callee policy. The
existing frontend rules decide each completion/escape delivery. No backward
per-entry result columns or value cells per policy are built.

A small forward handler lattice follows HANDLE/IGNORE/default dispositions and
ABEND activation, CANCEL and RESET. An eligible event alone does not authorize
ordinary continuation: IGNORE or a published explicit completion must allow it.
The control facts and subscriptions are released before discovering value
definitions. The previous unconditional boundary/event graph builder is removed.

Caller/value and predicate correlations remain relaxed: the value graph shares
physical return edges after control reachability. It can still produce extra
names and earlier source locations. This is not full call/return matching or a
proof that every combined physical path is executable. Program return/halt does
not create a resume edge.

Starting at the BEFORE operand of each CALL/CICS query, discover only demanded
logical definitions. Follow a declaration backward to a write, initial value or
control join. Bypass single-predecessor identity chains, caching the representative;
commands that do not change the declaration do not become new definition nodes.
Full assignments replace definitions. Weak effects retain candidates plus an
unknown remainder, following the existing local value semantics.

Joins form union equations. Collapse their strongly connected components, then
propagate candidates to a fixed point. Transformations remain separate equations:
a width-changing copy is not identified with a plain union. Each equation uses
local operands rather than a whole caller environment, and all queries read the
same completed graph. Equal shared answer sets are enumerated once per dependency
kind/file validation rule, preserving earliest provenance and notices.

The existing logical evaluator handles group MOVE, CORRESPONDING, numeric/text
fits, REDEFINES, INITIALIZE, SET, partial references and generic frontend effects.
Statement support and binding are reused, not reimplemented in a second frontend.
OCCURS uses the existing sparse logical table evaluator: one remainder for
unmaterialized positions and separate cells only for valid indexes identified
by source expressions. Unknown-index writes stay weak; exact full writes kill
only their selected cell; whole-table INITIALIZE resets both cells and remainder.
Static subscripts allocate cells immediately. Computed subscripts are learned
from shared value equations; new cells trigger another value-only round on the
same physical control graph. No PERFORM context solver is invoked or rebuilt.
Aliases, group text and partial-cell writes reuse the canonical local evaluator.
The previous declaration-only table override is removed.

Subscript and reference-modification addresses need an additional local rule: a merged offset
with several known values must still generate each known substring. Stream the
finite alternatives of address operands, evaluate each through the same local
transformer, and union them with the conservative merged-address remainder.
An address with no fact yet (BOTTOM) postpones evaluation. It is not an unknown
runtime address: treating it as UNKNOWN would insert an early weak write that
monotone candidate union could not retract later. Genuine UNKNOWN still retains
all possible writes and known candidates.
No product of *program paths or callers* is created. A local candidate product can
still be large, so exhaustion is an explicit RESOURCE_LIMIT, not truncation.
Generic STRING/arithmetic/external effects retain the product's existing known
candidates and unknown remainder; this does not add new string-construction
semantics that the baseline frontend/evaluator does not provide.

## Isolation and validation

`build.py` generates an ignored research overlay from the pinned main sources and
JAR. It exposes the existing local evaluator and bypasses the production control,
context solvers. Sparse table allocation and local transfer are reused; table
demand closure runs over the experimental value equations. Empty control/relevance adapters only satisfy
the unchanged CLI metric shape. They do no graph analysis. Production `src/`, POM
and scripts are unchanged; this is not yet a production replacement or PR.

The sparse graph reuses ideas/local evaluator integration from the separate
`experiment/value-origin-graph` branch; that branch remains untouched. This
experiment additionally bypasses identity chains, requires physical boundaries
for returns, supports joined substring addresses, and removes the restricted
witness-search algorithm rather than maintaining it beside the new one.

Run JVMs sequentially, with at most 512 MiB heap, 768 MiB RSS, 2 GiB host reserve,
60 s timeout and max-work 100M. Qualification uses 128 MiB heap. The runner records
raw output, commands, source/JAR/build hashes, resource limits and peak RSS. It
stops a campaign on a memory guard. A stopped run is not counted as passing.

```sh
export JAVA_HOME=/path/to/jdk21
python3 benchmark/experiments/dependency-witness-demand/build.py /tmp/rd-build \
  --jar benchmark/results/remote-requalification-20261009/remote.jar
python3 benchmark/experiments/dependency-witness-demand/run.py /tmp/rd-negatives \
  --build /tmp/rd-build --jar benchmark/results/remote-requalification-20261009/remote.jar \
  --modes main definitions --heap 128
python3 benchmark/experiments/dependency-witness-demand/run.py /tmp/rd-corpus \
  --build /tmp/rd-build --jar benchmark/results/remote-requalification-20261009/remote.jar \
  --focused --carddemo --heap 128
python3 benchmark/experiments/dependency-witness-demand/run.py /tmp/rd-scale \
  --build /tmp/rd-build --jar benchmark/results/remote-requalification-20261009/remote.jar \
  --sizes 32 64 128 512 2048
```

CLI exit 1 is PARTIAL, not failure, when output exists: the approximation is
always disclosed. Compare actual dependency names and source locations against
frozen baseline JSON. No lost baseline name is acceptable; additional names and
location changes are reported separately. Corpus parity is empirical, not a
universal soundness proof. `reaching-definitions-results.md` records this wave.
The subsequent boundary/handler correction and its qualifications are in
[control-fix-results.md](control-fix-results.md). `control-regressions.py`
compares 15 independent name oracles on main, the previous experiment build,
and the corrected build; pass `--jar`, `--before`, and `--after` build paths.

The table/INITIALIZE correction is qualified in
[table-fix-results.md](table-fix-results.md). The same bounded independent-oracle
runner accepts `--fixtures benchmark/experiments/dependency-witness-demand/table-fixtures`
for its table cases. Two cases deliberately expect fewer names than
frozen main: a copied index overwrites the exact cell, and whole-table INITIALIZE
resets its equivalent REDEFINES view, but frozen main conservatively retained OLD. The raw RED run and that separate baseline expectation are
preserved; corpus dependency preservation is checked separately.
