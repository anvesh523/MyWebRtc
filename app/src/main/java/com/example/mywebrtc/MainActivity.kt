package com.example.mywebrtc

import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import org.webrtc.IceCandidate
import org.webrtc.SessionDescription

class MainActivity : AppCompatActivity(), WebRTCManager.SignalingListener,
    WebRTCManager.RTCListener, SignalingClient.SignalingListener {

    private lateinit var webRTCManager: WebRTCManager
    private lateinit var signalingClient: SignalingClient
    private lateinit var roomIdEditText: EditText
    private lateinit var messageEditText: EditText
    private lateinit var chatTextView: TextView
    private lateinit var connectButton: Button
    private lateinit var sendButton: Button

    private var isInitiator = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        roomIdEditText = findViewById(R.id.roomIdEditText)
        messageEditText = findViewById(R.id.messageEditText)
        chatTextView = findViewById(R.id.chatTextView)
        connectButton = findViewById(R.id.connectButton)
        sendButton = findViewById(R.id.sendButton)

        webRTCManager = WebRTCManager(this, this, this)

        connectButton.setOnClickListener {
            val roomId = roomIdEditText.text.toString()
            if (roomId.isNotEmpty()) {
                signalingClient = SignalingClient(webRTCManager, roomId, this)
                signalingClient.connect()
                isInitiator = true
            }
        }

        sendButton.setOnClickListener {
            val message = messageEditText.text.toString()
            if (message.isNotEmpty()) {
                webRTCManager.sendMessage(message)
                appendToChat("Me: $message")
                messageEditText.text.clear()
            }
        }
    }

    override fun onConnected() {
        runOnUiThread {
            appendToChat("Connected to signaling server")
            webRTCManager.initializePeerConnection()
            if (isInitiator) {
                webRTCManager.createDataChannel()
                webRTCManager.createOffer()
            }
        }
    }

    override fun onDisconnected() {
        runOnUiThread {
            appendToChat("Disconnected from signaling server")
        }
    }

    override fun onOfferCreated(offer: SessionDescription) {
        Log.d("TAG_APP", "Offer created: ${offer.description}")
        signalingClient.sendOffer(offer)
    }

    override fun onAnswerCreated(answer: SessionDescription) {
        Log.d("TAG_APP", "Answer created: ${answer.description}")
        signalingClient.sendAnswer(answer)
    }

    override fun onIceCandidateCreated(iceCandidate: IceCandidate) {
        Log.d("TAG_APP", "ICE candidate: ${iceCandidate.sdp}")
        signalingClient.sendIceCandidate(iceCandidate)
    }

    override fun onDataReceived(data: String) {
        runOnUiThread {
            appendToChat("Peer: $data")
        }
    }

    private fun appendToChat(message: String) {
        chatTextView.append("$message\n")
    }

    override fun onDestroy() {
        webRTCManager.close()
        signalingClient.disconnect()
        super.onDestroy()
    }
}