package ingsis.language.worker

import ingsis.language.domain.engine.printscript.CncPrintScriptRunner
import ingsis.language.domain.engine.printscript.PrintScriptEngine
import ingsis.language.services.ExecutionService
import ingsis.language.services.LanguageRegistry
import ingsis.language.services.LanguageService
import ingsis.language.services.TestingService
import ingsis.language.worker.events.TaskEvent
import ingsis.language.worker.events.TaskType
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.mockito.Mockito
import org.springframework.data.domain.Range
import org.springframework.data.redis.connection.RedisStandaloneConfiguration
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import org.springframework.data.redis.connection.stream.Consumer
import org.springframework.data.redis.connection.stream.ReadOffset
import org.springframework.data.redis.connection.stream.StreamOffset
import org.springframework.data.redis.connection.stream.StreamReadOptions
import org.springframework.data.redis.connection.stream.StreamRecords
import org.springframework.data.redis.core.StringRedisTemplate
import org.testcontainers.containers.GenericContainer
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import java.time.Duration
import java.util.UUID

/** Exercises [RedisTaskWorker] against a real Redis: retries, poison messages, crash recovery and reclaim. */
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RedisTaskWorkerRedisTest {
    private val redis = GenericContainer(DockerImageName.parse("redis:7-alpine")).withExposedPorts(REDIS_PORT)
    private val objectMapper = JsonMapper.builder().findAndAddModules().build()
    private lateinit var factory: LettuceConnectionFactory
    private lateinit var template: StringRedisTemplate
    private lateinit var realProcessor: TaskProcessor

    private val streamOps get() = template.opsForStream<String, String>()

    @BeforeAll
    fun startRedis() {
        redis.start()
        factory = LettuceConnectionFactory(RedisStandaloneConfiguration(redis.host, redis.getMappedPort(REDIS_PORT)))
        factory.afterPropertiesSet()
        template = StringRedisTemplate(factory)
        template.afterPropertiesSet()

        val registry = LanguageRegistry(listOf(PrintScriptEngine(CncPrintScriptRunner())))
        val execution = ExecutionService(registry)
        realProcessor = TaskProcessor(LanguageService(registry, execution, TestingService(execution)), execution, TestingService(execution))
    }

    @AfterAll
    fun stopRedis() {
        factory.destroy()
        redis.stop()
    }

    private inner class Env(
        val processor: TaskProcessor = realProcessor,
    ) {
        val tasks = "tasks:${UUID.randomUUID()}"
        val results = "results:${UUID.randomUUID()}"
        val group = "group"

        fun worker(
            consumer: String = "worker-1",
            maxAttempts: Int = 3,
            claimIdleMs: Long = 60_000,
            processor: TaskProcessor = this.processor,
        ) = RedisTaskWorker(
            m_redisTemplate = template,
            m_taskProcessor = processor,
            m_objectMapper = objectMapper,
            m_appMode = "worker",
            m_tasksStream = tasks,
            m_resultsStream = results,
            m_consumerGroup = group,
            m_consumerName = consumer,
            m_maxAttempts = maxAttempts,
            m_claimIdleMs = claimIdleMs,
        ).also { it.initConsumerGroup() }

        fun enqueue(task: TaskEvent) {
            streamOps.add(StreamRecords.string(mapOf("data" to objectMapper.writeValueAsString(task))).withStreamKey(tasks))
        }

        fun results(): List<JsonNode> =
            streamOps.range(results, Range.unbounded<String>()).orEmpty().map { objectMapper.readTree(it.value["data"]) }

        fun pendingCount(): Long = streamOps.pending(tasks, group).totalPendingMessages
    }

    private fun lintTask(
        content: String,
        hash: String? = "hash-1",
    ) = TaskEvent(
        taskId = "t-1",
        snippetId = "s-1",
        type = TaskType.LINT,
        language = "printscript",
        version = "1.1",
        content = content,
        rules = """{"identifierFormat":"camel case"}""",
        contentHash = hash,
    )

    @Test
    fun `lint happy path publishes OK with the violations, echoes contentHash and acks`() {
        val env = Env()
        val worker = env.worker()
        env.enqueue(lintTask("let my_var: number = 1;"))

        worker.pollAndProcess(blockMs = 500)

        val result = env.results().single()
        assertEquals("OK", result["status"].asString())
        assertTrue(result["success"].asBoolean())
        assertEquals("hash-1", result["contentHash"].asString())
        assertEquals(1, result["attempts"].asInt())
        assertEquals("identifierFormat", result["errors"][0]["rule"].asString())
        assertEquals(0, env.pendingCount())
    }

    @Test
    fun `lint of a compliant snippet is OK with no errors`() {
        val env = Env()
        env.enqueue(lintTask("let myVar: number = 1;"))

        env.worker().pollAndProcess(blockMs = 500)

        val result = env.results().single()
        assertEquals("OK", result["status"].asString())
        assertEquals(0, result["errors"].size())
    }

    @Test
    fun `format happy path publishes the formatted content`() {
        val env = Env()
        env.enqueue(
            lintTask("let a:number=1;").copy(type = TaskType.FORMAT, rules = """{"enforce-spacing-around-equals":true}"""),
        )

        env.worker().pollAndProcess(blockMs = 500)

        val result = env.results().single()
        assertEquals("OK", result["status"].asString())
        assertEquals("hash-1", result["contentHash"].asString())
        assertTrue(result["formattedContent"].asString().contains("a:number = 1"))
        assertEquals(0, env.pendingCount())
    }

    @Test
    fun `format of invalid code publishes INVALID_SOURCE with diagnostics and acks`() {
        val env = Env()
        env.enqueue(lintTask("let a: number = ;").copy(type = TaskType.FORMAT, rules = "{}"))

        env.worker().pollAndProcess(blockMs = 500)

        val result = env.results().single()
        assertEquals("INVALID_SOURCE", result["status"].asString())
        assertEquals(false, result["success"].asBoolean())
        assertTrue(result["errors"].size() > 0)
        assertEquals(0, env.pendingCount())
    }

    @Test
    fun `an unsupported language is a non retryable FAILED published at the first attempt`() {
        val env = Env()
        env.enqueue(lintTask("let a = 1;").copy(language = "cobol"))

        env.worker().pollAndProcess(blockMs = 500)

        val result = env.results().single()
        assertEquals("FAILED", result["status"].asString())
        assertEquals(1, result["attempts"].asInt())
        assertTrue(result["errorMessage"].asString().contains("cobol"))
        assertEquals(0, env.pendingCount())
    }

    @Test
    fun `an exception on the first attempt leaves the entry pending and the retry publishes a single result`() {
        val flaky = Mockito.mock(TaskProcessor::class.java)
        Mockito
            .`when`(flaky.process(anyTask()))
            .thenThrow(IllegalStateException("transient"))
            .thenAnswer { realProcessor.process(it.getArgument(0)) }
        val env = Env(flaky)
        val worker = env.worker()
        env.enqueue(lintTask("let myVar: number = 1;"))

        worker.pollAndProcess(blockMs = 500)

        assertEquals(0, env.results().size)
        assertEquals(1, env.pendingCount())

        worker.pollAndProcess(blockMs = 100)

        val result = env.results().single()
        assertEquals("OK", result["status"].asString())
        assertEquals(2, result["attempts"].asInt())
        assertEquals(0, env.pendingCount())
    }

    @Test
    fun `a restarted consumer with the same name recovers its own pending entries before new ones`() {
        val env = Env()
        env.worker("worker-1")
        env.enqueue(lintTask("let myVar: number = 1;"))
        // "crash": the entry is delivered to worker-1 but never acknowledged.
        streamOps.read(
            Consumer.from(env.group, "worker-1"),
            StreamReadOptions.empty().count(1),
            StreamOffset.create(env.tasks, ReadOffset.lastConsumed()),
        )
        assertEquals(1, env.pendingCount())

        val restarted = env.worker("worker-1")
        env.enqueue(lintTask("let otherVar: number = 2;").copy(taskId = "t-2"))
        restarted.pollAndProcess(blockMs = 500)

        assertEquals(listOf("t-1", "t-2"), env.results().map { it["taskId"].asString() })
        assertEquals(0, env.pendingCount())
    }

    @Test
    fun `a poison message is published as FAILED after the max attempts and acked`() {
        val poison = Mockito.mock(TaskProcessor::class.java)
        Mockito.`when`(poison.process(anyTask())).thenThrow(IllegalStateException("kaboom"))
        val env = Env(poison)
        val worker = env.worker(maxAttempts = 3)
        env.enqueue(lintTask("let a: number = 1;"))

        worker.pollAndProcess(blockMs = 500)
        worker.pollAndProcess(blockMs = 100)
        assertEquals(0, env.results().size)
        assertEquals(1, env.pendingCount())
        worker.pollAndProcess(blockMs = 100)

        val result = env.results().single()
        assertEquals("FAILED", result["status"].asString())
        assertEquals(3, result["attempts"].asInt())
        assertEquals("hash-1", result["contentHash"].asString())
        assertTrue(result["errorMessage"].asString().contains("kaboom"))
        assertEquals(0, env.pendingCount())

        worker.pollAndProcess(blockMs = 100)
        assertEquals(1, env.results().size)
    }

    @Test
    fun `an entry left pending by a dead consumer is reclaimed and processed`() {
        val env = Env()
        env.enqueue(lintTask("let myVar: number = 1;"))
        env.worker("dead-worker")
        streamOps.read(
            Consumer.from(env.group, "dead-worker"),
            StreamReadOptions.empty().count(1),
            StreamOffset.create(env.tasks, ReadOffset.lastConsumed()),
        )
        val survivor = env.worker("survivor", claimIdleMs = 500)

        assertEquals(0, survivor.reclaimIdle(), "an entry that is not idle yet must not be taken")
        val deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos()
        var reclaimed = 0
        while (reclaimed == 0 && System.nanoTime() < deadline) {
            reclaimed = survivor.reclaimIdle()
            if (reclaimed == 0) Thread.sleep(POLL_MS)
        }
        assertEquals(1, reclaimed)

        val result = env.results().single()
        assertEquals("OK", result["status"].asString())
        assertEquals(2, result["attempts"].asInt())
        assertEquals(0, env.pendingCount())
    }

    @Test
    fun `results stream is trimmed approximately to the max length`() {
        val env = Env()
        val total = RedisTaskWorker.RESULTS_MAX_LEN.toInt() + 2_000
        val worker = env.worker()
        repeat(total) { env.enqueue(lintTask("let myVar: number = 1;").copy(taskId = "t-$it")) }

        while (worker.pollAndProcess(batchSize = 500, blockMs = 100) > 0) {
            // drain
        }

        val size = streamOps.size(env.results)
        assertTrue(size in 1 until total, "expected a trimmed stream but got $size of $total")
    }

    private fun enqueueRaw(
        env: Env,
        data: String,
    ) {
        streamOps.add(StreamRecords.string(mapOf("data" to data)).withStreamKey(env.tasks))
    }

    @Test
    fun `an entry already past the max deliveries is published as FAILED without processing`() {
        val processor = Mockito.mock(TaskProcessor::class.java)
        val env = Env(processor)
        val worker = env.worker(maxAttempts = 3)
        env.enqueue(lintTask("let a: number = 1;"))
        // Delivered to this consumer and re-delivered from its history until the counter exceeds the max.
        repeat(5) {
            streamOps.read(
                Consumer.from(env.group, "worker-1"),
                StreamReadOptions.empty().count(1),
                StreamOffset.create(env.tasks, if (it == 0) ReadOffset.lastConsumed() else ReadOffset.from("0")),
            )
        }

        worker.pollAndProcess(blockMs = 100)

        val result = env.results().single()
        assertEquals("FAILED", result["status"].asString())
        assertTrue(result["attempts"].asInt() > 3)
        Mockito.verifyNoInteractions(processor)
        assertEquals(0, env.pendingCount())
    }

    @Test
    fun `an unreadable payload is acked without publishing a result`() {
        val env = Env()
        val worker = env.worker()
        enqueueRaw(env, "{ not json")

        worker.pollAndProcess(blockMs = 500)

        assertEquals(0, env.results().size)
        assertEquals(0, env.pendingCount())
    }

    @Test
    fun `a readable but invalid task with a taskId is published as FAILED and acked`() {
        val env = Env()
        val worker = env.worker()
        enqueueRaw(env, """{"taskId":"t-9","snippetId":"s-9","type":"BOGUS","contentHash":"h9"}""")

        worker.pollAndProcess(blockMs = 500)

        val result = env.results().single()
        assertEquals("FAILED", result["status"].asString())
        assertEquals("t-9", result["taskId"].asString())
        assertEquals("s-9", result["snippetId"].asString())
        assertEquals("h9", result["contentHash"].asString())
        assertTrue(result["errorMessage"].asString().contains("BOGUS"))
        assertEquals(0, env.pendingCount())
    }

    private fun anyTask(): TaskEvent = Mockito.any(TaskEvent::class.java) ?: TaskEvent()

    private companion object {
        const val REDIS_PORT = 6379
        const val POLL_MS = 50L
    }
}
