package ingsis.language.worker

import ingsis.language.domain.model.Diagnostic
import ingsis.language.domain.model.DomainError
import ingsis.language.domain.model.ExecutionReport
import ingsis.language.domain.model.LanguageResult
import ingsis.language.domain.model.TestReport
import ingsis.language.domain.model.ValidationReport
import ingsis.language.services.ExecutionService
import ingsis.language.services.LanguageService
import ingsis.language.services.TestingService
import ingsis.language.worker.events.TaskEvent
import ingsis.language.worker.events.TaskType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito

class TaskProcessorTest {
    private val languageService = Mockito.mock(LanguageService::class.java)
    private val executionService = Mockito.mock(ExecutionService::class.java)
    private val testingService = Mockito.mock(TestingService::class.java)
    private val processor = TaskProcessor(languageService, executionService, testingService)

    @Test
    fun `process VALIDATE success returns result with errors`() {
        val diagnostics = listOf(Diagnostic("ERR", "Msg", 1, 1))
        Mockito
            .`when`(languageService.validate("printscript", "1.1", "let a = 1"))
            .thenReturn(LanguageResult.Success(ValidationReport(isValid = false, diagnostics = diagnostics)))

        val task =
            TaskEvent(
                taskId = "t1",
                snippetId = "s1",
                type = TaskType.VALIDATE,
                language = "printscript",
                version = "1.1",
                content = "let a = 1",
            )
        val result = processor.process(task)

        assertEquals("t1", result.taskId)
        assertEquals("s1", result.snippetId)
        assertEquals(TaskType.VALIDATE, result.type)
        assertTrue(result.success)
        assertEquals(diagnostics, result.errors)
    }

    @Test
    fun `process VALIDATE failure returns failure result`() {
        Mockito
            .`when`(languageService.validate("unknown", "1.1", "let a = 1"))
            .thenReturn(LanguageResult.Failure(DomainError.LanguageNotFound("unknown")))

        val task =
            TaskEvent(
                taskId = "t1",
                snippetId = "s1",
                type = TaskType.VALIDATE,
                language = "unknown",
                version = "1.1",
                content = "let a = 1",
            )
        val result = processor.process(task)

        assertFalse(result.success)
        assertEquals("Lenguaje no soportado: unknown", result.errorMessage)
    }

    @Test
    fun `process FORMAT success returns formatted content`() {
        Mockito
            .`when`(languageService.format("printscript", "1.1", "let a=1;", "{}"))
            .thenReturn(LanguageResult.Success("let a = 1;\n"))

        val task =
            TaskEvent(
                taskId = "t2",
                snippetId = "s2",
                type = TaskType.FORMAT,
                language = "printscript",
                version = "1.1",
                content = "let a=1;",
                rules = "{}",
            )
        val result = processor.process(task)

        assertTrue(result.success)
        assertEquals("let a = 1;\n", result.formattedContent)
    }

    @Test
    fun `process FORMAT failure returns failure result`() {
        Mockito
            .`when`(languageService.format("unknown", "1.1", "let a=1;", "{}"))
            .thenReturn(LanguageResult.Failure(DomainError.LanguageNotFound("unknown")))

        val task =
            TaskEvent(
                taskId = "t2",
                snippetId = "s2",
                type = TaskType.FORMAT,
                language = "unknown",
                version = "1.1",
                content = "let a=1;",
                rules = "{}",
            )
        val result = processor.process(task)

        assertFalse(result.success)
        assertEquals("Lenguaje no soportado: unknown", result.errorMessage)
    }

    @Test
    fun `process LINT success returns validation report diagnostics`() {
        val diagnostics = listOf(Diagnostic("CASE", "Not camel", 1, 5))
        Mockito
            .`when`(languageService.lint("printscript", "1.1", "let my_var = 1;", "{}"))
            .thenReturn(LanguageResult.Success(ValidationReport(isValid = false, diagnostics = diagnostics)))

        val task =
            TaskEvent(
                taskId = "t3",
                snippetId = "s3",
                type = TaskType.LINT,
                language = "printscript",
                version = "1.1",
                content = "let my_var = 1;",
                rules = "{}",
            )
        val result = processor.process(task)

        assertTrue(result.success)
        assertEquals(diagnostics, result.errors)
    }

    @Test
    fun `process LINT failure returns failure result`() {
        Mockito
            .`when`(languageService.lint("unknown", "1.1", "let my_var = 1;", "{}"))
            .thenReturn(LanguageResult.Failure(DomainError.LanguageNotFound("unknown")))

        val task =
            TaskEvent(
                taskId = "t3",
                snippetId = "s3",
                type = TaskType.LINT,
                language = "unknown",
                version = "1.1",
                content = "let my_var = 1;",
                rules = "{}",
            )
        val result = processor.process(task)

        assertFalse(result.success)
        assertEquals("Lenguaje no soportado: unknown", result.errorMessage)
    }

    @Test
    fun `process EXECUTE success returns outputs`() {
        val report = ExecutionReport(outputs = listOf("Hello"))
        Mockito
            .`when`(executionService.execute("printscript", "1.1", "println(\"Hello\");", emptyList()))
            .thenReturn(LanguageResult.Success(report))

        val task =
            TaskEvent(
                taskId = "t4",
                snippetId = "s4",
                type = TaskType.EXECUTE,
                language = "printscript",
                version = "1.1",
                content = "println(\"Hello\");",
            )
        val result = processor.process(task)

        assertTrue(result.success)
        assertEquals(listOf("Hello"), result.outputs)
        assertTrue(result.errors.isEmpty())
    }

    @Test
    fun `process EXECUTE failure returns failure result`() {
        Mockito
            .`when`(executionService.execute("unknown", "1.1", "println(\"Hello\");", emptyList()))
            .thenReturn(LanguageResult.Failure(DomainError.LanguageNotFound("unknown")))

        val task =
            TaskEvent(
                taskId = "t4",
                snippetId = "s4",
                type = TaskType.EXECUTE,
                language = "unknown",
                version = "1.1",
                content = "println(\"Hello\");",
            )
        val result = processor.process(task)

        assertFalse(result.success)
        assertEquals("Lenguaje no soportado: unknown", result.errorMessage)
    }

    @Test
    fun `process TEST success returns passed status and outputs`() {
        val report = TestReport(passed = true, expectedOutputs = listOf("1"), actualOutputs = listOf("1"))
        Mockito
            .`when`(testingService.test("printscript", "1.1", "println(1);", emptyList(), listOf("1")))
            .thenReturn(LanguageResult.Success(report))

        val task =
            TaskEvent(
                taskId = "t5",
                snippetId = "s5",
                type = TaskType.TEST,
                language = "printscript",
                version = "1.1",
                content = "println(1);",
                expectedOutputs = listOf("1"),
            )
        val result = processor.process(task)

        assertTrue(result.success)
        assertEquals(true, result.passed)
        assertEquals(listOf("1"), result.outputs)
    }

    @Test
    fun `process TEST failure returns failure result`() {
        Mockito
            .`when`(testingService.test("unknown", "1.1", "println(1);", emptyList(), listOf("1")))
            .thenReturn(LanguageResult.Failure(DomainError.LanguageNotFound("unknown")))

        val task =
            TaskEvent(
                taskId = "t5",
                snippetId = "s5",
                type = TaskType.TEST,
                language = "unknown",
                version = "1.1",
                content = "println(1);",
                expectedOutputs = listOf("1"),
            )
        val result = processor.process(task)

        assertFalse(result.success)
        assertEquals("Lenguaje no soportado: unknown", result.errorMessage)
    }
}
