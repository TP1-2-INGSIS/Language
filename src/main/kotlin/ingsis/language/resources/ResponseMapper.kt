package ingsis.language.resources

import ingsis.language.domain.model.DomainError
import ingsis.language.domain.model.LanguageResult
import ingsis.language.resources.dtos.DiagnosticResponse
import ingsis.language.resources.dtos.ErrorResponse
import org.springframework.http.ResponseEntity

fun DomainError.toResponse(): ResponseEntity<Any> {
    val statusCode =
        when (this) {
            is DomainError.LanguageNotFound -> 404
            is DomainError.UnsupportedVersion -> 400
            is DomainError.InvalidRules -> 400
            is DomainError.CapabilityNotSupported -> 501
            is DomainError.InvalidSource -> 422
        }

    return ResponseEntity
        .status(statusCode)
        .body(
            ErrorResponse(
                error = code,
                message = message,
                diagnostics = (this as? DomainError.InvalidSource)?.diagnostics.orEmpty().map(::DiagnosticResponse),
            ),
        )
}

fun <T, R : Any> LanguageResult<T>.toResponse(transform: (T) -> R): ResponseEntity<Any> =
    when (this) {
        is LanguageResult.Success -> ResponseEntity.ok(transform(value))
        is LanguageResult.Failure -> error.toResponse()
    }

fun <T : Any> LanguageResult<T>.toResponse(): ResponseEntity<Any> = toResponse { it }
