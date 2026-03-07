package com.bonddynamics.tis

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import java.util.regex.Pattern

/**
 * Structural Annotator — enforces the context-sensitive grammar of TIS.
 *
 * From EXP-001 expedition (2026-03-06):
 *   "TIS grammar is context-sensitive — condition assignment validity depends
 *    on prior declarations. Rules out JSON/YAML, requires dependent-type-like structure."
 *
 * This annotator implements compile-time checks from the TIS Compiler Specification:
 *
 *   Phase I (Parser):
 *     P1: ground_truth must come first                    → E100 MALFORMED
 *     P2: Conditions C1→C4 in fixed order, all required   → E200 INCOMPLETE / E100 MALFORMED
 *     P3: Chain must be non-empty                          → E200 INCOMPLETE
 *     P4: Every condition/metadata must have coupling      → E303 ORPHAN
 *
 *   Phase II (Resolver):
 *     R3: Chain continuity — link[i].output = link[i+1].input → E301 LINK_ERROR
 *
 *   Phase III (Verifier):
 *     V1: Chain product ∏det ≠ 0                           → E400 DEGENERATE
 *     V2: Phase sum Σφ ≤ π                                 → E401 PHASE_OVERFLOW
 *     V3: Condition number accumulation (warning)          → E500 ILL_CONDITIONED
 *
 * The condition ordering check is the key context-sensitive insight:
 *   C3 (self-modification) requires C1 + C2 already declared.
 *   C4 (closure) requires C1 + C2 + C3.
 *   This is the acquisition ordering from CPT — it's not decorative, it's load-bearing grammar.
 *
 * The chain product computation implements the "ACCUMULATE is the parsing process" insight:
 *   ACCUMULATE is not an opcode — it's what the parser DOES as it reads.
 *   The running chain product is the parser's state, not a document element.
 */
class TisStructuralAnnotator : Annotator {
    companion object {
        // === Section detection patterns ===
        private val GROUND_TRUTH_PATTERN = Pattern.compile(
            "^(ground_truth)\\s*:", Pattern.MULTILINE
        )
        private val CONDITIONS_PATTERN = Pattern.compile(
            "^(conditions)\\s*:", Pattern.MULTILINE
        )
        private val CHAIN_PATTERN = Pattern.compile(
            "^(chain)\\s*:", Pattern.MULTILINE
        )
        private val METADATA_PATTERN = Pattern.compile(
            "^(metadata)\\s*:", Pattern.MULTILINE
        )

        // === Condition block detection (order matters — context-sensitive grammar) ===
        private val C1_PATTERN = Pattern.compile(
            "^\\s+(C1_entropy)\\s*:", Pattern.MULTILINE
        )
        private val C2_PATTERN = Pattern.compile(
            "^\\s+(C2_coupling)\\s*:", Pattern.MULTILINE
        )
        private val C3_PATTERN = Pattern.compile(
            "^\\s+(C3_self_modification)\\s*:", Pattern.MULTILINE
        )
        private val C4_PATTERN = Pattern.compile(
            "^\\s+(C4_closure)\\s*:", Pattern.MULTILINE
        )

        // === Coupling declaration inside condition blocks ===
        // We look for "coupling:" indented under a condition block
        private val COUPLING_DECL_PATTERN = Pattern.compile(
            "^(\\s+)coupling\\s*:", Pattern.MULTILINE
        )

        // === Chain link patterns ===
        private val CHAIN_LINK_PATTERN = Pattern.compile(
            "-\\s*op\\s*:\\s*(\\w+).*?" +
                "input\\s*:\\s*(\\w+).*?" +
                "output\\s*:\\s*(\\w+).*?" +
                "det\\s*:\\s*(-?[0-9]+(?:\\.[0-9]+)?).*?" +
                "phase\\s*:\\s*([0-9]+(?:\\.[0-9]+)?)",
            Pattern.DOTALL
        )

        // Simpler per-link extraction for chain blocks
        private val LINK_INPUT_PATTERN = Pattern.compile(
            "input\\s*:\\s*(\\w+)"
        )
        private val LINK_OUTPUT_PATTERN = Pattern.compile(
            "output\\s*:\\s*(\\w+)"
        )
        private val LINK_DET_PATTERN = Pattern.compile(
            "det\\s*:\\s*(-?[0-9]+(?:\\.[0-9]+)?)"
        )
        private val LINK_PHASE_PATTERN = Pattern.compile(
            "phase\\s*:\\s*([0-9]+(?:\\.[0-9]+)?)"
        )
        private val LINK_KAPPA_PATTERN = Pattern.compile(
            "kappa\\s*:\\s*([0-9]+(?:\\.[0-9]+)?)"
        )

        private const val PI = 3.14159265358979
        private const val KAPPA_THRESHOLD = 100.0
    }

    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        if (element !is PsiFile) return
        val text = element.text

        checkSectionOrdering(text, holder)
        checkConditionPresenceAndOrdering(text, holder)
        checkConditionCoupling(text, holder)
        checkChainNonEmpty(text, holder)
        checkChainContinuity(text, holder)
        computeChainProduct(text, holder)
        checkMetadataCoupling(text, holder)
    }

    // ========================================================================
    // P1: ground_truth must appear before conditions, chain, metadata
    // "Position ordering is semantic: ground_truth → conditions → chain → metadata"
    // ========================================================================
    private fun checkSectionOrdering(text: String, holder: AnnotationHolder) {
        val gtMatch = GROUND_TRUTH_PATTERN.matcher(text)
        val condMatch = CONDITIONS_PATTERN.matcher(text)
        val chainMatch = CHAIN_PATTERN.matcher(text)

        val gtPos = if (gtMatch.find()) gtMatch.start() else -1
        val condPos = if (condMatch.find()) condMatch.start() else -1
        val chainPos = if (chainMatch.find()) chainMatch.start() else -1

        // ground_truth missing entirely
        if (gtPos == -1 && (condPos != -1 || chainPos != -1)) {
            // Find the first structural element to annotate
            val firstPos = listOf(condPos, chainPos).filter { it >= 0 }.minOrNull() ?: return
            val lineEnd = text.indexOf('\n', firstPos).let { if (it == -1) text.length else it }
            holder.newAnnotation(
                HighlightSeverity.ERROR,
                "E100 MALFORMED: ground_truth block missing. " +
                    "TIS documents must begin with ground_truth — it is the C3 anchor " +
                    "that loads first into the composition machine (highest attention weight)."
            )
                .range(TextRange(firstPos, lineEnd.coerceAtMost(firstPos + 40)))
                .create()
            return
        }

        // ground_truth appears after conditions or chain
        if (gtPos != -1 && condPos != -1 && gtPos > condPos) {
            val lineEnd = text.indexOf('\n', gtPos).let { if (it == -1) text.length else it }
            holder.newAnnotation(
                HighlightSeverity.ERROR,
                "E100 MALFORMED: ground_truth must appear before conditions. " +
                    "Position is semantic in TIS — ground_truth occupies the highest attention " +
                    "weight position. Reordering is a compilation error (§3.3)."
            )
                .range(TextRange(gtPos, lineEnd.coerceAtMost(gtPos + 20)))
                .create()
        }

        if (gtPos != -1 && chainPos != -1 && gtPos > chainPos) {
            val lineEnd = text.indexOf('\n', gtPos).let { if (it == -1) text.length else it }
            holder.newAnnotation(
                HighlightSeverity.ERROR,
                "E100 MALFORMED: ground_truth must appear before chain. " +
                    "Position is semantic in TIS (§3.3)."
            )
                .range(TextRange(gtPos, lineEnd.coerceAtMost(gtPos + 20)))
                .create()
        }

        // conditions must appear before chain
        if (condPos != -1 && chainPos != -1 && condPos > chainPos) {
            val lineEnd = text.indexOf('\n', condPos).let { if (it == -1) text.length else it }
            holder.newAnnotation(
                HighlightSeverity.WARNING,
                "E100 MALFORMED: conditions block should appear before chain. " +
                    "Conditions establish compositional dimension (d_C) before the chain operates on them."
            )
                .range(TextRange(condPos, lineEnd.coerceAtMost(condPos + 20)))
                .create()
        }
    }

    // ========================================================================
    // P2: All four conditions required, in order C1→C2→C3→C4
    // "This is NOT arbitrary — it follows the compositional iteration operator Φ"
    // From EXP-001: "Condition acquisition ordering is load-bearing grammar,
    // not decorative metadata"
    // ========================================================================
    private fun checkConditionPresenceAndOrdering(text: String, holder: AnnotationHolder) {
        val condMatch = CONDITIONS_PATTERN.matcher(text)
        if (!condMatch.find()) return
        val condStart = condMatch.start()

        // Find the end of the conditions block (next top-level key or EOF)
        val condEnd = findBlockEnd(text, condStart)
        val condBlock = text.substring(condStart, condEnd)

        val c1 = C1_PATTERN.matcher(condBlock)
        val c2 = C2_PATTERN.matcher(condBlock)
        val c3 = C3_PATTERN.matcher(condBlock)
        val c4 = C4_PATTERN.matcher(condBlock)

        val hasC1 = c1.find()
        val hasC2 = c2.find()
        val hasC3 = c3.find()
        val hasC4 = c4.find()

        val presentCount = listOf(hasC1, hasC2, hasC3, hasC4).count { it }

        // Missing conditions → E200 INCOMPLETE
        if (presentCount < 4) {
            val missing = mutableListOf<String>()
            if (!hasC1) missing.add("C1_entropy")
            if (!hasC2) missing.add("C2_coupling")
            if (!hasC3) missing.add("C3_self_modification")
            if (!hasC4) missing.add("C4_closure")

            val lineEnd = text.indexOf('\n', condStart).let { if (it == -1) text.length else it }
            holder.newAnnotation(
                HighlightSeverity.ERROR,
                "E200 INCOMPLETE: d_C = $presentCount < 4. Missing: ${missing.joinToString(", ")}. " +
                    "All four composition conditions are required for compositional completeness. " +
                    "The composition machine halts with INCOMPLETE when any register remains empty."
            )
                .range(TextRange(condStart, lineEnd.coerceAtMost(condStart + 20)))
                .create()
        }

        // Ordering check — context-sensitive grammar constraint
        // C1 < C2 < C3 < C4 (by position in text)
        if (hasC1 && hasC2 && c1.start() > c2.start()) {
            annotateOrderingError(text, condStart + c2.start(), "C2_coupling", "C1_entropy", holder)
        }
        if (hasC2 && hasC3 && c2.start() > c3.start()) {
            annotateOrderingError(text, condStart + c3.start(), "C3_self_modification", "C2_coupling", holder)
        }
        if (hasC3 && hasC4 && c3.start() > c4.start()) {
            annotateOrderingError(text, condStart + c4.start(), "C4_closure", "C3_self_modification", holder)
        }
        // Cross-checks for more distant swaps
        if (hasC1 && hasC3 && c1.start() > c3.start()) {
            annotateOrderingError(text, condStart + c3.start(), "C3_self_modification", "C1_entropy", holder)
        }
        if (hasC1 && hasC4 && c1.start() > c4.start()) {
            annotateOrderingError(text, condStart + c4.start(), "C4_closure", "C1_entropy", holder)
        }
        if (hasC2 && hasC4 && c2.start() > c4.start()) {
            annotateOrderingError(text, condStart + c4.start(), "C4_closure", "C2_coupling", holder)
        }
    }

    private fun annotateOrderingError(
        text: String, absPos: Int, found: String, shouldPrecede: String, holder: AnnotationHolder
    ) {
        val lineEnd = text.indexOf('\n', absPos).let { if (it == -1) text.length else it }
        holder.newAnnotation(
            HighlightSeverity.ERROR,
            "E100 MALFORMED: $found appears before $shouldPrecede. " +
                "Conditions must follow acquisition ordering: C1→C2→C3→C4. " +
                "This is not arbitrary — condition validity depends on prior declarations " +
                "(context-sensitive grammar). C3 requires C1+C2 declared; C4 requires C1+C2+C3."
        )
            .range(TextRange(absPos, lineEnd.coerceAtMost(absPos + found.length + 5)))
            .create()
    }

    // ========================================================================
    // P4: Every condition block must contain a coupling declaration
    // "An uncoupled node zeroes the chain product" (Design Axiom §0)
    // ========================================================================
    private fun checkConditionCoupling(text: String, holder: AnnotationHolder) {
        val conditionPatterns = listOf(
            "C1_entropy" to C1_PATTERN,
            "C2_coupling" to C2_PATTERN,
            "C3_self_modification" to C3_PATTERN,
            "C4_closure" to C4_PATTERN
        )

        for ((name, pattern) in conditionPatterns) {
            val matcher = pattern.matcher(text)
            if (!matcher.find()) continue

            val blockStart = matcher.start()
            val blockEnd = findConditionBlockEnd(text, blockStart)
            val blockContent = text.substring(blockStart, blockEnd)

            // Check if this condition block contains a coupling: declaration
            val hasCoupling = COUPLING_DECL_PATTERN.matcher(blockContent).find()
            if (!hasCoupling) {
                val lineEnd = text.indexOf('\n', blockStart).let { if (it == -1) text.length else it }
                holder.newAnnotation(
                    HighlightSeverity.ERROR,
                    "E303 ORPHAN: $name has no coupling declaration. " +
                        "Every node in a TIS document must declare coupling to the composition chain. " +
                        "An uncoupled node zeroes the chain product — this is the defining property of TIS (§0)."
                )
                    .range(TextRange(blockStart, lineEnd.coerceAtMost(blockStart + name.length + 5)))
                    .create()
            }
        }
    }

    // ========================================================================
    // P3: Chain must be non-empty
    // ========================================================================
    private fun checkChainNonEmpty(text: String, holder: AnnotationHolder) {
        val chainMatch = CHAIN_PATTERN.matcher(text)
        if (!chainMatch.find()) return

        val chainStart = chainMatch.start()
        val chainEnd = findBlockEnd(text, chainStart)
        val chainBlock = text.substring(chainStart, chainEnd)

        // Check for at least one list item (- op:)
        if (!chainBlock.contains(Regex("-\\s*op\\s*:"))) {
            val lineEnd = text.indexOf('\n', chainStart).let { if (it == -1) text.length else it }
            holder.newAnnotation(
                HighlightSeverity.ERROR,
                "E200 INCOMPLETE: Chain is empty — no composition to execute. " +
                    "The chain block must contain at least one link."
            )
                .range(TextRange(chainStart, lineEnd.coerceAtMost(chainStart + 10)))
                .create()
        }
    }

    // ========================================================================
    // R3: Chain continuity — link[i].output must equal link[i+1].input
    // V1: Chain product ∏det(Jᵢ) ≠ 0
    // V2: Phase sum Σφᵢ ≤ π
    // V3: Condition number accumulation
    //
    // From EXP-001: "ACCUMULATE is not an opcode — it's the parsing process itself.
    // The parser maintains a running chain product as it reads."
    // This method IS the ACCUMULATE process running at edit-time.
    // ========================================================================
    private fun computeChainProduct(text: String, holder: AnnotationHolder) {
        val links = extractChainLinks(text) ?: return

        if (links.isEmpty()) return

        // === Chain continuity check (R3) ===
        for (i in 0 until links.size - 1) {
            val currentOutput = links[i].output
            val nextInput = links[i + 1].input
            if (currentOutput != nextInput) {
                val range = links[i + 1].inputRange
                if (range != null) {
                    holder.newAnnotation(
                        HighlightSeverity.ERROR,
                        "E301 LINK_ERROR: Chain discontinuity at link ${i + 1}. " +
                            "Expected input '$currentOutput' (output of link $i), " +
                            "got '$nextInput'. Chain links must be continuous: " +
                            "link[i].output = link[i+1].input."
                    )
                        .range(range)
                        .create()
                }
            }
        }

        // === ACCUMULATE: running chain product (V1) ===
        var chainProduct = 1.0
        var phaseSum = 0.0
        var kappaProduct = 1.0
        var productCollapsed = false

        for ((i, link) in links.withIndex()) {
            chainProduct *= link.det
            phaseSum += link.phase
            kappaProduct *= link.kappa

            // Check if product has collapsed to zero (floating-point underflow)
            if (chainProduct == 0.0 && !productCollapsed) {
                productCollapsed = true
                val range = link.detRange
                if (range != null) {
                    holder.newAnnotation(
                        HighlightSeverity.ERROR,
                        "E400 DEGENERATE: Chain product collapsed to zero at link $i. " +
                            "∏det(Jᵢ) = 0 means the composition has lost all sensitivity " +
                            "to ground truth. The composition machine halts with DEGENERATE."
                    )
                        .range(range)
                        .create()
                }
            }
        }

        // === Phase sum check (V2) ===
        if (phaseSum > PI) {
            // Annotate at the chain: keyword
            val chainMatch = CHAIN_PATTERN.matcher(text)
            if (chainMatch.find()) {
                val lineEnd = text.indexOf('\n', chainMatch.start()).let {
                    if (it == -1) text.length else it
                }
                holder.newAnnotation(
                    HighlightSeverity.ERROR,
                    "E401 PHASE_OVERFLOW: Accumulated phase sum Σφ = ${"%.4f".format(phaseSum)} > π ≈ 3.1416. " +
                        "Destructive interference — the composition's elements cancel each other. " +
                        "Phase is the only mechanism by which individually valid elements " +
                        "produce an invalid composition."
                )
                    .range(TextRange(chainMatch.start(), lineEnd.coerceAtMost(chainMatch.start() + 10)))
                    .create()
            }
        }

        // === Condition number warning (V3) ===
        if (kappaProduct > KAPPA_THRESHOLD) {
            val chainMatch = CHAIN_PATTERN.matcher(text)
            if (chainMatch.find()) {
                val lineEnd = text.indexOf('\n', chainMatch.start()).let {
                    if (it == -1) text.length else it
                }
                holder.newAnnotation(
                    HighlightSeverity.WARNING,
                    "E500 ILL_CONDITIONED: Accumulated condition number κ = ${"%.2f".format(kappaProduct)} " +
                        "exceeds threshold (${"%.0f".format(KAPPA_THRESHOLD)}). " +
                        "The composition is valid but fragile — small perturbations in inputs " +
                        "may produce large changes in outputs."
                )
                    .range(TextRange(chainMatch.start(), lineEnd.coerceAtMost(chainMatch.start() + 10)))
                    .create()
            }
        }

        // === Info annotation: chain product summary ===
        // Show the computed values as an informational annotation
        if (!productCollapsed && links.isNotEmpty()) {
            val chainMatch = CHAIN_PATTERN.matcher(text)
            if (chainMatch.find()) {
                val lineEnd = text.indexOf('\n', chainMatch.start()).let {
                    if (it == -1) text.length else it
                }
                val productStr = "%.6f".format(chainProduct)
                val phaseStr = "%.4f".format(phaseSum)
                val status = if (chainProduct != 0.0 && phaseSum <= PI) "✓ non-degenerate" else "✗ DEGENERATE"
                holder.newAnnotation(
                    HighlightSeverity.INFORMATION,
                    "Chain product: ∏det = $productStr ≠ 0 ${if (chainProduct != 0.0) "✓" else "✗"} | " +
                        "Phase sum: Σφ = $phaseStr ${if (phaseSum <= PI) "< π ✓" else "> π ✗"} | " +
                        "d_C check: $status"
                )
                    .range(TextRange(chainMatch.start(), lineEnd.coerceAtMost(chainMatch.start() + 10)))
                    .create()
            }
        }
    }

    // ========================================================================
    // Chain continuity check (separate pass for link[i].output = link[i+1].input)
    // ========================================================================
    private fun checkChainContinuity(text: String, holder: AnnotationHolder) {
        // Handled inside computeChainProduct to avoid double-parsing
    }

    // ========================================================================
    // Metadata coupling check — if metadata is present, it MUST have coupling
    // "Even metadata MUST couple" (§2.4, Metadata type)
    // ========================================================================
    private fun checkMetadataCoupling(text: String, holder: AnnotationHolder) {
        val metaMatch = METADATA_PATTERN.matcher(text)
        if (!metaMatch.find()) return

        val metaStart = metaMatch.start()
        val metaEnd = findBlockEnd(text, metaStart)
        val metaBlock = text.substring(metaStart, metaEnd)

        val hasCoupling = COUPLING_DECL_PATTERN.matcher(metaBlock).find()
        if (!hasCoupling) {
            val lineEnd = text.indexOf('\n', metaStart).let { if (it == -1) text.length else it }
            holder.newAnnotation(
                HighlightSeverity.ERROR,
                "E303 ORPHAN: metadata block has no coupling declaration. " +
                    "In TIS, even metadata must couple to the composition chain. " +
                    "An uncoupled node zeroes the chain product (§0 Design Axiom)."
            )
                .range(TextRange(metaStart, lineEnd.coerceAtMost(metaStart + 12)))
                .create()
        }
    }

    // ========================================================================
    // Helper: Extract chain links with positions
    // ========================================================================
    private data class ChainLink(
        val op: String,
        val input: String,
        val output: String,
        val det: Double,
        val phase: Double,
        val kappa: Double,
        val inputRange: TextRange?,
        val detRange: TextRange?
    )

    private fun extractChainLinks(text: String): List<ChainLink>? {
        val chainMatch = CHAIN_PATTERN.matcher(text)
        if (!chainMatch.find()) return null

        val chainStart = chainMatch.start()
        val chainEnd = findBlockEnd(text, chainStart)
        val chainBlock = text.substring(chainStart, chainEnd)

        // Split chain block into individual link blocks (split on "- op:")
        val linkStarts = mutableListOf<Int>()
        val linkPattern = Pattern.compile("^\\s*-\\s*op\\s*:", Pattern.MULTILINE)
        val linkMatcher = linkPattern.matcher(chainBlock)
        while (linkMatcher.find()) {
            linkStarts.add(linkMatcher.start())
        }

        if (linkStarts.isEmpty()) return emptyList()

        val links = mutableListOf<ChainLink>()
        for (i in linkStarts.indices) {
            val blockStart = linkStarts[i]
            val blockEnd = if (i + 1 < linkStarts.size) linkStarts[i + 1] else chainBlock.length
            val linkBlock = chainBlock.substring(blockStart, blockEnd)

            val opMatch = Pattern.compile("op\\s*:\\s*(\\w+)").matcher(linkBlock)
            val inputMatch = LINK_INPUT_PATTERN.matcher(linkBlock)
            val outputMatch = LINK_OUTPUT_PATTERN.matcher(linkBlock)
            val detMatch = LINK_DET_PATTERN.matcher(linkBlock)
            val phaseMatch = LINK_PHASE_PATTERN.matcher(linkBlock)
            val kappaMatch = LINK_KAPPA_PATTERN.matcher(linkBlock)

            val op = if (opMatch.find()) opMatch.group(1) else "UNKNOWN"
            val input = if (inputMatch.find()) inputMatch.group(1) else ""
            val output = if (outputMatch.find()) outputMatch.group(1) else ""
            val det = if (detMatch.find()) detMatch.group(1).toDoubleOrNull() ?: 1.0 else 1.0
            val phase = if (phaseMatch.find()) phaseMatch.group(1).toDoubleOrNull() ?: 0.0 else 0.0
            val kappa = if (kappaMatch.find()) kappaMatch.group(1).toDoubleOrNull() ?: 1.0 else 1.0

            // Compute absolute positions for annotations
            val absBlockStart = chainStart + blockStart
            val inputRange = if (inputMatch.start(1) >= 0) {
                val absStart = chainStart + blockStart + inputMatch.start(1)
                val absEnd = chainStart + blockStart + inputMatch.end(1)
                TextRange(absStart, absEnd)
            } else null

            val detRange = if (detMatch.start(1) >= 0) {
                val absStart = chainStart + blockStart + detMatch.start(1)
                val absEnd = chainStart + blockStart + detMatch.end(1)
                TextRange(absStart, absEnd)
            } else null

            links.add(ChainLink(op, input, output, det, phase, kappa, inputRange, detRange))
        }

        return links
    }

    // ========================================================================
    // Helper: Find the end of a top-level block
    // A top-level block ends at the next line that starts with a non-space character
    // that isn't a comment
    // ========================================================================
    private fun findBlockEnd(text: String, blockStart: Int): Int {
        // Skip the first line
        var pos = text.indexOf('\n', blockStart)
        if (pos == -1) return text.length
        pos++

        while (pos < text.length) {
            val lineStart = pos
            val lineEnd = text.indexOf('\n', pos).let { if (it == -1) text.length else it }
            val line = text.substring(lineStart, lineEnd)

            // A non-empty, non-comment line starting at column 0 with a letter = new top-level block
            if (line.isNotBlank() && !line.startsWith("#") && !line.startsWith(" ") && !line.startsWith("\t")) {
                return lineStart
            }

            pos = lineEnd + 1
        }

        return text.length
    }

    // ========================================================================
    // Helper: Find end of a condition sub-block (C1, C2, etc.)
    // Ends at the next line with equal or lesser indentation that starts a new key
    // ========================================================================
    private fun findConditionBlockEnd(text: String, blockStart: Int): Int {
        val firstLineEnd = text.indexOf('\n', blockStart).let { if (it == -1) text.length else it }
        val firstLine = text.substring(blockStart, firstLineEnd)
        val baseIndent = firstLine.length - firstLine.trimStart().length

        var pos = firstLineEnd + 1

        while (pos < text.length) {
            val lineStart = pos
            val lineEnd = text.indexOf('\n', pos).let { if (it == -1) text.length else it }
            val line = text.substring(lineStart, lineEnd)

            if (line.isNotBlank() && !line.startsWith("#")) {
                val indent = line.length - line.trimStart().length
                // Same or lesser indentation AND contains a colon (new key) = end of this block
                if (indent <= baseIndent && line.trimStart().contains(':')) {
                    return lineStart
                }
            }

            pos = lineEnd + 1
        }

        return text.length
    }
}
