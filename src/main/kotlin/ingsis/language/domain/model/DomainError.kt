package ingsis.language.domain.model

sealed interface DomainError {
    val code: String
    val message: String

    data class LanguageNotFound(
        val language: String,
    ) : DomainError {
        override val code: String = "LANGUAGE_NOT_FOUND"
        override val message: String = "Lenguaje no soportado: $language"
    }

    data class UnsupportedVersion(
        val language: String,
        val version: String,
    ) : DomainError {
        override val code: String = "UNSUPPORTED_VERSION"
        override val message: String = "Versión no soportada: $version para $language"
    }

    data class CapabilityNotSupported(
        val language: String,
        val capability: String,
    ) : DomainError {
        override val code: String = "CAPABILITY_NOT_SUPPORTED"
        override val message: String = "El lenguaje $language no soporta $capability"
    }

    data class InvalidSource(
        override val message: String,
    ) : DomainError {
        override val code: String = "INVALID_SOURCE"
    }

    data class InvalidRules(
        override val message: String,
    ) : DomainError {
        override val code: String = "INVALID_RULES"
    }
}
