package ingsis.language.domain.engine

import ingsis.language.domain.model.LanguageResult
import ingsis.language.domain.model.ValidationReport

interface CodeValidator {
    fun validate(
        code: String,
        version: String,
    ): LanguageResult<ValidationReport>
}
