package ingsis.language.domain.engine.printscript

import ingsis.language.domain.model.InvalidRulesException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CncPrintScriptRunnerTest {
    private val runner = CncPrintScriptRunner()
    private val allLintRules =
        """{"identifierFormat":"camel case","mandatory-variable-or-literal-in-println":true,""" +
            """"mandatory-variable-or-literal-in-readInput":true}"""

    @Test
    fun `lint reports rule line and column of each violation`() {
        val code = "let my_var: number = 1;\nprintln(my_var + 1);\nlet x: string = readInput(\"a\" + \"b\");"

        val diagnostics = runner.lint(code, "1.1", allLintRules).diagnostics

        assertEquals(
            listOf(
                Triple("identifierFormat", 1, 5),
                Triple("mandatory-variable-or-literal-in-println", 2, 9),
                Triple("mandatory-variable-or-literal-in-readInput", 3, 27),
            ),
            diagnostics.map { Triple(it.rule, it.line, it.column) },
        )
    }

    @Test
    fun `lint accepts clean code and ignores unknown rules`() {
        val report = runner.lint("let myVar: number = 1;\nprintln(myVar);", "1.1", """{"unknown":true,"x":null}""")

        assertTrue(report.isValid)
    }

    @Test
    fun `lint reports syntax errors with position`() {
        val report = runner.lint("let a: number = ;", "1.0", "{}")

        assertFalse(report.isValid)
        assertEquals("syntax", report.diagnostics.single().rule)
        assertEquals(1, report.diagnostics.single().line)
        assertEquals(17, report.diagnostics.single().column)
    }

    @Test
    fun `lint and format reject rules that are not a json object`() {
        assertThrows(InvalidRulesException::class.java) { runner.lint("let a: number = 1;", "1.1", "[1]") }
        assertThrows(InvalidRulesException::class.java) { runner.format("let a: number = 1;", "1.1", "{nope") }
    }

    @Test
    fun `format applies the rules Snippets sends, including several structure rules at once`() {
        val rules =
            """{"enforce-spacing-before-colon-in-declaration":false,"enforce-spacing-after-colon-in-declaration":true,""" +
                """"enforce-spacing-around-equals":true,"line-breaks-after-println":1,"indent-inside-braces":4,""" +
                """"if-brace-same-line":true}"""

        val formatted = runner.format("let a:number=1;\nprintln(a);\nif(true)\n{\nprintln(a);\n}\n", "1.1", rules)

        assertEquals("let a: number = 1;\nprintln(a);\n\nif(true) {\n    println(a);\n}", formatted)
    }

    @Test
    fun `format without active rules returns the source`() {
        assertEquals("let a:number=1;", runner.format("let a:number=1;", "1.1", "{}"))
        assertEquals("let a:number=1;", runner.format("let a:number=1;", "1.1", ""))
    }

    @Test
    fun `execute stops with a diagnostic when there are not enough inputs`() {
        val report = runner.execute("println(\"hi\");\nlet a: string = readInput(\"x\");\nprintln(a);", "1.1", emptyList())

        assertEquals(listOf("hi"), report.outputs)
        assertFalse(report.isSuccess)
        assertEquals("runtime", report.error!!.rule)
    }

    @Test
    fun `execute reports runtime and semantic errors`() {
        val runtime = runner.execute("println(1 / 0);", "1.1", emptyList())
        assertEquals("runtime", runtime.error!!.rule)

        val semantic = runner.execute("println(b);", "1.1", emptyList())
        assertEquals("semantic", semantic.error!!.rule)
    }

    @Test
    fun `execute consumes inputs in order and reads env from the map`() {
        val code =
            "let a: string = readInput(\"a\");\nlet b: string = readInput(\"b\");\nprintln(b + a);\nprintln(readEnv(\"X\"));"

        val report = runner.execute(code, "1.1", listOf("1", "2"), mapOf("X" to "env"))

        assertEquals(listOf("21", "env"), report.outputs)
    }

    @Test
    fun `execute fails for an undefined env variable`() {
        val report = runner.execute("println(readEnv(\"NOPE_NOT_DEFINED\"));", "1.1", emptyList())

        assertFalse(report.isSuccess)
    }

    @Test
    fun `an unsupported version is reported as a diagnostic by validate`() {
        val report = runner.validate("println(1);", "9.9")

        assertFalse(report.isValid)
    }

    /**
     * [LintDiagnostics] recovers the rule from the text of the plain-string warnings CNC returns. If a CNC
     * bump rewords them, this test fails here instead of silently degrading every diagnostic to `lint` 1:1.
     */
    @Test
    fun `CNC lint warning prefixes that LintDiagnostics relies on are unchanged`() {
        val facade = cnc.printscript.PrintScriptEngine.from(cnc.printscript.PrintScriptVersion.from("1.1"))
        val config = RuleConfigMapper.lintConfig(allLintRules)

        fun warnings(code: String) = facade.lint(cnc.common.ContentManager.of(code), config)

        assertTrue(warnings("let a: number = ;").single().startsWith("Syntax error: "), "syntax prefix")
        assertTrue(warnings("let my_var: number = 1;").single().startsWith("La variable '"), "naming prefix")
        assertTrue(warnings("println(1 + 2);").single().contains("'println'"), "println marker")
        assertTrue(warnings("let a: string = readInput(\"a\" + \"b\");").single().contains("'readInput'"), "readInput marker")
        assertTrue(Regex("""row (\d+), col (\d+)""").containsMatchIn(warnings("let a: number = ;").single()), "position format")
    }
}
