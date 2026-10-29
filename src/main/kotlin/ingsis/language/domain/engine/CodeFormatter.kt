package ingsis.language.domain.engine

interface CodeFormatter {
    fun format(
        code: String,
        version: String,
        config: String = "",
    ): Result<String>
}
