package ingsis.language.resources.dtos

data class DiagnosticResponse(
    val rule: String,
    val message: String,
    val line: Int,
    val column: Int,
)
