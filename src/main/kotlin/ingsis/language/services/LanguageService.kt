package ingsis.language.services

import ingsis.language.domain.model.LanguageResult
import ingsis.language.domain.model.Languages
import ingsis.language.domain.model.ValidationReport
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
class LanguageService(
    private val m_registry: LanguageRegistry,
) {
    fun getAllLanguages(): LanguageResult<Languages> = LanguageResult.Success(m_registry.getAllLanguages())

    fun validate(
        language: String,
        version: String,
        content: String,
    ): LanguageResult<ValidationReport> =
        m_registry
            .getValidator(language, version)
            .flatMap { validator -> validator.validate(content, version) }
}
