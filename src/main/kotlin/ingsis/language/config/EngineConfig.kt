package ingsis.language.config

import ingsis.language.domain.engine.printscript.DefaultPrintScriptRunner
import ingsis.language.domain.engine.printscript.PrintScriptEngine
import ingsis.language.domain.engine.printscript.PrintScriptRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class EngineConfig {
    @Bean
    fun printScriptRunner(): PrintScriptRunner = DefaultPrintScriptRunner()

    @Bean
    fun printScriptEngine(runner: PrintScriptRunner): PrintScriptEngine = PrintScriptEngine(runner)
}
