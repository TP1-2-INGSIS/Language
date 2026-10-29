package ingsis.language.resources.dtos

data class ExecuteRequest(
    val content: String,
    val version: String,
    val inputs: List<String> = emptyList(),
)
