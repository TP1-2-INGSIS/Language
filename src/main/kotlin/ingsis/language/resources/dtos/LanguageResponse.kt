package ingsis.language.resources.dtos

import ingsis.language.domain.model.Language

data class LanguageResponse(
    val id: String,
    val versions: List<String> = emptyList(),
    val engineVersion: String = "",
) {
    constructor(language: Language) : this(
        id = language.name,
        versions = language.versions,
        engineVersion = language.engineVersion,
    )
}
