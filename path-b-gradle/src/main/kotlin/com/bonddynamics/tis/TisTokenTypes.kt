package com.bonddynamics.tis

import com.intellij.psi.TokenType
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.IFileElementType
import com.intellij.psi.tree.TokenSet

class TisTokenType(debugName: String) : IElementType(debugName, TisLanguage.INSTANCE) {
    override fun toString(): String = "TisTokenType.${super.toString()}"
}

class TisElementType(debugName: String) : IElementType(debugName, TisLanguage.INSTANCE)

object TisTokenTypes {
    // Structural
    @JvmField val FILE = IFileElementType(TisLanguage.INSTANCE)

    // Whitespace & comments
    @JvmField val WHITE_SPACE = TokenType.WHITE_SPACE
    @JvmField val COMMENT = TisTokenType("COMMENT")

    // Strings
    @JvmField val STRING = TisTokenType("STRING")

    // Keywords — top-level structure (highest visual weight → lowest)
    @JvmField val KW_GROUND_TRUTH = TisTokenType("KW_GROUND_TRUTH")
    @JvmField val KW_TIS = TisTokenType("KW_TIS")
    @JvmField val KW_SECTION = TisTokenType("KW_SECTION")           // conditions, chain, metadata

    // Condition type keywords
    @JvmField val KW_CONDITION = TisTokenType("KW_CONDITION")       // C1_entropy, C2_coupling, etc.

    // Field keywords
    @JvmField val KW_COUPLING = TisTokenType("KW_COUPLING")        // coupling (block keyword)
    @JvmField val KW_FIELD = TisTokenType("KW_FIELD")              // entity, domain, source, etc.

    // Determinant — the critical field
    @JvmField val KW_DET = TisTokenType("KW_DET")                  // the key "det"
    @JvmField val DET_VALUE = TisTokenType("DET_VALUE")             // non-zero det value
    @JvmField val DET_ZERO = TisTokenType("DET_ZERO")              // zero det value — TYPE ERROR

    // Phase
    @JvmField val KW_PHASE = TisTokenType("KW_PHASE")
    @JvmField val PHASE_VALUE = TisTokenType("PHASE_VALUE")

    // Other named numerics
    @JvmField val KW_KAPPA = TisTokenType("KW_KAPPA")
    @JvmField val KW_RATE = TisTokenType("KW_RATE")
    @JvmField val KW_JACOBIAN = TisTokenType("KW_JACOBIAN")
    @JvmField val KW_TIER = TisTokenType("KW_TIER")

    // Enums
    @JvmField val SIGNAL_ENUM = TisTokenType("SIGNAL_ENUM")         // entropy, coupling, self_mod, closure
    @JvmField val ARCHETYPE_ENUM = TisTokenType("ARCHETYPE_ENUM")   // strong_aligned, etc.
    @JvmField val BOOLEAN = TisTokenType("BOOLEAN")                 // true, false

    // Operators
    @JvmField val OP_NAME = TisTokenType("OP_NAME")                // HASH, COMPUTE_S, etc.

    // Numerics (generic)
    @JvmField val NUMBER = TisTokenType("NUMBER")

    // Punctuation
    @JvmField val COLON = TisTokenType("COLON")
    @JvmField val LBRACE = TisTokenType("LBRACE")
    @JvmField val RBRACE = TisTokenType("RBRACE")
    @JvmField val LBRACKET = TisTokenType("LBRACKET")
    @JvmField val RBRACKET = TisTokenType("RBRACKET")
    @JvmField val COMMA = TisTokenType("COMMA")
    @JvmField val DASH = TisTokenType("DASH")

    // Identifiers (references, names)
    @JvmField val IDENTIFIER = TisTokenType("IDENTIFIER")

    // Bad character
    @JvmField val BAD_CHARACTER = TokenType.BAD_CHARACTER

    // Token sets for parser
    @JvmField val COMMENTS = TokenSet.create(COMMENT)
    @JvmField val WHITESPACES = TokenSet.create(WHITE_SPACE)
    @JvmField val STRINGS = TokenSet.create(STRING)
}
