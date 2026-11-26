package ingsis.language.resources

import ingsis.language.resources.dtos.ErrorResponse
import ingsis.language.resources.dtos.ExecuteRequest
import ingsis.language.resources.dtos.ExecutionResponse
import ingsis.language.resources.dtos.FormatRequest
import ingsis.language.resources.dtos.FormatResponse
import ingsis.language.resources.dtos.LanguageResponse
import ingsis.language.resources.dtos.LintRequest
import ingsis.language.resources.dtos.LintResponse
import ingsis.language.resources.dtos.TestRequest
import ingsis.language.resources.dtos.TestResponse
import ingsis.language.resources.dtos.ValidateRequest
import ingsis.language.resources.dtos.ValidateResponse
import ingsis.language.services.LanguageService
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import tools.jackson.databind.JsonNode

@RestController
@RequestMapping("/languages", produces = [MediaType.APPLICATION_JSON_VALUE])
class LanguageResource(
    private val m_service: LanguageService,
) {
    @GetMapping
    fun language(): ResponseEntity<Any> = m_service.getAllLanguages().toResponse { it.map(::LanguageResponse) }

    @PostMapping("/{lang}/validate", consumes = [MediaType.APPLICATION_JSON_VALUE])
    fun validate(
        @PathVariable("lang") lang: String,
        @RequestBody(required = false) request: ValidateRequest?,
    ): ResponseEntity<Any> {
        validateRequest(request?.version)?.let { return it }

        return m_service
            .validate(lang, request!!.version, request.content)
            .toResponse(::ValidateResponse)
    }

    @PostMapping("/{lang}/format", consumes = [MediaType.APPLICATION_JSON_VALUE])
    fun format(
        @PathVariable("lang") lang: String,
        @RequestBody(required = false) request: FormatRequest?,
    ): ResponseEntity<Any> {
        validateRequest(request?.version)?.let { return it }

        return m_service
            .format(lang, request!!.version, request.content, request.rules.toRulesString())
            .toResponse(::FormatResponse)
    }

    @PostMapping("/{lang}/lint", consumes = [MediaType.APPLICATION_JSON_VALUE])
    fun lint(
        @PathVariable("lang") lang: String,
        @RequestBody(required = false) request: LintRequest?,
    ): ResponseEntity<Any> {
        validateRequest(request?.version)?.let { return it }

        return m_service
            .lint(lang, request!!.version, request.content, request.rules.toRulesString())
            .toResponse(::LintResponse)
    }

    @PostMapping("/{lang}/execute", consumes = [MediaType.APPLICATION_JSON_VALUE])
    fun execute(
        @PathVariable("lang") lang: String,
        @RequestBody(required = false) request: ExecuteRequest?,
    ): ResponseEntity<Any> {
        validateRequest(request?.version)?.let { return it }

        return m_service
            .execute(lang, request!!.version, request.content, request.inputs)
            .toResponse(::ExecutionResponse)
    }

    @PostMapping("/{lang}/test", consumes = [MediaType.APPLICATION_JSON_VALUE])
    fun test(
        @PathVariable("lang") lang: String,
        @RequestBody(required = false) request: TestRequest?,
    ): ResponseEntity<Any> {
        validateRequest(request?.version)?.let { return it }

        return m_service
            .test(lang, request!!.version, request.content, request.inputs, request.expectedOutputs)
            .toResponse(::TestResponse)
    }

    private fun validateRequest(version: String?): ResponseEntity<Any>? =
        if (version.isNullOrBlank()) {
            ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse(error = "BAD_REQUEST", message = "Faltan campos obligatorios"))
        } else {
            null
        }

    private fun JsonNode?.toRulesString(): String = if (this == null || isNull) "{}" else toString()
}
