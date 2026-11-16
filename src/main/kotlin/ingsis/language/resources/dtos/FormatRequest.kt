package ingsis.language.resources.dtos

import tools.jackson.databind.JsonNode

data class FormatRequest(
    val content: String,
    val version: String,
    val rules: JsonNode? = null,
)
