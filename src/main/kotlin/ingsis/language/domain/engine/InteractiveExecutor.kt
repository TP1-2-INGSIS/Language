package ingsis.language.domain.engine

import ingsis.language.domain.model.Diagnostic
import ingsis.language.domain.model.ExecutionIO

/** Capability of running a program with live I/O channels (interactive sessions). */
interface InteractiveExecutor {
    /** First syntax or semantic error of [code], or null when the source is valid. */
    fun firstSourceError(
        code: String,
        version: String,
    ): Diagnostic?

    /** Runs [code] with [io]. Returns the failure diagnostic, or null on success. */
    fun run(
        code: String,
        version: String,
        io: ExecutionIO,
    ): Diagnostic?
}
