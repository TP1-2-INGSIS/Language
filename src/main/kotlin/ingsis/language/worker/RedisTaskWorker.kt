package ingsis.language.worker

import com.fasterxml.jackson.databind.ObjectMapper
import io.quarkus.redis.datasource.RedisDataSource
import io.quarkus.redis.datasource.stream.StreamCommands
import io.quarkus.redis.datasource.stream.XGroupCreateArgs
import io.quarkus.redis.datasource.stream.XReadGroupArgs
import io.quarkus.runtime.ShutdownEvent
import io.quarkus.runtime.StartupEvent
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.event.Observes
import org.eclipse.microprofile.config.inject.ConfigProperty
import org.jboss.logging.Logger
import java.time.Duration
import java.util.concurrent.atomic.AtomicBoolean

@ApplicationScoped
class RedisTaskWorker(
    private val m_redisDataSource: RedisDataSource,
    private val m_taskProcessor: TaskProcessor,
    private val m_objectMapper: ObjectMapper,
    @ConfigProperty(name = "ingsis.app.mode", defaultValue = "all")
    private val m_appMode: String,
    @ConfigProperty(name = "ingsis.redis.stream.tasks", defaultValue = "tasks:printscript")
    private val m_tasksStream: String,
    @ConfigProperty(name = "ingsis.redis.stream.results", defaultValue = "tasks:results")
    private val m_resultsStream: String,
    @ConfigProperty(name = "ingsis.redis.stream.group", defaultValue = "language-workers")
    private val m_consumerGroup: String,
    @ConfigProperty(name = "ingsis.redis.stream.consumer", defaultValue = "worker-1")
    private val m_consumerName: String,
) {
    private val log: Logger = Logger.getLogger(RedisTaskWorker::class.java)
    private val running = AtomicBoolean(false)
    private var workerThread: Thread? = null

    val streamCommands: StreamCommands<String, String, String>
        get() = m_redisDataSource.stream(String::class.java)

    fun onStart(
        @Observes event: StartupEvent,
    ) {
        if (m_appMode.equals("web", ignoreCase = true)) {
            log.info("App mode is '$m_appMode'. RedisTaskWorker is disabled.")
            return
        }
        log.info("Starting RedisTaskWorker for stream '$m_tasksStream' in mode '$m_appMode'...")
        startWorkerLoop()
    }

    fun onStop(
        @Observes event: ShutdownEvent,
    ) {
        stopWorkerLoop()
    }

    fun initConsumerGroup() {
        try {
            streamCommands.xgroupCreate(
                m_tasksStream,
                m_consumerGroup,
                "0",
                XGroupCreateArgs().mkstream(),
            )
        } catch (e: Exception) {
            log.debug("Consumer group '$m_consumerGroup' already exists or stream ready: ${e.message}")
        }
    }

    fun pollAndProcess(
        batchSize: Int = 10,
        blockMs: Long = 1000,
    ): Int {
        val messages =
            streamCommands.xreadgroup(
                m_consumerGroup,
                m_consumerName,
                m_tasksStream,
                ">",
                XReadGroupArgs().count(batchSize).block(Duration.ofMillis(blockMs)),
            ) ?: return 0

        for (msg in messages) {
            try {
                val dataStr = msg.payload()["data"] ?: msg.payload()["payload"]
                if (dataStr != null) {
                    val task = m_objectMapper.readValue(dataStr, ingsis.language.worker.events.TaskEvent::class.java)
                    val result = m_taskProcessor.process(task)
                    val resultJson = m_objectMapper.writeValueAsString(result)
                    streamCommands.xadd(m_resultsStream, mapOf("data" to resultJson))
                }
            } catch (e: Exception) {
                log.error("Failed to process message id '${msg.id()}': ${e.message}", e)
            } finally {
                streamCommands.xack(m_tasksStream, m_consumerGroup, msg.id())
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

    fun isRunning(): Boolean = running.get()
}
