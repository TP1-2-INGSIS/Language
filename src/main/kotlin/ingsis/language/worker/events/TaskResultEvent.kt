package ingsis.language.worker.events

import ingsis.language.domain.model.Diagnostic

data class TaskResultEvent(
    val taskId: String,
    val snippetId: String,
    val type: TaskType,
    val success: Boolean,
    val outputs: List<String> = emptyList(),
    val formattedContent: String? = null,
    val errors: List<Diagnostic> = emptyList(),
    val passed: Boolean? = null,
    val errorMessage: String? = null,
)
