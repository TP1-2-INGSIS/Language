package ingsis.language.resources.dtos

import ingsis.language.domain.model.TestReport

data class TestResponse(
    val passed: Boolean,
    val expectedOutputs: List<String>,
    val actualOutputs: List<String>,
    val error: DiagnosticResponse? = null,
) {
    constructor(report: TestReport) : this(
        passed = report.passed,
        expectedOutputs = report.expectedOutputs,
        actualOutputs = report.actualOutputs,
        error = report.error?.let(::DiagnosticResponse),
    )
}
