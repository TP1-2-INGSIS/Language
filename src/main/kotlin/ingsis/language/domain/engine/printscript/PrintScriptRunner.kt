package ingsis.language.domain.engine.printscript

import ingsis.language.domain.model.ExecutionReport
import ingsis.language.domain.model.ValidationReport

interface PrintScriptRunner {
    fun validate(
        code: String,
        version: String,
    ): ValidationReport

    fun format(
        code: String,
        version: String,
        config: String,
    ): String

    fun lint(
        code: String,
        version: String,
        config: String,
    ): ValidationReport

    fun execute(
        code: String,
        version: String,
        inputs: List<String>,
    ): ExecutionReport
}
