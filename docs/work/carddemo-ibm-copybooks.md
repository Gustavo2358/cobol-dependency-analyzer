# IBM copybook coverage for CardDemo

- id: CARDDEMO-IBM-COPYBOOKS
- status: IN_PROGRESS
- scope: Extend the approved structural model catalogue to every IBM include in the pinned CardDemo source corpus. Preserve conservative dependency evidence.

## Discovery

CardDemo commit `59cc6c2fd7ebd7ef7925cad552a01a4b8b6e4d5e` contains 44 COBOL source files and 29 additional source variants in the UniKix ZIP. The Micro Focus ZIP contains no COBOL sources. ZIP AppleDouble metadata is not source. Inventory paths and SHA-256 hashes must equal the 73-program regression manifest.

Nine IBM members occur: DFHAID and DFHBMSCA (38 programs each), CMQGMOV/CMQMDV/CMQODV/CMQPMOV/CMQTML/CMQV (three programs each), SQLCA (four programs). Repeated COPYs are retained independently. The six MQ members are currently unresolved; SQLCA remains an opaque SQL INCLUDE. Application COPYs, generated BMS maps, IMSFUNCS/PCB declarations and DCLGEN members are supplied application artifacts, not IBM library fallbacks.

## Design and invariants

Keep the existing SourceMap → syntheticModel → nominalValues modelAssumed authority. No consumer inference from member spelling. Catalogue members have their documented levels, groups, PIC/USAGE and array declarations. Initial runtime values remain unknown. Real configured files win. Missing explicitly mapped SQL artifacts remain missing; fallback must not bypass configuration, I/O failure, nested SQL INCLUDE rules, or COPY REPLACING.

Preserve existing CICS artifact identities and structural-v2 content. MQ and Db2 use explicit vendor/profile artifact identities. SQLCA expansion uses the existing proved SQL INCLUDE preprocessor path, keeps SQL_INCLUDE classification and provenance, and emits the same typed model-input gap.

## Validation plan

Focused complete inventory/shape and provenance tests; real override and replacement/configuration guards; downstream IF/EVALUATE, unknown overwrite and target probes for MQ/SQLCA; existing 14 structural adversaries. Frontend FAST after stabilization. Execute all four production stages for all 73 CardDemo variants and compare candidates, reachability, supports and original provenance with the approved structural baseline. Investigate every delta. Contract remains SP 2.49 / nominal V2; downstream implementation should stay unchanged unless evidence identifies a defect.

No ALTER, unrelated repairs, or merge is authorized by this work item.

## Authority

- [IBM MQ COBOL programming](https://www.ibm.com/docs/en/ibm-mq/9.4.x?topic=mqi-cobol-programming)
- [MQGMO](https://www.ibm.com/docs/en/ibm-mq/9.4.x?topic=mqi-mqgmo-get-message-options)
- [MQMD](https://www.ibm.com/docs/en/ibm-mq/9.4.x?topic=mqi-mqmd-message-descriptor)
- [MQOD](https://www.ibm.com/docs/en/ibm-mq/9.4.x?topic=mqi-mqod-object-descriptor)
- [MQPMO](https://www.ibm.com/docs/en/ibm-mq/9.4.x?topic=mqi-mqpmo-put-message-options)
- [MQTM](https://www.ibm.com/docs/en/ibm-mq/9.4.x?topic=mqi-mqtm-trigger-message)
- [Db2 for z/OS 13 SQLCA](https://www.ibm.com/docs/en/db2-for-zos/13.0.0?topic=sqlca-included)
- [Official MQ redistributable reference](https://public.dhe.ibm.com/ibmdl/export/pub/software/websphere/messaging/mqdev/redist/)

## Catálogo e perfil

| Member | Declarations (including groups) |
| --- | ---: |
| CMQGMOV | 17 |
| CMQMDV | 30 |
| CMQODV | 38 |
| CMQPMOV | 21 |
| CMQTML | 10 |
| CMQV | 2353 |
| SQLCA | 23 |

MQ named field shapes match the official 9.4.0.0 redistributable in both 32/64-bit
variants. The 64-bit files additionally contain anonymous alignment padding in
MQMD/MQOD; those bytes are outside the nominal profile. Constant VALUEs differ
between profiles; neither is admitted as runtime evidence. Exact source hashes
are recorded in `carddemo-ibm-catalogue-sources.json`. The TSV test oracle records
every name, level, parent, PIC, USAGE and OCCURS. Production uses fixed COBOL
resources through the existing normalizer/parser, with no dynamic extraction.

The complete qualified references in two independently wrapped CMQMDV copies
resolve to different correct entities. Standalone qualifier-component diagnostics
may still report ambiguous MQMD; this existing diagnostic behavior does not
change the whole-reference binding and is not repaired in this campaign.

The locality test previously used SQLCA as an opaque INCLUDE. It now asserts
that modeled SQLCA supplies an unavailable MISSING_COPY proof input and still
cannot prove TARGET's cell. SQLDA preserves the original opaque-input assertion.
No dependency expected was relaxed.

## Qualification — 2026-09-27

- CardDemo: **73/73** programs, all four stages newly executed (292 processes),
  with pinned sources and immutable runtime jars. No parser diagnostics.
- All program/file candidate sets, executable supports, source COPY/INCLUDE
  provenance and source operand provenance preserved in **73/73**.
- Conditional candidate evidence and assumptions unchanged in **73/73**. Full
  conditional support payloads are unchanged in 71; COTRTLIC/COTRTUPC replace the
  opaque SQLCA input with unavailable model inputs. The adjacent DCLTRTYP SQL
  declaration now has its own opaque provenance. No candidate/support evidence
  disappeared and no uncertainty was silently closed.
- Seven SPs changed, exactly the three MQ consumers and four SQLCA consumers;
  the other 66 remain byte-identical. There are **24** new resolved model
  occurrences: 20 COPYs and four SQL INCLUDEs. Input remains partial.
- Structural E2E: **21/21**, including seven new MQ/SQLCA adversaries for numeric
  constants, IF/EVALUATE alternatives, model-width writes, unknown overwrite,
  real assignment supports and independent proved kill.
- Frontend FAST: **602 tests**, no failures/errors/skips. Package PASS.
- PERFORM 39, Chaos 48, aliases 14 and PERFORM adversaries 25: frontend reexecuted;
  all SP/compilation products byte-identical. Their downstream evidence is
  explicitly reused with unchanged input hashes and consumer binaries.

The initial MQ kill fixture used reserved word MESSAGE as a data name, producing
an honest parser gap. It was corrected to MQ-MESSAGE; its expected candidate and
forbidden-candidate assertions were retained. All 21 adversaries then passed in
a fresh full four-stage run. Initial RED logs remain available.

No lower/AIR/CFG/dependency implementation change was required by this expansion.
SP remains 2.49 / nominal V2. Consumer PRs update strict producer pins and document
reuse; they retain the structural confidence implementation already under review.
Full qualification and the historical 310-fixture matrix were not rerun because
the changed catalogue/preprocessing paths are covered by the focused suite,
frontend FAST and complete real corpus. No unexplained regression remains.

Raw products and logs are in `.synthetic-dfh/carddemo-expansion/`. The tracked
`carddemo-ibm-validation.json` records commands/results evidence hashes, exact
runtime jars, baseline heads and reuse scope. No merge was performed.
