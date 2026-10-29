package ingsis.language.resources

import ingsis.language.resources.dtos.DiagnosticResponse
import ingsis.language.resources.dtos.ErrorResponse
import ingsis.language.resources.dtos.LanguageResponse
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
    fun language(): Response =
        m_service.getAllLanguages().toResponse { languages ->
            Response.ok(LanguageResponse(languages)).build()
        }

    @POST
    @Path("/{lang}/validate")
    @Consumes(MediaType.APPLICATION_JSON)
    fun validate(
        @PathParam("lang") lang: String,
        request: ValidateRequest?,
    ): Response {
        if (request == null || request.version.isBlank()) {
            return Response
                .status(Response.Status.BAD_REQUEST)
                .entity(ErrorResponse(error = "BAD_REQUEST", message = "Faltan campos obligatorios"))
                .build()
        }

        return m_service.validate(lang, request.version, request.content).toResponse { report ->
            Response
                .ok(
                    ValidateResponse(
                        valid = report.isValid,
                        errors =
                            report.diagnostics.map {
                                DiagnosticResponse(
                                    rule = it.rule,
                                    message = it.message,
                                    line = it.line,
                                    column = it.column,
                                )
                            },
                    ),
                ).build()
        }
    }
}
