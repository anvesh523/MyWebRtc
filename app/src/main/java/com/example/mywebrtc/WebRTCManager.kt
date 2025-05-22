package com.example.mywebrtc

import android.content.Context
import android.util.Log
import org.webrtc.*
import java.nio.ByteBuffer

class WebRTCManager(
    context: Context,
    private val signalingListener: SignalingListener,
) {
    private val iceServers = listOf(
        PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
        PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer(),
        PeerConnection.IceServer.builder("stun:stun2.l.google.com:19302").createIceServer(),
        PeerConnection.IceServer.builder("stun:stun3.l.google.com:19302").createIceServer()
    )
    private val peerConnectionFactory: PeerConnectionFactory
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
                createDataChannel()
            }

            override fun onSignalingChange(signalingState: PeerConnection.SignalingState?) {
                Log.d("TAG_APP", "onSignalingChange: $signalingState")
            }
            override fun onIceConnectionChange(iceConnectionState: PeerConnection.IceConnectionState?) {
                Log.d("TAG_APP", "onSignalingChange: $iceConnectionState")
            }
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
                signalingListener.onDataReceived(data)
            }
            override fun onStateChange() {
                Log.d("TAG_APP", "State: ${dataChannel?.state()}")
                when(dataChannel?.state()) {
                    DataChannel.State.OPEN -> {
                        signalingListener.onDataChannelOpen()
                    }
                    DataChannel.State.CLOSED -> {
                        signalingListener.onDataChannelClose()
                    }
                    else -> {}
                }
            }
            override fun onBufferedAmountChange(l: Long) {}
        })
    }

    fun createOffer() {
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "false"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "false"))
        }

        peerConnection?.createOffer(object : SdpObserver {
            override fun onCreateSuccess(sessionDescription: SessionDescription) {
                peerConnection?.setLocalDescription(object : SdpObserver {
                    override fun onCreateSuccess(p0: SessionDescription?) {}
                    override fun onSetSuccess() {
                        Log.d("TAG_APP", "Offer created successfully")
                        signalingListener.onOfferCreated(sessionDescription)
                    }
                    override fun onCreateFailure(p0: String?) {}
                    override fun onSetFailure(p0: String?) {}
                }, sessionDescription)
            }
            override fun onSetSuccess() {}
            override fun onCreateFailure(p0: String?) {
                Log.d("TAG_APP", "Offer create failed: $p0")
            }
            override fun onSetFailure(p0: String?) {
                Log.d("TAG_APP", "Offer set failed: $p0")
            }
        }, constraints)
    }

    fun createAnswer() {
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "false"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "false"))
        }

        peerConnection?.createAnswer(object : SdpObserver {
            override fun onCreateSuccess(sessionDescription: SessionDescription) {
                Log.d("TAG_APP", "Answer created successfully")
                setLocalDescription(sessionDescription)
            }
            override fun onSetSuccess() {}
            override fun onCreateFailure(p0: String?) {
                Log.d("TAG_APP", "Answer create failed: $p0")
            }
            override fun onSetFailure(p0: String?) {
                Log.d("TAG_APP", "Answer set failed: $p0")
            }
        }, constraints)
    }

    fun setLocalDescription(sessionDescription: SessionDescription) {
        peerConnection?.setLocalDescription(object : SdpObserver {
            override fun onCreateSuccess(p0: SessionDescription?) {}
            override fun onSetSuccess() {
                Log.d("TAG_APP", "Local description set successfully")
                signalingListener.onAnswerCreated(sessionDescription)
            }
            override fun onCreateFailure(p0: String?) {
                Log.d("TAG_APP", "Local description create failed: $p0")
            }
            override fun onSetFailure(p0: String?) {
                Log.d("TAG_APP", "Local description set failed: $p0")
            }
        }, sessionDescription)
    }

    fun setRemoteDescription(sessionDescription: SessionDescription) {
        peerConnection?.setRemoteDescription(object : SdpObserver {
            override fun onCreateSuccess(p0: SessionDescription?) {}
            override fun onSetSuccess() {
                Log.d("TAG_APP", "Successfully set remote description")
                createAnswer()
            }
            override fun onSetFailure(p0: String) {
                Log.e("TAG_APP", "remote description create failed Failed: $p0")
            }
            override fun onCreateFailure(p0: String?) {
                Log.e("TAG_APP", "remote description set failed Failed: $p0")
            }
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
        dataChannel?.close()
        dataChannel = null

        peerConnection?.close()
        peerConnection = null
    }

    interface SignalingListener {
        fun onOfferCreated(offer: SessionDescription)
        fun onAnswerCreated(answer: SessionDescription)
        fun onIceCandidateCreated(iceCandidate: IceCandidate)
        fun onDataChannelOpen()
        fun onDataChannelClose()
        fun onDataReceived(data: String)
    }
}