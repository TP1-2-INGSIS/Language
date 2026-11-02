package ingsis.language.services

import ingsis.language.domain.model.ExecutionReport
import ingsis.language.domain.model.LanguageResult
import ingsis.language.domain.model.Languages
import ingsis.language.domain.model.TestReport
import ingsis.language.domain.model.ValidationReport
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
class LanguageService(
    private val m_registry: LanguageRegistry,
    private val m_executionService: ExecutionService,
    private val m_testingService: TestingService,
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
    ): LanguageResult<ExecutionReport> = m_executionService.execute(language, version, content, inputs)

    fun test(
        language: String,
        version: String,
        content: String,
        inputs: List<String>,
        expectedOutputs: List<String>,
    ): LanguageResult<TestReport> = m_testingService.test(language, version, content, inputs, expectedOutputs)
}
