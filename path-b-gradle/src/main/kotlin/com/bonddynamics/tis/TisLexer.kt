package com.bonddynamics.tis

import com.intellij.lexer.LexerBase
import com.intellij.psi.tree.IElementType

/**
 * Context-sensitive scanning lexer for TIS.
 *
 * Key behaviors:
 * - Recognizes YAML-like key: value structure
 * - Applies context-sensitive tokenization: `det: 0.0` → DET_ZERO (error token),
 *   `det: 0.85` → DET_VALUE (valid), `phase: 0.1` → PHASE_VALUE, etc.
 * - Keywords at line start (with appropriate indentation) get keyword tokens
 * - Handles comments, strings, booleans, enums
 *
 * This implements Rule L1 from the TIS Compiler Specification:
 * "A FLOAT token in `det` position with value 0.0 → HALT(E302, TYPE_ERROR)"
 */
class TisLexer : LexerBase() {
    private var buffer: CharSequence = ""
    private var startOffset = 0
    private var endOffset = 0
    private var currentOffset = 0
    private var tokenStart = 0
    private var tokenEnd = 0
    private var tokenType: IElementType? = null

    // Context tracking — what key are we currently reading the value for?
    private var lastKey: String = ""

    companion object {
        private val TOP_LEVEL_KEYWORDS = setOf("tis", "ground_truth", "conditions", "chain", "metadata")
        private val SECTION_KEYWORDS = setOf("conditions", "chain", "metadata")
        private val CONDITION_KEYWORDS = setOf("C1_entropy", "C2_coupling", "C3_self_modification", "C4_closure")
        private val FIELD_KEYWORDS = setOf(
            "entity", "domain", "testable", "source", "process", "target", "gradient",
            "protected", "eigenfunction", "eigenvalue", "value", "min", "max",
            "op", "input", "output", "signal", "archetype", "notes", "to", "rate"
        )
        private val SIGNAL_ENUMS = setOf("entropy", "coupling", "self_mod", "closure")
        private val ARCHETYPE_ENUMS = setOf("decoupled", "opposed", "orthogonal", "weak_aligned", "strong_aligned", "convergent")
        private val BOOLEANS = setOf("true", "false")

        private val ZERO_PATTERN = Regex("^0(\\.0+)?$")
    }

    override fun start(buffer: CharSequence, startOffset: Int, endOffset: Int, initialState: Int) {
        this.buffer = buffer
        this.startOffset = startOffset
        this.endOffset = endOffset
        this.currentOffset = startOffset
        this.tokenType = null
        this.lastKey = ""
        advance()
    }

    override fun getState(): Int = 0
    override fun getTokenType(): IElementType? = tokenType
    override fun getTokenStart(): Int = tokenStart
    override fun getTokenEnd(): Int = tokenEnd
    override fun getBufferSequence(): CharSequence = buffer
    override fun getBufferEnd(): Int = endOffset

    override fun advance() {
        if (currentOffset >= endOffset) {
            tokenType = null
            return
        }

        tokenStart = currentOffset

        val c = buffer[currentOffset]

        when {
            // Newline
            c == '\n' -> {
                currentOffset++
                tokenEnd = currentOffset
                tokenType = TisTokenTypes.WHITE_SPACE
            }

            // Whitespace (spaces, tabs)
            c == ' ' || c == '\t' || c == '\r' -> {
                skipWhile { it == ' ' || it == '\t' || it == '\r' }
                tokenEnd = currentOffset
                tokenType = TisTokenTypes.WHITE_SPACE
            }

            // Comment
            c == '#' -> {
                skipToEndOfLine()
                tokenEnd = currentOffset
                tokenType = TisTokenTypes.COMMENT
            }

            // String
            c == '"' -> {
                currentOffset++ // skip opening quote
                while (currentOffset < endOffset && buffer[currentOffset] != '"') {
                    if (buffer[currentOffset] == '\\' && currentOffset + 1 < endOffset) {
                        currentOffset++ // skip escape
                    }
                    currentOffset++
                }
                if (currentOffset < endOffset) currentOffset++ // skip closing quote
                tokenEnd = currentOffset
                tokenType = TisTokenTypes.STRING
            }

            // Punctuation
            c == ':' -> {
                currentOffset++
                tokenEnd = currentOffset
                tokenType = TisTokenTypes.COLON
            }
            c == '{' -> {
                currentOffset++
                tokenEnd = currentOffset
                tokenType = TisTokenTypes.LBRACE
            }
            c == '}' -> {
                currentOffset++
                tokenEnd = currentOffset
                tokenType = TisTokenTypes.RBRACE
            }
            c == '[' -> {
                currentOffset++
                tokenEnd = currentOffset
                tokenType = TisTokenTypes.LBRACKET
            }
            c == ']' -> {
                currentOffset++
                tokenEnd = currentOffset
                tokenType = TisTokenTypes.RBRACKET
            }
            c == ',' -> {
                currentOffset++
                tokenEnd = currentOffset
                tokenType = TisTokenTypes.COMMA
            }

            // Dash (YAML list marker) — only at start of value position
            c == '-' && peekIsListMarker() -> {
                currentOffset++
                tokenEnd = currentOffset
                tokenType = TisTokenTypes.DASH
            }

            // Number (including negative)
            c.isDigit() || (c == '-' && currentOffset + 1 < endOffset && buffer[currentOffset + 1].isDigit()) -> {
                scanNumber()
            }

            // Word (keyword, identifier, enum, boolean)
            c.isLetter() || c == '_' -> {
                scanWord()
            }

            // Anything else
            else -> {
                currentOffset++
                tokenEnd = currentOffset
                tokenType = TisTokenTypes.BAD_CHARACTER
            }
        }
    }

    private fun scanNumber() {
        val numStart = currentOffset
        if (buffer[currentOffset] == '-') currentOffset++
        skipWhile { it.isDigit() }
        if (currentOffset < endOffset && buffer[currentOffset] == '.') {
            currentOffset++
            skipWhile { it.isDigit() }
        }
        tokenEnd = currentOffset
        val numText = buffer.subSequence(numStart, tokenEnd).toString()

        tokenType = when (lastKey) {
            "det", "jacobian" -> {
                if (ZERO_PATTERN.matches(numText)) TisTokenTypes.DET_ZERO else TisTokenTypes.DET_VALUE
            }
            "phase" -> TisTokenTypes.PHASE_VALUE
            else -> TisTokenTypes.NUMBER
        }
    }

    private fun scanWord() {
        val wordStart = currentOffset
        skipWhile { it.isLetterOrDigit() || it == '_' }
        tokenEnd = currentOffset
        val word = buffer.subSequence(wordStart, tokenEnd).toString()

        // Check if this word is followed by a colon (making it a key)
        val afterWord = skipPeekNonSpace()
        val isKey = afterWord == ':'

        if (isKey) {
            // This is a key — categorize it and track context
            lastKey = word
            tokenType = classifyKey(word)
        } else {
            // This is a value — categorize based on last key context
            tokenType = classifyValue(word)
        }
    }

    private fun classifyKey(word: String): IElementType = when {
        word == "ground_truth" -> TisTokenTypes.KW_GROUND_TRUTH
        word == "tis" -> TisTokenTypes.KW_TIS
        word in SECTION_KEYWORDS -> TisTokenTypes.KW_SECTION
        word in CONDITION_KEYWORDS -> TisTokenTypes.KW_CONDITION
        word == "coupling" -> TisTokenTypes.KW_COUPLING
        word == "det" -> TisTokenTypes.KW_DET
        word == "phase" -> TisTokenTypes.KW_PHASE
        word == "kappa" -> TisTokenTypes.KW_KAPPA
        word == "rate" -> TisTokenTypes.KW_RATE
        word == "jacobian" -> TisTokenTypes.KW_JACOBIAN
        word == "tier" -> TisTokenTypes.KW_TIER
        word in FIELD_KEYWORDS -> TisTokenTypes.KW_FIELD
        else -> TisTokenTypes.IDENTIFIER
    }

    private fun classifyValue(word: String): IElementType = when {
        word in BOOLEANS -> TisTokenTypes.BOOLEAN
        // Enum detection based on context
        lastKey == "signal" && word in SIGNAL_ENUMS -> TisTokenTypes.SIGNAL_ENUM
        lastKey == "archetype" && word in ARCHETYPE_ENUMS -> TisTokenTypes.ARCHETYPE_ENUM
        // Operator names after op: are typically UPPER_CASE
        lastKey == "op" && word.all { it.isUpperCase() || it == '_' || it.isDigit() } -> TisTokenTypes.OP_NAME
        // Signal/archetype enums without explicit context (standalone values in inline blocks)
        word in SIGNAL_ENUMS -> TisTokenTypes.SIGNAL_ENUM
        word in ARCHETYPE_ENUMS -> TisTokenTypes.ARCHETYPE_ENUM
        else -> TisTokenTypes.IDENTIFIER
    }

    private fun peekIsListMarker(): Boolean {
        // A dash is a list marker if it's followed by a space (YAML sequence item)
        return currentOffset + 1 < endOffset && buffer[currentOffset + 1] == ' '
    }

    private fun skipWhile(predicate: (Char) -> Boolean) {
        while (currentOffset < endOffset && predicate(buffer[currentOffset])) {
            currentOffset++
        }
    }

    private fun skipToEndOfLine() {
        while (currentOffset < endOffset && buffer[currentOffset] != '\n') {
            currentOffset++
        }
    }

    private fun skipPeekNonSpace(): Char? {
        var peek = currentOffset
        while (peek < endOffset && (buffer[peek] == ' ' || buffer[peek] == '\t')) {
            peek++
        }
        return if (peek < endOffset) buffer[peek] else null
    }
}
