package ingsis.language.domain.model

data class ExecutionReport(
    val outputs: List<String> = emptyList(),
    val errors: List<String> = emptyList(),
    val executionTimeMs: Long = 0L,
) {
    val isSuccess: Boolean
        get() = errors.isEmpty()
}
