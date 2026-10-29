package ingsis.language.domain.model

data class Diagnostic(
    val rule: String,
    val message: String,
    val line: Int,
    val column: Int,
)

data class ValidationReport(
    val isValid: Boolean,
    val diagnostics: List<Diagnostic> = emptyList(),
)
