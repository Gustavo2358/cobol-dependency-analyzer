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
