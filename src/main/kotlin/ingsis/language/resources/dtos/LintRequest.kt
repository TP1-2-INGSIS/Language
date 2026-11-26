package ingsis.language.resources.dtos

import tools.jackson.databind.JsonNode

data class LintRequest(
    val content: String,
    val version: String,
    val rules: JsonNode? = null,
)
