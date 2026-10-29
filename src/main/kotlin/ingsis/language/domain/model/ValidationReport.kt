package ingsis.language.domain.model

enum class DiagnosticSeverity {
    INFO,
    WARNING,
    ERROR,
}

data class Diagnostic(
    val message: String,
    val line: Int,
    val column: Int,
    val severity: DiagnosticSeverity = DiagnosticSeverity.ERROR,
)

data class ValidationReport(
    val isValid: Boolean,
    val diagnostics: List<Diagnostic> = emptyList(),
)
