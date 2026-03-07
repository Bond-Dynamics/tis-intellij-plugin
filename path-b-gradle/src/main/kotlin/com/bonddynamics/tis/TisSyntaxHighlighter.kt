package com.bonddynamics.tis

import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.HighlighterColors
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase
import com.intellij.psi.tree.IElementType

class TisSyntaxHighlighter : SyntaxHighlighterBase() {
    companion object {
        // === ground_truth — STRONGEST visual weight ===
        @JvmField
        val GROUND_TRUTH = TextAttributesKey.createTextAttributesKey(
            "TIS_GROUND_TRUTH",
            DefaultLanguageHighlighterColors.KEYWORD
        )

        // === Version header ===
        @JvmField
        val VERSION_KEYWORD = TextAttributesKey.createTextAttributesKey(
            "TIS_VERSION_KEYWORD",
            DefaultLanguageHighlighterColors.KEYWORD
        )

        // === Section keywords (conditions, chain, metadata) ===
        @JvmField
        val SECTION_KEYWORD = TextAttributesKey.createTextAttributesKey(
            "TIS_SECTION_KEYWORD",
            DefaultLanguageHighlighterColors.KEYWORD
        )

        // === Condition type keywords (C1–C4) ===
        @JvmField
        val CONDITION_KEYWORD = TextAttributesKey.createTextAttributesKey(
            "TIS_CONDITION_KEYWORD",
            DefaultLanguageHighlighterColors.CLASS_NAME
        )

        // === Coupling block keyword ===
        @JvmField
        val COUPLING_KEYWORD = TextAttributesKey.createTextAttributesKey(
            "TIS_COUPLING_KEYWORD",
            DefaultLanguageHighlighterColors.KEYWORD
        )

        // === Field keywords (entity, domain, source, etc.) ===
        @JvmField
        val FIELD_KEYWORD = TextAttributesKey.createTextAttributesKey(
            "TIS_FIELD_KEYWORD",
            DefaultLanguageHighlighterColors.INSTANCE_FIELD
        )

        // === Determinant key (the word "det") ===
        @JvmField
        val DET_KEY = TextAttributesKey.createTextAttributesKey(
            "TIS_DET_KEY",
            DefaultLanguageHighlighterColors.KEYWORD
        )

        // === Determinant value — valid (non-zero) ===
        @JvmField
        val DET_VALUE = TextAttributesKey.createTextAttributesKey(
            "TIS_DET_VALUE",
            DefaultLanguageHighlighterColors.NUMBER
        )

        // === Determinant value — ZERO (TYPE ERROR E302) ===
        // Red with strikethrough — the core visual constraint of TIS
        @JvmField
        val DET_ZERO_ERROR: TextAttributesKey = TextAttributesKey.createTextAttributesKey(
            "TIS_DET_ZERO_ERROR"
        )

        // === Phase values ===
        @JvmField
        val PHASE_KEY = TextAttributesKey.createTextAttributesKey(
            "TIS_PHASE_KEY",
            DefaultLanguageHighlighterColors.KEYWORD
        )

        @JvmField
        val PHASE_VALUE = TextAttributesKey.createTextAttributesKey(
            "TIS_PHASE_VALUE",
            DefaultLanguageHighlighterColors.NUMBER
        )

        // === Other named numerics (kappa, rate, tier) ===
        @JvmField
        val NAMED_NUMERIC_KEY = TextAttributesKey.createTextAttributesKey(
            "TIS_NAMED_NUMERIC_KEY",
            DefaultLanguageHighlighterColors.KEYWORD
        )

        // === Signal & archetype enum values ===
        @JvmField
        val SIGNAL_ENUM = TextAttributesKey.createTextAttributesKey(
            "TIS_SIGNAL_ENUM",
            DefaultLanguageHighlighterColors.CONSTANT
        )

        @JvmField
        val ARCHETYPE_ENUM = TextAttributesKey.createTextAttributesKey(
            "TIS_ARCHETYPE_ENUM",
            DefaultLanguageHighlighterColors.CONSTANT
        )

        // === Operator names (HASH, COMPUTE_S, etc.) ===
        @JvmField
        val OP_NAME = TextAttributesKey.createTextAttributesKey(
            "TIS_OP_NAME",
            DefaultLanguageHighlighterColors.FUNCTION_DECLARATION
        )

        // === Booleans ===
        @JvmField
        val BOOLEAN = TextAttributesKey.createTextAttributesKey(
            "TIS_BOOLEAN",
            DefaultLanguageHighlighterColors.CONSTANT
        )

        // === Strings ===
        @JvmField
        val STRING = TextAttributesKey.createTextAttributesKey(
            "TIS_STRING",
            DefaultLanguageHighlighterColors.STRING
        )

        // === Numbers (generic) ===
        @JvmField
        val NUMBER = TextAttributesKey.createTextAttributesKey(
            "TIS_NUMBER",
            DefaultLanguageHighlighterColors.NUMBER
        )

        // === Comments ===
        @JvmField
        val COMMENT = TextAttributesKey.createTextAttributesKey(
            "TIS_COMMENT",
            DefaultLanguageHighlighterColors.LINE_COMMENT
        )

        // === Identifiers (references) ===
        @JvmField
        val IDENTIFIER = TextAttributesKey.createTextAttributesKey(
            "TIS_IDENTIFIER",
            DefaultLanguageHighlighterColors.IDENTIFIER
        )

        // === Punctuation ===
        @JvmField
        val PUNCTUATION = TextAttributesKey.createTextAttributesKey(
            "TIS_PUNCTUATION",
            DefaultLanguageHighlighterColors.OPERATION_SIGN
        )

        @JvmField
        val BAD_CHARACTER = TextAttributesKey.createTextAttributesKey(
            "TIS_BAD_CHARACTER",
            HighlighterColors.BAD_CHARACTER
        )

        init {
            // Default styling for zero-det tokens (red strikethrough) is applied by
            // TisZeroDetAnnotator at annotation time. The TextAttributesKey here is
            // used for color scheme customization via TisColorSettingsPage.
        }

        // Mapping arrays for getTokenHighlights
        private val GROUND_TRUTH_KEYS = arrayOf(GROUND_TRUTH)
        private val VERSION_KEYS = arrayOf(VERSION_KEYWORD)
        private val SECTION_KEYS = arrayOf(SECTION_KEYWORD)
        private val CONDITION_KEYS = arrayOf(CONDITION_KEYWORD)
        private val COUPLING_KEYS = arrayOf(COUPLING_KEYWORD)
        private val FIELD_KEYS = arrayOf(FIELD_KEYWORD)
        private val DET_KEY_KEYS = arrayOf(DET_KEY)
        private val DET_VALUE_KEYS = arrayOf(DET_VALUE)
        private val DET_ZERO_KEYS = arrayOf(DET_ZERO_ERROR)
        private val PHASE_KEY_KEYS = arrayOf(PHASE_KEY)
        private val PHASE_VALUE_KEYS = arrayOf(PHASE_VALUE)
        private val NAMED_NUMERIC_KEYS = arrayOf(NAMED_NUMERIC_KEY)
        private val SIGNAL_KEYS = arrayOf(SIGNAL_ENUM)
        private val ARCHETYPE_KEYS = arrayOf(ARCHETYPE_ENUM)
        private val OP_KEYS = arrayOf(OP_NAME)
        private val BOOLEAN_KEYS = arrayOf(BOOLEAN)
        private val STRING_KEYS = arrayOf(STRING)
        private val NUMBER_KEYS = arrayOf(NUMBER)
        private val COMMENT_KEYS = arrayOf(COMMENT)
        private val IDENTIFIER_KEYS = arrayOf(IDENTIFIER)
        private val PUNCTUATION_KEYS = arrayOf(PUNCTUATION)
        private val BAD_CHAR_KEYS = arrayOf(BAD_CHARACTER)
        private val EMPTY = emptyArray<TextAttributesKey>()
    }

    override fun getHighlightingLexer() = TisLexer()

    override fun getTokenHighlights(tokenType: IElementType?): Array<TextAttributesKey> = when (tokenType) {
        TisTokenTypes.KW_GROUND_TRUTH -> GROUND_TRUTH_KEYS
        TisTokenTypes.KW_TIS -> VERSION_KEYS
        TisTokenTypes.KW_SECTION -> SECTION_KEYS
        TisTokenTypes.KW_CONDITION -> CONDITION_KEYS
        TisTokenTypes.KW_COUPLING -> COUPLING_KEYS
        TisTokenTypes.KW_FIELD -> FIELD_KEYS
        TisTokenTypes.KW_DET -> DET_KEY_KEYS
        TisTokenTypes.DET_VALUE -> DET_VALUE_KEYS
        TisTokenTypes.DET_ZERO -> DET_ZERO_KEYS
        TisTokenTypes.KW_PHASE -> PHASE_KEY_KEYS
        TisTokenTypes.PHASE_VALUE -> PHASE_VALUE_KEYS
        TisTokenTypes.KW_KAPPA, TisTokenTypes.KW_RATE, TisTokenTypes.KW_JACOBIAN, TisTokenTypes.KW_TIER -> NAMED_NUMERIC_KEYS
        TisTokenTypes.SIGNAL_ENUM -> SIGNAL_KEYS
        TisTokenTypes.ARCHETYPE_ENUM -> ARCHETYPE_KEYS
        TisTokenTypes.OP_NAME -> OP_KEYS
        TisTokenTypes.BOOLEAN -> BOOLEAN_KEYS
        TisTokenTypes.STRING -> STRING_KEYS
        TisTokenTypes.NUMBER -> NUMBER_KEYS
        TisTokenTypes.COMMENT -> COMMENT_KEYS
        TisTokenTypes.IDENTIFIER -> IDENTIFIER_KEYS
        TisTokenTypes.COLON, TisTokenTypes.LBRACE, TisTokenTypes.RBRACE,
        TisTokenTypes.LBRACKET, TisTokenTypes.RBRACKET, TisTokenTypes.COMMA,
        TisTokenTypes.DASH -> PUNCTUATION_KEYS
        TisTokenTypes.BAD_CHARACTER -> BAD_CHAR_KEYS
        else -> EMPTY
    }
}
