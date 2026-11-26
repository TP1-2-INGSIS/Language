package ingsis.language.worker

import ingsis.language.worker.events.TaskEvent
import ingsis.language.worker.events.TaskResultEvent
import ingsis.language.worker.events.TaskType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito
import org.springframework.data.redis.connection.stream.Consumer
import org.springframework.data.redis.connection.stream.ReadOffset
import org.springframework.data.redis.connection.stream.RecordId
import org.springframework.data.redis.connection.stream.StreamOffset
import org.springframework.data.redis.connection.stream.StreamReadOptions
import org.springframework.data.redis.connection.stream.StreamRecords
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
    fun `pollAndProcess returns 0 when no messages`() {
        Mockito
            .`when`(
                streamOps.read(
                    any(Consumer::class.java),
                    any(StreamReadOptions::class.java),
                    any<StreamOffset<String>>(),
                ),
            ).thenReturn(emptyList())

        val worker = createWorker(appMode = "worker")

        val processed = worker.pollAndProcess(batchSize = 5, blockMs = 100)

        assertEquals(0, processed)
    }

    @Test
    fun `pollAndProcess processes message, writes result and sends acknowledge`() {
        val task = TaskEvent(taskId = "task-100", snippetId = "snip-1", type = TaskType.VALIDATE, language = "printscript", version = "1.1")
        val taskJson = objectMapper.writeValueAsString(task)
        val result = TaskResultEvent(taskId = "task-100", snippetId = "snip-1", type = TaskType.VALIDATE, success = true)

        val recordId = RecordId.of("1-0")
        val message = StreamRecords.string(mapOf("data" to taskJson)).withStreamKey("tasks:test").withId(recordId)
        Mockito
            .`when`(
                streamOps.read(
                    any(Consumer::class.java),
                    any(StreamReadOptions::class.java),
                    any<StreamOffset<String>>(),
                ),
            ).thenReturn(listOf(message))
        Mockito
            .`when`(taskProcessor.process(task))
            .thenReturn(result)

        val worker = createWorker(appMode = "worker")

        val processed = worker.pollAndProcess(batchSize = 5, blockMs = 100)

        assertEquals(1, processed)
        Mockito.verify(streamOps).add(eq("results:test"), eq(mapOf("data" to objectMapper.writeValueAsString(result))))
        Mockito.verify(streamOps).acknowledge(eq("tasks:test"), eq("group-1"), eq(recordId))
    }

    @Test
    fun `pollAndProcess still sends acknowledge on malformed message`() {
        val recordId = RecordId.of("2-0")
        val message = StreamRecords.string(mapOf("data" to "{ invalid json")).withStreamKey("tasks:test").withId(recordId)
        Mockito
            .`when`(
                streamOps.read(
                    any(Consumer::class.java),
                    any(StreamReadOptions::class.java),
                    any<StreamOffset<String>>(),
                ),
            ).thenReturn(listOf(message))

        val worker = createWorker(appMode = "worker")

        val processed = worker.pollAndProcess(batchSize = 5, blockMs = 100)

        assertEquals(1, processed)
        Mockito.verify(streamOps).acknowledge(eq("tasks:test"), eq("group-1"), eq(recordId))
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
}
