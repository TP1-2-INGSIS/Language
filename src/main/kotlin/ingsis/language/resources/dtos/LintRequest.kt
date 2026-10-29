package ingsis.language.resources.dtos

import com.fasterxml.jackson.databind.JsonNode

data class LintRequest(
    val content: String,
    val version: String,
    val rules: JsonNode? = null,
)
