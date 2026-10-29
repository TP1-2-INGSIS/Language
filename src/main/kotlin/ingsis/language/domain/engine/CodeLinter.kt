package ingsis.language.domain.engine

import ingsis.language.domain.model.LanguageResult
import ingsis.language.domain.model.ValidationReport

interface CodeLinter {
    fun lint(
        code: String,
        version: String,
        config: String,
    ): LanguageResult<ValidationReport>
}
