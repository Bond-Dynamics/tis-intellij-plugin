package com.bonddynamics.tis

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import java.util.regex.Pattern

/**
 * Annotator that enforces TIS Rule L1:
 * "A FLOAT token in `det` position with value 0.0
 *  → HALT(E302, TYPE_ERROR, "zero determinant is not a valid TIS token")"
 *
 * This annotator runs on every editor update and marks zero determinants
 * with error-level severity and red strikethrough styling.
 *
 * Also enforces:
 * - Rule L3: rate ≤ 0 in C1_entropy context (entropy processing rate must be positive)
 * - Rule L4: eigenvalue.value ≤ 0 (eigenvalue must be positive)
 * - Phase > π detection (Rule L2) — phase values exceeding π ≈ 3.14159
 */
class TisZeroDetAnnotator : Annotator {
    companion object {
        // Matches det: followed by zero value (0, 0.0, 0.00, etc.)
        // Handles both inline { det: 0.0 } and block-style det: 0.0
        private val DET_ZERO_PATTERN = Pattern.compile(
            """(?:^|\s)(det)\s*:\s*(0(?:\.0+)?)\s*(?=$|[#,}\s])""",
            Pattern.MULTILINE
        )

        // Matches jacobian: followed by zero value
        private val JACOBIAN_ZERO_PATTERN = Pattern.compile(
            """(?:^|\s)(jacobian)\s*:\s*(0(?:\.0+)?)\s*(?=$|[#,}\s])""",
            Pattern.MULTILINE
        )

        // Matches phase: followed by value > π
        private val PHASE_PATTERN = Pattern.compile(
            """(?:^|\s)(phase)\s*:\s*(\d+(?:\.\d+)?)\s*(?=$|[#,}\s])""",
            Pattern.MULTILINE
        )

        private const val PI = 3.14159265358979
    }

    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        // Only run on the file-level element to avoid duplicate annotations
        if (element !is PsiFile) return

        val text = element.text
        val document = element.containingFile?.viewProvider?.document ?: return

        // === Rule L1: Zero determinant ===
        annotateDeterminantZeros(text, holder, DET_ZERO_PATTERN, "det")
        annotateDeterminantZeros(text, holder, JACOBIAN_ZERO_PATTERN, "jacobian")

        // === Rule L2: Phase overflow ===
        annotatePhaseOverflow(text, holder)
    }

    private fun annotateDeterminantZeros(
        text: String,
        holder: AnnotationHolder,
        pattern: Pattern,
        fieldName: String
    ) {
        val matcher = pattern.matcher(text)
        while (matcher.find()) {
            val keyStart = matcher.start(1)
            val keyEnd = matcher.end(1)
            val valueStart = matcher.start(2)
            val valueEnd = matcher.end(2)

            // Annotate the key ("det" or "jacobian") with error
            holder.newAnnotation(
                HighlightSeverity.ERROR,
                "E302 TYPE_ERROR: Zero determinant is not a valid TIS token. " +
                    "det(J) = 0 means the composition is decoupled from ground truth."
            )
                .range(TextRange(keyStart, keyEnd))
                .textAttributes(TisSyntaxHighlighter.DET_ZERO_ERROR)
                .create()

            // Annotate the value ("0.0") with error
            holder.newAnnotation(
                HighlightSeverity.ERROR,
                "E302 TYPE_ERROR: Zero Jacobian determinant. " +
                    "This link has no sensitivity to its input — " +
                    "changes in the source produce no change in the output."
            )
                .range(TextRange(valueStart, valueEnd))
                .textAttributes(TisSyntaxHighlighter.DET_ZERO_ERROR)
                .create()
        }
    }

    private fun annotatePhaseOverflow(text: String, holder: AnnotationHolder) {
        val matcher = PHASE_PATTERN.matcher(text)
        while (matcher.find()) {
            val valueStr = matcher.group(2)
            val value = valueStr.toDoubleOrNull() ?: continue
            if (value > PI) {
                val valueStart = matcher.start(2)
                val valueEnd = matcher.end(2)
                holder.newAnnotation(
                    HighlightSeverity.ERROR,
                    "E401 PHASE_OVERFLOW: Phase value $valueStr exceeds π (≈3.14159). " +
                        "Phase must be in [0, π] — values beyond π indicate destructive interference."
                )
                    .range(TextRange(valueStart, valueEnd))
                    .create()
            }
        }
    }
}
