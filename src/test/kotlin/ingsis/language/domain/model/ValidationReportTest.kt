package ingsis.language.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ValidationReportTest {
    @Test
    fun `diagnostic defaults severity to ERROR`() {
        val diagnostic = Diagnostic(message = "Missing semicolon", line = 10, column = 5)
        assertEquals("Missing semicolon", diagnostic.message)
        assertEquals(10, diagnostic.line)
        assertEquals(5, diagnostic.column)
        assertEquals(DiagnosticSeverity.ERROR, diagnostic.severity)
    }

    @Test
    fun `validation report stores diagnostics and validity correctly`() {
        val diagnostic =
            Diagnostic(
                message = "Identifier should be camelCase",
                line = 2,
                column = 1,
                severity = DiagnosticSeverity.WARNING,
            )
        val report = ValidationReport(isValid = false, diagnostics = listOf(diagnostic))

        assertFalse(report.isValid)
        assertEquals(1, report.diagnostics.size)
        assertEquals(DiagnosticSeverity.WARNING, report.diagnostics.first().severity)
    }

    @Test
    fun `valid report has empty diagnostics by default`() {
        val report = ValidationReport(isValid = true)
        assertTrue(report.isValid)
        assertTrue(report.diagnostics.isEmpty())
    }
}
