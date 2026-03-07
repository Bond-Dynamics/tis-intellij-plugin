package com.bonddynamics.tis

import com.intellij.lang.ASTNode
import com.intellij.lang.ParserDefinition
import com.intellij.lang.PsiParser
import com.intellij.lexer.Lexer
import com.intellij.openapi.project.Project
import com.intellij.psi.FileViewProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.tree.IFileElementType
import com.intellij.psi.tree.TokenSet
import com.intellij.extapi.psi.PsiFileBase
import com.intellij.lang.PsiBuilder

class TisParserDefinition : ParserDefinition {
    override fun createLexer(project: Project?): Lexer = TisLexer()

    override fun createParser(project: Project?): PsiParser = TisParser()

    override fun getFileNodeType(): IFileElementType = TisTokenTypes.FILE

    override fun getCommentTokens(): TokenSet = TisTokenTypes.COMMENTS

    override fun getWhitespaceTokens(): TokenSet = TisTokenTypes.WHITESPACES

    override fun getStringLiteralElements(): TokenSet = TisTokenTypes.STRINGS

    override fun createElement(node: ASTNode): PsiElement = TisPsiElement(node)

    override fun createFile(viewProvider: FileViewProvider): PsiFile = TisFile(viewProvider)
}

/**
 * Minimal parser — consumes all tokens into a flat tree.
 * Full structural parsing (§4.3 of the TIS spec) is deferred to the compiler implementation.
 * The editor plugin only needs token-level analysis for highlighting and annotation.
 */
class TisParser : PsiParser {
    override fun parse(root: com.intellij.psi.tree.IElementType, builder: PsiBuilder): ASTNode {
        val rootMarker = builder.mark()
        while (!builder.eof()) {
            builder.advanceLexer()
        }
        rootMarker.done(root)
        return builder.treeBuilt
    }
}

class TisFile(viewProvider: FileViewProvider) : PsiFileBase(viewProvider, TisLanguage.INSTANCE) {
    override fun getFileType() = TisFileType.INSTANCE
    override fun toString() = "TIS File"
}

class TisPsiElement(node: ASTNode) : com.intellij.extapi.psi.ASTWrapperPsiElement(node)
