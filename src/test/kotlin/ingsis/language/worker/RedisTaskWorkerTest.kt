package ingsis.language.worker

import com.fasterxml.jackson.databind.ObjectMapper
import ingsis.language.worker.events.TaskEvent
import ingsis.language.worker.events.TaskResultEvent
import ingsis.language.worker.events.TaskType
import io.quarkus.redis.datasource.RedisDataSource
import io.quarkus.redis.datasource.stream.StreamCommands
import io.quarkus.redis.datasource.stream.StreamMessage
import io.quarkus.redis.datasource.stream.XGroupCreateArgs
import io.quarkus.redis.datasource.stream.XReadGroupArgs
import io.quarkus.runtime.ShutdownEvent
import io.quarkus.runtime.StartupEvent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito

class RedisTaskWorkerTest {
    private val redisDataSource = Mockito.mock(RedisDataSource::class.java)

    @Suppress("UNCHECKED_CAST")
    private val streamCommands = Mockito.mock(StreamCommands::class.java) as StreamCommands<String, String, String>
    private val taskProcessor = Mockito.mock(TaskProcessor::class.java)
    private val objectMapper = ObjectMapper()

    @BeforeEach
    fun setUp() {
        Mockito.`when`(redisDataSource.stream(String::class.java)).thenReturn(streamCommands)
    }

    @Test
    fun `onStart does nothing when appMode is web`() {
        val worker =
            RedisTaskWorker(
                m_redisDataSource = redisDataSource,
                m_taskProcessor = taskProcessor,
                m_objectMapper = objectMapper,
                m_appMode = "web",
                m_tasksStream = "tasks:test",
                m_resultsStream = "results:test",
                m_consumerGroup = "group-1",
                m_consumerName = "worker-1",
            )

        worker.onStart(StartupEvent())

        assertFalse(worker.isRunning())
    }

    @Test
    fun `initConsumerGroup calls xgroupCreate with mkstream`() {
        val worker =
            RedisTaskWorker(
                m_redisDataSource = redisDataSource,
                m_taskProcessor = taskProcessor,
                m_objectMapper = objectMapper,
                m_appMode = "worker",
                m_tasksStream = "tasks:test",
                m_resultsStream = "results:test",
                m_consumerGroup = "group-1",
                m_consumerName = "worker-1",
            )

        worker.initConsumerGroup()

        Mockito.verify(streamCommands).xgroupCreate(eq("tasks:test"), eq("group-1"), eq("0"), any(XGroupCreateArgs::class.java))
    }

    @Test
    fun `initConsumerGroup handles exception gracefully when group exists`() {
        Mockito
            .`when`(streamCommands.xgroupCreate(eq("tasks:test"), eq("group-1"), eq("0"), any(XGroupCreateArgs::class.java)))
            .thenThrow(RuntimeException("BUSYGROUP Consumer Group name already exists"))

        val worker =
            RedisTaskWorker(
                m_redisDataSource = redisDataSource,
                m_taskProcessor = taskProcessor,
                m_objectMapper = objectMapper,
                m_appMode = "worker",
                m_tasksStream = "tasks:test",
                m_resultsStream = "results:test",
                m_consumerGroup = "group-1",
                m_consumerName = "worker-1",
            )

        worker.initConsumerGroup()
        // No exception thrown
    }

    @Test
    fun `pollAndProcess returns 0 when no messages`() {
        Mockito
            .`when`(streamCommands.xreadgroup(eq("group-1"), eq("worker-1"), eq("tasks:test"), eq(">"), any(XReadGroupArgs::class.java)))
            .thenReturn(emptyList())

        val worker =
            RedisTaskWorker(
                m_redisDataSource = redisDataSource,
                m_taskProcessor = taskProcessor,
                m_objectMapper = objectMapper,
                m_appMode = "worker",
                m_tasksStream = "tasks:test",
                m_resultsStream = "results:test",
                m_consumerGroup = "group-1",
                m_consumerName = "worker-1",
            )

        val processed = worker.pollAndProcess(batchSize = 5, blockMs = 100)

        assertEquals(0, processed)
    }

    @Test
    fun `pollAndProcess processes message, writes result and sends xack`() {
        val task = TaskEvent(taskId = "task-100", snippetId = "snip-1", type = TaskType.VALIDATE, language = "printscript", version = "1.1")
        val taskJson = objectMapper.writeValueAsString(task)
        val result = TaskResultEvent(taskId = "task-100", snippetId = "snip-1", type = TaskType.VALIDATE, success = true)

        val message = StreamMessage("tasks:test", "msg-id-1", mapOf("data" to taskJson))
        Mockito
            .`when`(streamCommands.xreadgroup(eq("group-1"), eq("worker-1"), eq("tasks:test"), eq(">"), any(XReadGroupArgs::class.java)))
            .thenReturn(listOf(message))
        Mockito
            .`when`(taskProcessor.process(task))
            .thenReturn(result)

        val worker =
            RedisTaskWorker(
                m_redisDataSource = redisDataSource,
                m_taskProcessor = taskProcessor,
                m_objectMapper = objectMapper,
                m_appMode = "worker",
                m_tasksStream = "tasks:test",
                m_resultsStream = "results:test",
                m_consumerGroup = "group-1",
                m_consumerName = "worker-1",
            )

        val processed = worker.pollAndProcess(batchSize = 5, blockMs = 100)

        assertEquals(1, processed)
        Mockito.verify(streamCommands).xadd(eq("results:test"), eq(mapOf("data" to objectMapper.writeValueAsString(result))))
        Mockito.verify(streamCommands).xack(eq("tasks:test"), eq("group-1"), eq("msg-id-1"))
    }

    @Test
    fun `pollAndProcess still sends xack on malformed message`() {
        val message = StreamMessage("tasks:test", "msg-bad-1", mapOf("data" to "{ invalid json"))
        Mockito
            .`when`(streamCommands.xreadgroup(eq("group-1"), eq("worker-1"), eq("tasks:test"), eq(">"), any(XReadGroupArgs::class.java)))
            .thenReturn(listOf(message))

        val worker =
            RedisTaskWorker(
                m_redisDataSource = redisDataSource,
                m_taskProcessor = taskProcessor,
                m_objectMapper = objectMapper,
                m_appMode = "worker",
                m_tasksStream = "tasks:test",
                m_resultsStream = "results:test",
                m_consumerGroup = "group-1",
                m_consumerName = "worker-1",
            )

        val processed = worker.pollAndProcess(batchSize = 5, blockMs = 100)

        assertEquals(1, processed)
        Mockito.verify(streamCommands).xack(eq("tasks:test"), eq("group-1"), eq("msg-bad-1"))
    }

    @Test
    fun `startWorkerLoop and stopWorkerLoop control running state`() {
        val worker =
            RedisTaskWorker(
                m_redisDataSource = redisDataSource,
                m_taskProcessor = taskProcessor,
                m_objectMapper = objectMapper,
                m_appMode = "worker",
                m_tasksStream = "tasks:test",
                m_resultsStream = "results:test",
                m_consumerGroup = "group-1",
                m_consumerName = "worker-1",
            )

        worker.startWorkerLoop()
        assertTrue(worker.isRunning())

        worker.stopWorkerLoop()
        assertFalse(worker.isRunning())
        worker.onStop(ShutdownEvent())
    }
}
