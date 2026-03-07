package com.bonddynamics.tis

import com.intellij.lang.Language

class TisLanguage private constructor() : Language("tis") {
    companion object {
        @JvmStatic
        val INSTANCE = TisLanguage()
    }

    override fun getDisplayName(): String = "Truth Instruction Set"
    override fun isCaseSensitive(): Boolean = true
}
