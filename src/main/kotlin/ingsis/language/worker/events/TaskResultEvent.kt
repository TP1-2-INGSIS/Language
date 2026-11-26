package ingsis.language.worker.events

import ingsis.language.domain.model.Diagnostic

data class TaskResultEvent(
    val taskId: String,
    val snippetId: String,
    val type: TaskType,
    val status: TaskStatus,
    val contentHash: String? = null,
    val attempts: Int = 1,
    val outputs: List<String> = emptyList(),
    val formattedContent: String? = null,
    val errors: List<Diagnostic> = emptyList(),
    val passed: Boolean? = null,
    val errorMessage: String? = null,
) {
    /** Kept for compatibility with consumers that only know the boolean: `status == OK`. */
    val success: Boolean
        get() = status == TaskStatus.OK
}
