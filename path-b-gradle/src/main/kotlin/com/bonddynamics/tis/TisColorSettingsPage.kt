package com.bonddynamics.tis

import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.fileTypes.SyntaxHighlighter
import com.intellij.openapi.options.colors.AttributesDescriptor
import com.intellij.openapi.options.colors.ColorDescriptor
import com.intellij.openapi.options.colors.ColorSettingsPage
import javax.swing.Icon

class TisColorSettingsPage : ColorSettingsPage {
    companion object {
        private val DESCRIPTORS = arrayOf(
            AttributesDescriptor("Keywords//ground_truth (composition anchor)", TisSyntaxHighlighter.GROUND_TRUTH),
            AttributesDescriptor("Keywords//Version header (tis:)", TisSyntaxHighlighter.VERSION_KEYWORD),
            AttributesDescriptor("Keywords//Section keywords (conditions, chain, metadata)", TisSyntaxHighlighter.SECTION_KEYWORD),
            AttributesDescriptor("Keywords//Condition types (C1–C4)", TisSyntaxHighlighter.CONDITION_KEYWORD),
            AttributesDescriptor("Keywords//Coupling block", TisSyntaxHighlighter.COUPLING_KEYWORD),
            AttributesDescriptor("Keywords//Field names", TisSyntaxHighlighter.FIELD_KEYWORD),
            AttributesDescriptor("Determinant//det keyword", TisSyntaxHighlighter.DET_KEY),
            AttributesDescriptor("Determinant//Valid value (non-zero)", TisSyntaxHighlighter.DET_VALUE),
            AttributesDescriptor("Determinant//Zero value (TYPE ERROR)", TisSyntaxHighlighter.DET_ZERO_ERROR),
            AttributesDescriptor("Phase//phase keyword", TisSyntaxHighlighter.PHASE_KEY),
            AttributesDescriptor("Phase//Phase value", TisSyntaxHighlighter.PHASE_VALUE),
            AttributesDescriptor("Numeric keys (kappa, rate, tier)", TisSyntaxHighlighter.NAMED_NUMERIC_KEY),
            AttributesDescriptor("Enums//Signal type", TisSyntaxHighlighter.SIGNAL_ENUM),
            AttributesDescriptor("Enums//Archetype", TisSyntaxHighlighter.ARCHETYPE_ENUM),
            AttributesDescriptor("Operator name", TisSyntaxHighlighter.OP_NAME),
            AttributesDescriptor("Boolean", TisSyntaxHighlighter.BOOLEAN),
            AttributesDescriptor("String", TisSyntaxHighlighter.STRING),
            AttributesDescriptor("Number", TisSyntaxHighlighter.NUMBER),
            AttributesDescriptor("Comment", TisSyntaxHighlighter.COMMENT),
            AttributesDescriptor("Identifier", TisSyntaxHighlighter.IDENTIFIER),
            AttributesDescriptor("Punctuation", TisSyntaxHighlighter.PUNCTUATION),
        )

        private val DEMO_TEXT = """
# TIS Test: ECDSA Signer
tis: "1.0"

ground_truth:
  entity: private_key_k
  domain: "secp256k1 elliptic curve"
  testable: true

conditions:
  C1_entropy:
    source: transaction_data
    process: hash_to_scalar
    rate: 1.0
    coupling:
      to: ground_truth
      det: 0.99
      phase: 0.01

  C2_coupling:
    target: signing_equation
    gradient: scalar_multiplication
    rate: 0.99
    coupling:
      to: C1_entropy
      det: 0.0
      phase: 0.0

  C3_self_modification:
    entity: ground_truth
    jacobian: 0.95
    coupling:
      to: C2_coupling
      det: 0.95
      phase: 0.05

  C4_closure:
    eigenfunction: signature_verification
    eigenvalue:
      value: 1.0
      min: 0.9
      max: 1.1
    coupling:
      to: C3_self_modification
      det: 0.98
      phase: 0.02

chain:
  - op: HASH
    input: transaction_data
    output: message_scalar_z
    det: 0.99
    phase: 0.01
    signal: entropy

  - op: COMPUTE_S
    input: r_component
    output: signature_rs
    det: 0.99
    phase: 0.01
    signal: self_mod

metadata:
  coupling:
    to: chain
    det: 1.0
    phase: 0.0
  tier: 0.95
  archetype: strong_aligned
  notes: "Test document"
""".trimIndent()
    }

    override fun getIcon(): Icon = TisIcons.FILE
    override fun getHighlighter(): SyntaxHighlighter = TisSyntaxHighlighter()
    override fun getDemoText(): String = DEMO_TEXT
    override fun getAdditionalHighlightingTagToDescriptorMap(): Map<String, TextAttributesKey>? = null
    override fun getAttributeDescriptors(): Array<AttributesDescriptor> = DESCRIPTORS
    override fun getColorDescriptors(): Array<ColorDescriptor> = ColorDescriptor.EMPTY_ARRAY
    override fun getDisplayName(): String = "Truth Instruction Set"
}
