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

## CLI integration and validation

[Packaged CLI qualification](cli-integration-results.md) records the JAR hashes,
source equivalence, regression results and the precise-mode heap128 limitation.

Both algorithms now ship in the normal product JAR. The frontend, nominal binding,
logical evaluator and sparse table allocation are shared. `--solver` chooses the
analysis algorithm once per invocation; no automatic fallback or per-query
selection exists. `precise` remains the default. The old source overlay and
`build.py` were removed: there are no shadow classes or separate research compiler.
The implementations live under `src/main/java/com/imd/cobolexplorer/`.

## Run the alternative on a source file

Use the same JAR as the default analyzer:

```sh
java -Xmx512m -jar target/cobol-dependency-analyzer.jar \
  --solver reaching-definitions \
  --source programa.cbl --copy-dir copybooks --output dependencies.json \
  --max-work 100000000 --metrics metrics.jsonl
```

For the original algorithm use `--solver precise`, or omit `--solver`. Building
with `mvn -DskipTests package` is the normal application build; users of an already
built JAR need no Python, Git history, Java compiler or special classpath.

Reaching definitions shares physical flow and definitions, relaxing
predicate/caller-value correlations. It can report extra names and earlier
provenance. Exit 1 with output and `REACHING_DEFINITIONS_APPROXIMATION` is expected
PARTIAL, even if a particular result matches precise. Invalid solver names, missing
values and exhausted resources return 2 and preserve the previous dependency
output. There is no automatic retry. Solver choice and graph/work counters are
published only in the `--metrics` sidecar; dependency JSON stays unchanged.

For a comparison using the committed fixtures:

```sh
python3 benchmark/experiments/dependency-witness-demand/run.py /tmp/rd-smoke \
  --jar target/cobol-dependency-analyzer.jar --modes main definitions --heap 128
python3 benchmark/experiments/dependency-witness-demand/check.py /tmp/rd-smoke
```

The runner's historical mode labels `main` and `definitions` invoke the explicit
CLI choices `precise` and `reaching-definitions`. Frozen corpus inputs/outputs are
local ignored artifacts; the commands above require only tracked fixtures.

Qualification uses sequential JVMs with 128 MiB heap (512 MiB for scale), 768 MiB
RSS guard, 2 GiB host reserve, 60 s timeout and max-work 100M. Raw output, commands,
input/JAR hashes and resource telemetry are preserved. A guard stop never counts
as passing.

```sh
python3 benchmark/experiments/dependency-witness-demand/control-regressions.py /tmp/rd-tables \
  --jar target/cobol-dependency-analyzer.jar \
  --fixtures src/test/resources/dependency-definitions
python3 benchmark/experiments/dependency-witness-demand/run.py /tmp/rd-corpus \
  --jar target/cobol-dependency-analyzer.jar --focused --carddemo --heap 128
python3 benchmark/experiments/dependency-witness-demand/run.py /tmp/rd-scale \
  --jar target/cobol-dependency-analyzer.jar --sizes 2048 --family dynamic-only
```

Previous overlay reports are historical evidence for the pinned baseline and
compiled overlays they identify. They are not relabeled as CLI integration tests.
The corporate source itself has not been tested.

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
runner accepts `--fixtures src/test/resources/dependency-definitions`
for its table cases. Two cases deliberately expect fewer names than
frozen main: a copied index overwrites the exact cell, and whole-table INITIALIZE
resets its equivalent REDEFINES view, but frozen main conservatively retained OLD. The raw RED run and that separate baseline expectation are
preserved; corpus dependency preservation is checked separately.
