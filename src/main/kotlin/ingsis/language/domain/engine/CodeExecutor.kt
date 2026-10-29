package ingsis.language.domain.engine

import ingsis.language.domain.model.ExecutionReport

interface CodeExecutor {
    fun execute(
        code: String,
        version: String,
        inputs: List<String> = emptyList(),
    ): Result<ExecutionReport>
}
