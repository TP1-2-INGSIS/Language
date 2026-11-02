package ingsis.language.worker.events

data class TaskEvent(
    val taskId: String = "",
    val snippetId: String = "",
    val type: TaskType = TaskType.VALIDATE,
    val language: String = "",
    val version: String = "",
    val content: String = "",
    val rules: String = "{}",
    val inputs: List<String> = emptyList(),
    val expectedOutputs: List<String> = emptyList(),
)
