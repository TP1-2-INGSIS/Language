package ingsis.language.config

import ingsis.language.domain.engine.printscript.CncPrintScriptRunner
import ingsis.language.domain.engine.printscript.PrintScriptEngine
import ingsis.language.domain.engine.printscript.PrintScriptRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.util.Properties

@Configuration
class EngineConfig {
    @Bean
    fun printScriptRunner(): PrintScriptRunner = CncPrintScriptRunner()

    @Bean
    fun printScriptEngine(runner: PrintScriptRunner): PrintScriptEngine = PrintScriptEngine(runner, loadEngineVersion())

    private fun loadEngineVersion(): String {
        val properties = Properties()
        EngineConfig::class.java.getResourceAsStream("/engine.properties")?.use(properties::load)
        return properties.getProperty("printscript.engine-version", "unknown")
    }
}
