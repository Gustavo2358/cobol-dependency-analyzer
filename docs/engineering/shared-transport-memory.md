# Publication-owned transport sharing

The canonical port is immutable. Transport projection may share DTOs only for
fully equal typed paragraph facts, including identity, entry, ordered statements,
completions and provenance. The lookup is local to one publication; hashes are
accelerators and full equality decides. No source names, adjacency or range
heuristics decide control. Different paragraph payloads remain different facts.

A read-only mapping list keeps the immutable source membership order and computes
one temporary statement/range DTO at a time. A unit-local dictionary owns the
unique paragraph DTOs. Serialization visits every original membership; the JSON
contract/version/field order and bytes do not change. A byte API still has its
intrinsic output size; the file API has no document-sized byte buffer.

With S statements, M procedure memberships, U distinct complete paragraph facts
and P paragraph payload, writing takes O(S+M+P_wire) work and DTO ownership is
O(U payload + immutable input inventory). Source memberships may still be large;
this does not change their semantics or promise constant memory for unique data.
The finite input inventories establish termination. No global intern pool exists.

A retention oracle walks the actual owned document after serialization, without
expanding computed views, and bounds retained paragraph DTOs by distinct published
facts. Frozen existing wire tests and write/serialize parity preserve behavior.
The same generated overlap/control sources are used for elapsed time and RSS.

## Identity buckets before complete equality (pre-code law)

Full-payload HashMap keys rehash statement/completion membership and provenance
for every lookup, including unique paragraphs. Retained no-increase performance
REDs final-01/final-02 justify replacing only this work. Published identity selects
an owner-local bucket; complete immutable payload equality still decides reuse.
Same-ID variants, colliding identities and all physical/typed failures remain
independent. No payload hash is needed. Cost is cheap-key lookup plus unavoidable
full equality within a bucket; distinct variants can still make buckets large.
Finite iteration terminates; no cap, source heuristic or semantic omission is used.
The independent oracle makes full payload hashing throw and checks hand-authored
equal payloads, same-ID unequal proofs and Aa/BB key collisions.

The architecture oracle admits the exact private ParagraphMemo owner and its
nested entry and scans their dependencies under the same closed adapter rule.
No broad transport-package exemption or frontend/projection dependency is added.

## Typed ordinary-entry and overlap proof indexes (pre-code)

Ordinary incoming exclusion is exactly: every unclosed GOTO statement belongs
to the queried member set, and every normal/closed-GOTO predecessor of a member
also belongs to that set. Unreachable edges are included, matching the existing
rule. Build reverse transfers once per unit from the same typed statement,
continuation and GOTO evidence; query only incoming edges to members. Distinct
member sets overlap iff any statement belongs to both; equal sets remain exempt.
An incidence index computes those flags in expected O(total memberships) without pairwise
range intersection. No parser text, naming or distance participates. All proof
facts/gaps and procedure inventories remain unchanged. Independent scalar edge
scans and pairwise intersections, permutations, disconnected sets and duplicate
equal sets are the oracles; existing perform-family/goto product fixtures govern
final integration and unchanged complete wire.
