package ingsis.language.worker

import ingsis.language.worker.events.TaskEvent
import ingsis.language.worker.events.TaskResultEvent
import ingsis.language.worker.events.TaskStatus
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.SmartLifecycle
import org.springframework.data.domain.Range
import org.springframework.data.redis.connection.RedisStreamCommands.TrimOptions
import org.springframework.data.redis.connection.RedisStreamCommands.XAddOptions
import org.springframework.data.redis.connection.RedisStreamCommands.XClaimOptions
import org.springframework.data.redis.connection.stream.Consumer
import org.springframework.data.redis.connection.stream.MapRecord
import org.springframework.data.redis.connection.stream.ReadOffset
import org.springframework.data.redis.connection.stream.RecordId
import org.springframework.data.redis.connection.stream.StreamOffset
import org.springframework.data.redis.connection.stream.StreamReadOptions
import org.springframework.data.redis.connection.stream.StreamRecords
import org.springframework.data.redis.core.StreamOperations
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import tools.jackson.core.JacksonException
import tools.jackson.databind.ObjectMapper
import java.net.InetAddress
import java.net.UnknownHostException
import java.time.Duration
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Fault-tolerant consumer of the tasks stream.
 *
 * - Each iteration first drains this consumer's own pending entries (XREADGROUP id `0`: crash recovery and
 *   retries), periodically reclaims entries idle in dead consumers, and finally reads new ones (`>`).
 * - A task is acknowledged only after its result has been published. A retryable exception leaves the entry
 *   pending so it is delivered again.
 * - After [m_maxAttempts] deliveries a `FAILED` result is published and the entry is acknowledged, which skips
 *   the poison message.
 */
@Component
class RedisTaskWorker(
    private val m_redisTemplate: StringRedisTemplate,
    private val m_taskProcessor: TaskProcessor,
    private val m_objectMapper: ObjectMapper,
    @Value("\${ingsis.app.mode:all}")
    private val m_appMode: String,
    @Value("\${ingsis.redis.stream.tasks:tasks:printscript}")
    private val m_tasksStream: String,
    @Value("\${ingsis.redis.stream.results:tasks:results}")
    private val m_resultsStream: String,
    @Value("\${ingsis.redis.stream.group:language-workers}")
    private val m_consumerGroup: String,
    @Value("\${ingsis.redis.stream.consumer:}")
    private val m_consumerName: String = "",
    @Value("\${ingsis.redis.max-attempts:3}")
    private val m_maxAttempts: Int = DEFAULT_MAX_ATTEMPTS,
    @Value("\${ingsis.redis.claim-idle-ms:60000}")
    private val m_claimIdleMs: Long = DEFAULT_CLAIM_IDLE_MS,
) : SmartLifecycle {
    private val log = LoggerFactory.getLogger(RedisTaskWorker::class.java)
    private val running = AtomicBoolean(false)
    private var workerThread: Thread? = null
    private var lastReclaimAt = 0L
    private val reclaimIntervalMs = (m_claimIdleMs / 2).coerceIn(MIN_RECLAIM_INTERVAL_MS, MAX_RECLAIM_INTERVAL_MS)

    @Volatile
    private var lastRecovered = 0

    /** The configured name, or the hostname (unique per replica) when blank. */
    val consumerName: String = m_consumerName.ifBlank { hostName() }

    val streamOps: StreamOperations<String, String, String>
        get() = m_redisTemplate.opsForStream()

    override fun start() {
        if (m_appMode.equals("web", ignoreCase = true)) {
            log.info("App mode is '$m_appMode'. RedisTaskWorker is disabled.")
            return
        }
        log.info("Starting RedisTaskWorker '$consumerName' for stream '$m_tasksStream' in mode '$m_appMode'...")
        startWorkerLoop()
    }

    override fun stop() {
        stopWorkerLoop()
    }

    override fun isRunning(): Boolean = running.get()

    /** Creates the group (and the stream, MKSTREAM) if missing. */
    fun initConsumerGroup() {
        try {
            streamOps.createGroup(m_tasksStream, ReadOffset.from("0"), m_consumerGroup)
        } catch (e: Exception) {
            log.debug("Consumer group '$m_consumerGroup' already exists or stream ready: ${e.message}")
        }
    }

    /** One worker iteration: own pending entries, reclaimed entries, then new ones. Returns how many it handled. */
    fun pollAndProcess(
        batchSize: Int = 10,
        blockMs: Long = 1000,
    ): Int {
        lastRecovered = recoverOwnPending(batchSize)
        var handled = lastRecovered
        if (System.currentTimeMillis() - lastReclaimAt >= reclaimIntervalMs) {
            lastReclaimAt = System.currentTimeMillis()
            handled += reclaimIdle(batchSize)
        }
        val messages =
            streamOps.read(
                Consumer.from(m_consumerGroup, consumerName),
                StreamReadOptions.empty().count(batchSize.toLong()).block(Duration.ofMillis(blockMs)),
                StreamOffset.create(m_tasksStream, ReadOffset.lastConsumed()),
            ) ?: return handled
        return handled + handle(messages)
    }

    /** Re-delivers the entries this consumer read but never acknowledged (restart or earlier failure). */
    fun recoverOwnPending(batchSize: Int = 10): Int {
        val messages =
            streamOps.read(
                Consumer.from(m_consumerGroup, consumerName),
                StreamReadOptions.empty().count(batchSize.toLong()),
                StreamOffset.create(m_tasksStream, ReadOffset.from("0")),
            ) ?: return 0
        return handle(messages)
    }

    /**
     * Takes over entries idle for at least `REDIS_CLAIM_IDLE_MS` in other consumers (XPENDING + XCLAIM, the
     * explicit form of XAUTOCLAIM, which spring-data-redis does not expose) and processes them.
     */
    fun reclaimIdle(batchSize: Int = 100): Int {
        val pending = streamOps.pending(m_tasksStream, m_consumerGroup, Range.unbounded<String>(), batchSize.toLong())
        val stale =
            pending
                .filter { it.consumerName != consumerName && it.elapsedTimeSinceLastDelivery.toMillis() >= m_claimIdleMs }
                .map { it.id }
                .toList()
        if (stale.isEmpty()) return 0
        val claimed =
            streamOps.claim(
                m_tasksStream,
                m_consumerGroup,
                consumerName,
                XClaimOptions.minIdle(Duration.ofMillis(m_claimIdleMs)).ids(stale),
            )
        if (claimed.isNotEmpty()) log.info("Reclaimed ${claimed.size} idle task(s) from dead consumers")
        return handle(claimed)
    }

    private fun handle(messages: List<MapRecord<String, String, String>>): Int {
        for (msg in messages) {
            try {
                processMessage(msg)
            } catch (e: Exception) {
                log.error("Failed to process message id '${msg.id}', it stays pending: ${e.message}", e)
            }
        }
        return messages.size
    }

    private fun processMessage(msg: MapRecord<String, String, String>) {
        val dataStr = msg.value["data"] ?: msg.value["payload"]
        val task = dataStr?.let { m_objectMapper.parseTask(it) }
        if (task == null) {
            log.error("Discarding unreadable message id '${msg.id}'")
            acknowledge(msg.id)
            return
        }
        val attempts = streamOps.deliveryCount(m_tasksStream, m_consumerGroup, msg.id)
        if (attempts > m_maxAttempts) {
            publishFailure(msg.id, task, attempts, "Se agotaron los intentos ($m_maxAttempts)")
            return
        }
        try {
            val result = m_taskProcessor.process(task).copy(attempts = attempts)
            publish(result)
            acknowledge(msg.id)
        } catch (e: Exception) {
            if (attempts < m_maxAttempts) {
                log.warn("Attempt $attempts/$m_maxAttempts of task '${task.taskId}' failed, will retry: ${e.message}")
                throw e
            }
            log.error("Task '${task.taskId}' failed after $attempts attempts: ${e.message}", e)
            publishFailure(msg.id, task, attempts, "Falló tras $attempts intentos: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    private fun publishFailure(
        id: RecordId,
        task: TaskEvent,
        attempts: Int,
        reason: String,
    ) {
        publish(
            TaskResultEvent(
                taskId = task.taskId,
                snippetId = task.snippetId,
                type = task.type,
                status = TaskStatus.FAILED,
                contentHash = task.contentHash,
                attempts = attempts,
                errorMessage = reason,
            ),
        )
        acknowledge(id)
    }

    private fun publish(result: TaskResultEvent) {
        val record =
            StreamRecords
                .string(mapOf("data" to m_objectMapper.writeValueAsString(result)))
                .withStreamKey(m_resultsStream)
        streamOps.add(record, XAddOptions.trim(TrimOptions.maxLen(RESULTS_MAX_LEN).approximate()))
    }

    private fun acknowledge(id: RecordId) {
        streamOps.acknowledge(m_tasksStream, m_consumerGroup, id)
    }

    fun startWorkerLoop() {
        initConsumerGroup()
        running.set(true)
        workerThread =
            Thread(
                {
                    while (running.get()) {
                        try {
                            pollAndProcess()
                            // Pending entries are re-delivered at once; back off so a failing task is not hammered.
                            if (lastRecovered > 0) Thread.sleep(RETRY_BACKOFF_MS)
                        } catch (e: InterruptedException) {
                            Thread.currentThread().interrupt()
                            break
                        } catch (e: Exception) {
                            if (running.get()) {
                                log.error("Error in Redis worker loop: ${e.message}", e)
                                Thread.sleep(ERROR_BACKOFF_MS)
                            }
                        }
                    }
                },
                "redis-worker-thread",
            ).apply {
                isDaemon = true
                start()
            }
    }

    fun stopWorkerLoop() {
        running.set(false)
        workerThread?.interrupt()
        workerThread = null
    }

    companion object {
        const val DEFAULT_MAX_ATTEMPTS = 3
        const val DEFAULT_CLAIM_IDLE_MS = 60_000L
        const val RESULTS_MAX_LEN = 10_000L
        private const val MIN_RECLAIM_INTERVAL_MS = 100L
        private const val MAX_RECLAIM_INTERVAL_MS = 10_000L
        private const val RETRY_BACKOFF_MS = 1000L
        private const val ERROR_BACKOFF_MS = 1000L

        private fun hostName(): String =
            try {
                InetAddress.getLocalHost().hostName
            } catch (_: UnknownHostException) {
                System.getenv("HOSTNAME") ?: "worker-1"
            }
    }
}

private fun ObjectMapper.parseTask(data: String): TaskEvent? =
    try {
        readValue(data, TaskEvent::class.java)
    } catch (e: JacksonException) {
        LoggerFactory.getLogger(RedisTaskWorker::class.java).error("Malformed task payload: ${e.message}")
        null
    }

/** Number of times Redis has delivered the entry (XPENDING), counting the current delivery. */
private fun StreamOperations<String, String, String>.deliveryCount(
    stream: String,
    group: String,
    id: RecordId,
): Int = pending(stream, group, Range.just(id.value), 1L).firstOrNull()?.totalDeliveryCount?.toInt() ?: 1
