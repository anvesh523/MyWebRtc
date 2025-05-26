package com.example.mywebrtc

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.http.Body
import retrofit2.http.POST

class WebNetworkCall {

    companion object NetworkClient {
        private val baseUrl: String get() = "https://your-log-server.com/"

        @Volatile
        private var INSTANCE: LogService? = null

        fun getClient(): LogService = INSTANCE ?: synchronized(this) {
            val instance = Retrofit.Builder().baseUrl(baseUrl)
                .build().create(LogService::class.java)
            INSTANCE = instance
            instance
        }
    }

    internal suspend fun fireLogDataNetworkCall(logEntry: LogEntry) = flow {
        getClient().sendLogs(logEntry).runCatching {
            if (isSuccessful) emit(this) else null
        }.onFailure {
            emit(it.message)
        }
    }
}

data class LogEntry(
    val timestamp: Long = 0L,
    val message: String = "",
    val attemptCount: Int = 0,
    val isCritical: Boolean = false
)

interface LogService {
    @POST("logs/batch")
    suspend fun sendLogs(@Body logData: LogEntry): Response<String>
}