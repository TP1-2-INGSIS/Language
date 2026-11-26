package ingsis.language.websocket

import ingsis.language.domain.engine.InteractiveExecutor
import ingsis.language.domain.model.Diagnostic
import ingsis.language.domain.model.ExecutionIO
import ingsis.language.domain.model.LanguageResult
import ingsis.language.services.LanguageRegistry
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.stereotype.Component
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator
import org.springframework.web.socket.handler.TextWebSocketHandler
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.SynchronousQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Interactive execution over WebSocket (`/languages/{language}/run`).
 *
 * Protocol: the client sends `start`, then optionally `input` / `stop`; the server sends `output`,
 * `input-request`, `finished` and `error`. Each session runs on its own thread taken from a bounded pool.
 */
@Suppress("TooGenericExceptionCaught") // session boundary: any failure must reach the client as an error message
@Component
@ConditionalOnExpression("!'\${ingsis.app.mode:all}'.equalsIgnoreCase('worker')")
class ExecutionWebSocketHandler(
    private val m_registry: LanguageRegistry,
    private val m_mapper: ObjectMapper,
    @Value("\${ingsis.execution.input-timeout-seconds:60}") private val m_inputTimeoutSeconds: Long,
    @Value("\${ingsis.execution.max-duration-seconds:300}") private val m_maxDurationSeconds: Long,
    @Value("\${ingsis.execution.max-sessions:8}") maxSessions: Int,
) : TextWebSocketHandler() {
    private val log = LoggerFactory.getLogger(ExecutionWebSocketHandler::class.java)
    private val sessions = ConcurrentHashMap<String, RunSession>()
    private val pool =
        ThreadPoolExecutor(maxSessions, maxSessions, IDLE_SECONDS, TimeUnit.SECONDS, SynchronousQueue()) { task ->
            Thread(task, "ws-execution").apply { isDaemon = true }
        }
    private val watchdog =
        Executors.newSingleThreadScheduledExecutor { task ->
            Thread(task, "ws-execution-watchdog").apply { isDaemon = true }
        }

    /** Number of execution threads currently busy. */
    fun activeExecutions(): Int = pool.activeCount

    override fun afterConnectionEstablished(session: WebSocketSession) {
        val decorated = ConcurrentWebSocketSessionDecorator(session, SEND_TIME_LIMIT_MS, SEND_BUFFER_LIMIT)
        sessions[session.id] =
            RunSession(
                decorated,
                session.uri
                    ?.path
                    ?.let(::languageOf)
                    .orEmpty(),
            )
    }

    override fun handleTextMessage(
        session: WebSocketSession,
        message: TextMessage,
    ) {
        val run = sessions[session.id] ?: return
        val node =
            try {
                m_mapper.readTree(message.payload)
            } catch (_: Exception) {
                return run.fail("Mensaje inválido: se esperaba JSON")
            }
        when (node.path("type").asString("")) {
            "start" -> start(run, node)
            "input" -> run.inputs.offer(node.path("value").asString(""))
            "stop" -> run.finish(null)
            else -> run.fail("Tipo de mensaje no soportado")
        }
    }

    override fun afterConnectionClosed(
        session: WebSocketSession,
        status: CloseStatus,
    ) {
        sessions.remove(session.id)?.finish(null)
    }

    override fun handleTransportError(
        session: WebSocketSession,
        exception: Throwable,
    ) {
        sessions.remove(session.id)?.finish(null)
    }

    private fun start(
        run: RunSession,
        node: JsonNode,
    ) {
        if (!run.started.compareAndSet(false, true)) return run.fail("La sesión ya fue iniciada")
        val content = node.path("content").asString("")
        val version = node.path("version").asString("")
        val env = node.path("env").properties().associate { it.key to it.value.asString("") }
        val executor =
            when (val found = m_registry.getInteractiveExecutor(run.language, version)) {
                is LanguageResult.Success -> found.value
                is LanguageResult.Failure -> return run.fail(found.error.message)
            }
        try {
            run.future = pool.submit { execute(run, executor, content, version, env) }
        } catch (_: RejectedExecutionException) {
            run.fail("busy")
        }
    }

    private fun execute(
        run: RunSession,
        executor: InteractiveExecutor,
        content: String,
        version: String,
        env: Map<String, String>,
    ) {
        run.thread = Thread.currentThread()
        val watchdog =
            watchdog.schedule(
                { run.fail("Tiempo máximo de ejecución excedido ($m_maxDurationSeconds s)") },
                m_maxDurationSeconds,
                TimeUnit.SECONDS,
            )
        try {
            val syntaxError = executor.firstSourceError(content, version)
            if (syntaxError != null) return run.fail(syntaxError)
            val io =
                ExecutionIO(
                    input = { prompt -> requestInput(run, prompt) },
                    env = env::get,
                    output = { value -> run.send(mapOf("type" to "output", "value" to value)) },
                )
            val error = executor.run(content, version, io)
            if (error != null) run.fail(error) else run.finish(mapOf("type" to "finished"))
        } catch (e: Exception) {
            log.warn("Interactive execution failed", e)
            run.fail(e.message ?: e.toString())
        } finally {
            watchdog.cancel(false)
            run.thread = null
            Thread.interrupted()
        }
    }

    private fun requestInput(
        run: RunSession,
        prompt: String,
    ): String {
        run.send(mapOf("type" to "input-request", "prompt" to prompt))
        val value = run.inputs.poll(m_inputTimeoutSeconds, TimeUnit.SECONDS)
        if (value == null) {
            run.fail("Tiempo de espera de input agotado ($m_inputTimeoutSeconds s)")
            error("Tiempo de espera de input agotado")
        }
        return value
    }

    private fun languageOf(path: String): String = path.removeSuffix("/").removeSuffix("/run").substringAfterLast('/')

    /** State of one connected client. Terminal messages are sent exactly once. */
    private inner class RunSession(
        private val socket: WebSocketSession,
        val language: String,
    ) {
        val inputs = LinkedBlockingQueue<String>()
        val started = AtomicBoolean(false)
        private val done = AtomicBoolean(false)

        @Volatile var thread: Thread? = null

        @Volatile var future: Future<*>? = null

        fun send(payload: Map<String, Any?>) {
            if (socket.isOpen) socket.sendMessage(TextMessage(m_mapper.writeValueAsString(payload)))
        }

        fun fail(diagnostic: Diagnostic) = fail(diagnostic.message, diagnostic.line, diagnostic.column)

        fun fail(
            message: String,
            line: Int? = null,
            column: Int? = null,
        ) = finish(
            buildMap {
                put("type", "error")
                put("message", message)
                line?.let { put("line", it) }
                column?.let { put("column", it) }
            },
        )

        /** Sends the terminal [message] (if any), closes the socket and stops the execution thread. Idempotent. */
        fun finish(message: Map<String, Any?>?) {
            if (!done.compareAndSet(false, true)) return
            try {
                message?.let(::send)
                if (socket.isOpen) socket.close(CloseStatus.NORMAL)
            } catch (e: Exception) {
                log.debug("Could not close session cleanly", e)
            }
            sessions.values.remove(this)
            future?.cancel(true)
            thread?.takeIf { it !== Thread.currentThread() }?.interrupt()
        }
    }

    private companion object {
        const val IDLE_SECONDS = 30L
        const val SEND_TIME_LIMIT_MS = 10_000
        const val SEND_BUFFER_LIMIT = 512 * 1024
    }
}
