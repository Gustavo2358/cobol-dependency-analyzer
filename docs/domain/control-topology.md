# Frontend control topology — SP 2.39.0

The frontend owns COBOL control structure. The closed Semantic Product transports
that structure; lower binds and materializes it without discovering paragraph,
arm or range completion from legacy statement facts.

## Authority and migration

`controlTopology` is required in 2.39.0. Its authority is
`FRONTEND_CONTROL_TOPOLOGY_R1`. Versions through 2.38 retain their historical
interpretation and reject the new field. New wire without topology is rejected.
The producer's branch already contained 2.38 before this wave. Historical State
constructors and typed ports remain available and do not acquire topology.
New and legacy assemblers are selected once. Legacy control fields remain useful
for compatibility and operation payloads, never as an alternative authority for
completion in the new assembler.

## Closed algebra

Occurrence identity reuses `statement:N`. Region identities use frontend AST
identity plus role; they do not depend on transport ordering or program names.
Every region owns an explicit boundary. Root procedure, paragraph, IF/EVALUATE
parent and arms, FILE parent and handlers, inline body and invocation range are
first-class records. Declarative sections are isolated from ordinary flow.
A RANGE references ordered existing regions and its binding names the last
boundary as endpoint. The range does not duplicate source occurrences.

Targets are OCCURRENCE, REGION_ENTRY, COMPLETE(region), PROGRAM_RETURN or
UNKNOWN_LOCAL(region). Outcomes distinguish NORMAL, BRANCH, EXPLICIT_TRANSFER,
LOCAL_INVOKE, PROGRAM_RETURN and UNKNOWN_LOCAL. COMPLETE is symbolic: the same
paragraph has an ordinary default and can complete the matching active binding.
A THRU intermediate boundary follows its ordinary default; only the final active
endpoint resumes the caller. Arm completion composes through the parent boundary.
An explicit transfer/return does not acquire a completion edge.

Bindings carry their own resume and finite phase graph. BODY and RESUME are
endpoints; predicate/effect phases publish routing explicitly. ONCE, BEFORE/AFTER,
TIMES and single-level VARYING use this graph. Nonpositive literal counts retain
the historical unavailable-count capability frontier; their resolved range stays
inventoried without an executable binding or resume bypass. Value/effect precision is separate
from routing. Inline repetition may retain unknown predicate/effect values.

## Proof and integrity

Every region, boundary, outcome, target and binding cites proof identities.
Proofs distinguish local grammar, resolved targets, expanded includes, input
isolation and partial unknown knowledge; provenance retains expanded/original
locations and include chains. Missing DATA input does not erase independently
closed procedure syntax. Missing procedure structure stays unavailable.

The same occurrence/outcome inventory drives reference closure and emission,
including event-specific FILE handlers. Targets are bound in the same activation
as the emitting operation. Invalid identities, missing targets, inconsistent
ownership/endpoints, malformed phases and symbolic alias/proof cycles reject
before AIR. Executable control loops remain representable.

## Partiality and scope

UNKNOWN_LOCAL identifies the region whose source control knowledge is unavailable.
It is not a guessed set of destinations and does not claim source impossibility.
The current AIR backend preserves an explicit unavailable projection frontier:
UNSUPPORTED coverage, control UNAVAILABLE, proof/context origins, and an open
control envelope with no licensed labels. No return, divergence, fallthrough,
all-label scope or synthetic caller bypass is inferred. CFG exposes the open
frontier; dependencies distinguish completed computation in the known model from
incomplete source coverage. See AIR 00 §5, 05 §6 and 06 §§1,3.2,8.

Source occurrences outside the entry projection remain explicit coverage items;
unreachable activation trees are not expanded. Recursion retains its existing
unsupported local frontier, without a return bypass. Section-target PERFORM,
special EXIT, multilevel VARYING and unresolved callbacks remain explicit limits.
USE/SORT callback bindings are not implemented in this slice: source occurrences
remain inventoried, unavailable routes are explicit, and SORT cannot bypass a
required callback. The pinned CardDemo 73 has no USE/SORT local callback plans.

## Validation and review

Contract tests cover old/new separation, malformed wire and in-memory parity,
roundtrip, physical permutation and reference mutation. Region tests cover terminal
verb substitution, ordinary versus performed entry, THRU endpoints, two callers,
nesting, predicate/input gaps and FILE handler inventory/event separation.
Real source witnesses, external full73 redistribution and backend scale remain
mandatory campaign gates; unit tests alone do not qualify the wave.

## FILE executable target authority (R1-R1)

For SP2.39, ControlTopology is the sole executable target authority. FILE
`fileInventory` retains domain facts: operation identity, event/effect association,
handler metadata and source evidence used by the frontend when building topology.
Each destination slot has exactly one published outcome role
`file/<use ordinal>/<event>/<destination ordinal>` on its source occurrence.
The producer verifies that a handler outcome names the corresponding FILE_HANDLER
region and that the region entry is the source handler entry.

The consumer validates FILE domain shape and role coverage, but it never obtains
an executable target from the legacy handler member order or continuation. The
source reference closure and the emitter both resolve the published outcome.
There is no consumer fallback or target equality reconstruction against FILE
metadata; an absent role is invalid input before AIR. Unsupported callback
knowledge remains UNKNOWN_LOCAL. Historical SP contracts retain legacy routing.

Permanent tests: producer FileTopologyAuthorityTest checks every destination and
its handler entry; lower FileTopologyAuthoritySuite reverses legacy handler member
metadata while retaining topology and requires identical AIR modulo publication
namespace. It removes each FILE outcome and its occurrence reference, leaving an
internally well-shaped graph, and requires rejection before AIR through both wire
and typed ports. The unchanged ControlTopologyAuthorityTest is in fixed FAST.

### Outcome role cardinality (R1-R1-F03)

Each occurrence has at most one outcome for a given role. IDs distinguish records,
not competing interpretations of a control slot. This invariant applies to all
outcomes, including FILE destination slots; alternatives have distinct roles.
The same role on different source occurrences is valid. Duplicate roles reject
before AIR even when their targets agree, regardless of outcome identity or
physical inventory order. Producer and consumer typed constructors validate this
invariant; the new wire decoder uses the same consumer validation. No target is
recovered from fileInventory, and no verb or program exception is introduced.

SP2.39's shape and authority are unchanged. This corrects acceptance of malformed
input under the existing single-outcome role contract; it does not reinterpret
historical SP versions or introduce R2 fact locality. Permanent FILE authority
tests cover FILE and non-FILE duplicate roles, equal/conflicting targets,
first/last identities and reversed inventory, with typed/wire rejection parity.

## FILE composite control — SP 2.51

`FRONTEND_CONTROL_TOPOLOGY_R2` adds `fileFlows` and the `FILE_POINT` target.
Other topology rules are inherited from R1. The writer selects 2.51 only when
a nonempty composite flow is published; empty inventories are omitted for R1.
Each flow owns an existing source occurrence and points keyed by opaque IDs.
USE points bind fileInventory ordinals; CHOICE points publish finite aggregate
alternatives. USE has one ordinary target. Event outcomes remain the authority
for each result (including handler/UNKNOWN_LOCAL routes and critical exits).
Only same-owner FILE outcomes/points can target a FILE_POINT. Regions, boundaries
and invocation resumes cannot enter halfway through another statement.

The consumer checks use inventory equality, ownership, proof provenance, entry,
references and reachability of every point in the ordinary internal graph. These
points never enlarge the COBOL occurrence inventory. It binds them with the
current activation, including PERFORM endpoint and CICS state context. The
handler-state analysis traverses the same published points, retaining derivations.

OPEN/CLOSE chains visit successive operands before completing their statement.
SORT/MERGE without procedure callbacks publish INPUT → WORK → OUTPUT; selection
loops retain unknown participant order/count. Critical-error remainders and
unknown USE/SORT callbacks remain explicit. No physical FILE metadata becomes
an alternative source of executable destinations. Earlier contracts retain
their historical routing. See [work item and oracles](../work/file-composite-control.md).

The structural-only port may omit I/O event routes. Its USE point then supplies
ordinary continuation directly. This does not admit memory effects or close error
coverage. SP2.51 still requires a locality inventory: an unavailable physical-profile
input and empty facts/bindings express absence of storage analysis explicitly.
An unavailable FILE event plan with no continuation is not a contradictory claim.
An explicit event continuation is still validated against structural completion.

## SP2.56 — sentenças, busca e terminações

Uma região `SENTENCE` publica a conclusão do período quando houver NEXT SENTENCE. Seu `ESCAPE` abandona frames inline contidos e liga a conclusão ao contexto procedural ativo; não equivale à continuação do IF/SEARCH. `SEARCH`/`SEARCH_ARM` abstraem a busca interna de SEARCH ALL como decisão match/fim; o corpo WHEN não é uma iteração. O índice permanece MAY desconhecido (`SEARCH_INDEX_MAY`), sem prova de valor ou kill.

`PROGRAM_HALT` representa STOP RUN sem sucessor, distinto de `PROGRAM_RETURN`. Lower publica a alternativa AIR HaltAlternative com controle fechado e efeitos de finalização abertos (o codec atual não admite Halt isolado). EXIT PROGRAM contido retorna; para unidade externa, o papel main/chamado não é conhecido pelo contrato: retorno e continuação são possibilidades distintas. ENTRY não executa uma chamada e não concede nova raiz: há somente continuação sequencial, quando alcançado. Entradas alternativas continuam fora da projeção principal. [Regra e testes W6](../work/carddemo-control-w6.md).

## SP 2.57 — active binding reentry

`Binding.reentryPolicy` belongs to the producer contract. `SOURCE_UNDEFINED` means
that invoking the same binding identity while it is still active has no defined
source semantics. `UNSPECIFIED` preserves historical publications; it does not
grant recursive return semantics. The legacy typed constructor supplies UNSPECIFIED.
A consumer must not derive this policy from paragraph names or nesting depth.
Sequential invocation after completion is not reentry.

The IBM Enterprise COBOL producer publishes SOURCE_UNDEFINED for PERFORM bindings,
including every VARYING level through the containing binding. IBM's
[Basic PERFORM rule](https://www.ibm.com/docs/en/cobol-zos/6.4.0?topic=statement-basic-perform)
forbids a PERFORM causing its own execution. Conditional source possibilities do
not prove that this happens at runtime; no condition evaluation is implied.
A reached reentry remains open control and effects, with no invented return,
halt or kill. Source dependency possibilities retain their existing conditional
authority and do not certify execution through an undefined activation.

## FILE routes after parser recovery

A recovered FILE use can remain in the inventory while its statement has no
proved normal outcome. In that case every `file/<ordinal>/<event>/<slot>` role
is still published, with `UNKNOWN_LOCAL` and `PARTIAL_UNKNOWN` evidence rooted
in the source occurrence. The producer cannot infer handler-region entry,
internal operand sequencing or statement completion from recovered FILE metadata.
`PartialFileTopologyTest` covers native operations, callbacks, composites and
malformed syntax before/after the operation; valid-route authority remains tested
by `FileTopologyAuthorityTest` and `FileCompositeTopologyTest`.
