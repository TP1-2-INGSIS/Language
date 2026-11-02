package ingsis.language.domain.engine.printscript

import ingsis.language.domain.engine.CodeExecutor
import ingsis.language.domain.engine.CodeFormatter
import ingsis.language.domain.engine.CodeLinter
import ingsis.language.domain.engine.CodeValidator
import ingsis.language.domain.engine.LanguageEngine
import ingsis.language.domain.model.ExecutionReport
import ingsis.language.domain.model.LanguageResult
import ingsis.language.domain.model.ValidationReport
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
class PrintScriptEngine(
    private val m_runner: PrintScriptRunner,
) : LanguageEngine,
    CodeValidator,
    CodeFormatter,
    CodeLinter,
    CodeExecutor {
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
    ): LanguageResult<String> = LanguageResult.Success(m_runner.format(code, version, config))

    override fun lint(
        code: String,
        version: String,
        config: String,
    ): LanguageResult<ValidationReport> = LanguageResult.Success(m_runner.lint(code, version, config))

    override fun execute(
        code: String,
        version: String,
        inputs: List<String>,
    ): LanguageResult<ExecutionReport> = LanguageResult.Success(m_runner.execute(code, version, inputs))
}
