package ingsis.language.domain.model

/**
 * I/O channels of a program execution. Lambda based so the same engine can be driven by a
 * batch execution (inputs taken from a list, outputs collected) or by an interactive session
 * (inputs requested from a client, outputs streamed).
 *
 * @property input called on `readInput(prompt)`; receives the prompt and returns the value.
 * @property env called on `readEnv(name)`; returns null when the variable is not defined.
 * @property output called on every `println`, without a trailing newline.
 */
data class ExecutionIO(
    val input: (String) -> String,
    val env: (String) -> String? = { null },
    val output: (String) -> Unit,
)
