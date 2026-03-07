# TIS IntelliJ Plugin

Language support for **TIS (Truth Instruction Set)** — a composition description language at L0 of the GTC/CPT/PMCA/Application stack where degeneracy is grammatically inexpressible.

TIS has four primitive types: **Entity** (noun), **Condition** (adjective), **Coupling** (verb), **Scope** (sentence boundary). ACCUMULATE is not a type — it's the parsing process itself. The parser maintains a running chain product as it reads. A degenerate composition is a syntax error, not a runtime exception.

This plugin implements the **Expression IS Verification** principle at the editor level: the same pattern that appears in DNA→protein folding (unstable proteins can't be expressed), ISA→processor execution (invalid instructions are inexpressible), and type theory (illegal states unrepresentable).

## Features

### Lexer-Level Checks (Rule L1–L4)
- **E302 TYPE_ERROR**: `det: 0.0` and `det: 0` render as red strikethrough. Zero determinants are caught at tokenization — the lexer refuses to produce the token.
- **E302 TYPE_ERROR**: `jacobian: 0.0` same treatment — it's also a determinant.
- **E401 PHASE_OVERFLOW**: Individual `phase:` values exceeding π flagged immediately.

### Structural Annotator (Rules P1–P5, R3, V1–V3)
- **E100 MALFORMED**: `ground_truth` must appear first. Position is semantic — it occupies the highest attention weight position in the composition machine.
- **E100 MALFORMED**: Condition ordering C1→C2→C3→C4 enforced. This is the acquisition ordering from CPT — it's load-bearing grammar, not decorative metadata. C3 (self-modification) requires C1+C2 already declared. C4 (closure) requires C1+C2+C3.
- **E200 INCOMPLETE**: All four conditions required (d_C = 4). Missing any condition = composition machine halts with INCOMPLETE.
- **E303 ORPHAN**: Conditions without `coupling:` declarations. Every node must couple to the chain — an uncoupled node zeroes the chain product (Design Axiom §0).
- **E303 ORPHAN**: Metadata block without `coupling:`. Even metadata must couple if present.
- **E301 LINK_ERROR**: Chain continuity — `link[i].output` must equal `link[i+1].input`.
- **Chain product computation**: ∏det(Jᵢ) accumulated across all chain links, displayed as informational annotation. This IS the ACCUMULATE process running at edit-time.
- **E401 PHASE_OVERFLOW**: Accumulated Σφ > π across all chain links = destructive interference. Phase is the only mechanism by which individually valid elements produce an invalid composition.
- **E500 ILL_CONDITIONED**: Accumulated condition number ∏κ(Jᵢ) exceeding threshold = warning (fragile composition).
- **E400 DEGENERATE**: Chain product collapses to zero (floating-point underflow).

### Syntax Highlighting
- `ground_truth:` — **strongest visual weight** (composition anchor)
- `C1_entropy:`, `C2_coupling:`, `C3_self_modification:`, `C4_closure:` — distinct type color
- `det: 0.99` — valid determinant numeric; `det: 0.0` — **red strikethrough**
- `phase: 0.01` — distinct phase numeric (first-class, not optional metadata)
- `entropy`, `coupling`, `self_mod`, `closure` — signal enum constant
- `strong_aligned`, `convergent`, etc. — archetype enum constant
- `HASH`, `COMPUTE_S`, `VERIFY_OWN` — operator function color
- `# comment` — dimmed
- `"string"` — string color
- Comment toggling with `Ctrl+/` (`Cmd+/` on Mac)
- Color customization: `Settings > Editor > Color Scheme > Truth Instruction Set`

---

## Installation

### Path A: TextMate Bundle (Quick — Highlighting Only)

Syntax highlighting in 30 seconds. No build tools needed.

1. Open IntelliJ IDEA (or any JetBrains IDE)
2. Go to `Settings > Editor > TextMate Bundles`
3. Click `+`
4. Select the `path-a-textmate/` directory
5. Click `OK` to apply
6. Open any `.tis` file

**Limitations:** Path A provides highlighting + zero-det error scoping only. It does NOT include the structural annotator (condition ordering, chain continuity, chain product computation, orphan detection). For full enforcement, use Path B.

### Path B: Gradle Plugin (Full — Highlighting + All Structural Checks)

#### Prerequisites
- JDK 17+
- IntelliJ IDEA 2024.1+

#### Build

```bash
cd path-b-gradle/
./gradlew buildPlugin
```

Output: `build/distributions/tis-intellij-plugin-1.1.0.zip`

#### Install

1. `Settings > Plugins`
2. Gear icon → `Install Plugin from Disk...`
3. Select the `.zip`
4. Restart IDE

#### Verify

Open test files in order:

| File | Expected Result |
|---|---|
| `test_1a_ecdsa_signer.tis` | **Clean** — no errors. Valid d_C=4 composition. |
| `test_2_comprehensive_errors.tis` | E302 (4×), E303 (2×), E301 (1×), E401 (2×) |
| `test_3_ordering_violations.tis` | E100 ordering, E200 d_C=2 |
| `test_4_position_violation.tis` | E100 ground_truth position |

---

## Error Code Reference

| Code | Phase | Name | Plugin Check | GTC Mapping |
|---|---|---|---|---|
| E000 | IV | ACCEPT | (info annotation on chain:) | d_C = 4 |
| E100 | I | MALFORMED | ground_truth position, condition ordering | No C3 anchor / Φ ordering |
| E200 | I | INCOMPLETE | Missing conditions count | d_C < 4 |
| E301 | II | LINK_ERROR | Chain link[i].output ≠ link[i+1].input | Broken chain link |
| E302 | 0 | TYPE_ERROR | `det: 0.0`, `jacobian: 0.0` | det(J) = 0 |
| E303 | I | ORPHAN | Condition/metadata without coupling: | Uncoupled component |
| E400 | III | DEGENERATE | Chain product = 0 | ∏det(Jᵢ) = 0 |
| E401 | III | PHASE_OVERFLOW | Single phase > π, accumulated Σφ > π | Destructive interference |
| E500 | III | ILL_CONDITIONED | Accumulated κ too large (warning) | Fragile composition |

---

## Architecture

### Four Primitive Types (from EXP-001)

The TextMate grammar and lexer organize TIS tokens into four categories derived from the expedition's grammar analysis:

| Type | Role | Scope Prefix | Examples |
|---|---|---|---|
| **Entity** | Noun | `support.type.entity` | `entity`, `domain`, `source`, `process`, `target` |
| **Condition** | Adjective | `entity.name.type.condition` | `C1_entropy`, `C2_coupling`, `eigenfunction`, `rate` |
| **Coupling** | Verb | `keyword.operator.coupling` | `coupling`, `det`, `phase`, `kappa`, `jacobian` |
| **Scope** | Sentence boundary | `keyword.control.scope` | `ground_truth`, `conditions`, `chain`, `metadata` |

**ACCUMULATE** is not a type — it's what the parser DOES. The structural annotator's chain product computation implements ACCUMULATE at edit-time.

### Plugin Components

| Component | Spec Rule | What It Enforces |
|---|---|---|
| `TisLexer` | L1–L4 | Context-sensitive tokenization. Tracks which key is being read to classify numeric values (det-zero vs det-valid vs phase vs generic). |
| `TisSyntaxHighlighter` | — | Maps token types to visual attributes. DET_ZERO gets red+strikethrough. |
| `TisZeroDetAnnotator` | L1, L2 | Regex-based fallback for zero-det and phase-overflow detection. Catches edge cases the lexer context might miss (inline YAML blocks). |
| `TisStructuralAnnotator` | P1–P5, R3, V1–V3 | Context-sensitive grammar enforcement: ordering, completeness, continuity, chain product, phase sum, orphans. |
| `TisCommenter` | — | Comment toggling with `#`. |
| `TisColorSettingsPage` | — | User customization of all TIS highlight colors. |

---

## File Structure

```
tis-intellij-plugin/
├── README.md
├── path-a-textmate/                       # TextMate bundle (quick)
│   ├── Syntaxes/tis.tmLanguage.json       # Grammar with 4 primitive type scopes
│   ├── Preferences/tis.tmPreferences.json
│   ├── language-configuration.json
│   └── package.json
├── path-b-gradle/                         # Gradle plugin (full)
│   ├── build.gradle.kts
│   ├── settings.gradle.kts
│   ├── gradle.properties
│   └── src/main/
│       ├── kotlin/com/bonddynamics/tis/
│       │   ├── TisLanguage.kt
│       │   ├── TisFileType.kt
│       │   ├── TisIcons.kt
│       │   ├── TisTokenTypes.kt
│       │   ├── TisLexer.kt               # Context-sensitive scanner (Rule L1)
│       │   ├── TisSyntaxHighlighter.kt
│       │   ├── TisSyntaxHighlighterFactory.kt
│       │   ├── TisParserDefinition.kt
│       │   ├── TisZeroDetAnnotator.kt     # Zero-det + phase overflow (L1, L2)
│       │   ├── TisStructuralAnnotator.kt  # Context-sensitive grammar (P1-V3)
│       │   ├── TisCommenter.kt
│       │   └── TisColorSettingsPage.kt
│       └── resources/
│           ├── META-INF/plugin.xml
│           └── icons/tis-file.svg
└── testdata/
    ├── test_1a_ecdsa_signer.tis           # Valid (E000 ACCEPT)
    ├── test_2_comprehensive_errors.tis     # All error types
    ├── test_3_ordering_violations.tis      # Context-sensitive grammar
    └── test_4_position_violation.tis       # Position semantics
```

## Spec Compliance

This plugin implements editor-side enforcement from the [TIS Compiler Specification v0.1](./TIS_Compiler_Specification_v0_1.md), covering Phases 0–III of the compilation pipeline. Phase IV (Emitter) is out of scope for editor tooling.

The structural insights are from EXP-001 (2026-03-06):
- **Context-sensitive grammar**: Condition ordering depends on prior declarations (D002)
- **Phase is first-class**: Not optional metadata — it's the interference mechanism (D003)
- **ACCUMULATE as process**: Chain product computed by the parser, not a document element (D002)
- **Expression IS Verification**: The grammar preventing invalid structures (isomorphism, evidence: 2.0)
