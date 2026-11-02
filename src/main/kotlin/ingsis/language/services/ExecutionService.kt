package ingsis.language.services

import ingsis.language.domain.model.ExecutionReport
import ingsis.language.domain.model.LanguageResult
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
class ExecutionService(
    private val m_registry: LanguageRegistry,
) {
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
