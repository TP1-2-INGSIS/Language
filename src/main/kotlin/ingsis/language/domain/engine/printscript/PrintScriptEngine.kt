package ingsis.language.domain.engine.printscript

import ingsis.language.domain.engine.CodeExecutor
import ingsis.language.domain.engine.CodeFormatter
import ingsis.language.domain.engine.CodeLinter
import ingsis.language.domain.engine.CodeValidator
import ingsis.language.domain.engine.InteractiveExecutor
import ingsis.language.domain.engine.LanguageEngine
import ingsis.language.domain.model.Diagnostic
import ingsis.language.domain.model.DomainError
import ingsis.language.domain.model.ExecutionIO
import ingsis.language.domain.model.ExecutionReport
import ingsis.language.domain.model.InvalidRulesException
import ingsis.language.domain.model.LanguageResult
import ingsis.language.domain.model.ValidationReport

class PrintScriptEngine(
    private val m_runner: PrintScriptRunner,
    override val engineVersion: String = "unknown",
) : LanguageEngine,
    CodeValidator,
    CodeFormatter,
    CodeLinter,
    CodeExecutor,
    InteractiveExecutor {
    override val name: String = "printscript"
    override val versions: List<String> = listOf("1.0", "1.1")

    override fun validate(
        code: String,
        version: String,
    ): LanguageResult<ValidationReport> = LanguageResult.Success(m_runner.validate(code, version))

    override fun format(
        code: String,
        version: String,
        config: String,
    ): LanguageResult<String> {
        val validation = m_runner.validate(code, version)
        if (!validation.isValid) {
            return LanguageResult.Failure(
                DomainError.InvalidSource("El código fuente no es válido", validation.diagnostics),
            )
        }
        return withRules { m_runner.format(code, version, config) }
    }

    override fun lint(
        code: String,
        version: String,
        config: String,
    ): LanguageResult<ValidationReport> = withRules { m_runner.lint(code, version, config) }

    override fun execute(
        code: String,
        version: String,
        inputs: List<String>,
    ): LanguageResult<ExecutionReport> = LanguageResult.Success(m_runner.execute(code, version, inputs))

    override fun firstSourceError(
        code: String,
        version: String,
    ): Diagnostic? = m_runner.validate(code, version).diagnostics.firstOrNull()

    override fun run(
        code: String,
        version: String,
        io: ExecutionIO,
    ): Diagnostic? = m_runner.run(code, version, io)

    private fun <T> withRules(block: () -> T): LanguageResult<T> =
        try {
            LanguageResult.Success(block())
        } catch (e: InvalidRulesException) {
            LanguageResult.Failure(DomainError.InvalidRules(e.message ?: "Reglas inválidas"))
        }
}
