package com.example.mywebrtc

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.http.Body
import retrofit2.http.POST
import java.util.concurrent.ConcurrentLinkedQueue

object LogQueueManager {
    private const val MAX_RETRIES = 3
    private const val BATCH_SIZE = 20
    private const val FLUSH_INTERVAL_MS = 2000L
    private const val MAX_QUEUE_SIZE = 1000

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val logChannel = Channel<LogEntry>(Channel.UNLIMITED)
    private val pendingLogs = ConcurrentLinkedQueue<LogEntry>()
    private val retrofit: Retrofit = Retrofit.Builder()
        .baseUrl("https://your-log-server.com/")
        .build()
    private val logService = retrofit.create(LogService::class.java)

    init {
        startLogConsumer()
        startPeriodicFlush()
    }

    fun enqueueLog(message: String, isCritical: Boolean = false) {
        if (pendingLogs.size >= MAX_QUEUE_SIZE) {
            // Remove oldest log if queue is full
            pendingLogs.poll()
        }

        pendingLogs.add(
            LogEntry(
                timestamp = System.currentTimeMillis(),
                message = message,
                attemptCount = 0,
                isCritical = isCritical
            )
        )

        // Trigger immediate processing for critical logs
        if (isCritical) {
            scope.launch {
                logChannel.send(LogEntry()) // Dummy entry to wake up consumer
            }
        }
    }

    private fun startLogConsumer() {
        scope.launch {
            for (log in logChannel) {
                val batch = mutableListOf<LogEntry>()

                // Prioritize critical logs
                val criticalLog = pendingLogs.firstOrNull { it.isCritical }
                if (criticalLog != null) {
                    batch.add(criticalLog)
                    pendingLogs.remove(criticalLog)
                } else {
                    pendingLogs.take(BATCH_SIZE).let {
                        batch.addAll(it)
                        pendingLogs.removeAll(it)
                    }
                }

                if (batch.isNotEmpty()) {
                    sendBatchWithRetry(batch)
                }
            }
        }
    }

    private fun startPeriodicFlush() {
        scope.launch {
            while (true) {
                delay(FLUSH_INTERVAL_MS)
                if (pendingLogs.isNotEmpty()) {
                    logChannel.send(LogEntry()) // Trigger flush
                }
            }
        }
    }

    private suspend fun sendBatchWithRetry(batch: List<LogEntry>) {
        val retryBatch = mutableListOf<LogEntry>()

        try {
            val response = logService.sendLogs(batch)
            if (!response.isSuccessful) {
                retryBatch.addAll(batch.filter { it.attemptCount < MAX_RETRIES })
            }
        } catch (e: Exception) {
            retryBatch.addAll(batch.filter { it.attemptCount < MAX_RETRIES })
        }

        // Requeue logs that need retrying
        retryBatch.forEach { log ->
            pendingLogs.add(log.copy(attemptCount = log.attemptCount + 1))
        }
    }
}

data class LogEntry(
    val timestamp: Long = 0L,
    val message: String = "",
    val attemptCount: Int = 0,
    val isCritical: Boolean = false
)

object WebRtcLogger {

    fun info(message: String) {
        Log.d("TAG_APP", message)
        LogQueueManager.enqueueLog(message)
    }

    fun error(e: Throwable? = null) {
        val message = e?.stackTraceToString().toString()
        Log.e("TAG_APP", message)
        LogQueueManager.enqueueLog(message)
    }
    // WebRtcLogger.e("PeerConnection", "CreateOffer failed", e)
}

interface LogService {
    @POST("logs/batch")
    suspend fun sendLogs(@Body logs: List<LogEntry>): Response<Void>
}