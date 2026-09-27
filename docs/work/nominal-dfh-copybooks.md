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
