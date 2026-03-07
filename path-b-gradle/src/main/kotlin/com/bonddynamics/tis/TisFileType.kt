package com.bonddynamics.tis

import com.intellij.openapi.fileTypes.LanguageFileType
import javax.swing.Icon

class TisFileType private constructor() : LanguageFileType(TisLanguage.INSTANCE) {
    companion object {
        @JvmField
        val INSTANCE = TisFileType()
    }

    override fun getName(): String = "TIS File"
    override fun getDescription(): String = "Truth Instruction Set composition specification"
    override fun getDefaultExtension(): String = "tis"
    override fun getIcon(): Icon = TisIcons.FILE
}
