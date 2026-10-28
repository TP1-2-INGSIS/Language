package com.resources

import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType

@Path("/languages")
class Language {
    @GET
    @Produces(MediaType.TEXT_PLAIN)
    fun language() = "To be implemented."
}
