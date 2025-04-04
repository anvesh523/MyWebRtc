package com.example.mywebrtc

import android.content.Context
import android.util.Log
import org.webrtc.*
import java.nio.ByteBuffer

class WebRTCManager(
    context: Context,
    private val signalingListener: SignalingListener,
    private val rtcListener: RTCListener
) {
    private val peerConnectionFactory: PeerConnectionFactory
    private val iceServers = listOf(
        PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
        PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer(),
        PeerConnection.IceServer.builder("stun:stun2.l.google.com:19302").createIceServer()
    )
    private var peerConnection: PeerConnection? = null
    private var dataChannel: DataChannel? = null

    init {
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(context)
                .setEnableInternalTracer(true)
                .createInitializationOptions()
        )

        peerConnectionFactory = PeerConnectionFactory.builder()
            .createPeerConnectionFactory()
    }

    fun initializePeerConnection() {
        peerConnection = peerConnectionFactory.createPeerConnection(iceServers, object : PeerConnection.Observer {
            override fun onIceCandidate(iceCandidate: IceCandidate) {
                signalingListener.onIceCandidateCreated(iceCandidate)
            }

            override fun onDataChannel(dataChannel: DataChannel) {
                this@WebRTCManager.dataChannel = dataChannel
                dataChannel.registerObserver(object : DataChannel.Observer {
                    override fun onMessage(buffer: DataChannel.Buffer) {
                        val data = String(buffer.data.array())
                        rtcListener.onDataReceived(data)
                    }
                    override fun onStateChange() {}
                    override fun onBufferedAmountChange(l: Long) {}
                })
            }

            override fun onSignalingChange(signalingState: PeerConnection.SignalingState?) {}
            override fun onIceConnectionChange(iceConnectionState: PeerConnection.IceConnectionState?) {}
            override fun onIceConnectionReceivingChange(b: Boolean) {}
            override fun onIceCandidatesRemoved(p0: Array<out IceCandidate>?) {}
            override fun onIceGatheringChange(iceGatheringState: PeerConnection.IceGatheringState?) {}
            override fun onAddStream(mediaStream: MediaStream?) {}
            override fun onRemoveStream(mediaStream: MediaStream?) {}
            override fun onRenegotiationNeeded() {}
            override fun onAddTrack(rtpReceiver: RtpReceiver?, mediaStreams: Array<out MediaStream>?) {}
        })
    }

    fun createDataChannel() {
        val init = DataChannel.Init()
        init.ordered = true
        init.negotiated = false
        dataChannel = peerConnection?.createDataChannel("chat", init)
        dataChannel?.registerObserver(object : DataChannel.Observer {
            override fun onMessage(buffer: DataChannel.Buffer) {
                val data = String(buffer.data.array())
                rtcListener.onDataReceived(data)
            }
            override fun onStateChange() {
                Log.d("WebRTC", "State: ${dataChannel?.state()}")
            }
            override fun onBufferedAmountChange(l: Long) {}
        })
    }

    fun createOffer() {
        peerConnection?.createOffer(object : SdpObserver {
            override fun onCreateSuccess(sessionDescription: SessionDescription) {
                peerConnection?.setLocalDescription(object : SdpObserver {
                    override fun onCreateSuccess(p0: SessionDescription?) {}
                    override fun onSetSuccess() {
                        signalingListener.onOfferCreated(sessionDescription)
                    }
                    override fun onCreateFailure(p0: String?) {}
                    override fun onSetFailure(p0: String?) {}
                }, sessionDescription)
            }
            override fun onSetSuccess() {}
            override fun onCreateFailure(p0: String?) {}
            override fun onSetFailure(p0: String?) {}
        }, MediaConstraints())
    }

    fun createAnswer() {
        peerConnection?.createAnswer(object : SdpObserver {
            override fun onCreateSuccess(sessionDescription: SessionDescription) {
                Log.d("WebRTC", "Answer created successfully")
                peerConnection?.setLocalDescription(object : SdpObserver {
                    override fun onCreateSuccess(p0: SessionDescription?) {}
                    override fun onSetSuccess() {
                        Log.d("WebRTC", "Local description set successfully")
                        signalingListener.onAnswerCreated(sessionDescription)
                    }
                    override fun onCreateFailure(p0: String?) {}
                    override fun onSetFailure(p0: String?) {}
                }, sessionDescription)
            }
            override fun onSetSuccess() {}
            override fun onCreateFailure(p0: String?) {}
            override fun onSetFailure(p0: String?) {}
        }, MediaConstraints())
    }

    fun setRemoteDescription(sessionDescription: SessionDescription) {
        peerConnection?.setRemoteDescription(object : SdpObserver {
            override fun onCreateSuccess(p0: SessionDescription?) {}
            override fun onSetSuccess() {
                Log.d("WebRTC", "Successfully set remote description")
                // For answers, this is where you'd add tracks if needed
            }
            override fun onSetFailure(error: String) {
                Log.e("WebRTC", "Failed to set remote description: $error")
            }
            override fun onCreateFailure(p0: String?) {}
        }, sessionDescription)
    }

    fun addIceCandidate(iceCandidate: IceCandidate) {
        peerConnection?.addIceCandidate(iceCandidate)
    }

    fun sendMessage(message: String) {
        val buffer = ByteBuffer.wrap(message.toByteArray())
        dataChannel?.send(DataChannel.Buffer(buffer, false))
    }

    fun close() {
        peerConnection?.close()
        peerConnection = null
        dataChannel = null
    }

    interface SignalingListener {
        fun onOfferCreated(offer: SessionDescription)
        fun onAnswerCreated(answer: SessionDescription)
        fun onIceCandidateCreated(iceCandidate: IceCandidate)
    }

    interface RTCListener {
        fun onDataReceived(data: String)
    }
}