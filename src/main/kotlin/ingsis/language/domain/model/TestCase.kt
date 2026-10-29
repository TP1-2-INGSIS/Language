package ingsis.language.domain.model

data class TestCase(
    val name: String = "",
    val inputs: List<String> = emptyList(),
    val expectedOutputs: List<String> = emptyList(),
)

enum class TestStatus {
    PASSED,
    FAILED,
    ERROR,
}

data class TestCaseResult(
    val testCase: TestCase,
    val status: TestStatus,
    val actualOutputs: List<String> = emptyList(),
    val errorMessage: String? = null,
)

data class TestReport(
    val results: List<TestCaseResult> = emptyList(),
) {
    val totalCount: Int
        get() = results.size

    val passedCount: Int
        get() = results.count { it.status == TestStatus.PASSED }

    val isAllPassed: Boolean
        get() = results.all { it.status == TestStatus.PASSED }
}
