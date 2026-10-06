# tis-intellij-plugin (retired)

This plugin is retired (BOND-75, 2026-10-05). The repository is archived and read-only.

## Why

The plugin implemented a YAML-shaped `.tis` dialect (`tis: "1.0"`, `ground_truth:`, `conditions:`, `chain:`,
`metadata:`) with its own error codes (E000–E401). No codec ever read that dialect and no producer writes it. The
canonical `.tis` grammar is the block form (`DECLARE`, `CONDITION`, `COUPLE`, `ACCUMULATE`, `SCOPE`) defined in
`forge-os-bus/.docs/ingestion-pipeline/TIS_FORMAT_SPEC.md`, and the plugin could not read it:

- block keywords lexed as plain identifiers, and every `/` of a `//` comment as a bad character;
- its structural checks matched none of the current `.tis` files the reference parser accepts;
- it flagged spec-valid input (a zero `det`, a phase above π) as errors and missed real faults (unindented keys,
  repeated keys, malformed numbers, unclosed lists, yield and `d_C` mismatches).

An editor that confidently validates a grammar nothing emits is worse than no plugin. The 2026-10-05 audit is
recorded on BOND-75.

## What replaces it

- `.tis` is moving to strict TOML (`.tis` v2, BOND-72 / BOND-76). Every JetBrains IDE supports TOML out of the box.
- Until then, validate a v1 `.tis` with the reference parser in `verdex-codec-kt` (`TisParser.parse`, then
  `TisEngine.execute`), or the gateway's `POST /api/vhc/VALIDATE`.
- `.vdx` is a binary container; IDE support for it is tracked in FORGE-2293.

## History

The source is in this repository's history: the last commit before retirement is the parent of the commit that
removed `path-a-textmate/`, `path-b-gradle/` and `testdata/`. The four YAML-ish fixtures were converted to the
canonical grammar in `composition-health` (`coupling/src/test/resources/tis/`), where a test runs them through the
reference parser.
