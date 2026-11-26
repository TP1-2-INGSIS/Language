package ingsis.language.worker.events

enum class TaskStatus {
    /** The task was processed (a LINT with violations is still OK). */
    OK,

    /** The code does not parse; `errors` carries the syntax diagnostics. */
    INVALID_SOURCE,

    /** Non-retryable error or attempts exhausted; `errorMessage` says why. */
    FAILED,
}
