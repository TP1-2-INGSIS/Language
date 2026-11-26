package ingsis.language.websocket

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.test.context.ActiveProfiles
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.client.standard.StandardWebSocketClient
import org.springframework.web.socket.handler.TextWebSocketHandler
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = ["ingsis.execution.input-timeout-seconds=1", "ingsis.execution.max-sessions=1"],
)
@ActiveProfiles("test")
class ExecutionWebSocketTest {
    @LocalServerPort
    private var port: Int = 0

    @Autowired
    private lateinit var mapper: ObjectMapper

    @Autowired
    private lateinit var handler: ExecutionWebSocketHandler

    private inner class Client(
        language: String = "printscript",
    ) {
        val messages = LinkedBlockingQueue<JsonNode>()
        val closed = LinkedBlockingQueue<CloseStatus>()
        val session: WebSocketSession =
            StandardWebSocketClient()
                .execute(
                    object : TextWebSocketHandler() {
                        override fun handleTextMessage(
                            session: WebSocketSession,
                            message: TextMessage,
                        ) {
                            messages.add(mapper.readTree(message.payload))
                        }

                        override fun afterConnectionClosed(
                            session: WebSocketSession,
                            status: CloseStatus,
                        ) {
                            closed.add(status)
                        }
                    },
                    "ws://localhost:$port/languages/$language/run",
                ).get(5, TimeUnit.SECONDS)

        fun send(payload: Map<String, Any?>) = session.sendMessage(TextMessage(mapper.writeValueAsString(payload)))

        fun start(
            content: String,
            version: String = "1.1",
        ) = send(mapOf("type" to "start", "content" to content, "version" to version))

        fun next(): JsonNode = messages.poll(5, TimeUnit.SECONDS) ?: error("no message received")

        fun awaitClosed(): CloseStatus = closed.poll(5, TimeUnit.SECONDS) ?: error("session not closed")
    }

    @Test
    fun `println only program streams outputs then finishes`() {
        val client = Client()
        client.start("println(\"a\");\nprintln(\"b\");", "1.0")
        assertEquals("output", client.next()["type"].asString())
        assertEquals("b", client.next()["value"].asString())
        assertEquals("finished", client.next()["type"].asString())
        assertEquals(CloseStatus.NORMAL.code, client.awaitClosed().code)
    }

    @Test
    fun `read input requests input then continues`() {
        val client = Client()
        client.start("let name: string = readInput(\"Name?\");\nprintln(\"Hi \" + name);")
        val request = client.next()
        assertEquals("input-request", request["type"].asString())
        assertEquals("Name?", request["prompt"].asString())
        client.send(mapOf("type" to "input", "value" to "Bob"))
        val output = client.next()
        assertEquals("output", output["type"].asString())
        assertEquals("Hi Bob", output["value"].asString())
        assertEquals("finished", client.next()["type"].asString())
        assertEquals(CloseStatus.NORMAL.code, client.awaitClosed().code)
    }

    @Test
    fun `env values are available to the program`() {
        val client = Client()
        client.send(
            mapOf(
                "type" to "start",
                "content" to "println(readEnv(\"GREETING\"));",
                "version" to "1.1",
                "env" to mapOf("GREETING" to "hola"),
            ),
        )
        assertEquals("hola", client.next()["value"].asString())
        assertEquals("finished", client.next()["type"].asString())
    }

    @Test
    fun `syntax error is reported with position and closes`() {
        val client = Client()
        client.start("println(\"a\")\nprintln(\"b\");")
        val error = client.next()
        assertEquals("error", error["type"].asString())
        assertTrue(error["line"].asInt() >= 1)
        assertTrue(error["column"].asInt() >= 1)
        client.awaitClosed()
    }

    @Test
    fun `input timeout sends error and closes`() {
        val client = Client()
        client.start("println(readInput(\"x\"));")
        assertEquals("input-request", client.next()["type"].asString())
        val error = client.next()
        assertEquals("error", error["type"].asString())
        client.awaitClosed()
        awaitIdle()
    }

    @Test
    fun `stop closes the session and releases the thread`() {
        val client = Client()
        client.start("println(readInput(\"x\"));")
        assertEquals("input-request", client.next()["type"].asString())
        assertEquals(1, handler.activeExecutions())
        client.send(mapOf("type" to "stop"))
        client.awaitClosed()
        awaitIdle()
    }

    @Test
    fun `client disconnect releases the thread`() {
        val client = Client()
        client.start("println(readInput(\"x\"));")
        assertEquals("input-request", client.next()["type"].asString())
        client.session.close()
        awaitIdle()
    }

    @Test
    fun `unknown language is an error`() {
        val client = Client("cobol")
        client.start("println(1);")
        assertEquals("error", client.next()["type"].asString())
        client.awaitClosed()
    }

    @Test
    fun `unknown version is an error`() {
        val client = Client()
        client.start("println(1);", "9.9")
        assertEquals("error", client.next()["type"].asString())
        client.awaitClosed()
    }

    @Test
    fun `invalid first message is an error`() {
        val client = Client()
        client.session.sendMessage(TextMessage("not json"))
        assertEquals("error", client.next()["type"].asString())
        client.awaitClosed()
    }

    @Test
    fun `unsupported message type is an error`() {
        val client = Client()
        client.send(mapOf("type" to "bogus"))
        assertEquals("error", client.next()["type"].asString())
        client.awaitClosed()
    }

    @Test
    fun `starting twice is an error`() {
        val client = Client()
        client.start("println(readInput(\"x\"));")
        assertEquals("input-request", client.next()["type"].asString())
        client.start("println(1);")
        assertEquals("error", client.next()["type"].asString())
        client.awaitClosed()
        awaitIdle()
    }

    @Test
    fun `second concurrent session is rejected as busy`() {
        val first = Client()
        first.start("println(readInput(\"x\"));")
        assertEquals("input-request", first.next()["type"].asString())
        val second = Client()
        second.start("println(1);")
        assertEquals("busy", second.next()["message"].asString())
        second.awaitClosed()
        first.send(mapOf("type" to "stop"))
        awaitIdle()
    }

    private fun awaitIdle() {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (handler.activeExecutions() > 0 && System.nanoTime() < deadline) Thread.sleep(20)
        assertEquals(0, handler.activeExecutions())
    }
}
