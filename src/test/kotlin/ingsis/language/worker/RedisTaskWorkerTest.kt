package ingsis.language.worker

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito
import org.springframework.data.redis.connection.stream.ReadOffset
import org.springframework.data.redis.core.StreamOperations
import org.springframework.data.redis.core.StringRedisTemplate
import tools.jackson.databind.json.JsonMapper

class RedisTaskWorkerTest {
    private val redisTemplate = Mockito.mock(StringRedisTemplate::class.java)

    @Suppress("UNCHECKED_CAST")
    private val streamOps = Mockito.mock(StreamOperations::class.java) as StreamOperations<String, String, String>
    private val taskProcessor = Mockito.mock(TaskProcessor::class.java)
    private val objectMapper = JsonMapper.builder().findAndAddModules().build()

    @BeforeEach
    fun setUp() {
        Mockito.`when`(redisTemplate.opsForStream<String, String>()).thenReturn(streamOps)
    }

    @Test
    fun `start does nothing when appMode is web`() {
        val worker = createWorker(appMode = "web")

        worker.start()

        assertFalse(worker.isRunning)
    }

    @Test
    fun `initConsumerGroup calls createGroup with offset 0`() {
        val worker = createWorker(appMode = "worker")

        worker.initConsumerGroup()

        Mockito.verify(streamOps).createGroup(eq("tasks:test"), eq(ReadOffset.from("0")), eq("group-1"))
    }

    @Test
    fun `initConsumerGroup handles exception gracefully when group exists`() {
        Mockito
            .`when`(streamOps.createGroup(eq("tasks:test"), eq(ReadOffset.from("0")), eq("group-1")))
            .thenThrow(RuntimeException("BUSYGROUP Consumer Group name already exists"))

        val worker = createWorker(appMode = "worker")

        worker.initConsumerGroup()
    }

    @Test
    fun `startWorkerLoop and stopWorkerLoop control running state`() {
        val worker = createWorker(appMode = "worker")

        worker.startWorkerLoop()
        assertTrue(worker.isRunning)

        worker.stopWorkerLoop()
        assertFalse(worker.isRunning)
        worker.stop()
    }

    private fun createWorker(appMode: String): RedisTaskWorker =
        RedisTaskWorker(
            m_redisTemplate = redisTemplate,
            m_taskProcessor = taskProcessor,
            m_objectMapper = objectMapper,
            m_appMode = appMode,
            m_tasksStream = "tasks:test",
            m_resultsStream = "results:test",
            m_consumerGroup = "group-1",
            m_consumerName = "worker-1",
        )

    @Test
    fun `consumer name defaults to the hostname when blank and honors the override`() {
        val blank = RedisTaskWorker(redisTemplate, taskProcessor, objectMapper, "worker", "t", "r", "g", "")
        val named = RedisTaskWorker(redisTemplate, taskProcessor, objectMapper, "worker", "t", "r", "g", "mine")

        assertTrue(blank.consumerName.isNotBlank())
        assertEquals("mine", named.consumerName)
    }
}
