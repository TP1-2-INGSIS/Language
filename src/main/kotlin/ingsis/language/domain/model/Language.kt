package ingsis.language.domain.model

data class Language(
    val name: String,
    val versions: List<String> = emptyList(),
    val engineVersion: String = "",
)

typealias Languages = List<Language>
