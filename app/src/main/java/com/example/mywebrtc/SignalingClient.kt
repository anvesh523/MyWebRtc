package com.example.mywebrtc

import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.*
import okio.ByteString
import org.json.JSONObject
import org.webrtc.IceCandidate
import org.webrtc.SessionDescription
import java.util.concurrent.TimeUnit

class SignalingClient(
    private val webRTCManager: WebRTCManager,
    private val roomId: String,
    private val listener: SignalingListener
) {
    private lateinit var client: OkHttpClient
    private var webSocket: WebSocket? = null
    private val coroutineScope = CoroutineScope(Dispatchers.IO)

   suspend fun connect() {
        client = OkHttpClient.Builder()
            .pingInterval(60, TimeUnit.SECONDS)
            .readTimeout(3, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()

        val request = Request.Builder().url("ws://10.0.2.2:8080/ws?room=$roomId&type=android").build()
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d("TAG_APP", "WebSocket connected successfully")
                listener.onConnected()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleMessage(text)
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                handleMessage(bytes.utf8())
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d("TAG_APP", "WebSocket closed. The reason: $reason")
                listener.onDisconnected()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.d("TAG_APP", "Error: ${response?.message}  message: ${t.message} ")
                reconnect()
            }
        })
    }

    private fun reconnect() {
        coroutineScope.launch {
            delay(2000)
            connect()
        }
    }

    private fun handleMessage(message: String) {
        Log.d("TAG_APP", "Received: $message")
        try {
            val json = JSONObject(message)
            when (json.getString("type")) {
                "offer" -> {
                    Log.d("TAG_APP", "Processing offer...")
                    val offer = SessionDescription(
                        SessionDescription.Type.OFFER,
                        json.getString("sdp")
                    )
                    webRTCManager.setRemoteDescription(offer)
                }
                "answer" -> {
                    val answer = SessionDescription(
                        SessionDescription.Type.ANSWER,
                        json.getString("sdp")
                    )
                    webRTCManager.setRemoteDescription(answer)
                }
                "candidate" -> {
                    val iceCandidate = IceCandidate(
                        json.getString("id"),
                        json.getInt("index"),
                        json.getString("candidate")
                    )
                    webRTCManager.addIceCandidate(iceCandidate)
                }
                "error" -> {
                    Log.d("TAG_APP", "Something went wrong")
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Log.e("TAG_APP", "Error handling message", e)
        }
    }

    fun sendOffer(offer: SessionDescription) {
        val json = JSONObject().apply {
            put("type", "offer")
            put("sdp", offer.description)
        }
        webSocket?.send(json.toString())
    }

    fun sendAnswer(answer: SessionDescription) {
        val json = JSONObject().apply {
            put("type", "answer")
            put("sdp", answer.description)
        }
        webSocket?.send(json.toString())
    }

    fun sendIceCandidate(iceCandidate: IceCandidate) {
        val json = JSONObject().apply {
            put("type", "candidate")
            put("id", iceCandidate.sdpMid)
            put("label", iceCandidate.sdpMLineIndex)
            put("candidate", iceCandidate.sdp)
        }
        webSocket?.send(json.toString())
    }

    fun disconnect() {
        webSocket?.close(1000, "User disconnected")
        client.apply {
            dispatcher.executorService.shutdown()
            connectionPool.evictAll()
            cache?.close()
        }
    }

    interface SignalingListener {
        fun onConnected()
        fun onDisconnected()
    }
}