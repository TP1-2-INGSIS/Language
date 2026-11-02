package ingsis.language.domain.engine.printscript

import ingsis.language.domain.model.ExecutionReport
import ingsis.language.domain.model.ValidationReport
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
class DefaultPrintScriptRunner : PrintScriptRunner {
    override fun validate(
        code: String,
        version: String,
    ): ValidationReport = ValidationReport(isValid = true, diagnostics = emptyList())

    override fun format(
        code: String,
        version: String,
        config: String,
    ): String = code

    override fun lint(
        code: String,
        version: String,
        config: String,
    ): ValidationReport = ValidationReport(isValid = true, diagnostics = emptyList())

    override fun execute(
        code: String,
        version: String,
        inputs: List<String>,
    ): ExecutionReport = ExecutionReport(outputs = emptyList(), error = null)
}
