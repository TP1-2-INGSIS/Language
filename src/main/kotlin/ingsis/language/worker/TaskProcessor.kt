package ingsis.language.worker

import ingsis.language.domain.model.LanguageResult
import ingsis.language.services.ExecutionService
import ingsis.language.services.LanguageService
import ingsis.language.services.TestingService
import ingsis.language.worker.events.TaskEvent
import ingsis.language.worker.events.TaskResultEvent
import ingsis.language.worker.events.TaskType
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
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
                TaskResultEvent(
                    taskId = task.taskId,
                    snippetId = task.snippetId,
                    type = task.type,
                    success = true,
                    errors = result.value.diagnostics,
                )
            is LanguageResult.Failure ->
                TaskResultEvent(
                    taskId = task.taskId,
                    snippetId = task.snippetId,
                    type = task.type,
                    success = false,
                    errorMessage = result.error.message,
                )
        }

    private fun handleFormat(task: TaskEvent): TaskResultEvent =
        when (val result = m_languageService.format(task.language, task.version, task.content, task.rules)) {
            is LanguageResult.Success ->
                TaskResultEvent(
                    taskId = task.taskId,
                    snippetId = task.snippetId,
                    type = task.type,
                    success = true,
                    formattedContent = result.value,
                )
            is LanguageResult.Failure ->
                TaskResultEvent(
                    taskId = task.taskId,
                    snippetId = task.snippetId,
                    type = task.type,
                    success = false,
                    errorMessage = result.error.message,
                )
        }

    private fun handleLint(task: TaskEvent): TaskResultEvent =
        when (val result = m_languageService.lint(task.language, task.version, task.content, task.rules)) {
            is LanguageResult.Success ->
                TaskResultEvent(
                    taskId = task.taskId,
                    snippetId = task.snippetId,
                    type = task.type,
                    success = true,
                    errors = result.value.diagnostics,
                )
            is LanguageResult.Failure ->
                TaskResultEvent(
                    taskId = task.taskId,
                    snippetId = task.snippetId,
                    type = task.type,
                    success = false,
                    errorMessage = result.error.message,
                )
        }

    private fun handleExecute(task: TaskEvent): TaskResultEvent =
        when (val result = m_executionService.execute(task.language, task.version, task.content, task.inputs)) {
            is LanguageResult.Success ->
                TaskResultEvent(
                    taskId = task.taskId,
                    snippetId = task.snippetId,
                    type = task.type,
                    success = result.value.error == null,
                    outputs = result.value.outputs,
                    errors = listOfNotNull(result.value.error),
                )
            is LanguageResult.Failure ->
                TaskResultEvent(
                    taskId = task.taskId,
                    snippetId = task.snippetId,
                    type = task.type,
                    success = false,
                    errorMessage = result.error.message,
                )
        }

    private fun handleTest(task: TaskEvent): TaskResultEvent =
        when (
            val result =
                m_testingService.test(
                    task.language,
                    task.version,
                    task.content,
                    task.inputs,
                    task.expectedOutputs,
                )
        ) {
            is LanguageResult.Success ->
                TaskResultEvent(
                    taskId = task.taskId,
                    snippetId = task.snippetId,
                    type = task.type,
                    success = true,
                    passed = result.value.passed,
                    outputs = result.value.actualOutputs,
                    errors = listOfNotNull(result.value.error),
                )
            is LanguageResult.Failure ->
                TaskResultEvent(
                    taskId = task.taskId,
                    snippetId = task.snippetId,
                    type = task.type,
                    success = false,
                    errorMessage = result.error.message,
                )
        }
}
