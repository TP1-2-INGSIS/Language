package ingsis.language.domain.engine.printscript

import cnc.common.ContentManager
import cnc.common.ErrorType
import cnc.common.Failure
import cnc.common.Result
import cnc.language.InterpreterIO
import cnc.language.LanguageFacade
import cnc.printscript.PrintScriptVersion
import cnc.token.Token
import cnc.token.TokenType
import ingsis.language.domain.model.Diagnostic
import ingsis.language.domain.model.ExecutionIO
import ingsis.language.domain.model.ExecutionReport
import ingsis.language.domain.model.ValidationReport
import cnc.printscript.PrintScriptEngine as CncEngine

/** [PrintScriptRunner] backed by the CNC engine (published to GitHub Packages). */
class CncPrintScriptRunner : PrintScriptRunner {
    override fun validate(
        code: String,
        version: String,
    ): ValidationReport {
        val failure = guarded { facade(version).validate(ContentManager.of(code)) }
        return if (failure == null) ValidationReport(true) else ValidationReport(false, listOf(failure))
    }

    override fun format(
        code: String,
        version: String,
        config: String,
    ): String {
        val passes = RuleConfigMapper.formatPasses(config)
        val facade = facade(version)
        return passes.fold(code) { current, pass -> facade.format(ContentManager.of(current), pass) }
    }

    override fun lint(
        code: String,
        version: String,
        config: String,
    ): ValidationReport {
        val cncConfig = RuleConfigMapper.lintConfig(config)
        val facade = facade(version)
        val warnings = facade.lint(ContentManager.of(code), cncConfig)
        val diagnostics = LintDiagnostics(facade.lex(ContentManager.of(code)).toList()).toDiagnostics(warnings)
        return ValidationReport(diagnostics.isEmpty(), diagnostics)
    }

    override fun execute(
        code: String,
        version: String,
        inputs: List<String>,
        env: Map<String, String>,
    ): ExecutionReport {
        val outputs = mutableListOf<String>()
        val pending = ArrayDeque(inputs)
        val io =
            ExecutionIO(
                input = { prompt ->
                    pending.removeFirstOrNull() ?: error("No hay más entradas para readInput(\"$prompt\")")
                },
                env = env::get,
                output = { outputs.add(it) },
            )
        val started = System.nanoTime()
        val error = run(code, version, io)
        val elapsedMs = (System.nanoTime() - started) / NANOS_PER_MILLI
        return ExecutionReport(outputs = outputs, error = error, executionTimeMs = elapsedMs)
    }

    override fun run(
        code: String,
        version: String,
        io: ExecutionIO,
    ): Diagnostic? {
        val cncIo = InterpreterIO(input = io.input, envProvider = io.env, output = io.output)
        return guarded { facade(version).execute(ContentManager.of(code), cncIo) }
    }

    private fun facade(version: String): LanguageFacade = CncEngine.from(PrintScriptVersion.from(version))

    /** Runs a CNC operation, turning a failed result or an exception into a [Diagnostic]. */
    private fun guarded(block: () -> Result<*>): Diagnostic? =
        try {
            (block() as? Failure<*>)?.toDiagnostic()
        } catch (e: Exception) {
            Diagnostic(rule = ErrorType.RUNTIME.rule(), message = e.message ?: e.toString(), line = 1, column = 1)
        }

    private companion object {
        const val NANOS_PER_MILLI = 1_000_000L
    }
}

private val POSITION = Regex("""row (\d+), col (\d+)""")

/** CNC positions are 0-based; diagnostics are 1-based. Falls back to 1:1 when the message has no position. */
private fun positionIn(message: String): Pair<Int, Int> {
    val match = POSITION.find(message) ?: return 1 to 1
    return match.groupValues[1].toInt() + 1 to match.groupValues[2].toInt() + 1
}

private fun ErrorType.rule(): String = name.lowercase()

internal fun Failure<*>.toDiagnostic(): Diagnostic {
    val (line, column) = positionIn(msg)
    return Diagnostic(rule = type.rule(), message = msg, line = line, column = column)
}

/**
 * CNC lint warnings are plain strings without position. The rule is recovered from the message
 * and the position by scanning the token stream for the offending construct.
 */
internal class LintDiagnostics(
    private val tokens: List<Token>,
) {
    private val usedDeclarations = mutableSetOf<Int>()

    fun toDiagnostics(warnings: List<String>): List<Diagnostic> {
        val callQueues =
            mapOf(
                "println" to ArrayDeque(complexCalls("println")),
                "readInput" to ArrayDeque(complexCalls("readInput")),
            )
        return warnings.map { warning ->
            when {
                warning.startsWith(SYNTAX_PREFIX) -> syntax(warning)
                warning.startsWith("La variable '") -> naming(warning)
                warning.contains("'println'") -> call(warning, RuleConfigMapper.PRINTLN_RULE, callQueues.getValue("println"))
                warning.contains("'readInput'") -> call(warning, RuleConfigMapper.READ_INPUT_RULE, callQueues.getValue("readInput"))
                else -> Diagnostic("lint", warning, 1, 1)
            }
        }
    }

    private fun syntax(warning: String): Diagnostic {
        val (line, column) = positionIn(warning)
        return Diagnostic("syntax", warning.removePrefix(SYNTAX_PREFIX), line, column)
    }

    private fun naming(warning: String): Diagnostic {
        val name = Regex("""'(.*?)'""").find(warning)?.groupValues?.get(1)
        val index =
            tokens.indices.firstOrNull { i ->
                i > 0 &&
                    i !in usedDeclarations &&
                    tokens[i].text == name &&
                    tokens[i - 1].type == TokenType.KEYWORD
            }
        index?.let { usedDeclarations.add(it) }
        val pos = index?.let { tokens[it].pos }
        return Diagnostic(RuleConfigMapper.IDENTIFIER_FORMAT, warning, (pos?.row ?: 0) + 1, (pos?.col ?: 0) + 1)
    }

    private fun call(
        warning: String,
        rule: String,
        queue: ArrayDeque<Token>,
    ): Diagnostic {
        val pos = queue.removeFirstOrNull()?.pos
        return Diagnostic(rule, warning, (pos?.row ?: 0) + 1, (pos?.col ?: 0) + 1)
    }

    /** First token of every argument of [function] calls that is not a lone identifier or literal. */
    private fun complexCalls(function: String): List<Token> {
        val result = mutableListOf<Token>()
        for (i in tokens.indices) {
            if (tokens[i].text != function || tokens.getOrNull(i + 1)?.text != "(") continue
            var depth = 0
            val argument = mutableListOf<Token>()
            var j = i + 1
            while (j < tokens.size) {
                val token = tokens[j]
                if (token.text == "(") depth++
                if (token.text == ")") depth--
                if (depth == 0) break
                if (j > i + 1) argument.add(token)
                j++
            }
            val simple = argument.size == 1 && argument[0].type !in NON_VALUE_TOKENS
            if (argument.isNotEmpty() && !simple) result.add(argument[0])
        }
        return result
    }

    private companion object {
        const val SYNTAX_PREFIX = "Syntax error: "
        val NON_VALUE_TOKENS = setOf(TokenType.OPERATOR, TokenType.SYMBOL, TokenType.INVALID)
    }
}
