package ingsis.language.services

import ingsis.language.domain.model.LanguageResult
import ingsis.language.domain.model.TestReport
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
class TestingService(
    private val m_executionService: ExecutionService,
) {
    fun test(
        language: String,
        version: String,
        content: String,
        inputs: List<String>,
        expectedOutputs: List<String>,
    ): LanguageResult<TestReport> =
        m_executionService.execute(language, version, content, inputs).map { report ->
            val passed = report.error == null && report.outputs == expectedOutputs
            TestReport(
                passed = passed,
                expectedOutputs = expectedOutputs,
                actualOutputs = report.outputs,
                error = report.error,
            )
        }
}
