package ingsis.language.worker

import ingsis.language.domain.model.Diagnostic
import ingsis.language.domain.model.DomainError
import ingsis.language.domain.model.LanguageResult
import ingsis.language.domain.model.SYNTAX_RULE
import ingsis.language.services.ExecutionService
import ingsis.language.services.LanguageService
import ingsis.language.services.TestingService
import ingsis.language.worker.events.TaskEvent
import ingsis.language.worker.events.TaskResultEvent
import ingsis.language.worker.events.TaskStatus
import ingsis.language.worker.events.TaskType
import org.springframework.stereotype.Component

/**
 * Runs a task through the same services as REST. A [LanguageResult.Failure] is non-retryable and is
 * mapped to a result; unexpected exceptions propagate so the worker can retry the delivery.
 */
@Component
class TaskProcessor(
    private val m_languageService: LanguageService,
    private val m_executionService: ExecutionService,
    private val m_testingService: TestingService,
) {
    fun process(task: TaskEvent): TaskResultEvent =
        when (task.type) {
            TaskType.VALIDATE -> handleValidate(task)
            TaskType.FORMAT -> handleFormat(task)
            TaskType.LINT -> handleLint(task)
            TaskType.EXECUTE -> handleExecute(task)
            TaskType.TEST -> handleTest(task)
        }

    private fun handleValidate(task: TaskEvent): TaskResultEvent =
        when (val result = m_languageService.validate(task.language, task.version, task.content)) {
            is LanguageResult.Success ->
                if (result.value.isValid) {
                    result(task, TaskStatus.OK)
                } else {
                    result(task, TaskStatus.INVALID_SOURCE, errors = result.value.diagnostics)
                }
            is LanguageResult.Failure -> failure(task, result.error)
        }

    private fun handleFormat(task: TaskEvent): TaskResultEvent =
        when (val result = m_languageService.format(task.language, task.version, task.content, task.rules)) {
            is LanguageResult.Success -> result(task, TaskStatus.OK, formattedContent = result.value)
            is LanguageResult.Failure -> failure(task, result.error)
        }

    private fun handleLint(task: TaskEvent): TaskResultEvent =
        when (val result = m_languageService.lint(task.language, task.version, task.content, task.rules)) {
            is LanguageResult.Success -> {
                val diagnostics = result.value.diagnostics
                val status = if (diagnostics.any { it.rule == SYNTAX_RULE }) TaskStatus.INVALID_SOURCE else TaskStatus.OK
                result(task, status, errors = diagnostics)
            }
            is LanguageResult.Failure -> failure(task, result.error)
        }

    private fun handleExecute(task: TaskEvent): TaskResultEvent =
        when (val result = m_executionService.execute(task.language, task.version, task.content, task.inputs)) {
            is LanguageResult.Success ->
                result(
                    task,
                    statusOf(result.value.error),
                    outputs = result.value.outputs,
                    errors = listOfNotNull(result.value.error),
                )
            is LanguageResult.Failure -> failure(task, result.error)
        }

    private fun handleTest(task: TaskEvent): TaskResultEvent =
        when (
            val result =
                m_testingService.test(task.language, task.version, task.content, task.inputs, task.expectedOutputs)
        ) {
            is LanguageResult.Success ->
                result(
                    task,
                    statusOf(result.value.error),
                    passed = result.value.passed,
                    outputs = result.value.actualOutputs,
                    errors = listOfNotNull(result.value.error),
                )
            is LanguageResult.Failure -> failure(task, result.error)
        }

    /** Code that does not lex/parse/type-check is an invalid source; a runtime error is still a processed task. */
    private fun statusOf(error: Diagnostic?): TaskStatus =
        if (error != null && error.rule in COMPILE_RULES) TaskStatus.INVALID_SOURCE else TaskStatus.OK

    private fun failure(
        task: TaskEvent,
        error: DomainError,
    ): TaskResultEvent =
        when (error) {
            is DomainError.InvalidSource ->
                result(task, TaskStatus.INVALID_SOURCE, errors = error.diagnostics, errorMessage = error.message)
            else -> result(task, TaskStatus.FAILED, errorMessage = error.message)
        }

    @Suppress("LongParameterList")
    private fun result(
        task: TaskEvent,
        status: TaskStatus,
        outputs: List<String> = emptyList(),
        formattedContent: String? = null,
        errors: List<Diagnostic> = emptyList(),
        passed: Boolean? = null,
        errorMessage: String? = null,
    ) = TaskResultEvent(
        taskId = task.taskId,
        snippetId = task.snippetId,
        type = task.type,
        status = status,
        contentHash = task.contentHash,
        outputs = outputs,
        formattedContent = formattedContent,
        errors = errors,
        passed = passed,
        errorMessage = errorMessage,
    )

    private companion object {
        val COMPILE_RULES = setOf("lexer", "parser", "semantic")
    }
}
