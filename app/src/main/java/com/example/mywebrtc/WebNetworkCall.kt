package com.example.mywebrtc

import android.os.Parcelable
import kotlinx.coroutines.flow.flow
import kotlinx.parcelize.Parcelize
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST

class WebNetworkCall {

    companion object NetworkClient {
        private val baseUrl: String get() = "https://your-log-server.com/"

        private val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        private val okHttpClient = OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .build()

        @Volatile
        private var INSTANCE: LogService? = null

        fun getClient(): LogService = INSTANCE ?: synchronized(this) {
            val instance = Retrofit.Builder().baseUrl(baseUrl)
                .client(okHttpClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build().create(LogService::class.java)
            INSTANCE = instance
            instance
        }
    }

    internal suspend fun fireLogDataNetworkCall(logEntry: LogEntry) = flow {
        runCatching {
            val response = getClient().sendLogs(logEntry)
            if (response.isSuccessful)
                emit(response)
            else
                null
        }.onFailure {
            emit(it.message)
        }
    }
}

@Parcelize
data class LogEntry(
    val timestamp: Long = 0L,
    val message: String = "",
    val attemptCount: Int = 0,
    val isCritical: Boolean = false
) : Parcelable

interface LogService {
    @POST("logs/batch")
    suspend fun sendLogs(@Body logData: LogEntry): Response<ResponseBody>
}