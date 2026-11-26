package ingsis.language.domain.model

/** Rule name of the diagnostics produced for code that does not parse. */
const val SYNTAX_RULE = "syntax"

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
