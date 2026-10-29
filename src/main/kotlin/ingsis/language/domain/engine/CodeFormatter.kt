package ingsis.language.domain.engine

import ingsis.language.domain.model.LanguageResult

interface CodeFormatter {
    fun format(
        code: String,
        version: String,
        config: String,
    ): LanguageResult<String>
}
