package ingsis.language.websocket

import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.socket.config.annotation.EnableWebSocket
import org.springframework.web.socket.config.annotation.WebSocketConfigurer
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean

/** Interactive execution endpoint. Active whenever the web API is (APP_MODE web or all). */
@Configuration
@EnableWebSocket
@ConditionalOnExpression("!'\${ingsis.app.mode:all}'.equalsIgnoreCase('worker')")
class ExecutionSocketConfig(
    private val m_handler: ExecutionWebSocketHandler,
) : WebSocketConfigurer {
    override fun registerWebSocketHandlers(registry: WebSocketHandlerRegistry) {
        registry.addHandler(m_handler, "/languages/{language}/run").setAllowedOriginPatterns("*")
    }

    @Bean
    fun webSocketContainer(): ServletServerContainerFactoryBean =
        ServletServerContainerFactoryBean().apply { setMaxTextMessageBufferSize(MAX_MESSAGE_BYTES) }

    private companion object {
        const val MAX_MESSAGE_BYTES = 1024 * 1024
    }
}
