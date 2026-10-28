package ingsis.language.resources.dtos

import ingsis.language.domain.model.Languages

data class LanguageResponse(
    val languages: Languages = listOf(),
)
