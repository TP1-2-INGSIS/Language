package ingsis.language.websocket

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.test.context.ActiveProfiles
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.client.standard.StandardWebSocketClient
import org.springframework.web.socket.handler.TextWebSocketHandler
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = [
        "ingsis.execution.max-duration-seconds=1",
        "ingsis.execution.start-timeout-seconds=1",
        "ingsis.execution.input-timeout-seconds=30",
    ],
)
@ActiveProfiles("test")
class ExecutionLimitsWebSocketTest {
    @LocalServerPort
    private var port: Int = 0

    private class Probe : TextWebSocketHandler() {
        val messages = LinkedBlockingQueue<String>()
        val closed = LinkedBlockingQueue<CloseStatus>()

        override fun handleTextMessage(
            session: WebSocketSession,
            message: TextMessage,
        ) {
            messages.add(message.payload)
        }

        override fun afterConnectionClosed(
            session: WebSocketSession,
            status: CloseStatus,
        ) {
            closed.add(status)
        }
    }

    private fun connect(probe: Probe): WebSocketSession =
        StandardWebSocketClient()
            .execute(probe, "ws://localhost:$port/languages/printscript/run")
            .get(5, TimeUnit.SECONDS)

    @Test
    fun `total execution limit closes with internal error code`() {
        val probe = Probe()
        val session = connect(probe)
        session.sendMessage(
            TextMessage("""{"type":"start","content":"println(readInput(\"x\"));","version":"1.1"}"""),
        )
        assertEquals(true, probe.messages.poll(5, TimeUnit.SECONDS)!!.contains("input-request"))
        assertEquals(true, probe.messages.poll(5, TimeUnit.SECONDS)!!.contains("\"error\""))
        assertEquals(1011, probe.closed.poll(5, TimeUnit.SECONDS)!!.code)
    }

    @Test
    fun `missing start closes with policy violation`() {
        val probe = Probe()
        connect(probe)
        assertEquals(true, probe.messages.poll(5, TimeUnit.SECONDS)!!.contains("\"error\""))
        assertEquals(1008, probe.closed.poll(5, TimeUnit.SECONDS)!!.code)
    }
}
