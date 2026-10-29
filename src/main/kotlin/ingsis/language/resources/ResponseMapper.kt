package ingsis.language.resources

import ingsis.language.domain.model.DomainError
import ingsis.language.domain.model.LanguageResult
import ingsis.language.resources.dtos.ErrorResponse
import jakarta.ws.rs.core.Response

fun DomainError.toResponse(): Response {
    val statusCode =
        when (this) {
            is DomainError.LanguageNotFound -> 404
            is DomainError.UnsupportedVersion -> 400
            is DomainError.InvalidRules -> 400
            is DomainError.CapabilityNotSupported -> 501
            is DomainError.InvalidSource -> 422
        }

    return Response
        .status(statusCode)
        .entity(ErrorResponse(error = code, message = message))
        .build()
}

fun <T> LanguageResult<T>.toResponse(onSuccess: (T) -> Response): Response =
    when (this) {
        is LanguageResult.Success -> onSuccess(value)
        is LanguageResult.Failure -> error.toResponse()
    }
