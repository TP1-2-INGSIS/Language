package ingsis.language.domain.engine.printscript

import ingsis.language.domain.model.Diagnostic
import ingsis.language.domain.model.ExecutionIO
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

    /** Runs [code] feeding `readInput` from [inputs] in order and `readEnv` from [env]; outputs are collected. */
    fun execute(
        code: String,
        version: String,
        inputs: List<String>,
        env: Map<String, String> = emptyMap(),
    ): ExecutionReport

    /** Runs [code] with arbitrary [io] channels. Returns the failure diagnostic, or null on success. */
    fun run(
        code: String,
        version: String,
        io: ExecutionIO,
    ): Diagnostic?
}
