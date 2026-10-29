package ingsis.language.domain.model

sealed interface LanguageResult<out T> {
    data class Success<out T>(
        val value: T,
    ) : LanguageResult<T>

    data class Failure(
        val error: DomainError,
    ) : LanguageResult<Nothing>

    val isSuccess: Boolean
        get() = this is Success

    val isFailure: Boolean
        get() = this is Failure

    fun getOrNull(): T? =
        when (this) {
            is Success -> value
            is Failure -> null
        }

    fun errorOrNull(): DomainError? =
        when (this) {
            is Success -> null
            is Failure -> error
        }

    fun <R> map(transform: (T) -> R): LanguageResult<R> =
        when (this) {
            is Success -> Success(transform(value))
            is Failure -> this
        }

    fun <R> flatMap(transform: (T) -> LanguageResult<R>): LanguageResult<R> =
        when (this) {
            is Success -> transform(value)
            is Failure -> this
        }
}
