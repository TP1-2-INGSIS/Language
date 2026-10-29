package ingsis.language.resources

import com.fasterxml.jackson.databind.JsonNode
import ingsis.language.resources.dtos.ErrorResponse
import ingsis.language.resources.dtos.ExecuteRequest
import ingsis.language.resources.dtos.ExecutionResponse
import ingsis.language.resources.dtos.FormatRequest
import ingsis.language.resources.dtos.FormatResponse
import ingsis.language.resources.dtos.LanguageResponse
import ingsis.language.resources.dtos.LintRequest
import ingsis.language.resources.dtos.LintResponse
import ingsis.language.resources.dtos.ValidateRequest
import ingsis.language.resources.dtos.ValidateResponse
import ingsis.language.services.LanguageService
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.GET
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response

@Path("/languages")
@Produces(MediaType.APPLICATION_JSON)
class LanguageResource(
    private val m_service: LanguageService,
) {
    @GET
    fun language(): Response = m_service.getAllLanguages().toResponse(::LanguageResponse)

    @POST
    @Path("/{lang}/validate")
    @Consumes(MediaType.APPLICATION_JSON)
    fun validate(
        @PathParam("lang") lang: String,
        request: ValidateRequest?,
    ): Response {
        validateRequest(request?.version)?.let { return it }

        return m_service
            .validate(lang, request!!.version, request.content)
            .toResponse(::ValidateResponse)
    }

    @POST
    @Path("/{lang}/format")
    @Consumes(MediaType.APPLICATION_JSON)
    fun format(
        @PathParam("lang") lang: String,
        request: FormatRequest?,
    ): Response {
        validateRequest(request?.version)?.let { return it }

        return m_service
            .format(lang, request!!.version, request.content, request.rules.toRulesString())
            .toResponse(::FormatResponse)
    }

    @POST
    @Path("/{lang}/lint")
    @Consumes(MediaType.APPLICATION_JSON)
    fun lint(
        @PathParam("lang") lang: String,
        request: LintRequest?,
    ): Response {
        validateRequest(request?.version)?.let { return it }

        return m_service
            .lint(lang, request!!.version, request.content, request.rules.toRulesString())
            .toResponse(::LintResponse)
    }

    @POST
    @Path("/{lang}/execute")
    @Consumes(MediaType.APPLICATION_JSON)
    fun execute(
        @PathParam("lang") lang: String,
        request: ExecuteRequest?,
    ): Response {
        validateRequest(request?.version)?.let { return it }

        return m_service
            .execute(lang, request!!.version, request.content, request.inputs)
            .toResponse(::ExecutionResponse)
    }

    private fun validateRequest(version: String?): Response? =
        if (version.isNullOrBlank()) {
            Response
                .status(Response.Status.BAD_REQUEST)
                .entity(ErrorResponse(error = "BAD_REQUEST", message = "Faltan campos obligatorios"))
                .build()
        } else {
            null
        }

    private fun JsonNode?.toRulesString(): String = if (this == null || isNull) "{}" else toString()
}
