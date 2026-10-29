package ingsis.language.resources.dtos

import ingsis.language.domain.model.Diagnostic

data class DiagnosticResponse(
    val rule: String,
    val message: String,
    val line: Int,
    val column: Int,
) {
    constructor(diagnostic: Diagnostic) : this(
        rule = diagnostic.rule,
        message = diagnostic.message,
        line = diagnostic.line,
        column = diagnostic.column,
    )
}
