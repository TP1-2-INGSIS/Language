package ingsis.language.domain.model

data class Language(
    val name: String,
    val versions: List<String> = emptyList(),
)

typealias Languages = List<Language>
