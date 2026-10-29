package ingsis.language.domain.model

data class TestReport(
    val passed: Boolean,
    val expectedOutputs: List<String> = emptyList(),
    val actualOutputs: List<String> = emptyList(),
    val error: Diagnostic? = null,
)
