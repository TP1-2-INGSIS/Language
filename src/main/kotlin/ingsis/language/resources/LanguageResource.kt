package ingsis.language.resources

import ingsis.language.resources.dtos.LanguageResponse
import ingsis.language.services.LanguageService
import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
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
        m_service
            .getAllLanguages()
            .fold(
                onSuccess = { lang -> Response.ok(LanguageResponse(lang)) },
                onFailure = { Response.serverError() },
            ).build()
}
