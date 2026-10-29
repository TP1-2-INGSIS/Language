package ingsis.language.resources.dtos

import ingsis.language.domain.model.ValidationReport

data class LintResponse(
    val violations: List<DiagnosticResponse> = emptyList(),
) {
    constructor(report: ValidationReport) : this(
        violations = report.diagnostics.map(::DiagnosticResponse),
    )
}
