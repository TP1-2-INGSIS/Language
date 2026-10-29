package ingsis.language.services

import ingsis.language.domain.engine.CodeExecutor
import ingsis.language.domain.engine.CodeFormatter
import ingsis.language.domain.engine.CodeLinter
import ingsis.language.domain.engine.CodeValidator
import ingsis.language.domain.model.Diagnostic
import ingsis.language.domain.model.DomainError
import ingsis.language.domain.model.ExecutionReport
import ingsis.language.domain.model.Language
import ingsis.language.domain.model.LanguageResult
import ingsis.language.domain.model.ValidationReport
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito

class LanguageServiceTest {
    private val registry = Mockito.mock(LanguageRegistry::class.java)
    private val service = LanguageService(registry)

    @Test
    fun `getAllLanguages delegates to registry and returns success`() {
        val dummyLanguages = listOf(Language(name = "test-lang", versions = listOf("0.1", "1.0")))
        Mockito.`when`(registry.getAllLanguages()).thenReturn(dummyLanguages)

        val result = service.getAllLanguages()

        assertTrue(result.isSuccess)
        assertEquals(dummyLanguages, result.getOrNull())
    }

    @Test
    fun `validate delegates to validator and returns report`() {
        val validator = Mockito.mock(CodeValidator::class.java)
        val expectedReport =
            ValidationReport(
                isValid = false,
                diagnostics = listOf(Diagnostic("UNEXPECTED_TOKEN", "Missing ;", 1, 10)),
            )

        Mockito
            .`when`(registry.getValidator("printscript", "1.1"))
            .thenReturn(LanguageResult.Success(validator))
        Mockito
            .`when`(validator.validate("let a = 1", "1.1"))
            .thenReturn(LanguageResult.Success(expectedReport))

        val result = service.validate("printscript", "1.1", "let a = 1")

        assertTrue(result.isSuccess)
        assertEquals(expectedReport, result.getOrNull())
    }

    @Test
    fun `validate fails when registry returns failure`() {
        Mockito
            .`when`(registry.getValidator("unknown", "1.0"))
            .thenReturn(LanguageResult.Failure(DomainError.LanguageNotFound("unknown")))

        val result = service.validate("unknown", "1.0", "let a = 1")

        assertTrue(result.isFailure)
        assertTrue(result.errorOrNull() is DomainError.LanguageNotFound)
    }

    @Test
    fun `format delegates to formatter and returns formatted string`() {
        val formatter = Mockito.mock(CodeFormatter::class.java)

        Mockito
            .`when`(registry.getFormatter("printscript", "1.1"))
            .thenReturn(LanguageResult.Success(formatter))
        Mockito
            .`when`(formatter.format("let a=1;", "1.1", "{}"))
            .thenReturn(LanguageResult.Success("let a = 1;\n"))

        val result = service.format("printscript", "1.1", "let a=1;", "{}")

        assertTrue(result.isSuccess)
        assertEquals("let a = 1;\n", result.getOrNull())
    }

    @Test
    fun `format fails when registry returns failure`() {
        Mockito
            .`when`(registry.getFormatter("unknown", "1.0"))
            .thenReturn(LanguageResult.Failure(DomainError.LanguageNotFound("unknown")))

        val result = service.format("unknown", "1.0", "let a=1;", "{}")

        assertTrue(result.isFailure)
        assertTrue(result.errorOrNull() is DomainError.LanguageNotFound)
    }

    @Test
    fun `lint delegates to linter and returns validation report`() {
        val linter = Mockito.mock(CodeLinter::class.java)
        val expectedReport =
            ValidationReport(
                isValid = false,
                diagnostics = listOf(Diagnostic("CASE_RULE", "Not camelCase", 1, 5)),
            )

        Mockito
            .`when`(registry.getLinter("printscript", "1.1"))
            .thenReturn(LanguageResult.Success(linter))
        Mockito
            .`when`(linter.lint("let my_var = 1;", "1.1", "{}"))
            .thenReturn(LanguageResult.Success(expectedReport))

        val result = service.lint("printscript", "1.1", "let my_var = 1;", "{}")

        assertTrue(result.isSuccess)
        assertEquals(expectedReport, result.getOrNull())
    }

    @Test
    fun `lint fails when registry returns failure`() {
        Mockito
            .`when`(registry.getLinter("unknown", "1.0"))
            .thenReturn(LanguageResult.Failure(DomainError.LanguageNotFound("unknown")))

        val result = service.lint("unknown", "1.0", "let my_var = 1;", "{}")

        assertTrue(result.isFailure)
        assertTrue(result.errorOrNull() is DomainError.LanguageNotFound)
    }

    @Test
    fun `execute delegates to executor and returns execution report`() {
        val executor = Mockito.mock(CodeExecutor::class.java)
        val expectedReport = ExecutionReport(outputs = listOf("Ana"))

        Mockito
            .`when`(registry.getExecutor("printscript", "1.1"))
            .thenReturn(LanguageResult.Success(executor))
        Mockito
            .`when`(executor.execute("println(\"Ana\");", "1.1", emptyList()))
            .thenReturn(LanguageResult.Success(expectedReport))

        val result = service.execute("printscript", "1.1", "println(\"Ana\");", emptyList())

        assertTrue(result.isSuccess)
        assertEquals(expectedReport, result.getOrNull())
    }

    @Test
    fun `execute fails when registry returns failure`() {
        Mockito
            .`when`(registry.getExecutor("unknown", "1.0"))
            .thenReturn(LanguageResult.Failure(DomainError.LanguageNotFound("unknown")))

        val result = service.execute("unknown", "1.0", "println(\"Ana\");", emptyList())

        assertTrue(result.isFailure)
        assertTrue(result.errorOrNull() is DomainError.LanguageNotFound)
    }

    @Test
    fun `test returns passed = true when execution succeeds and outputs match expectedOutputs`() {
        val executor = Mockito.mock(CodeExecutor::class.java)
        val executionReport = ExecutionReport(outputs = listOf("Hola Ana"))

        Mockito
            .`when`(registry.getExecutor("printscript", "1.1"))
            .thenReturn(LanguageResult.Success(executor))
        Mockito
            .`when`(executor.execute("println(\"Hola \" + n);", "1.1", listOf("Ana")))
            .thenReturn(LanguageResult.Success(executionReport))

        val result = service.test("printscript", "1.1", "println(\"Hola \" + n);", listOf("Ana"), listOf("Hola Ana"))

        assertTrue(result.isSuccess)
        val report = result.getOrNull()!!
        assertTrue(report.passed)
        assertEquals(listOf("Hola Ana"), report.expectedOutputs)
        assertEquals(listOf("Hola Ana"), report.actualOutputs)
        assertNull(report.error)
    }

    @Test
    fun `test returns passed = false when execution succeeds but outputs do not match expectedOutputs`() {
        val executor = Mockito.mock(CodeExecutor::class.java)
        val executionReport = ExecutionReport(outputs = listOf("Hola  Ana"))

        Mockito
            .`when`(registry.getExecutor("printscript", "1.1"))
            .thenReturn(LanguageResult.Success(executor))
        Mockito
            .`when`(executor.execute("println(\"Hola \" + n);", "1.1", listOf("Ana")))
            .thenReturn(LanguageResult.Success(executionReport))

        val result = service.test("printscript", "1.1", "println(\"Hola \" + n);", listOf("Ana"), listOf("Hola Ana"))

        assertTrue(result.isSuccess)
        val report = result.getOrNull()!!
        assertFalse(report.passed)
        assertEquals(listOf("Hola Ana"), report.expectedOutputs)
        assertEquals(listOf("Hola  Ana"), report.actualOutputs)
        assertNull(report.error)
    }

    @Test
    fun `test returns passed = false when execution has error`() {
        val executor = Mockito.mock(CodeExecutor::class.java)
        val diagnostic = Diagnostic("INPUTS_EXHAUSTED", "Input requested but none left", 1, 17)
        val executionReport = ExecutionReport(outputs = emptyList(), error = diagnostic)

        Mockito
            .`when`(registry.getExecutor("printscript", "1.1"))
            .thenReturn(LanguageResult.Success(executor))
        Mockito
            .`when`(executor.execute("readInput();", "1.1", emptyList()))
            .thenReturn(LanguageResult.Success(executionReport))

        val result = service.test("printscript", "1.1", "readInput();", emptyList(), listOf("Ana"))

        assertTrue(result.isSuccess)
        val report = result.getOrNull()!!
        assertFalse(report.passed)
        assertEquals(listOf("Ana"), report.expectedOutputs)
        assertEquals(emptyList<String>(), report.actualOutputs)
        assertEquals(diagnostic, report.error)
    }

    @Test
    fun `test fails when registry returns failure`() {
        Mockito
            .`when`(registry.getExecutor("unknown", "1.0"))
            .thenReturn(LanguageResult.Failure(DomainError.LanguageNotFound("unknown")))

        val result = service.test("unknown", "1.0", "readInput();", emptyList(), listOf("Ana"))

        assertTrue(result.isFailure)
        assertTrue(result.errorOrNull() is DomainError.LanguageNotFound)
    }
}
