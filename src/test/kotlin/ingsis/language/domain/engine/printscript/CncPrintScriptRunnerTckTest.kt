package ingsis.language.domain.engine.printscript

import ingsis.language.domain.model.ExecutionIO
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.TestFactory
import java.io.File

/** Runs the PrintScript TCK cases (copied from printscript-tck-CNC) through [CncPrintScriptRunner]. */
class CncPrintScriptRunnerTckTest {
    private val runner = CncPrintScriptRunner()
    private val root = File(javaClass.getResource("/tck")!!.toURI())

    private fun cases(
        kind: String,
        version: String,
    ): List<File> = File(root, "$kind/$version").listFiles()!!.sortedBy { it.name }

    @TestFactory
    fun validation(): List<DynamicTest> =
        listOf("1.0", "1.1").flatMap { version ->
            cases("validation", version).map { file ->
                dynamicTest("validation $version ${file.name}") {
                    val report = runner.validate(file.readText(), version)
                    assertEquals(file.name.startsWith("valid"), report.isValid, report.diagnostics.toString())
                    if (!report.isValid) {
                        val d = report.diagnostics.single()
                        assertTrue(d.rule.isNotBlank() && d.message.isNotBlank() && d.line >= 1 && d.column >= 1)
                    }
                }
            }
        }

    @TestFactory
    fun linter(): List<DynamicTest> =
        listOf("1.0", "1.1").flatMap { version ->
            cases("linter", version).map { dir ->
                dynamicTest("linter $version ${dir.name}") {
                    val report = runner.lint(File(dir, "main.ps").readText(), version, File(dir, "config.json").readText())
                    assertEquals(dir.name.startsWith("valid"), report.isValid, report.diagnostics.toString())
                }
            }
        }

    @TestFactory
    fun formatter(): List<DynamicTest> =
        listOf("1.0", "1.1").flatMap { version ->
            // as in the TCK, 1.1 also runs the 1.0 formatter cases
            val dirs = if (version == "1.0") cases("formatter", "1.0") else cases("formatter", "1.0") + cases("formatter", "1.1")
            dirs.map { dir ->
                dynamicTest("formatter $version ${dir.name}") {
                    val formatted = runner.format(File(dir, "main.ps").readText(), version, File(dir, "config.json").readText())
                    assertEquals(golden(File(dir, "golden.ps")), formatted)
                }
            }
        }

    @TestFactory
    fun printStatements(): List<DynamicTest> =
        listOf("1.0", "1.1").flatMap { version ->
            cases("print-statement", version).map { dir ->
                dynamicTest("print-statement $version ${dir.name}") {
                    val inputFile = File(dir, "input.txt")
                    val inputs = if (inputFile.exists()) inputFile.readLines() else emptyList()
                    val expected = File(dir, "output.txt").readLines()

                    // readInput prompts are not part of `outputs`; the TCK expects them in the stream.
                    val stream = mutableListOf<String>()
                    val queue = ArrayDeque(inputs)
                    val error =
                        runner.run(
                            File(dir, "main.ps").readText(),
                            version,
                            ExecutionIO(
                                input = { prompt ->
                                    stream.add(prompt)
                                    queue.removeFirst()
                                },
                                env = { name -> if (name == "BEST_FOOTBALL_CLUB") "San Lorenzo" else null },
                                output = { stream.add(it) },
                            ),
                        )
                    assertNull(error)
                    assertEquals(expected, stream)

                    val report =
                        runner.execute(
                            File(dir, "main.ps").readText(),
                            version,
                            inputs,
                            mapOf(
                                "BEST_FOOTBALL_CLUB" to "San Lorenzo",
                            ),
                        )
                    assertNull(report.error)
                    assertEquals(expected.filterNot { it == "Name:" }, report.outputs)
                }
            }
        }

    @org.junit.jupiter.api.Test
    fun `invalid sources report rule, line and column`() {
        val report = runner.validate("let a: number = 1\nlet b: number = 2;", "1.0")
        assertFalse(report.isValid)
        assertEquals("parser", report.diagnostics.single().rule)
        assertEquals(2, report.diagnostics.single().line)
        assertEquals(1, report.diagnostics.single().column)
    }

    private fun golden(file: File): String = file.readLines().joinToString("\n")
}
