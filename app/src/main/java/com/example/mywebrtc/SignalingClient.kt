package com.example.mywebrtc

import android.os.Handler
import android.os.Looper
import android.util.Log
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
    private val client = OkHttpClient.Builder()
        .readTimeout(3, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private var webSocket: WebSocket? = null

    fun connect() {
        val request = Request.Builder()
            .url("ws://10.0.2.2:8080/ws/$roomId") // Local server IP
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                listener.onConnected()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleMessage(text)
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                handleMessage(bytes.utf8())
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                listener.onDisconnected()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.d("WebRTC", "Error: ${response?.message}  T: ${t.message} ")
                listener.onDisconnected()
            }
        })
    }

    private fun handleMessage(message: String) {
        Log.d("WebRTC", "Received: $message")
        try {
            val json = JSONObject(message)
            when (json.getString("type")) {
                "offer" -> {
                    Log.d("WebRTC", "Processing offer...")
                    val offer = SessionDescription(
                        SessionDescription.Type.OFFER,
                        json.getString("sdp")
                    )
                    // 1. Set remote description first
                    webRTCManager.setRemoteDescription(offer)

                    // 2. Create data channel if not initiator
                    webRTCManager.createDataChannel()

                    // 3. Create answer after 500ms delay (helps with timing issues)
                    Handler(Looper.getMainLooper()).postDelayed({
                        webRTCManager.createAnswer()
                    }, 500)
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
                        json.getInt("label"),
                        json.getString("candidate")
                    )
                    webRTCManager.addIceCandidate(iceCandidate)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Log.e("Signaling", "Error handling message", e)
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
    }

    interface SignalingListener {
        fun onConnected()
        fun onDisconnected()
    }
}