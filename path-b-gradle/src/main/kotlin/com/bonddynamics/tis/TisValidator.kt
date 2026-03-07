package com.bonddynamics.tis

import java.util.regex.Pattern

/**
 * Standalone TIS validator — pure function from text → errors.
 *
 * Extracts the validation logic from TisStructuralAnnotator and TisZeroDetAnnotator
 * into a form testable without IntelliJ platform dependencies.
 *
 * Every rule maps to the TIS Compiler Specification:
 *   L1-L4: Lexer-level (zero det, phase overflow on individual values)
 *   P1-P4: Parser-level (ordering, completeness, coupling, chain non-empty)
 *   R3:    Resolver-level (chain continuity)
 *   V1-V3: Verifier-level (chain product, phase sum, condition number)
 */
class TisValidator {

    data class TisError(
        val code: String,      // E100, E200, E301, E302, E303, E400, E401, E500
        val severity: Severity,
        val message: String,
        val position: Int      // character offset in source text
    )

    enum class Severity { ERROR, WARNING, INFO }

    companion object {
        // === Section detection ===
        private val GROUND_TRUTH_PATTERN = Pattern.compile("^(ground_truth)\\s*:", Pattern.MULTILINE)
        private val CONDITIONS_PATTERN = Pattern.compile("^(conditions)\\s*:", Pattern.MULTILINE)
        private val CHAIN_PATTERN = Pattern.compile("^(chain)\\s*:", Pattern.MULTILINE)
        private val METADATA_PATTERN = Pattern.compile("^(metadata)\\s*:", Pattern.MULTILINE)

        // === Condition blocks ===
        // Use [^\S\n]+ (non-newline whitespace) to prevent matching across lines
        // when comments are stripped to whitespace-only lines
        private val C1_PATTERN = Pattern.compile("^[^\\S\\n]+(C1_entropy)[^\\S\\n]*:", Pattern.MULTILINE)
        private val C2_PATTERN = Pattern.compile("^[^\\S\\n]+(C2_coupling)[^\\S\\n]*:", Pattern.MULTILINE)
        private val C3_PATTERN = Pattern.compile("^[^\\S\\n]+(C3_self_modification)[^\\S\\n]*:", Pattern.MULTILINE)
        private val C4_PATTERN = Pattern.compile("^[^\\S\\n]+(C4_closure)[^\\S\\n]*:", Pattern.MULTILINE)

        // === Coupling declaration ===
        private val COUPLING_DECL_PATTERN = Pattern.compile("^([^\\S\\n]+)coupling[^\\S\\n]*:", Pattern.MULTILINE)

        // === Chain link parsing ===
        private val LINK_INPUT_PATTERN = Pattern.compile("input\\s*:\\s*(\\w+)")
        private val LINK_OUTPUT_PATTERN = Pattern.compile("output\\s*:\\s*(\\w+)")
        private val LINK_DET_PATTERN = Pattern.compile("det\\s*:\\s*(-?[0-9]+(?:\\.[0-9]+)?)")
        private val LINK_PHASE_PATTERN = Pattern.compile("phase\\s*:\\s*([0-9]+(?:\\.[0-9]+)?)")
        private val LINK_KAPPA_PATTERN = Pattern.compile("kappa\\s*:\\s*([0-9]+(?:\\.[0-9]+)?)")

        // === Lexer-level patterns ===
        private val DET_ZERO_PATTERN = Pattern.compile(
            """(?:^|\s)(det)\s*:\s*(0(?:\.0+)?)\s*(?=$|[#,}\s])""", Pattern.MULTILINE
        )
        private val JACOBIAN_ZERO_PATTERN = Pattern.compile(
            """(?:^|\s)(jacobian)\s*:\s*(0(?:\.0+)?)\s*(?=$|[#,}\s])""", Pattern.MULTILINE
        )
        private val PHASE_PATTERN = Pattern.compile(
            """(?:^|\s)(phase)\s*:\s*(\d+(?:\.\d+)?)\s*(?=$|[#,}\s])""", Pattern.MULTILINE
        )

        private const val PI = 3.14159265358979
        private const val KAPPA_THRESHOLD = 100.0
    }

    fun validate(text: String): List<TisError> {
        val errors = mutableListOf<TisError>()

        // Strip comments from content lines for value-level checks.
        // Comments can contain example values like "det: 0.0" that would
        // produce false positives. Structure checks use stripped text;
        // position offsets are approximate but sufficient for reporting.
        val stripped = stripComments(text)

        // Lexer-level checks (L1-L4) — on stripped text to avoid comment false positives
        checkZeroDeterminants(stripped, errors)
        checkIndividualPhaseOverflow(stripped, errors)

        // Parser-level checks (P1-P4) — structural patterns survive comment stripping
        checkSectionOrdering(stripped, errors)
        checkConditionPresenceAndOrdering(stripped, errors)
        checkConditionCoupling(stripped, errors)
        checkChainNonEmpty(stripped, errors)
        checkMetadataCoupling(stripped, errors)

        // Resolver + Verifier checks (R3, V1-V3)
        checkChainContinuityAndProduct(stripped, errors)

        return errors
    }

    /**
     * Strips comment content from each line while preserving line structure.
     * A comment starts with '#' — everything from '#' to end of line is removed.
     * This prevents regex patterns from matching example values inside comments.
     */
    private fun stripComments(text: String): String =
        text.lines().joinToString("\n") { line ->
            val hashIndex = line.indexOf('#')
            if (hashIndex >= 0) line.substring(0, hashIndex) else line
        }

    // ========================================================================
    // L1: Zero determinant → E302 TYPE_ERROR
    // ========================================================================
    private fun checkZeroDeterminants(text: String, errors: MutableList<TisError>) {
        for ((pattern, fieldName) in listOf(DET_ZERO_PATTERN to "det", JACOBIAN_ZERO_PATTERN to "jacobian")) {
            val matcher = pattern.matcher(text)
            while (matcher.find()) {
                errors.add(TisError(
                    code = "E302",
                    severity = Severity.ERROR,
                    message = "TYPE_ERROR: Zero $fieldName determinant. det(J) = 0 means decoupled from ground truth.",
                    position = matcher.start(1)
                ))
            }
        }
    }

    // ========================================================================
    // L2: Individual phase > pi → E401 PHASE_OVERFLOW
    // ========================================================================
    private fun checkIndividualPhaseOverflow(text: String, errors: MutableList<TisError>) {
        val matcher = PHASE_PATTERN.matcher(text)
        while (matcher.find()) {
            val value = matcher.group(2).toDoubleOrNull() ?: continue
            if (value > PI) {
                errors.add(TisError(
                    code = "E401",
                    severity = Severity.ERROR,
                    message = "PHASE_OVERFLOW: Phase value ${matcher.group(2)} exceeds pi.",
                    position = matcher.start(2)
                ))
            }
        }
    }

    // ========================================================================
    // P1: ground_truth must come first → E100 MALFORMED
    // ========================================================================
    private fun checkSectionOrdering(text: String, errors: MutableList<TisError>) {
        val gtMatch = GROUND_TRUTH_PATTERN.matcher(text)
        val condMatch = CONDITIONS_PATTERN.matcher(text)
        val chainMatch = CHAIN_PATTERN.matcher(text)

        val gtPos = if (gtMatch.find()) gtMatch.start() else -1
        val condPos = if (condMatch.find()) condMatch.start() else -1
        val chainPos = if (chainMatch.find()) chainMatch.start() else -1

        if (gtPos == -1 && (condPos != -1 || chainPos != -1)) {
            val firstPos = listOf(condPos, chainPos).filter { it >= 0 }.minOrNull() ?: return
            errors.add(TisError(
                code = "E100",
                severity = Severity.ERROR,
                message = "MALFORMED: ground_truth block missing.",
                position = firstPos
            ))
            return
        }

        if (gtPos != -1 && condPos != -1 && gtPos > condPos) {
            errors.add(TisError(
                code = "E100",
                severity = Severity.ERROR,
                message = "MALFORMED: ground_truth must appear before conditions.",
                position = gtPos
            ))
        }

        if (gtPos != -1 && chainPos != -1 && gtPos > chainPos) {
            errors.add(TisError(
                code = "E100",
                severity = Severity.ERROR,
                message = "MALFORMED: ground_truth must appear before chain.",
                position = gtPos
            ))
        }
    }

    // ========================================================================
    // P2: Condition presence (d_C = 4) and ordering C1→C2→C3→C4
    // ========================================================================
    private fun checkConditionPresenceAndOrdering(text: String, errors: MutableList<TisError>) {
        val condMatch = CONDITIONS_PATTERN.matcher(text)
        if (!condMatch.find()) return
        val condStart = condMatch.start()
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

            errors.add(TisError(
                code = "E200",
                severity = Severity.ERROR,
                message = "INCOMPLETE: d_C = $presentCount < 4. Missing: ${missing.joinToString(", ")}.",
                position = condStart
            ))
        }

        // Ordering violations → E100 MALFORMED
        data class CondInfo(val name: String, val present: Boolean, val pos: Int)
        val conditions = listOf(
            CondInfo("C1_entropy", hasC1, if (hasC1) c1.start() else -1),
            CondInfo("C2_coupling", hasC2, if (hasC2) c2.start() else -1),
            CondInfo("C3_self_modification", hasC3, if (hasC3) c3.start() else -1),
            CondInfo("C4_closure", hasC4, if (hasC4) c4.start() else -1)
        )

        for (i in conditions.indices) {
            for (j in i + 1 until conditions.size) {
                val earlier = conditions[i]
                val later = conditions[j]
                if (earlier.present && later.present && earlier.pos > later.pos) {
                    errors.add(TisError(
                        code = "E100",
                        severity = Severity.ERROR,
                        message = "MALFORMED: ${later.name} appears before ${earlier.name}. " +
                            "Acquisition ordering C1->C2->C3->C4 required.",
                        position = condStart + later.pos
                    ))
                }
            }
        }
    }

    // ========================================================================
    // P4: Every condition must have coupling → E303 ORPHAN
    // ========================================================================
    private fun checkConditionCoupling(text: String, errors: MutableList<TisError>) {
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

            if (!COUPLING_DECL_PATTERN.matcher(blockContent).find()) {
                errors.add(TisError(
                    code = "E303",
                    severity = Severity.ERROR,
                    message = "ORPHAN: $name has no coupling declaration.",
                    position = blockStart
                ))
            }
        }
    }

    // ========================================================================
    // P3: Chain must be non-empty
    // ========================================================================
    private fun checkChainNonEmpty(text: String, errors: MutableList<TisError>) {
        val chainMatch = CHAIN_PATTERN.matcher(text)
        if (!chainMatch.find()) return

        val chainStart = chainMatch.start()
        val chainEnd = findBlockEnd(text, chainStart)
        val chainBlock = text.substring(chainStart, chainEnd)

        if (!chainBlock.contains(Regex("-\\s*op\\s*:"))) {
            errors.add(TisError(
                code = "E200",
                severity = Severity.ERROR,
                message = "INCOMPLETE: Chain is empty.",
                position = chainStart
            ))
        }
    }

    // ========================================================================
    // Metadata coupling → E303 ORPHAN
    // ========================================================================
    private fun checkMetadataCoupling(text: String, errors: MutableList<TisError>) {
        val metaMatch = METADATA_PATTERN.matcher(text)
        if (!metaMatch.find()) return

        val metaStart = metaMatch.start()
        val metaEnd = findBlockEnd(text, metaStart)
        val metaBlock = text.substring(metaStart, metaEnd)

        if (!COUPLING_DECL_PATTERN.matcher(metaBlock).find()) {
            errors.add(TisError(
                code = "E303",
                severity = Severity.ERROR,
                message = "ORPHAN: metadata block has no coupling declaration.",
                position = metaStart
            ))
        }
    }

    // ========================================================================
    // R3 + V1 + V2 + V3: Chain continuity, product, phase, kappa
    // ========================================================================
    private fun checkChainContinuityAndProduct(text: String, errors: MutableList<TisError>) {
        val links = extractChainLinks(text) ?: return
        if (links.isEmpty()) return

        // R3: Chain continuity
        for (i in 0 until links.size - 1) {
            val currentOutput = links[i].output
            val nextInput = links[i + 1].input
            if (currentOutput != nextInput) {
                errors.add(TisError(
                    code = "E301",
                    severity = Severity.ERROR,
                    message = "LINK_ERROR: Chain discontinuity at link ${i + 1}. " +
                        "Expected input '$currentOutput', got '$nextInput'.",
                    position = links[i + 1].position
                ))
            }
        }

        // V1: Chain product
        var chainProduct = 1.0
        var phaseSum = 0.0
        var kappaProduct = 1.0
        var productCollapsed = false

        for ((i, link) in links.withIndex()) {
            chainProduct *= link.det
            phaseSum += link.phase
            kappaProduct *= link.kappa

            if (chainProduct == 0.0 && !productCollapsed) {
                productCollapsed = true
                errors.add(TisError(
                    code = "E400",
                    severity = Severity.ERROR,
                    message = "DEGENERATE: Chain product collapsed to zero at link $i.",
                    position = link.position
                ))
            }
        }

        // V2: Phase sum
        if (phaseSum > PI) {
            val chainMatch = CHAIN_PATTERN.matcher(text)
            if (chainMatch.find()) {
                errors.add(TisError(
                    code = "E401",
                    severity = Severity.ERROR,
                    message = "PHASE_OVERFLOW: Accumulated phase sum = ${"%.4f".format(phaseSum)} > pi.",
                    position = chainMatch.start()
                ))
            }
        }

        // V3: Condition number
        if (kappaProduct > KAPPA_THRESHOLD) {
            val chainMatch = CHAIN_PATTERN.matcher(text)
            if (chainMatch.find()) {
                errors.add(TisError(
                    code = "E500",
                    severity = Severity.WARNING,
                    message = "ILL_CONDITIONED: Accumulated kappa = ${"%.2f".format(kappaProduct)} exceeds threshold.",
                    position = chainMatch.start()
                ))
            }
        }
    }

    // ========================================================================
    // Chain link extraction
    // ========================================================================
    private data class ChainLink(
        val op: String,
        val input: String,
        val output: String,
        val det: Double,
        val phase: Double,
        val kappa: Double,
        val position: Int
    )

    private fun extractChainLinks(text: String): List<ChainLink>? {
        val chainMatch = CHAIN_PATTERN.matcher(text)
        if (!chainMatch.find()) return null

        val chainStart = chainMatch.start()
        val chainEnd = findBlockEnd(text, chainStart)
        val chainBlock = text.substring(chainStart, chainEnd)

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

            links.add(ChainLink(op, input, output, det, phase, kappa, chainStart + blockStart))
        }

        return links
    }

    // ========================================================================
    // Block boundary helpers
    // ========================================================================
    private fun findBlockEnd(text: String, blockStart: Int): Int {
        var pos = text.indexOf('\n', blockStart)
        if (pos == -1) return text.length
        pos++

        while (pos < text.length) {
            val lineStart = pos
            val lineEnd = text.indexOf('\n', pos).let { if (it == -1) text.length else it }
            val line = text.substring(lineStart, lineEnd)

            if (line.isNotBlank() && !line.startsWith("#") && !line.startsWith(" ") && !line.startsWith("\t")) {
                return lineStart
            }
            pos = lineEnd + 1
        }
        return text.length
    }

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
                if (indent <= baseIndent && line.trimStart().contains(':')) {
                    return lineStart
                }
            }
            pos = lineEnd + 1
        }
        return text.length
    }
}
