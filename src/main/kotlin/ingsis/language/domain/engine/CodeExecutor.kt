package ingsis.language.domain.engine

import ingsis.language.domain.model.ExecutionReport
import ingsis.language.domain.model.LanguageResult

interface CodeExecutor {
    fun execute(
        code: String,
        version: String,
        inputs: List<String>,
    ): LanguageResult<ExecutionReport>
}
