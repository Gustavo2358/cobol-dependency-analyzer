# Default work budget — 2026-10-10

CLI and `Options.defaults()` now share `DEFAULT_MAX_WORK = 100000000`.
The previous default was 1000000. Help and README disclose the new value.
Explicit `--max-work N` still overrides it for either solver.

- Focused analyzer/CLI: 60 tests passed, including explicit-budget failure and preservation of the previous output.
- FAST: 1096 tests / 142 suites, zero failures/errors/skips.
- Packaged CLI, reaching definitions, dynamic-only N=2048 **without --max-work**: 7.83 s; RSS 627.3 MiB; heap512.
- All 2049 expected program names; full dependency JSON identical to the previously published N=2048 run.
- Exit 1/PARTIAL is the expected correlation-approximation disclosure; no resource guard fired.
- JAR SHA256: `1153e987c23dc8d2a94baa4371171c972c510e36e902ea1c2b0045aeec6e1a16`.

The first FAST attempt exhausted the 128 MiB Maven heap during Java compilation after grammar generation. A separate compiler process, also capped at 128 MiB, passed the complete FAST. The failed log is retained in `benchmark/results/rd-default-fast-20261010/`; the successful retry is in `benchmark/results/rd-default-fast-forked-20261010/`. No heap was raised.

CardDemo and broad standalone comparisons were not rerun. Their qualified runs already explicitly selected this same 100-million budget; the default change does not modify analysis algorithms or invalidate those explicit-budget results. This is evidence reuse, not a new corpus execution.

Raw N=2048 command, telemetry, output and runner: `benchmark/results/rd-default-max-work-20261010/`. Focused and packaging logs were copied there. Compact results: [default-max-work-results.json](default-max-work-results.json).
