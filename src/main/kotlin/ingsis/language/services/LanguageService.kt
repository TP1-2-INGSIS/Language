package ingsis.language.services

import ingsis.language.domain.model.ExecutionReport
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

    fun format(
        language: String,
        version: String,
        content: String,
        rules: String,
    ): LanguageResult<String> =
        m_registry
            .getFormatter(language, version)
            .flatMap { formatter -> formatter.format(content, version, rules) }

    fun lint(
        language: String,
        version: String,
        content: String,
        rules: String,
    ): LanguageResult<ValidationReport> =
        m_registry
            .getLinter(language, version)
            .flatMap { linter -> linter.lint(content, version, rules) }

    fun execute(
        language: String,
        version: String,
        content: String,
        inputs: List<String>,
    ): LanguageResult<ExecutionReport> =
        m_registry
            .getExecutor(language, version)
            .flatMap { executor -> executor.execute(content, version, inputs) }
}
