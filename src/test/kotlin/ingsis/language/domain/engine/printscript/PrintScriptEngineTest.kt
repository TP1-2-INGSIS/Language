package ingsis.language.domain.engine.printscript

import ingsis.language.domain.model.Diagnostic
import ingsis.language.domain.model.ExecutionReport
import ingsis.language.domain.model.ValidationReport
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
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
        Mockito.`when`(runner.format("code", "1.1", "{}")).thenReturn("formatted code")

        val result = engine.format("code", "1.1", "{}")

        assertTrue(result.isSuccess)
        assertEquals("formatted code", result.getOrNull())
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
    fun `DefaultPrintScriptRunner provides sensible defaults`() {
        val defaultRunner = DefaultPrintScriptRunner()

        val valReport = defaultRunner.validate("code", "1.1")
        assertTrue(valReport.isValid)
        assertTrue(valReport.diagnostics.isEmpty())

        assertEquals("code", defaultRunner.format("code", "1.1", "{}"))

        val lintReport = defaultRunner.lint("code", "1.1", "{}")
        assertTrue(lintReport.isValid)

        val execReport = defaultRunner.execute("code", "1.1", emptyList())
        assertTrue(execReport.outputs.isEmpty())
        assertNull(execReport.error)
    }
}
