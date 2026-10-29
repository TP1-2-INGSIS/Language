package ingsis.language.resources.dtos

data class ErrorResponse(
    val error: String,
    val message: String,
    val details: List<String> = emptyList(),
)
