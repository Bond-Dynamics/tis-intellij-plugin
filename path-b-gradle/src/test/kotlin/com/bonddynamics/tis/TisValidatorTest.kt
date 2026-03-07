package com.bonddynamics.tis

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import java.io.File

/**
 * TIS Instruction Set Validation Suite
 *
 * Proves the TIS grammar enforcement by running the validator against
 * the canonical test files and asserting exact error signatures.
 *
 * Test data from testdata/:
 *   test_1a — Valid ECDSA signer (E000 ACCEPT, d_C=4)
 *   test_2  — Comprehensive errors (E302, E303, E301, E401)
 *   test_3  — Ordering violations (E100, E200)
 *   test_4  — Position violation (E100)
 */
class TisValidatorTest {

    private val validator = TisValidator()
    private val testdataDir = File("../testdata")

    private fun loadTestFile(name: String): String =
        File(testdataDir, name).readText()

    private fun List<TisValidator.TisError>.countByCode(code: String): Int =
        count { it.code == code }

    private fun List<TisValidator.TisError>.errorCodes(): List<String> =
        filter { it.severity == TisValidator.Severity.ERROR }.map { it.code }

    // ====================================================================
    // Test 1A: ECDSA Legitimate Signer — MUST ACCEPT (zero errors)
    // ====================================================================
    @Nested
    @DisplayName("Test 1A: ECDSA Legitimate Signer (E000 ACCEPT)")
    inner class Test1aEcdsaSigner {

        private val text = loadTestFile("test_1a_ecdsa_signer.tis")
        private val errors = validator.validate(text)

        @Test
        @DisplayName("No errors — valid d_C=4 composition")
        fun noErrors() {
            val errorList = errors.filter { it.severity == TisValidator.Severity.ERROR }
            assertEquals(0, errorList.size,
                "Expected E000 ACCEPT (zero errors) for valid ECDSA signer composition. " +
                "Got: ${errorList.map { "${it.code}: ${it.message}" }}")
        }

        @Test
        @DisplayName("No E302 — all determinants are non-zero")
        fun noZeroDeterminants() {
            assertEquals(0, errors.countByCode("E302"),
                "Valid composition must have no zero determinants")
        }

        @Test
        @DisplayName("No E303 — all conditions have coupling declarations")
        fun noOrphans() {
            assertEquals(0, errors.countByCode("E303"),
                "Valid composition must have no orphan nodes")
        }

        @Test
        @DisplayName("No E301 — chain links are continuous")
        fun noLinkErrors() {
            assertEquals(0, errors.countByCode("E301"),
                "Valid composition must have continuous chain links")
        }

        @Test
        @DisplayName("No E401 — phase sum < pi (0.07 < 3.14)")
        fun noPhaseOverflow() {
            assertEquals(0, errors.countByCode("E401"),
                "Valid composition must not overflow phase")
        }

        @Test
        @DisplayName("No E100 — ground_truth is first, conditions ordered")
        fun noMalformed() {
            assertEquals(0, errors.countByCode("E100"),
                "Valid composition must have correct section ordering")
        }

        @Test
        @DisplayName("No E200 — all four conditions present (d_C=4)")
        fun noIncomplete() {
            assertEquals(0, errors.countByCode("E200"),
                "Valid composition must have d_C=4")
        }

        @Test
        @DisplayName("No E400 — chain product != 0 (0.933)")
        fun noDegenerate() {
            assertEquals(0, errors.countByCode("E400"),
                "Valid composition must not be degenerate")
        }
    }

    // ====================================================================
    // Test 2: Comprehensive Errors — exercises every error code
    // ====================================================================
    @Nested
    @DisplayName("Test 2: Comprehensive Errors")
    inner class Test2ComprehensiveErrors {

        private val text = loadTestFile("test_2_comprehensive_errors.tis")
        private val errors = validator.validate(text)

        @Test
        @DisplayName("E302 TYPE_ERROR — 4 zero determinants (det:0.0, det:0, jacobian:0.0, chain det:0.0)")
        fun zeroDeterminants() {
            val e302Count = errors.countByCode("E302")
            assertEquals(4, e302Count,
                "Expected 4x E302 (det:0.0 in C3 coupling, det:0 in C4 coupling, " +
                "jacobian:0.0 in C3, det:0.0 in chain link 0). Got $e302Count. " +
                "Errors: ${errors.filter { it.code == "E302" }.map { it.message }}")
        }

        @Test
        @DisplayName("E303 ORPHAN — C2 missing coupling + metadata missing coupling")
        fun orphanNodes() {
            val e303Count = errors.countByCode("E303")
            assertEquals(2, e303Count,
                "Expected 2x E303 (C2_coupling orphan + metadata orphan). Got $e303Count. " +
                "Errors: ${errors.filter { it.code == "E303" }.map { it.message }}")
        }

        @Test
        @DisplayName("E301 LINK_ERROR — chain discontinuity at link 2")
        fun chainDiscontinuity() {
            val e301Count = errors.countByCode("E301")
            assertEquals(1, e301Count,
                "Expected 1x E301 (wrong_reference != intermediate_b). Got $e301Count")
        }

        @Test
        @DisplayName("E401 PHASE_OVERFLOW — individual (4.0 > pi) + accumulated (5.70 > pi)")
        fun phaseOverflow() {
            val e401Count = errors.countByCode("E401")
            assertEquals(2, e401Count,
                "Expected 2x E401 (phase:4.0 individual + accumulated 5.70>pi). Got $e401Count")
        }

        @Test
        @DisplayName("No E100 — ground_truth is first, sections in order")
        fun noMalformed() {
            assertEquals(0, errors.countByCode("E100"),
                "Sections are correctly ordered in test_2")
        }

        @Test
        @DisplayName("No E200 — all four conditions present")
        fun noIncomplete() {
            assertEquals(0, errors.countByCode("E200"),
                "All four conditions present in test_2")
        }

        @Test
        @DisplayName("E400 DEGENERATE — chain product collapses (first link has det:0.0)")
        fun degenerateChain() {
            val e400Count = errors.countByCode("E400")
            assertEquals(1, e400Count,
                "Expected 1x E400 (chain product collapses at link 0 with det:0.0). Got $e400Count")
        }
    }

    // ====================================================================
    // Test 3: Ordering Violations — context-sensitive grammar
    // ====================================================================
    @Nested
    @DisplayName("Test 3: Ordering Violations (Context-Sensitive Grammar)")
    inner class Test3OrderingViolations {

        private val text = loadTestFile("test_3_ordering_violations.tis")
        private val errors = validator.validate(text)

        @Test
        @DisplayName("E200 INCOMPLETE — d_C=2 (C2 and C3 missing)")
        fun incompleteConditions() {
            val e200 = errors.filter { it.code == "E200" }
            assertEquals(1, e200.size, "Expected 1x E200 for d_C=2")
            assertTrue(e200[0].message.contains("d_C = 2"),
                "Should report d_C = 2. Got: ${e200[0].message}")
            assertTrue(e200[0].message.contains("C2_coupling"),
                "Should list C2_coupling as missing")
            assertTrue(e200[0].message.contains("C3_self_modification"),
                "Should list C3_self_modification as missing")
        }

        @Test
        @DisplayName("E100 MALFORMED — C4 appears before C1 (ordering violation)")
        fun orderingViolation() {
            val e100 = errors.filter { it.code == "E100" }
            assertTrue(e100.isNotEmpty(),
                "Expected E100 for C4 appearing before C1. Got none. All errors: " +
                errors.map { "${it.code}: ${it.message}" })
            assertTrue(e100.any { it.message.contains("C4_closure") && it.message.contains("C1_entropy") },
                "Should flag C4 appearing before C1. Got: ${e100.map { it.message }}")
        }

        @Test
        @DisplayName("No E100 section ordering — ground_truth is first")
        fun groundTruthFirst() {
            val sectionErrors = errors.filter {
                it.code == "E100" && it.message.contains("ground_truth")
            }
            assertEquals(0, sectionErrors.size,
                "ground_truth is correctly positioned first in test_3")
        }
    }

    // ====================================================================
    // Test 4: Position Violation — ground_truth appears after conditions
    // ====================================================================
    @Nested
    @DisplayName("Test 4: Position Violation (ground_truth Misplaced)")
    inner class Test4PositionViolation {

        private val text = loadTestFile("test_4_position_violation.tis")
        private val errors = validator.validate(text)

        @Test
        @DisplayName("E100 MALFORMED — ground_truth appears after conditions")
        fun groundTruthPosition() {
            val e100 = errors.filter {
                it.code == "E100" && it.message.contains("ground_truth")
            }
            assertTrue(e100.isNotEmpty(),
                "Expected E100 for misplaced ground_truth. Got none. All errors: " +
                errors.map { "${it.code}: ${it.message}" })
            assertTrue(e100.any { it.message.contains("before conditions") },
                "Should report ground_truth must appear before conditions. Got: ${e100.map { it.message }}")
        }

        @Test
        @DisplayName("No E200 — all four conditions are present (d_C=4)")
        fun allConditionsPresent() {
            assertEquals(0, errors.countByCode("E200"),
                "All four conditions are present in test_4")
        }

        @Test
        @DisplayName("No E303 — all conditions have coupling declarations")
        fun allConditionsCoupled() {
            assertEquals(0, errors.countByCode("E303"),
                "All conditions have coupling in test_4")
        }

        @Test
        @DisplayName("No E302 — no zero determinants")
        fun noZeroDets() {
            assertEquals(0, errors.countByCode("E302"),
                "No zero determinants in test_4")
        }
    }

    // ====================================================================
    // Unit-level validation of individual rules
    // ====================================================================
    @Nested
    @DisplayName("Rule Isolation Tests")
    inner class RuleIsolation {

        @Test
        @DisplayName("L1: det: 0.0 is a type error")
        fun detZeroIsTypeError() {
            val tis = """
                tis: "1.0"
                ground_truth:
                  entity: test
                conditions:
                  C1_entropy:
                    source: x
                    coupling:
                      to: ground_truth
                      det: 0.0
                      phase: 0.01
            """.trimIndent()

            val errors = validator.validate(tis)
            assertTrue(errors.any { it.code == "E302" },
                "det: 0.0 must produce E302. Got: ${errors.map { it.code }}")
        }

        @Test
        @DisplayName("L1: det: 0 (integer zero) is also a type error")
        fun detIntegerZeroIsTypeError() {
            val tis = """
                tis: "1.0"
                ground_truth:
                  entity: test
                conditions:
                  C1_entropy:
                    source: x
                    coupling:
                      to: ground_truth
                      det: 0
                      phase: 0.01
            """.trimIndent()

            val errors = validator.validate(tis)
            assertTrue(errors.any { it.code == "E302" },
                "det: 0 (integer) must produce E302. Got: ${errors.map { it.code }}")
        }

        @Test
        @DisplayName("L1: jacobian: 0.0 is a type error")
        fun jacobianZeroIsTypeError() {
            val tis = """
                tis: "1.0"
                ground_truth:
                  entity: test
                conditions:
                  C3_self_modification:
                    entity: test
                    jacobian: 0.0
            """.trimIndent()

            val errors = validator.validate(tis)
            assertTrue(errors.any { it.code == "E302" },
                "jacobian: 0.0 must produce E302")
        }

        @Test
        @DisplayName("L2: phase: 4.0 exceeds pi")
        fun phaseOverflowIndividual() {
            val tis = """
                tis: "1.0"
                ground_truth:
                  entity: test
                chain:
                  - op: TEST
                    input: a
                    output: b
                    det: 0.9
                    phase: 4.0
            """.trimIndent()

            val errors = validator.validate(tis)
            assertTrue(errors.any { it.code == "E401" },
                "phase: 4.0 must produce E401")
        }

        @Test
        @DisplayName("V2: accumulated phase sum > pi produces E401")
        fun accumulatedPhaseOverflow() {
            val tis = """
                tis: "1.0"
                ground_truth:
                  entity: test
                chain:
                  - op: A
                    input: x
                    output: y
                    det: 0.9
                    phase: 1.5
                  - op: B
                    input: y
                    output: z
                    det: 0.9
                    phase: 2.0
            """.trimIndent()

            val errors = validator.validate(tis)
            assertTrue(errors.any { it.code == "E401" && it.message.contains("Accumulated") },
                "Accumulated phase 3.5 > pi must produce E401")
        }

        @Test
        @DisplayName("R3: chain discontinuity produces E301")
        fun chainDiscontinuity() {
            val tis = """
                tis: "1.0"
                ground_truth:
                  entity: test
                chain:
                  - op: A
                    input: x
                    output: y
                    det: 0.9
                    phase: 0.1
                  - op: B
                    input: wrong
                    output: z
                    det: 0.9
                    phase: 0.1
            """.trimIndent()

            val errors = validator.validate(tis)
            assertTrue(errors.any { it.code == "E301" },
                "Mismatched chain link input must produce E301")
        }

        @Test
        @DisplayName("Expression IS Verification: valid composition produces zero errors")
        fun expressionIsVerification() {
            val tis = """
                tis: "1.0"
                ground_truth:
                  entity: test_target
                  domain: "validation"
                  testable: true
                conditions:
                  C1_entropy:
                    source: input
                    process: reducer
                    rate: 1.0
                    coupling:
                      to: ground_truth
                      det: 0.99
                      phase: 0.01
                  C2_coupling:
                    target: weights
                    gradient: grad
                    rate: 0.5
                    coupling:
                      to: C1_entropy
                      det: 0.95
                      phase: 0.02
                  C3_self_modification:
                    entity: test_target
                    jacobian: 0.90
                    coupling:
                      to: C2_coupling
                      det: 0.90
                      phase: 0.03
                  C4_closure:
                    eigenfunction: monitor
                    eigenvalue:
                      value: 1.0
                      min: 0.5
                      max: 2.0
                    coupling:
                      to: C3_self_modification
                      det: 0.88
                      phase: 0.02
                chain:
                  - op: PROCESS
                    input: input
                    output: result
                    det: 0.95
                    phase: 0.05
                    signal: entropy
                metadata:
                  coupling:
                    to: chain
                    det: 1.0
                    phase: 0.0
                  tier: 0.80
                  archetype: strong_aligned
            """.trimIndent()

            val errors = validator.validate(tis)
            val actualErrors = errors.filter { it.severity == TisValidator.Severity.ERROR }
            assertEquals(0, actualErrors.size,
                "A well-formed TIS composition must produce zero errors. " +
                "Expression IS Verification: if it compiles, it's valid. " +
                "Got: ${actualErrors.map { "${it.code}: ${it.message}" }}")
        }
    }
}
