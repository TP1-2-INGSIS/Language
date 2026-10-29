package ingsis.language.resources.dtos

import ingsis.language.domain.model.ValidationReport

data class ValidateResponse(
    val valid: Boolean,
    val errors: List<DiagnosticResponse> = emptyList(),
) {
    constructor(report: ValidationReport) : this(
        valid = report.isValid,
        errors = report.diagnostics.map(::DiagnosticResponse),
    )
}
