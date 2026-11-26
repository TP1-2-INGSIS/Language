package ingsis.language.domain.model

/** Thrown by an engine when the rules JSON it receives is not a JSON object. */
class InvalidRulesException(
    message: String,
) : RuntimeException(message)
