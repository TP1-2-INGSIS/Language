package ingsis.language.resources.dtos

data class ValidateResponse(
    val valid: Boolean,
    val errors: List<DiagnosticResponse> = emptyList(),
)
