# Analyzer integration — DONE / MERGED

Approved on 2026-10-01. [Frontend #77](https://github.com/imd/proleap-poc/pull/77)
merged as `fb88bf8299f368d65f5fb61b009261faae441735`, preserving qualified HEAD
`188300e78c792b8f39db0c4f04c03f4b1716273e` and its implementation commits.
The fixes cover continuation normalization, partial FILE publication, SUPPRESS,
partial EVALUATE/IF composition, localized CICS nominal gaps and JSON GENERATE.

[Discovery #59](https://github.com/imd/proleap-poc/pull/59) merged as
`3e3ad03bb29442e8d1c53fb9ade2f66d309d315d`. Its D0 measurements and recommendations remain historical, not a description
of current pipeline coverage.
No product code or normative contract came from that documentation publication.

## Qualification retained

[Final implementation Fast CI](https://github.com/imd/proleap-poc/actions/runs/36794479412)
passed. The checkpoint-4 qualification records Fast 725, local full 1,296 Maven
tests (zero failures/errors, one existing opt-in skip), normalization and naming.
It includes 28 new four-stage JSON GENERATE runs and four rejected oracle mutations.
The fresh 560-SP replay was byte-identical to the previous qualified checkpoint;
2,240 downstream products were reused after consumer identity/hash checks.

The integration changes documentation only. Production, grammar, tests and build
inputs remain equal to the qualified HEAD; corpus and full evidence are reused,
not represented as new executions. See [publication fixes](stage5-photo-publication-fixes.md)
and [JSON GENERATE qualification](stage5-json-generate.md).

PARTIAL, source qualification, unresolved runtime values/effects and the existing
short-record literal policy remain. JSON serialization at runtime and JSON PARSE
are outside this change. This closure does not authorize another capability.

Final repository revisions, main CIs and equivalent consumer pins are recorded
in workspace `artefatos-e2e/analyzer-integration-20261001/REPORT.md`.
