package ingsis.language.resources.dtos

import ingsis.language.domain.model.ExecutionReport

data class ExecutionResponse(
    val outputs: List<String> = emptyList(),
    val error: DiagnosticResponse? = null,
) {
    constructor(report: ExecutionReport) : this(
        outputs = report.outputs,
        error =
            report.error?.let {
                DiagnosticResponse(
                    rule = it.rule,
                    message = it.message,
                    line = it.line,
                    column = it.column,
                )
            },
    )
}
