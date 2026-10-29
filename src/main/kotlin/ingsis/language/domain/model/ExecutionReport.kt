package ingsis.language.domain.model

data class ExecutionReport(
    val outputs: List<String> = emptyList(),
    val error: Diagnostic? = null,
    val executionTimeMs: Long = 0L,
) {
    val isSuccess: Boolean
        get() = error == null
}
