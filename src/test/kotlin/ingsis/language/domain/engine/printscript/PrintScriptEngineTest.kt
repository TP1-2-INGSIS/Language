package ingsis.language.domain.engine.printscript

import ingsis.language.domain.model.Diagnostic
import ingsis.language.domain.model.DomainError
import ingsis.language.domain.model.ExecutionReport
import ingsis.language.domain.model.InvalidRulesException
import ingsis.language.domain.model.ValidationReport
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito

class PrintScriptEngineTest {
    private val runner = Mockito.mock(PrintScriptRunner::class.java)
    private val engine = PrintScriptEngine(runner)

    @Test
    fun `metadata returns printscript and supported versions`() {
        assertEquals("printscript", engine.name)
        assertEquals(listOf("1.0", "1.1"), engine.versions)
    }

    @Test
    fun `validate delegates to runner`() {
        val expected = ValidationReport(isValid = false, diagnostics = listOf(Diagnostic("ERR", "Msg", 1, 1)))
        Mockito.`when`(runner.validate("code", "1.1")).thenReturn(expected)

        val result = engine.validate("code", "1.1")

        assertTrue(result.isSuccess)
        assertEquals(expected, result.getOrNull())
    }

    @Test
    fun `format delegates to runner`() {
        Mockito.`when`(runner.validate("code", "1.1")).thenReturn(ValidationReport(true))
        Mockito.`when`(runner.format("code", "1.1", "{}")).thenReturn("formatted code")

        val result = engine.format("code", "1.1", "{}")

        assertTrue(result.isSuccess)
        assertEquals("formatted code", result.getOrNull())
    }

    @Test
    fun `format of invalid code is an InvalidSource failure with the diagnostics and never formats`() {
        val diagnostics = listOf(Diagnostic("parser", "bad", 1, 1))
        Mockito.`when`(runner.validate("code", "1.1")).thenReturn(ValidationReport(false, diagnostics))

        val error = engine.format("code", "1.1", "{}").errorOrNull()

        assertEquals(DomainError.InvalidSource("El código fuente no es válido", diagnostics), error)
        Mockito.verify(runner, Mockito.never()).format("code", "1.1", "{}")
    }

    @Test
    fun `lint delegates to runner`() {
        val expected = ValidationReport(isValid = true, diagnostics = emptyList())
        Mockito.`when`(runner.lint("code", "1.1", "{}")).thenReturn(expected)

        val result = engine.lint("code", "1.1", "{}")

        assertTrue(result.isSuccess)
        assertEquals(expected, result.getOrNull())
    }

    @Test
    fun `execute delegates to runner`() {
        val expected = ExecutionReport(outputs = listOf("out"))
        Mockito.`when`(runner.execute("code", "1.1", listOf("in"))).thenReturn(expected)

        val result = engine.execute("code", "1.1", listOf("in"))

        assertTrue(result.isSuccess)
        assertEquals(expected, result.getOrNull())
    }

    @Test
    fun `invalid rules become an InvalidRules failure`() {
        Mockito.`when`(runner.validate("code", "1.1")).thenReturn(ValidationReport(true))
        Mockito.`when`(runner.format("code", "1.1", "[]")).thenThrow(InvalidRulesException("bad"))
        Mockito.`when`(runner.lint("code", "1.1", "[]")).thenThrow(InvalidRulesException("bad"))

        assertEquals(DomainError.InvalidRules("bad"), engine.format("code", "1.1", "[]").errorOrNull())
        assertEquals(DomainError.InvalidRules("bad"), engine.lint("code", "1.1", "[]").errorOrNull())
    }

    @Test
    fun `engine exposes its version`() {
        assertEquals("2.0.0", PrintScriptEngine(runner, "2.0.0").engineVersion)
    }
}
