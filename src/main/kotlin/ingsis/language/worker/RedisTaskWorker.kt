package ingsis.language.worker

import ingsis.language.worker.events.TaskEvent
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.SmartLifecycle
import org.springframework.data.redis.connection.stream.Consumer
import org.springframework.data.redis.connection.stream.ReadOffset
import org.springframework.data.redis.connection.stream.StreamOffset
import org.springframework.data.redis.connection.stream.StreamReadOptions
import org.springframework.data.redis.core.StreamOperations
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper
import java.time.Duration
import java.util.concurrent.atomic.AtomicBoolean

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
    @Value("\${ingsis.redis.stream.consumer:worker-1}")
    private val m_consumerName: String,
) : SmartLifecycle {
    private val log = LoggerFactory.getLogger(RedisTaskWorker::class.java)
    private val running = AtomicBoolean(false)
    private var workerThread: Thread? = null

    val streamOps: StreamOperations<String, String, String>
        get() = m_redisTemplate.opsForStream()

    override fun start() {
        if (m_appMode.equals("web", ignoreCase = true)) {
            log.info("App mode is '$m_appMode'. RedisTaskWorker is disabled.")
            return
        }
        log.info("Starting RedisTaskWorker for stream '$m_tasksStream' in mode '$m_appMode'...")
        startWorkerLoop()
    }

    override fun stop() {
        stopWorkerLoop()
    }

    override fun isRunning(): Boolean = running.get()

    fun initConsumerGroup() {
        try {
            streamOps.createGroup(m_tasksStream, ReadOffset.from("0"), m_consumerGroup)
        } catch (e: Exception) {
            log.debug("Consumer group '$m_consumerGroup' already exists or stream ready: ${e.message}")
        }
    }

    fun pollAndProcess(
        batchSize: Int = 10,
        blockMs: Long = 1000,
    ): Int {
        val messages =
            streamOps.read(
                Consumer.from(m_consumerGroup, m_consumerName),
                StreamReadOptions.empty().count(batchSize.toLong()).block(Duration.ofMillis(blockMs)),
                StreamOffset.create(m_tasksStream, ReadOffset.lastConsumed()),
            ) ?: return 0

        for (msg in messages) {
            try {
                val payload = msg.value
                val dataStr = payload["data"] ?: payload["payload"]
                if (dataStr != null) {
                    val task = m_objectMapper.readValue(dataStr, TaskEvent::class.java)
                    val result = m_taskProcessor.process(task)
                    val resultJson = m_objectMapper.writeValueAsString(result)
                    streamOps.add(m_resultsStream, mapOf("data" to resultJson))
                }
            } catch (e: Exception) {
                log.error("Failed to process message id '${msg.id}': ${e.message}", e)
            } finally {
                streamOps.acknowledge(m_tasksStream, m_consumerGroup, msg.id)
            }
        }
        return messages.size
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
                        } catch (e: Exception) {
                            if (running.get()) {
                                log.error("Error in Redis worker loop: ${e.message}", e)
                                Thread.sleep(1000)
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
}
