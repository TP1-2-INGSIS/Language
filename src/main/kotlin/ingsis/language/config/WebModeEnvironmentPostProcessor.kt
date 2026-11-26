package ingsis.language.config

import org.springframework.boot.EnvironmentPostProcessor
import org.springframework.boot.SpringApplication
import org.springframework.core.Ordered
import org.springframework.core.env.ConfigurableEnvironment
import org.springframework.core.env.MapPropertySource

class WebModeEnvironmentPostProcessor :
    EnvironmentPostProcessor,
    Ordered {
    override fun postProcessEnvironment(
        environment: ConfigurableEnvironment,
        application: SpringApplication,
    ) {
        val appMode = environment.getProperty("ingsis.app.mode")
        if (appMode.equals("web", ignoreCase = true)) {
            environment.propertySources.addFirst(
                MapPropertySource(
                    "webModeDefaults",
                    mapOf("management.health.redis.enabled" to false),
                ),
            )
        }
    }

    override fun getOrder(): Int = Ordered.LOWEST_PRECEDENCE
}
